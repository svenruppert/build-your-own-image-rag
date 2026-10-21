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
package com.svenruppert.flow.views.shared;

import com.svenruppert.functional.result.Unit;
import com.svenruppert.functional.result.Result;
import com.svenruppert.imagerag.settings.SettingsService;
import com.svenruppert.imagerag.bootstrap.ServiceRegistry;
import com.svenruppert.imagerag.domain.enums.VectorBackendType;
import com.svenruppert.imagerag.ollama.OllamaConfig;
import com.svenruppert.imagerag.pipeline.IngestionPipeline;
import com.svenruppert.imagerag.service.*;

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

  public SettingsService settings() {
    return registry.getSettingsService();
  }

  public ImageCatalogService catalog() {
    return registry.getImageCatalogService();
  }

  public ImageModerationService moderation() {
    return registry.getImageModerationService();
  }

  public SearchHistoryService searchHistory() {
    return registry.getSearchHistoryService();
  }

  public PromptTestService promptTests() {
    return registry.getPromptTestService();
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

  public Result<Unit, String> deleteImage(UUID imageId, String reason) {
    return registry.deleteImage(imageId, reason);
  }

  public Result<Unit, String> restoreImage(UUID imageId, String reason) {
    return registry.restoreImage(imageId, reason);
  }

  public void rebuildVectorIndex() {
    registry.rebuildVectorIndex();
  }

  public void rebuildKeywordIndex() {
    registry.rebuildKeywordIndex();
  }
}
