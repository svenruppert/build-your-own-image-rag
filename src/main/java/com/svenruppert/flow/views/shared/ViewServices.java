package com.svenruppert.flow.views.shared;

import com.svenruppert.imagerag.bootstrap.ServiceRegistry;
import com.svenruppert.imagerag.domain.ProcessingSettings;
import com.svenruppert.imagerag.domain.enums.VectorBackendType;
import com.svenruppert.imagerag.ollama.OllamaClient;
import com.svenruppert.imagerag.ollama.OllamaConfig;
import com.svenruppert.imagerag.persistence.PersistenceService;
import com.svenruppert.imagerag.pipeline.IngestionPipeline;
import com.svenruppert.imagerag.service.*;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * View-facing access point for application services.
 * <p>Keeps Vaadin views from depending directly on the global {@link ServiceRegistry}
 * while the application still uses a manual dependency container internally.
 */
public class ViewServices {

  private final ServiceRegistry registry;
  private final ImagePreviewFactory imagePreviews;

  private ViewServices(ServiceRegistry registry) {
    this.registry = registry;
    this.imagePreviews = new ImagePreviewFactory(
        registry.getImageStorageService(), registry.getPreviewService());
  }

  public static ViewServices current() {
    return new ViewServices(ServiceRegistry.getInstance());
  }

  public ImagePreviewFactory imagePreviews() {
    return imagePreviews;
  }

  public ProcessingSettings processingSettings() {
    return registry.getProcessingSettings();
  }

  public PersistenceService persistence() {
    return registry.getPersistenceService();
  }

  public PreviewService previews() {
    return registry.getPreviewService();
  }

  public ImageStorageService imageStorage() {
    return registry.getImageStorageService();
  }

  public OllamaConfig ollamaConfig() {
    return registry.getOllamaConfig();
  }

  public OllamaClient ollamaClient() {
    return registry.getOllamaClient();
  }

  public KeywordIndexService keywordIndex() {
    return registry.getKeywordIndexService();
  }

  public PromptTemplateService promptTemplates() {
    return registry.getPromptTemplateService();
  }

  public QueryUnderstandingService queryUnderstanding() {
    return registry.getQueryUnderstandingService();
  }

  public SearchService search() {
    return registry.getSearchService();
  }

  public ExecutorService searchExecutor() {
    return registry.getSearchExecutor();
  }

  public IngestionPipeline ingestionPipeline() {
    return registry.getIngestionPipeline();
  }

  public ReprocessingService reprocessing() {
    return registry.getReprocessingService();
  }

  public AuditService audit() {
    return registry.getAuditService();
  }

  public TaxonomySuggestionService taxonomySuggestions() {
    return registry.getTaxonomySuggestionService();
  }

  public TaxonomyAnalysisService taxonomyAnalysis() {
    return registry.getTaxonomyAnalysisService();
  }

  public WhyNotFoundService whyNotFound() {
    return registry.getWhyNotFoundService();
  }

  public ClusterDiscoveryService clusterDiscovery() {
    return registry.getClusterDiscoveryService();
  }

  public SearchStrategyAutopilot searchStrategyAutopilot() {
    return registry.getSearchStrategyAutopilot();
  }

  public VectorBackendType vectorBackendType() {
    return registry.getVectorBackendType();
  }

  public void deleteImage(UUID imageId)
      throws IOException {
    registry.deleteImage(imageId);
  }

  public void restoreImage(UUID imageId) {
    registry.restoreImage(imageId);
  }

  public void rebuildVectorIndex() {
    registry.rebuildVectorIndex();
  }

  public void rebuildKeywordIndex() {
    registry.rebuildKeywordIndex();
  }

  public void updateSearchParallelism(int parallelism) {
    registry.updateSearchParallelism(parallelism);
  }
}
