package com.svenruppert.imagerag.bootstrap;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.imagerag.domain.ProcessingSettings;
import com.svenruppert.imagerag.ollama.OllamaClient;
import com.svenruppert.imagerag.ollama.OllamaConfig;
import com.svenruppert.imagerag.persistence.PersistenceService;
import com.svenruppert.imagerag.pipeline.IngestionPipeline;
import com.svenruppert.imagerag.service.*;
import com.svenruppert.imagerag.service.impl.*;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manual dependency injection container.
 * All services are singletons. Initialize once at application startup via {@link #initialize()}.
 */
public class ServiceRegistry
    implements HasLogger {

  private static volatile ServiceRegistry serviceRegistry;

  private final ProcessingSettings processingSettings;
  private final OllamaConfig ollamaConfig;
  private final OllamaClient ollamaClient;
  private final PersistenceService persistenceService;
  private final ImageStorageService imageStorageService;
  private final PreviewService previewService;
  private final MetadataExtractionService metadataExtractionService;
  private final ReverseGeocodingService reverseGeocodingService;
  private final VisionAnalysisService visionAnalysisService;
  private final SemanticDerivationService semanticDerivationService;
  private final SensitivityAssessmentService sensitivityAssessmentService;
  private final EmbeddingService embeddingService;
  private final VectorIndexService vectorIndexService;
  private final QueryUnderstandingService queryUnderstandingService;
  private final SearchService searchService;
  private final ReprocessingService reprocessingService;
  private final IngestionPipeline ingestionPipeline;

  /**
   * Shared executor for background search tasks across all UI sessions.
   * Sized by {@link ProcessingSettings#getSearchParallelism()}.
   * Resized dynamically via {@link #updateSearchParallelism(int)}.
   */
  private volatile ExecutorService searchExecutor;

  private ServiceRegistry()
      throws IOException {
    Path projectDir = Paths.get(System.getProperty("user.dir"));

    // Runtime settings (parallelism, etc.) — not persisted, reset on restart
    processingSettings = new ProcessingSettings();

    // Infrastructure
    ollamaConfig = new OllamaConfig();
    ollamaClient = new OllamaClient(ollamaConfig);

    if (!ollamaClient.isAvailable()) {
      logger().warn("Ollama is NOT reachable at {}. Vision and embedding features will degrade gracefully.",
                    ollamaConfig.baseUrl());
    } else {
      logger().info("Ollama connected at {}", ollamaConfig.baseUrl());
    }

    // Persistence — EclipseStore under <project>/_data
    persistenceService = new PersistenceService(projectDir.resolve("_data"));

    // Storage — image files under <project>/_data_images
    imageStorageService = new ImageStorageServiceImpl(projectDir.resolve("_data_images"));

    // Preview cache — scaled tile thumbnails under <project>/_data_images_previews
    previewService = new PreviewServiceImpl(projectDir.resolve("_data_images_previews"));

    // Metadata
    metadataExtractionService = new MetadataExtractionServiceImpl();
    reverseGeocodingService = new ReverseGeocodingServiceImpl();

    // AI services
    visionAnalysisService = new VisionAnalysisServiceImpl(ollamaClient, ollamaConfig);
    semanticDerivationService = new SemanticDerivationServiceImpl(ollamaClient, ollamaConfig);
    sensitivityAssessmentService = new SensitivityAssessmentServiceImpl();

    // Embedding + vector index
    embeddingService = new EmbeddingServiceImpl(ollamaClient);
    vectorIndexService = new VectorIndexServiceImpl();

    // Restore vector index from persisted analyses after restart
    restoreVectorIndex();

    // Approve all existing SAFE images (migration for data ingested before approval feature)
    migrateApprovals();

    // Rebuild hash index from any images already in the store
    // (covers images ingested before the duplicate-detection feature was added)
    persistenceService.rebuildHashIndex();

    // Search
    queryUnderstandingService = new QueryUnderstandingServiceImpl(ollamaClient);
    searchService = new SearchServiceImpl(
        queryUnderstandingService, embeddingService,
        vectorIndexService, persistenceService);

    // Shared search executor — initial size = configured search parallelism (default 1)
    searchExecutor = buildSearchExecutor(processingSettings.getSearchParallelism());

    // Async ingestion pipeline — handles uploads AND reprocessing jobs.
    // Must be constructed before reprocessingService so the latter can delegate to it.
    ingestionPipeline = new IngestionPipeline(
        imageStorageService, metadataExtractionService, reverseGeocodingService,
        visionAnalysisService, semanticDerivationService, sensitivityAssessmentService,
        embeddingService, vectorIndexService, persistenceService);

    // Reprocessing service — thin facade that delegates to the pipeline for visibility.
    // Reprocessing jobs appear in the Pipeline view just like upload jobs.
    reprocessingService = new ReprocessingServiceImpl(ingestionPipeline, persistenceService);

    logger().info("ServiceRegistry initialized. {} images in store.",
                  persistenceService.findAllImages().size());
  }

  public static ServiceRegistry getInstance() {
    if (serviceRegistry == null) {
      synchronized (ServiceRegistry.class) {
        if (serviceRegistry == null) {
          throw new IllegalStateException(
              "ServiceRegistry not initialized. Call initialize() at application startup.");
        }
      }
    }
    return serviceRegistry;
  }

  public static synchronized void initialize()
      throws IOException {
    if (serviceRegistry == null) {
      serviceRegistry = new ServiceRegistry();
    }
  }

  // -------------------------------------------------------------------------
  // Startup helper: re-embed persisted analyses into the in-memory vector index
  // -------------------------------------------------------------------------

  private void restoreVectorIndex() {
    var imageIds = persistenceService.findAllIndexedImageIds();
    logger().info("Restoring vector index for {} images...", imageIds.size());
    int restored = 0;

    for (var imageId : imageIds) {
      try {
        var analysisOpt = persistenceService.findAnalysis(imageId);
        if (analysisOpt.isEmpty()) continue;

        var analysis = analysisOpt.get();
        String embeddingText = buildEmbeddingText(analysis);
        float[] vector = embeddingService.embed(embeddingText);

        if (vector != null && vector.length > 0) {
          vectorIndexService.index(imageId, vector);
          restored++;
        }
      } catch (Exception e) {
        logger().warn("Could not restore vector for image {}: {}", imageId, e.getMessage());
      }
    }
    logger().info("Restored {}/{} vector entries", restored, imageIds.size());
  }

  /**
   * One-time migration: approve all existing images that are SAFE according to their
   * stored sensitivity assessment. Images without an assessment (or REVIEW/SENSITIVE)
   * are left locked and must be manually approved in the Overview view.
   *
   * <p>This is idempotent — already-approved images are simply added again (no-op).
   */
  private void migrateApprovals() {
    var allImages = persistenceService.findAllImages();
    int approved = 0;
    int locked = 0;
    int pending = 0;

    for (var image : allImages) {
      var assessmentOpt = persistenceService.findAssessment(image.getId());
      if (assessmentOpt.isEmpty()) {
        // No assessment yet (still in pipeline or failed) — leave locked
        pending++;
        continue;
      }
      var risk = assessmentOpt.get().getRiskLevel();
      if (risk == com.svenruppert.imagerag.domain.enums.RiskLevel.SAFE) {
        persistenceService.approveImage(image.getId());
        approved++;
      } else {
        locked++;
      }
    }

    logger().info("Approval migration complete — auto-approved: {}, locked (REVIEW/SENSITIVE): {}, pending assessment: {}",
                  approved, locked, pending);
  }

  private String buildEmbeddingText(com.svenruppert.imagerag.domain.SemanticAnalysis analysis) {
    StringBuilder sb = new StringBuilder();
    if (analysis.getSummary() != null) sb.append(analysis.getSummary());
    if (analysis.getTags() != null && !analysis.getTags().isEmpty()) {
      sb.append(" Tags: ").append(String.join(", ", analysis.getTags()));
    }
    return sb.toString();
  }

  // --- Accessors ---

  // -------------------------------------------------------------------------
  // Image management — orchestrated delete (vector index + file + persistence)
  // -------------------------------------------------------------------------

  /**
   * Completely removes an image from every data store:
   * in-memory vector index, file system, and EclipseStore.
   */
  public void deleteImage(UUID imageId)
      throws IOException {
    vectorIndexService.remove(imageId);
    imageStorageService.delete(imageId);
    persistenceService.deleteImage(imageId);
    logger().info("Fully deleted image imageId={}", imageId);
  }

  // -------------------------------------------------------------------------
  // Search executor — shared bounded pool across all UI sessions
  // -------------------------------------------------------------------------

  private static ExecutorService buildSearchExecutor(int parallelism) {
    int clamped = Math.max(1, Math.min(4, parallelism));
    if (clamped <= 1) {
      return Executors.newSingleThreadExecutor(
          Thread.ofVirtual().name("search-worker").factory());
    }
    return Executors.newFixedThreadPool(
        clamped, Thread.ofVirtual().name("search-", 0).factory());
  }

  /**
   * Returns the shared executor for search background tasks.
   * {@link com.svenruppert.flow.views.search.SearchView} submits its background tasks here
   * instead of maintaining a per-session executor.
   */
  public ExecutorService getSearchExecutor() {
    return searchExecutor;
  }

  /**
   * Resizes the shared search executor to the requested parallelism.
   * The old executor is shut down gracefully (in-flight tasks complete normally).
   * Bounded to [1, 4].
   */
  public synchronized void updateSearchParallelism(int parallelism) {
    int clamped = Math.max(1, Math.min(4, parallelism));
    ExecutorService old = searchExecutor;
    searchExecutor = buildSearchExecutor(clamped);
    old.shutdown();
    processingSettings.setSearchParallelism(clamped);
    logger().info("Search executor resized to {} worker(s)", clamped);
  }

  // --- Accessors ---

  public ProcessingSettings getProcessingSettings() {
    return processingSettings;
  }

  public PreviewService getPreviewService() {
    return previewService;
  }

  public OllamaConfig getOllamaConfig() {
    return ollamaConfig;
  }

  public OllamaClient getOllamaClient() {
    return ollamaClient;
  }

  public PersistenceService getPersistenceService() {
    return persistenceService;
  }

  public ImageStorageService getImageStorageService() {
    return imageStorageService;
  }

  public MetadataExtractionService getMetadataExtractionService() {
    return metadataExtractionService;
  }

  public ReverseGeocodingService getReverseGeocodingService() {
    return reverseGeocodingService;
  }

  public VisionAnalysisService getVisionAnalysisService() {
    return visionAnalysisService;
  }

  public SemanticDerivationService getSemanticDerivationService() {
    return semanticDerivationService;
  }

  public SensitivityAssessmentService getSensitivityAssessmentService() {
    return sensitivityAssessmentService;
  }

  public EmbeddingService getEmbeddingService() {
    return embeddingService;
  }

  public VectorIndexService getVectorIndexService() {
    return vectorIndexService;
  }

  public QueryUnderstandingService getQueryUnderstandingService() {
    return queryUnderstandingService;
  }

  public SearchService getSearchService() {
    return searchService;
  }

  public IngestionPipeline getIngestionPipeline() {
    return ingestionPipeline;
  }

  public ReprocessingService getReprocessingService() {
    return reprocessingService;
  }
}
