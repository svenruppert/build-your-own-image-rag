package com.svenruppert.imagerag.persistence;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.imagerag.domain.*;
import com.svenruppert.imagerag.domain.enums.RiskLevel;
import com.svenruppert.imagerag.domain.enums.SearchMode;
import com.svenruppert.imagerag.domain.enums.SourceCategory;
import com.svenruppert.imagerag.domain.enums.SuggestionStatus;
import com.svenruppert.imagerag.settings.SettingsStore;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * EclipseStore-backed persistence facade.
 * <p>The work is split across cohesive repositories ({@link ImageRepository},
 * {@link AnalysisRepository}, {@link VectorStore}, {@link SearchHistoryStore},
 * {@link TaxonomyStore}, {@link AuditStore}, {@link PromptVersionStore},
 * {@link SettingsOverrideStore}) that share one {@link PersistenceContext}.
 * <p>Thread-safe: every read runs under the shared read lock and every mutation together
 * with its {@code store(...)} call runs under the shared write lock, so the ingestion
 * workers, search threads and UI threads never see a collection while it is modified.
 * Collection getters return immutable copies.
 */
public class PersistenceService
    implements HasLogger, SettingsStore {

  private final PersistenceContext ctx;
  private final ImageRepository images;
  private final AnalysisRepository analyses;
  private final VectorStore vectors;
  private final SearchHistoryStore searchHistory;
  private final TaxonomyStore taxonomy;
  private final AuditStore audit;
  private final PromptVersionStore prompts;
  private final SettingsOverrideStore settingOverrides;

  public PersistenceService(Path storagePath) {
    ctx = new PersistenceContext(storagePath);
    images = new ImageRepository(ctx);
    analyses = new AnalysisRepository(ctx);
    vectors = new VectorStore(ctx);
    searchHistory = new SearchHistoryStore(ctx);
    taxonomy = new TaxonomyStore(ctx);
    audit = new AuditStore(ctx);
    prompts = new PromptVersionStore(ctx);
    settingOverrides = new SettingsOverrideStore(ctx);
    if (ctx.loadedExistingData()) {
      analyses.migrateBrokenFlagSets();
      ctx.read(() -> {
        logger().info("EclipseStore loaded existing data: {} images, {} approved, {} recent searches",
                      ctx.root().getImages().size(), ctx.root().getApprovedImageIds().size(),
                      ctx.root().getRecentSearches().size());
        return null;
      });
    }
  }

  public void shutdown() {
    ctx.shutdown();
    logger().info("EclipseStore shut down");
  }

  // ── Images, approval, archive ─────────────────────────────────────────────

  public void saveImage(ImageAsset asset) {
    images.saveImage(asset);
  }

  /**
   * Permanently removes all persisted data for the given image. Caller is responsible for
   * also removing the image file, preview cache and in-memory index entries.
   */
  public void deleteImage(UUID imageId) {
    images.deleteImage(imageId);
  }

  public void approveImage(UUID imageId) {
    images.approveImage(imageId);
  }

  public void unapproveImage(UUID imageId) {
    images.unapproveImage(imageId);
  }

  public boolean isApproved(UUID imageId) {
    return images.isApproved(imageId);
  }

  public boolean isDeleted(UUID imageId) {
    return images.isDeleted(imageId);
  }

  public Optional<ImageAsset> findImage(UUID imageId) {
    return images.findImage(imageId);
  }

  public List<ImageAsset> findAllImages() {
    return images.findAllImages();
  }

  public List<ImageAsset> findAllImagesIncludingDeleted() {
    return images.findAllImagesIncludingDeleted();
  }

  public List<ImageAsset> findArchivedImages() {
    return images.findArchivedImages();
  }

  public void softDeleteImage(UUID imageId, String reason) {
    images.softDeleteImage(imageId, reason);
  }

  public void restoreImage(UUID imageId) {
    images.restoreImage(imageId);
  }

  public Optional<UUID> findImageIdByHash(String sha256) {
    return images.findImageIdByHash(sha256);
  }

  public void registerHash(String sha256, UUID imageId) {
    images.registerHash(sha256, imageId);
  }

  public void removeHash(String sha256) {
    images.removeHash(sha256);
  }

  public void rebuildHashIndex() {
    images.rebuildHashIndex();
  }

  // ── Analysis results and corrections ──────────────────────────────────────

  public void saveMetadata(UUID imageId, ImageMetadataInfo metadata) {
    analyses.saveMetadata(imageId, metadata);
  }

  public void saveLocation(UUID imageId, LocationSummary location) {
    analyses.saveLocation(imageId, location);
  }

  public void saveAnalysis(UUID imageId, SemanticAnalysis analysis) {
    analyses.saveAnalysis(imageId, analysis);
  }

  public void saveAssessment(UUID imageId, SensitivityAssessment assessment) {
    analyses.saveAssessment(imageId, assessment);
  }

  public void saveOcrResult(UUID imageId, OcrResult result) {
    analyses.saveOcrResult(imageId, result);
  }

  public Optional<SemanticAnalysis> findAnalysis(UUID imageId) {
    return analyses.findAnalysis(imageId);
  }

  public Optional<SensitivityAssessment> findAssessment(UUID imageId) {
    return analyses.findAssessment(imageId);
  }

  public Optional<LocationSummary> findLocation(UUID imageId) {
    return analyses.findLocation(imageId);
  }

  public Optional<ImageMetadataInfo> findMetadata(UUID imageId) {
    return analyses.findMetadata(imageId);
  }

  public Optional<OcrResult> findOcrResult(UUID imageId) {
    return analyses.findOcrResult(imageId);
  }

  public void updateRiskLevel(UUID imageId, RiskLevel newLevel) {
    analyses.updateRiskLevel(imageId, newLevel);
  }

  public void updateSourceCategory(UUID imageId, SourceCategory newCategory) {
    analyses.updateSourceCategory(imageId, newCategory);
  }

  public void updateSecondaryCategories(UUID imageId, List<SourceCategory> categories) {
    analyses.updateSecondaryCategories(imageId, categories);
  }

  /**
   * @return number of analyses whose category assignment was normalized
   */
  public int normalizeAllCategoryAssignments() {
    return analyses.normalizeAllCategoryAssignments();
  }

  // ── Vectors ───────────────────────────────────────────────────────────────

  public void saveVectorEntry(UUID imageId, VectorEntry entry) {
    vectors.saveVectorEntry(imageId, entry);
  }

  public Optional<VectorEntry> findVectorEntry(UUID imageId) {
    return vectors.findVectorEntry(imageId);
  }

  public List<UUID> findAllIndexedImageIds() {
    return vectors.findAllIndexedImageIds();
  }

  public void saveRawVector(UUID imageId, float[] vector) {
    vectors.saveRawVector(imageId, vector);
  }

  public void removeRawVector(UUID imageId) {
    vectors.removeRawVector(imageId);
  }

  public Map<UUID, float[]> findAllRawVectors() {
    return vectors.findAllRawVectors();
  }

  public int getRawVectorCount() {
    return vectors.getRawVectorCount();
  }

  public Optional<float[]> findRawVector(UUID imageId) {
    return vectors.findRawVector(imageId);
  }

  // ── Search history, saved views, tuning presets ───────────────────────────

  public void addRecentSearch(String query, String finalQuery, SearchMode mode) {
    searchHistory.addRecentSearch(query, finalQuery, mode);
  }

  public void deleteRecentSearches(List<RecentSearchEntry> toDelete) {
    searchHistory.deleteRecentSearches(toDelete);
  }

  public List<RecentSearchEntry> getRecentSearches() {
    return searchHistory.getRecentSearches();
  }

  public void clearRecentSearches() {
    searchHistory.clearRecentSearches();
  }

  public void saveSavedSearchView(SavedSearchView view) {
    searchHistory.saveSavedSearchView(view);
  }

  public List<SavedSearchView> getSavedSearchViews() {
    return searchHistory.getSavedSearchViews();
  }

  public void deleteSavedSearchView(UUID viewId) {
    searchHistory.deleteSavedSearchView(viewId);
  }

  public void saveTuningPreset(SearchTuningPreset preset) {
    searchHistory.saveTuningPreset(preset);
  }

  public List<SearchTuningPreset> findAllTuningPresets() {
    return searchHistory.findAllTuningPresets();
  }

  public void deleteTuningPreset(UUID presetId) {
    searchHistory.deleteTuningPreset(presetId);
  }

  // ── Taxonomy maintenance ──────────────────────────────────────────────────

  public void saveTaxonomySuggestion(TaxonomySuggestion suggestion) {
    taxonomy.saveTaxonomySuggestion(suggestion);
  }

  public List<TaxonomySuggestion> findAllSuggestions() {
    return taxonomy.findAllSuggestions();
  }

  public List<TaxonomySuggestion> findSuggestionsByStatus(SuggestionStatus status) {
    return taxonomy.findSuggestionsByStatus(status);
  }

  public Optional<TaxonomySuggestion> findSuggestion(UUID suggestionId) {
    return taxonomy.findSuggestion(suggestionId);
  }

  public void clearTaxonomySuggestions() {
    taxonomy.clearTaxonomySuggestions();
  }

  public void saveCategoryMetadata(CategoryMetadata meta) {
    taxonomy.saveCategoryMetadata(meta);
  }

  public Optional<CategoryMetadata> findCategoryMetadata(SourceCategory category) {
    return taxonomy.findCategoryMetadata(category);
  }

  public List<CategoryMetadata> findAllCategoryMetadata() {
    return taxonomy.findAllCategoryMetadata();
  }

  public void saveClusterSuggestion(CategoryClusterSuggestion suggestion) {
    taxonomy.saveClusterSuggestion(suggestion);
  }

  public List<CategoryClusterSuggestion> findAllClusterSuggestions() {
    return taxonomy.findAllClusterSuggestions();
  }

  public void replaceAllClusterSuggestions(List<CategoryClusterSuggestion> suggestions) {
    taxonomy.replaceAllClusterSuggestions(suggestions);
  }

  public void deleteClusterSuggestion(UUID id) {
    taxonomy.deleteClusterSuggestion(id);
  }

  // ── Audit log ─────────────────────────────────────────────────────────────

  public void addAuditEntry(AuditEntry entry) {
    audit.addAuditEntry(entry);
  }

  public List<AuditEntry> getAuditLog() {
    return audit.getAuditLog();
  }

  // ── Prompt template versions ──────────────────────────────────────────────

  public void savePromptVersion(PromptTemplateVersion version) {
    prompts.savePromptVersion(version);
  }

  public List<PromptTemplateVersion> findPromptVersions(String promptKey) {
    return prompts.findPromptVersions(promptKey);
  }

  public List<PromptTemplateVersion> findAllPromptVersions() {
    return prompts.findAllPromptVersions();
  }

  public Optional<PromptTemplateVersion> findActivePromptVersion(String promptKey) {
    return prompts.findActivePromptVersion(promptKey);
  }

  public void deletePromptVersion(UUID id) {
    prompts.deletePromptVersion(id);
  }

  // ── Runtime setting overrides (SettingsStore) ─────────────────────────────

  @Override
  public Map<String, String> loadSettingOverrides() {
    return settingOverrides.loadSettingOverrides();
  }

  @Override
  public void putSettingOverride(String key, String value) {
    settingOverrides.putSettingOverride(key, value);
  }

  @Override
  public void removeSettingOverride(String key) {
    settingOverrides.removeSettingOverride(key);
  }

  @Override
  public void clearSettingOverrides() {
    settingOverrides.clearSettingOverrides();
  }
}
