package com.svenruppert.imagerag.bootstrap;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.imagerag.domain.ProcessingSettings;
import com.svenruppert.imagerag.domain.enums.VectorBackendType;
import com.svenruppert.imagerag.ollama.OllamaClient;
import com.svenruppert.imagerag.ollama.OllamaConfig;
import com.svenruppert.imagerag.persistence.PersistenceService;
import com.svenruppert.imagerag.pipeline.IngestionPipeline;
import com.svenruppert.imagerag.service.*;
import com.svenruppert.imagerag.service.impl.*;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
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
  /** Which vector backend was selected at startup — exposed for diagnostics / UI. */
  private final VectorBackendType vectorBackendType;
  private final KeywordIndexService keywordIndexService;
  private final QueryUnderstandingService queryUnderstandingService;
  private final SearchService searchService;
  private final ReprocessingService reprocessingService;
  private final IngestionPipeline ingestionPipeline;
  private final AuditService auditService;

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

    // Embedding service
    embeddingService = new EmbeddingServiceImpl(ollamaClient);

    // ── Vector backend selection ──────────────────────────────────────────
    // Controlled by imagerag.properties (classpath): vector.backend=gigamap-jvector
    // A JVM system property -Dvector.backend=... overrides the file value.
    // Absence or any other value falls back to the existing in-memory backend.
    vectorBackendType = VectorBackendType.fromConfig();
    logger().info("Vector backend selected: {}", vectorBackendType);

    switch (vectorBackendType) {
      case GIGAMAP_JVECTOR -> {
        var jvectorBackend = new EclipseStoreGigaMapJVectorBackend(persistenceService);
        vectorIndexService = jvectorBackend;
        // If no raw vectors have been persisted yet (first run with this backend,
        // or first switch from IN_MEMORY), migrate by re-embedding from analysis text.
        if (jvectorBackend.isEmpty() && !persistenceService.findAllIndexedImageIds().isEmpty()) {
          logger().info("GIGAMAP_JVECTOR backend: no persisted raw vectors found "
                        + "— running one-time migration by re-embedding existing analyses.");
          migrateToGigaMapJVector(jvectorBackend);
        }
        // Load persisted vectors and build the JVector HNSW index (no Ollama needed).
        jvectorBackend.initialize();
      }
      default -> {
        // IN_MEMORY backend: existing implementation, re-embed on each restart.
        vectorIndexService = new VectorIndexServiceImpl();
        restoreVectorIndex();
      }
    }

    // Keyword index — BM25 via Lucene, stored on disk
    try {
      keywordIndexService = new KeywordIndexServiceImpl(projectDir.resolve("_data_keyword_index"));
      restoreKeywordIndex();
    } catch (IOException e) {
      throw new IOException("Failed to initialize keyword index: " + e.getMessage(), e);
    }

    // Audit service
    auditService = new AuditServiceImpl(persistenceService);

    // Approve all existing SAFE images (migration for data ingested before approval feature)
    migrateApprovals();

    // Rebuild hash index from any images already in the store
    // (covers images ingested before the duplicate-detection feature was added)
    persistenceService.rebuildHashIndex();

    // Search
    queryUnderstandingService = new QueryUnderstandingServiceImpl(ollamaClient);
    searchService = new SearchServiceImpl(
        queryUnderstandingService, embeddingService,
        vectorIndexService, persistenceService, keywordIndexService);

    // Shared search executor — initial size = configured search parallelism (default 1)
    searchExecutor = buildSearchExecutor(processingSettings.getSearchParallelism());

    // Async ingestion pipeline — handles uploads AND reprocessing jobs.
    // Must be constructed before reprocessingService so the latter can delegate to it.
    ingestionPipeline = new IngestionPipeline(
        imageStorageService, metadataExtractionService, reverseGeocodingService,
        visionAnalysisService, semanticDerivationService, sensitivityAssessmentService,
        embeddingService, vectorIndexService, persistenceService,
        keywordIndexService, ollamaConfig);

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

  /**
   * Releases all resources held by the singleton:
   * <ul>
   *   <li>Stops the ingestion pipeline executor (interrupts in-flight jobs).</li>
   *   <li>Shuts down the shared search executor.</li>
   *   <li>Closes the Lucene {@code IndexWriter} (releases the on-disk file lock).</li>
   *   <li>Shuts down EclipseStore (flushes and closes storage channels).</li>
   * </ul>
   *
   * <p>Must be called when the servlet context is destroyed (e.g. hot-reload or
   * server stop) to prevent {@link java.nio.channels.OverlappingFileLockException}
   * when a new instance is created in the same JVM process.  After this call the
   * singleton reference is cleared so {@link #initialize()} can create a fresh
   * instance.
   */
  public static synchronized void shutdown() {
    if (serviceRegistry == null) {
      return;
    }
    ServiceRegistry sr = serviceRegistry;
    serviceRegistry = null;          // clear first so getInstance() fails fast during teardown

    try {
      sr.ingestionPipeline.shutdown();
    } catch (Exception e) {
      sr.logger().warn("Error shutting down ingestion pipeline: {}", e.getMessage());
    }
    try {
      sr.searchExecutor.shutdownNow();
    } catch (Exception e) {
      sr.logger().warn("Error shutting down search executor: {}", e.getMessage());
    }
    try {
      sr.keywordIndexService.close();
    } catch (Exception e) {
      sr.logger().warn("Error closing Lucene keyword index: {}", e.getMessage());
    }
    try {
      sr.persistenceService.shutdown();
    } catch (Exception e) {
      sr.logger().warn("Error shutting down EclipseStore: {}", e.getMessage());
    }
    sr.logger().info("ServiceRegistry shut down cleanly.");
  }

  // -------------------------------------------------------------------------
  // Startup helper: re-embed persisted analyses into the in-memory vector index
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
   * Restores the IN_MEMORY vector index by re-embedding all persisted analyses.
   * Called only for the {@link VectorBackendType#IN_MEMORY} backend.
   * Requires Ollama to be available.
   */
  private void restoreVectorIndex() {
    var imageIds = persistenceService.findAllIndexedImageIds();
    logger().info("Restoring in-memory vector index for {} images...", imageIds.size());
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
    logger().info("Restored {}/{} vector entries into in-memory index", restored, imageIds.size());
  }

  /**
   * One-time migration: re-embeds all existing analysed images and persists their
   * raw vectors to the EclipseStore {@code rawVectorStore} (AppDataRoot) so the
   * {@link VectorBackendType#GIGAMAP_JVECTOR} backend can rebuild its JVector index
   * at subsequent restarts <em>without</em> calling Ollama.
   *
   * <p>This runs only once — when switching from the in-memory backend to the
   * GigaMap/JVector backend for the first time.  Subsequent restarts skip this
   * step because {@link EclipseStoreGigaMapJVectorBackend#isEmpty()} returns
   * {@code false} once any vectors have been persisted.
   */
  private void migrateToGigaMapJVector(EclipseStoreGigaMapJVectorBackend backend) {
    var imageIds = persistenceService.findAllIndexedImageIds();
    logger().info("Migrating {} images to EclipseStore GigaMap raw-vector store...",
                  imageIds.size());
    int migrated = 0;

    for (var imageId : imageIds) {
      try {
        var analysisOpt = persistenceService.findAnalysis(imageId);
        if (analysisOpt.isEmpty()) continue;

        String embeddingText = buildEmbeddingText(analysisOpt.get());
        float[] vector = embeddingService.embed(embeddingText);

        if (vector != null && vector.length > 0) {
          // Persist directly via persistenceService — the backend.index() call
          // would trigger a full rebuild after every image; here we batch-persist
          // and let initialize() build the index once at the end.
          persistenceService.saveRawVector(imageId, vector);
          migrated++;
        }
      } catch (Exception e) {
        logger().warn("Could not migrate vector for image {}: {}", imageId, e.getMessage());
      }
    }
    logger().info("Migration complete: {}/{} vectors written to EclipseStore GigaMap",
                  migrated, imageIds.size());
  }

  private void restoreKeywordIndex() {
    logger().info("Restoring keyword index from persisted analyses...");
    int indexed = 0;
    for (var asset : persistenceService.findAllImages()) {
      try {
        var analysis = persistenceService.findAnalysis(asset.getId()).orElse(null);
        var location = persistenceService.findLocation(asset.getId()).orElse(null);
        var ocr = persistenceService.findOcrResult(asset.getId()).orElse(null);
        String summary = analysis != null ? analysis.getSummary() : null;
        List<String> tags = analysis != null && analysis.getTags() != null ? analysis.getTags() : List.of();
        String catLabel = analysis != null && analysis.getSourceCategory() != null ? analysis.getSourceCategory().name() : null;
        String locText = location != null ? location.toHumanReadable() : null;
        String ocrText = ocr != null ? ocr.getExtractedText() : null;
        keywordIndexService.index(asset.getId(), asset.getOriginalFilename(), summary,
                                  tags, catLabel, locText, ocrText);
        indexed++;
      } catch (Exception e) {
        logger().warn("Could not restore keyword index for image {}: {}", asset.getId(), e.getMessage());
      }
    }
    logger().info("Keyword index restored: {} documents", indexed);
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

  // --- Accessors ---

  // -------------------------------------------------------------------------
  // Image management — orchestrated delete (vector index + file + persistence)
  // -------------------------------------------------------------------------

  private String buildEmbeddingText(com.svenruppert.imagerag.domain.SemanticAnalysis analysis) {
    StringBuilder sb = new StringBuilder();
    if (analysis.getSummary() != null) sb.append(analysis.getSummary());
    if (analysis.getTags() != null && !analysis.getTags().isEmpty()) {
      sb.append(" Tags: ").append(String.join(", ", analysis.getTags()));
    }
    return sb.toString();
  }

  // -------------------------------------------------------------------------
  // Search executor — shared bounded pool across all UI sessions
  // -------------------------------------------------------------------------

  /**
   * Completely removes an image from every data store:
   * in-memory vector index, file system, and EclipseStore.
   */
  public void deleteImage(UUID imageId)
      throws IOException {
    vectorIndexService.remove(imageId);
    keywordIndexService.remove(imageId);
    imageStorageService.delete(imageId);
    persistenceService.deleteImage(imageId);
    logger().info("Fully deleted image imageId={}", imageId);
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

  /**
   * Returns the vector backend type that was selected at startup.
   * Useful for diagnostics, UI indicators, or conditional rebuild logic.
   */
  public VectorBackendType getVectorBackendType() {
    return vectorBackendType;
  }

  public KeywordIndexService getKeywordIndexService() {
    return keywordIndexService;
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

  public AuditService getAuditService() {
    return auditService;
  }
}
