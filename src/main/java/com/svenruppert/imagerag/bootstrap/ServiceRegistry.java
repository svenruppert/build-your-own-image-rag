/*
 * Copyright © 2026 Sven Ruppert (sven.ruppert@gmail.com)
 *
 * Licensed under the EUPL, Version 1.2 (the "Licence");
 * you may not use this file except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *     https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */
package com.svenruppert.imagerag.bootstrap;

import com.svenruppert.functional.result.Unit;
import com.svenruppert.functional.result.Result;
import com.svenruppert.imagerag.domain.enums.AuditAction;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.imagerag.domain.enums.VectorBackendType;
import com.svenruppert.imagerag.ollama.OllamaClient;
import com.svenruppert.imagerag.ollama.OllamaConfig;
import com.svenruppert.imagerag.maintenance.ImageLifecycleService;
import com.svenruppert.imagerag.maintenance.KeywordIndexMaintenance;
import com.svenruppert.imagerag.maintenance.StartupMigrations;
import com.svenruppert.imagerag.maintenance.VectorIndexMaintenance;
import com.svenruppert.imagerag.persistence.PersistenceService;
import com.svenruppert.imagerag.pipeline.IngestionPipeline;
import com.svenruppert.imagerag.service.*;
import com.svenruppert.imagerag.service.impl.*;
import com.svenruppert.imagerag.settings.SettingChange;
import com.svenruppert.imagerag.settings.Settings;
import com.svenruppert.imagerag.settings.SettingsService;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manual dependency injection container.
 * All services are singletons. Initialize once at application startup via {@link #initialize()}.
 * <p>This class only wires services together. Startup migrations, index maintenance and
 * the delete/restore orchestration live in {@code com.svenruppert.imagerag.maintenance}.
 */
public class ServiceRegistry
    implements HasLogger {


  private static volatile ServiceRegistry serviceRegistry;

  private final SettingsService settingsService;
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
  /**
   * Which vector backend was selected at startup — exposed for diagnostics / UI.
   */
  private final VectorBackendType vectorBackendType;
  private final KeywordIndexService keywordIndexService;
  private final QueryUnderstandingService queryUnderstandingService;
  private final SearchService searchService;
  private final ReprocessingService reprocessingService;
  private final IngestionPipeline ingestionPipeline;
  private final AuditService auditService;
  private final TaxonomySuggestionService taxonomySuggestionService;
  private final TaxonomyAnalysisService taxonomyAnalysisService;
  private final PromptTemplateService promptTemplateService;
  private final WhyNotFoundService whyNotFoundService;
  private final ClusterDiscoveryService clusterDiscoveryService;
  private final ImageCatalogService imageCatalogService;
  private final ImageModerationService imageModerationService;
  private final SearchHistoryService searchHistoryService;
  private final PromptTestService promptTestService;
  private final SearchStrategyAutopilot searchStrategyAutopilot;
  private final VectorIndexMaintenance vectorIndexMaintenance;
  private final KeywordIndexMaintenance keywordIndexMaintenance;
  private final ImageLifecycleService imageLifecycleService;

  /**
   * Shared executor for background search tasks across all UI sessions.
   * Sized by {@link Settings#SEARCH_PARALLELISM} and resized whenever that setting changes.
   */
  private volatile ExecutorService searchExecutor;

  private ServiceRegistry()
      throws IOException {
    Path projectDir = Paths.get(System.getProperty("user.dir"));

    // Infrastructure
    ollamaConfig = new OllamaConfig();
    ollamaClient = new OllamaClient(ollamaConfig);

    if (!ollamaClient.isAvailable()) {
      logger().warn("Ollama is NOT reachable at {}. Vision and embedding features will degrade gracefully.", ollamaConfig.baseUrl());
    } else {
      logger().info("Ollama connected at {}", ollamaConfig.baseUrl());
    }

    // Persistence — EclipseStore under <project>/_data
    persistenceService = new PersistenceService(projectDir.resolve("_data"));

    // Runtime settings — user overrides are persisted in EclipseStore
    settingsService = SettingsService.getInstance();
    settingsService.attachStore(persistenceService);

    // Storage — image files under <project>/_data_images
    imageStorageService = new ImageStorageServiceImpl(projectDir.resolve("_data_images"));

    // Preview cache — scaled tile thumbnails under <project>/_data_images_previews
    previewService = new PreviewServiceImpl(projectDir.resolve("_data_images_previews"));

    // Metadata
    metadataExtractionService = new MetadataExtractionServiceImpl();
    reverseGeocodingService = new ReverseGeocodingServiceImpl();

    // AI services
    var visionAnalysis = new VisionAnalysisServiceImpl(ollamaClient, ollamaConfig);
    var semanticDerivation = new SemanticDerivationServiceImpl(ollamaClient, ollamaConfig);
    visionAnalysisService = visionAnalysis;
    semanticDerivationService = semanticDerivation;
    sensitivityAssessmentService = new SensitivityAssessmentServiceImpl();

    // Embedding service
    embeddingService = new EmbeddingServiceImpl(ollamaClient);

    // ── Vector backend selection ──────────────────────────────────────────
    // Controlled by imagerag.properties (classpath): vector.backend=gigamap-jvector
    // A JVM system property -Dvector.backend=... overrides the file value.
    // Absence or any other value falls back to the existing in-memory backend.
    vectorBackendType = AppConfig.getInstance().getVectorBackendType();
    logger().info("Vector backend selected: {}", vectorBackendType);

    vectorIndexService = (vectorBackendType == VectorBackendType.GIGAMAP_JVECTOR)
        ? new EclipseStoreGigaMapJVectorBackend(persistenceService)
        : new VectorIndexServiceImpl();
    vectorIndexMaintenance = new VectorIndexMaintenance(
        persistenceService, embeddingService, vectorIndexService, ollamaConfig, vectorBackendType);
    vectorIndexMaintenance.prepareAtStartup();

    // Keyword index — BM25 via Lucene, stored on disk
    try {
      keywordIndexService = new KeywordIndexServiceImpl(projectDir.resolve("_data_keyword_index"));
    } catch (IOException e) {
      throw new IOException("Failed to initialize keyword index: " + e.getMessage(), e);
    }
    keywordIndexMaintenance = new KeywordIndexMaintenance(persistenceService, keywordIndexService);
    keywordIndexMaintenance.restoreAll();

    // Audit service
    auditService = new AuditServiceImpl(persistenceService);
    imageLifecycleService = new ImageLifecycleService(
        persistenceService, vectorIndexService, keywordIndexService, keywordIndexMaintenance,
        previewService, imageStorageService, auditService);


    new StartupMigrations(persistenceService).run();

    // Search
    queryUnderstandingService = new QueryUnderstandingServiceImpl(ollamaClient);
    searchService = new SearchServiceImpl(queryUnderstandingService, embeddingService, vectorIndexService, persistenceService, keywordIndexService);

    // Shared search executor — initial size = configured search parallelism
    searchExecutor = buildSearchExecutor(settingsService.get(Settings.SEARCH_PARALLELISM));

    // Async ingestion pipeline — handles uploads AND reprocessing jobs.
    // Must be constructed before reprocessingService so the latter can delegate to it.
    ingestionPipeline = new IngestionPipeline(imageStorageService, metadataExtractionService, reverseGeocodingService, visionAnalysisService, semanticDerivationService, sensitivityAssessmentService, embeddingService, vectorIndexService, persistenceService, keywordIndexService, ollamaConfig);
    ingestionPipeline.updateParallelism(settingsService.get(Settings.PIPELINE_INGESTION_PARALLELISM));

    // Reprocessing service — thin facade that delegates to the pipeline for visibility.
    // Reprocessing jobs appear in the Pipeline view just like upload jobs.
    reprocessingService = new ReprocessingServiceImpl(ingestionPipeline, persistenceService);

    // Taxonomy maintenance services
    taxonomySuggestionService = new TaxonomySuggestionServiceImpl(persistenceService);
    taxonomyAnalysisService = new TaxonomyAnalysisServiceImpl(persistenceService, ollamaClient, ollamaConfig);

    // Prompt template service — manages versioned prompt overrides
    promptTemplateService = new PromptTemplateServiceImpl(persistenceService);

    // Import built-in prompts on first startup (idempotent)
    promptTemplateService.importBuiltInIfAbsent(VisionAnalysisServiceImpl.PROMPT_KEY, VisionAnalysisServiceImpl.PROMPT_VERSION, VisionAnalysisServiceImpl.VISION_PROMPT);
    promptTemplateService.importBuiltInIfAbsent(VisionAnalysisServiceImpl.OCR_PROMPT_KEY, VisionAnalysisServiceImpl.OCR_PROMPT_VERSION, VisionAnalysisServiceImpl.OCR_PROMPT);
    promptTemplateService.importBuiltInIfAbsent(SemanticDerivationServiceImpl.PROMPT_KEY, SemanticDerivationServiceImpl.DERIVATION_PROMPT_VERSION, SemanticDerivationServiceImpl.DERIVATION_PROMPT_TEMPLATE);

    // Wire PromptTemplateService into analysis services
    visionAnalysis.setPromptTemplateService(promptTemplateService);
    semanticDerivation.setPromptTemplateService(promptTemplateService);

    // Why-not-found diagnostic service
    whyNotFoundService = new WhyNotFoundServiceImpl(persistenceService, embeddingService, vectorIndexService, keywordIndexService);

    // Cluster discovery service — k-means on raw vectors
    clusterDiscoveryService = new ClusterDiscoveryServiceImpl(persistenceService);

    // UI-facing application services — views never touch persistence or Ollama directly
    imageCatalogService = new ImageCatalogServiceImpl(persistenceService);
    imageModerationService = new ImageModerationServiceImpl(persistenceService, auditService);
    searchHistoryService = new SearchHistoryServiceImpl(persistenceService);
    promptTestService = new PromptTestServiceImpl(ollamaClient, persistenceService, imageStorageService);

    // Search-strategy autopilot — heuristic advisor (stateless, no constructor args)
    searchStrategyAutopilot = new SearchStrategyAutopilot();

    settingsService.addListener(this::onSettingChanged);

    logger().info("ServiceRegistry initialized. {} images in store.", persistenceService.findAllImages().size());
  }

  public static ServiceRegistry getInstance() {
    if (serviceRegistry == null) {
      synchronized (ServiceRegistry.class) {
        if (serviceRegistry == null) {
          throw new IllegalStateException("ServiceRegistry not initialized. Call initialize() at application startup.");
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

    sr.shutdownStep("ingestion pipeline", sr.ingestionPipeline::shutdown);
    sr.shutdownStep("search executor", sr.searchExecutor::shutdownNow);
    sr.shutdownStep("Lucene keyword index", sr.keywordIndexService::close);
    SettingsService.getInstance().detach();
    sr.shutdownStep("EclipseStore", sr.persistenceService::shutdown);
    sr.logger().info("ServiceRegistry shut down cleanly.");
  }

  private void shutdownStep(String component, Runnable step) {
    try {
      step.run();
    } catch (RuntimeException e) {
      // Teardown boundary: one failing component must not prevent the others from
      // being released (file locks, executor threads).
      logger().warn("Error shutting down {}: {}", component, e.getMessage(), e);
    }
  }

  private static ExecutorService buildSearchExecutor(int parallelism) {
    int clamped = Math.clamp(parallelism, Settings.SEARCH_PARALLELISM.min(), Settings.SEARCH_PARALLELISM.max());
    if (clamped <= 1) {
      return Executors.newSingleThreadExecutor(Thread.ofVirtual().name("search-worker").factory());
    }
    return Executors.newFixedThreadPool(clamped, Thread.ofVirtual().name("search-", 0).factory());
  }

  /**
   * Completely removes an image from every data store:
   * in-memory vector index, keyword index, image file, preview cache, and EclipseStore.
   * <p>This is a permanent, irreversible operation.  The caller (UI) is responsible
   * for confirming with the user before invoking this method.
   */
  public Result<Unit, String> deleteImage(UUID imageId, String reason) {
    return imageLifecycleService.delete(imageId, reason);
  }

  /**
   * Restores a previously archived (soft-deleted) image to the active dataset.
   * See {@link ImageLifecycleService#restore(UUID, String)}.
   */
  public Result<Unit, String> restoreImage(UUID imageId, String reason) {
    return imageLifecycleService.restore(imageId, reason);
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
   */
  private synchronized void resizeSearchExecutor(int parallelism) {
    ExecutorService old = searchExecutor;
    searchExecutor = buildSearchExecutor(parallelism);
    old.shutdown();
    logger().info("Search executor resized to {} worker(s)", parallelism);
  }

  /**
   * Applies settings that need active reconfiguration and records every change
   * in the audit log.
   */
  private void onSettingChanged(SettingChange change) {
    if (change.setting() == Settings.SEARCH_PARALLELISM) {
      resizeSearchExecutor(settingsService.get(Settings.SEARCH_PARALLELISM));
    } else if (change.setting() == Settings.PIPELINE_INGESTION_PARALLELISM) {
      ingestionPipeline.updateParallelism(settingsService.get(Settings.PIPELINE_INGESTION_PARALLELISM));
    }
    auditService.log(change.reset() ? AuditAction.SETTING_RESET : AuditAction.SETTING_CHANGED, null, null,
                     change.setting().key() + ": " + change.oldValue() + " -> " + change.newValue());
  }

  // --- Accessors ---

  public SettingsService getSettingsService() {
    return settingsService;
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

  public TaxonomySuggestionService getTaxonomySuggestionService() {
    return taxonomySuggestionService;
  }

  public TaxonomyAnalysisService getTaxonomyAnalysisService() {
    return taxonomyAnalysisService;
  }

  public PromptTemplateService getPromptTemplateService() {
    return promptTemplateService;
  }

  public WhyNotFoundService getWhyNotFoundService() {
    return whyNotFoundService;
  }

  public ClusterDiscoveryService getClusterDiscoveryService() {
    return clusterDiscoveryService;
  }

  public ImageCatalogService getImageCatalogService() {
    return imageCatalogService;
  }

  public ImageModerationService getImageModerationService() {
    return imageModerationService;
  }

  public SearchHistoryService getSearchHistoryService() {
    return searchHistoryService;
  }

  public PromptTestService getPromptTestService() {
    return promptTestService;
  }

  public SearchStrategyAutopilot getSearchStrategyAutopilot() {
    return searchStrategyAutopilot;
  }

  // -------------------------------------------------------------------------
  // Index rebuilds — explicit maintenance actions triggered from the UI
  // -------------------------------------------------------------------------

  /**
   * Re-embeds all analysed images and rebuilds the active vector index.
   * Blocks until done; call it on a background thread.
   */
  public void rebuildVectorIndex() {
    vectorIndexMaintenance.rebuild();
  }

  /**
   * Rebuilds the Lucene keyword (BM25) index from persisted analyses.
   * Blocks until done; call it on a background thread.
   */
  public void rebuildKeywordIndex() {
    keywordIndexMaintenance.rebuild();
  }
}
