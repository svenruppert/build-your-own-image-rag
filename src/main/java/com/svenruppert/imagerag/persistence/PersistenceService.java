package com.svenruppert.imagerag.persistence;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.imagerag.domain.*;
import com.svenruppert.imagerag.domain.enums.*;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.List.*;

public class PersistenceService
    implements HasLogger {

  private static final int MAX_RECENT_SEARCHES = 20;
  private final EmbeddedStorageManager storage;
  private final AppDataRoot root;

  // -------------------------------------------------------------------------
  // Save operations
  // -------------------------------------------------------------------------

  public PersistenceService(Path storagePath) {
    AppDataRoot candidate = new AppDataRoot();
    storage = EmbeddedStorage.start(candidate, storagePath);
    Object loadedRoot = storage.root();
    if (loadedRoot instanceof AppDataRoot existingRoot) {
      root = existingRoot;
      // EclipseStore reconstructs objects via Unsafe, bypassing constructors.
      // Any field added after the first persistence run will be null in loaded data.
      // Calling getApprovedImageIds() triggers lazy init (creates the HashSet if null),
      // then we persist the root once to save the new field reference.
      existingRoot.getApprovedImageIds();    // lazy init if null
      existingRoot.getRecentSearches();      // lazy init if null (added later)
      existingRoot.getHashIndex();           // lazy init if null (added later)
      existingRoot.getOcrResults();          // lazy init if null (added later)
      existingRoot.getSavedSearchViews();    // lazy init if null (added later)
      existingRoot.getAuditLog();            // lazy init if null (added later)
      existingRoot.getRawVectorStore();      // lazy init if null (added for GIGAMAP_JVECTOR backend)
      existingRoot.getTaxonomySuggestions(); // lazy init if null (added for taxonomy maintenance)
      existingRoot.getCategoryMetadata();    // lazy init if null (added for taxonomy maintenance)
      existingRoot.getTuningPresets();       // lazy init if null (added for Search Tuning Lab)
      storage.store(existingRoot);           // persist updated field references
      migrateEnumSets();                  // fix any EnumSet fields broken by Unsafe reconstruction
      logger().info("EclipseStore loaded existing data: {} images, {} approved, {} recent searches",
                    root.getImages().size(), root.getApprovedImageIds().size(),
                    root.getRecentSearches().size());
    } else {
      root = candidate;
      storage.setRoot(root);
      storage.storeRoot();
      logger().info("EclipseStore initialized with fresh root");
    }
  }

  public void saveImage(ImageAsset asset) {
    root.getImages().put(asset.getId(), asset);
    storage.store(root.getImages());
  }

  public void saveMetadata(UUID imageId, ImageMetadataInfo metadata) {
    root.getMetadata().put(imageId, metadata);
    storage.store(root.getMetadata());
  }

  public void saveLocation(UUID imageId, LocationSummary location) {
    root.getLocations().put(imageId, location);
    storage.store(root.getLocations());
  }

  public void saveAnalysis(UUID imageId, SemanticAnalysis analysis) {
    root.getAnalyses().put(imageId, analysis);
    storage.store(root.getAnalyses());
  }

  public void saveAssessment(UUID imageId, SensitivityAssessment assessment) {
    root.getAssessments().put(imageId, assessment);
    storage.store(root.getAssessments());
  }

  // -------------------------------------------------------------------------
  // Delete — removes all data associated with an image across all collections
  // -------------------------------------------------------------------------

  public void saveVectorEntry(UUID imageId, VectorEntry entry) {
    root.getVectorEntries().put(imageId, entry);
    storage.store(root.getVectorEntries());
  }

  // -------------------------------------------------------------------------
  // Privacy / approval
  // -------------------------------------------------------------------------

  /**
   * Permanently removes all persisted data for the given image.
   * Caller is responsible for also removing the image file, preview cache,
   * and in-memory index entries (vector + keyword).
   */
  public void deleteImage(UUID imageId) {
    // Remove hash index entry before removing the asset record
    ImageAsset asset = root.getImages().get(imageId);
    if (asset != null && asset.getSha256() != null) {
      root.getHashIndex().remove(asset.getSha256());
      storage.store(root.getHashIndex());
    }
    root.getImages().remove(imageId);
    root.getMetadata().remove(imageId);
    root.getLocations().remove(imageId);
    root.getAnalyses().remove(imageId);
    root.getAssessments().remove(imageId);
    root.getVectorEntries().remove(imageId);
    root.getApprovedImageIds().remove(imageId);
    // Also clean derived data added in later iterations
    root.getOcrResults().remove(imageId);
    root.getRawVectorStore().remove(imageId);
    storage.store(root.getImages());
    storage.store(root.getMetadata());
    storage.store(root.getLocations());
    storage.store(root.getAnalyses());
    storage.store(root.getAssessments());
    storage.store(root.getVectorEntries());
    storage.store(root.getApprovedImageIds());
    storage.store(root.getOcrResults());
    storage.store(root.getRawVectorStore());
    logger().info("Permanently deleted all persisted data for imageId={}", imageId);
  }

  // -------------------------------------------------------------------------
  // In-place updates for user corrections
  // -------------------------------------------------------------------------

  /**
   * Mark an image as approved so it appears in search results.
   */
  public void approveImage(UUID imageId) {
    root.getApprovedImageIds().add(imageId);
    storage.store(root.getApprovedImageIds());
  }

  /**
   * Update the risk level of an existing assessment in place and persist the change.
   */
  public void updateRiskLevel(UUID imageId, RiskLevel newLevel) {
    findAssessment(imageId).ifPresent(assessment -> {
      assessment.setRiskLevel(newLevel);
      storage.store(assessment);
      logger().info("Updated riskLevel to {} for imageId={}", newLevel, imageId);
    });
  }

  /**
   * Update the source category of an existing analysis in place and persist the change.
   */
  public void updateSourceCategory(UUID imageId, SourceCategory newCategory) {
    findAnalysis(imageId).ifPresent(analysis -> {
      analysis.setSourceCategory(newCategory);
      storage.store(analysis);
      logger().info("Updated sourceCategory to {} for imageId={}", newCategory, imageId);
    });
  }

  /**
   * Update the secondary (additional) categories of an existing analysis and persist.
   *
   * <p>The list replaces any previously stored secondary categories. Pass an empty list
   * to clear all secondary categories.
   */
  public void updateSecondaryCategories(UUID imageId,
                                        java.util.List<SourceCategory> categories) {
    findAnalysis(imageId).ifPresent(analysis -> {
      analysis.setSecondaryCategories(
          categories != null ? categories : of());
      storage.store(analysis);
      logger().info("Updated secondaryCategories for imageId={}: {}", imageId, categories);
    });
  }

  /**
   * Remove approval — image will no longer appear in search results.
   */
  public void unapproveImage(UUID imageId) {
    root.getApprovedImageIds().remove(imageId);
    storage.store(root.getApprovedImageIds());
  }

  // -------------------------------------------------------------------------
  // Find / query
  // -------------------------------------------------------------------------

  /**
   * Returns {@code true} if the image has been approved for search.
   */
  public boolean isApproved(UUID imageId) {
    return root.getApprovedImageIds().contains(imageId);
  }

  public Optional<ImageAsset> findImage(UUID imageId) {
    return Optional.ofNullable(root.getImages().get(imageId));
  }

  public List<ImageAsset> findAllImages() {
    return root.getImages().values().stream()
        .filter(a -> !a.isDeleted())
        .collect(Collectors.toList());
  }

  public List<ImageAsset> findAllImagesIncludingDeleted() {
    return new ArrayList<>(root.getImages().values());
  }

  public Optional<SemanticAnalysis> findAnalysis(UUID imageId) {
    return Optional.ofNullable(root.getAnalyses().get(imageId));
  }

  public Optional<SensitivityAssessment> findAssessment(UUID imageId) {
    return Optional.ofNullable(root.getAssessments().get(imageId));
  }

  public Optional<LocationSummary> findLocation(UUID imageId) {
    return Optional.ofNullable(root.getLocations().get(imageId));
  }

  public Optional<ImageMetadataInfo> findMetadata(UUID imageId) {
    return Optional.ofNullable(root.getMetadata().get(imageId));
  }

  public Optional<VectorEntry> findVectorEntry(UUID imageId) {
    return Optional.ofNullable(root.getVectorEntries().get(imageId));
  }

  // -------------------------------------------------------------------------
  // Hash-based duplicate detection
  // -------------------------------------------------------------------------

  public List<UUID> findAllIndexedImageIds() {
    return new ArrayList<>(root.getVectorEntries().keySet());
  }

  /**
   * Returns the imageId of an already-stored asset whose file content matches
   * the given SHA-256 hash, or {@link java.util.Optional#empty()} if no duplicate exists.
   */
  public Optional<UUID> findImageIdByHash(String sha256) {
    if (sha256 == null || sha256.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(root.getHashIndex().get(sha256));
  }

  /**
   * Registers a sha256 → imageId mapping in the hash index and persists the change.
   * Called by the ingestion pipeline after a new image has been stored successfully.
   */
  public void registerHash(String sha256, UUID imageId) {
    if (sha256 == null || sha256.isBlank()) {
      return;
    }
    root.getHashIndex().put(sha256, imageId);
    storage.store(root.getHashIndex());
  }

  /**
   * Removes the given hash from the index.
   * Called when an image is deleted to keep the index consistent.
   */
  public void removeHash(String sha256) {
    if (sha256 == null || sha256.isBlank()) {
      return;
    }
    root.getHashIndex().remove(sha256);
    storage.store(root.getHashIndex());
  }

  // -------------------------------------------------------------------------
  // Recent search history
  // -------------------------------------------------------------------------

  /**
   * Rebuilds the hash index from scratch by iterating all stored {@link ImageAsset}s.
   * Call once on application startup to make the index consistent with any images
   * that were persisted before the hash-index feature existed.
   */
  public void rebuildHashIndex() {
    Map<String, UUID> index = root.getHashIndex();
    index.clear();
    int indexed = 0;
    for (ImageAsset asset : root.getImages().values()) {
      if (asset.getSha256() != null && !asset.getSha256().isBlank()) {
        index.put(asset.getSha256(), asset.getId());
        indexed++;
      }
    }
    storage.store(index);
    logger().info("Hash index rebuilt: {} entries", indexed);
  }

  /**
   * Prepends the search entry to the recent-search list (case-insensitive deduplication
   * on the original query), then trims the list to {@value #MAX_RECENT_SEARCHES} entries
   * and persists.
   *
   * @param query      the original user query
   * @param finalQuery the LLM-transformed embedding text (may be null for transform-only runs)
   * @param mode       which search mode was used
   */
  public void addRecentSearch(String query, String finalQuery, SearchMode mode) {
    if (query == null || query.isBlank()) {
      return;
    }
    String trimmed = query.trim();
    List<RecentSearchEntry> recent = root.getRecentSearches();
    // Remove any existing entry with the same original query (case-insensitive) so the
    // freshly submitted one bubbles to the top.
    for (int i = recent.size() - 1; i >= 0; i--) {
      if (trimmed.equalsIgnoreCase(recent.get(i).getQuery())) {
        recent.remove(i);
      }
    }
    recent.add(0, new RecentSearchEntry(trimmed, finalQuery, mode, Instant.now()));
    while (recent.size() > MAX_RECENT_SEARCHES) {
      recent.remove(recent.size() - 1);
    }
    storage.store(recent);
    logger().debug("Recent search history updated: {} entries", recent.size());
  }

  /**
   * Removes the specified entries from the recent-search history and persists the change.
   * Uses object identity ({@link Object#equals}) for matching — callers should pass the
   * exact instances returned by {@link #getRecentSearches()}.
   */
  public void deleteRecentSearches(List<RecentSearchEntry> toDelete) {
    if (toDelete == null || toDelete.isEmpty()) {
      return;
    }
    root.getRecentSearches().removeAll(toDelete);
    storage.store(root.getRecentSearches());
    logger().info("Deleted {} recent search entries", toDelete.size());
  }

  /**
   * Returns an unmodifiable snapshot of the recent-search list (newest first).
   */
  public List<RecentSearchEntry> getRecentSearches() {
    return Collections.unmodifiableList(root.getRecentSearches());
  }

  /**
   * Clears the entire recent-search history and persists the change.
   */
  public void clearRecentSearches() {
    root.getRecentSearches().clear();
    storage.store(root.getRecentSearches());
    logger().info("Recent search history cleared");
  }

  // -------------------------------------------------------------------------
  // Raw vector store — used by the GIGAMAP_JVECTOR backend
  // -------------------------------------------------------------------------

  /**
   * Persists a raw embedding vector for the given image.
   * Called by {@code EclipseStoreGigaMapJVectorBackend} whenever a vector is indexed.
   * EclipseStore is the durable source of truth; the JVector HNSW index is rebuilt
   * from these values at startup.
   */
  public void saveRawVector(UUID imageId, float[] vector) {
    root.getRawVectorStore().put(imageId, vector);
    storage.store(root.getRawVectorStore());
  }

  /**
   * Removes the raw embedding vector for the given image.
   * Called on delete / remove by the {@code GIGAMAP_JVECTOR} backend.
   */
  public void removeRawVector(UUID imageId) {
    root.getRawVectorStore().remove(imageId);
    storage.store(root.getRawVectorStore());
  }

  /**
   * Returns a snapshot of all persisted raw vectors.
   * Used by {@code EclipseStoreGigaMapJVectorBackend} to rebuild the JVector
   * HNSW index at startup without calling the embedding model.
   */
  public Map<UUID, float[]> findAllRawVectors() {
    return new HashMap<>(root.getRawVectorStore());
  }

  /**
   * Returns the number of raw vectors currently persisted.
   * Used to decide whether a one-time migration from the in-memory backend is needed.
   */
  public int getRawVectorCount() {
    return root.getRawVectorStore().size();
  }

  /**
   * Looks up a single raw embedding vector by imageId without copying the entire map.
   * Used by the tuning-lab feedback scorer to fetch candidate vectors on demand.
   *
   * @return the stored embedding, or {@link Optional#empty()} if not persisted
   */
  public Optional<float[]> findRawVector(UUID imageId) {
    return Optional.ofNullable(root.getRawVectorStore().get(imageId));
  }

  // -------------------------------------------------------------------------
  // OcrResult
  // -------------------------------------------------------------------------

  public void saveOcrResult(UUID imageId, OcrResult result) {
    root.getOcrResults().put(imageId, result);
    storage.store(root.getOcrResults());
  }

  public Optional<OcrResult> findOcrResult(UUID imageId) {
    return Optional.ofNullable(root.getOcrResults().get(imageId));
  }

  // -------------------------------------------------------------------------
  // SavedSearchView
  // -------------------------------------------------------------------------

  public void saveSavedSearchView(SavedSearchView view) {
    root.getSavedSearchViews().add(view);
    storage.store(root.getSavedSearchViews());
  }

  public List<SavedSearchView> getSavedSearchViews() {
    return Collections.unmodifiableList(root.getSavedSearchViews());
  }

  public void deleteSavedSearchView(UUID viewId) {
    root.getSavedSearchViews().removeIf(v -> viewId.equals(v.getId()));
    storage.store(root.getSavedSearchViews());
  }

  // -------------------------------------------------------------------------
  // AuditEntry
  // -------------------------------------------------------------------------

  public void addAuditEntry(AuditEntry entry) {
    List<AuditEntry> log = root.getAuditLog();
    log.add(0, entry);
    // Keep last 500 entries
    while (log.size() > 500) log.remove(log.size() - 1);
    storage.store(log);
  }

  public List<AuditEntry> getAuditLog() {
    return Collections.unmodifiableList(root.getAuditLog());
  }

  // -------------------------------------------------------------------------
  // Soft delete / restore
  // -------------------------------------------------------------------------

  public void softDeleteImage(UUID imageId, String reason) {
    findImage(imageId).ifPresent(asset -> {
      asset.setDeleted(true);
      asset.setDeletedAt(java.time.Instant.now());
      asset.setDeletedReason(reason);
      storage.store(asset);
    });
    unapproveImage(imageId);
  }

  public void restoreImage(UUID imageId) {
    findImage(imageId).ifPresent(asset -> {
      asset.setDeleted(false);
      asset.setDeletedAt(null);
      asset.setDeletedReason(null);
      storage.store(asset);
    });
    // Re-approve only if the stored sensitivity assessment is SAFE.
    // REVIEW / SENSITIVE images remain locked and require manual approval in the Overview.
    findAssessment(imageId).ifPresent(assessment -> {
      if (assessment.getRiskLevel() == RiskLevel.SAFE) {
        approveImage(imageId);
        logger().info("Auto-approved restored image {} (SAFE risk)", imageId);
      } else {
        logger().info("Restored image {} remains locked ({} risk) — manual approval required",
                      imageId, assessment.getRiskLevel());
      }
    });
  }

  /**
   * Returns all images that have been soft-deleted (archived), regardless of their
   * approval state or risk level.  Used by the Archive view.
   */
  public List<ImageAsset> findArchivedImages() {
    return root.getImages().values().stream()
        .filter(ImageAsset::isDeleted)
        .sorted(Comparator.comparing(
            a -> a.getDeletedAt() != null ? a.getDeletedAt() : Instant.EPOCH,
            Comparator.reverseOrder()))
        .collect(Collectors.toList());
  }

  // -------------------------------------------------------------------------
  // Taxonomy suggestions
  // -------------------------------------------------------------------------

  /**
   * Persists a {@link TaxonomySuggestion}.  If a suggestion with the same id already
   * exists it is replaced; otherwise the suggestion is appended.
   */
  public void saveTaxonomySuggestion(TaxonomySuggestion suggestion) {
    List<TaxonomySuggestion> list = root.getTaxonomySuggestions();
    list.removeIf(s -> suggestion.getId().equals(s.getId()));
    list.add(suggestion);
    storage.store(list);
  }

  /**
   * Returns an unmodifiable snapshot of all stored taxonomy suggestions (newest first).
   */
  public List<TaxonomySuggestion> findAllSuggestions() {
    return Collections.unmodifiableList(root.getTaxonomySuggestions());
  }

  /**
   * Returns suggestions filtered by status.
   */
  public List<TaxonomySuggestion> findSuggestionsByStatus(SuggestionStatus status) {
    return root.getTaxonomySuggestions().stream()
        .filter(s -> s.getStatus() == status)
        .collect(Collectors.toList());
  }

  /**
   * Returns a single suggestion by id.
   */
  public Optional<TaxonomySuggestion> findSuggestion(java.util.UUID suggestionId) {
    return root.getTaxonomySuggestions().stream()
        .filter(s -> suggestionId.equals(s.getId()))
        .findFirst();
  }

  /**
   * Removes all stored taxonomy suggestions.  Used before a fresh analysis run so
   * the user sees only the most recent batch of proposals.
   */
  public void clearTaxonomySuggestions() {
    root.getTaxonomySuggestions().clear();
    storage.store(root.getTaxonomySuggestions());
  }

  // -------------------------------------------------------------------------
  // Category lifecycle metadata
  // -------------------------------------------------------------------------

  /**
   * Persists (creates or replaces) category lifecycle metadata for the given category.
   */
  public void saveCategoryMetadata(CategoryMetadata meta) {
    root.getCategoryMetadata().put(meta.getCategory().name(), meta);
    storage.store(root.getCategoryMetadata());
  }

  /**
   * Returns the stored {@link CategoryMetadata} for the given category, or empty if
   * no metadata record has been created (the category is implicitly ACTIVE).
   */
  public Optional<CategoryMetadata> findCategoryMetadata(SourceCategory category) {
    if (category == null) return Optional.empty();
    return Optional.ofNullable(root.getCategoryMetadata().get(category.name()));
  }

  /**
   * Returns an unmodifiable snapshot of all stored category metadata records.
   */
  public List<CategoryMetadata> findAllCategoryMetadata() {
    return Collections.unmodifiableList(new ArrayList<>(root.getCategoryMetadata().values()));
  }

  // ── Search Tuning Lab presets ─────────────────────────────────────────────

  /**
   * Persists a new or updated tuning preset.
   * Any existing preset with the same {@link com.svenruppert.imagerag.domain.SearchTuningPreset#getId()}
   * is replaced; a fresh preset (never saved before) is appended.
   */
  public void saveTuningPreset(com.svenruppert.imagerag.domain.SearchTuningPreset preset) {
    List<com.svenruppert.imagerag.domain.SearchTuningPreset> list = root.getTuningPresets();
    // Replace existing entry with matching id, or append
    boolean replaced = false;
    for (int i = 0; i < list.size(); i++) {
      if (preset.getId().equals(list.get(i).getId())) {
        list.set(i, preset);
        replaced = true;
        break;
      }
    }
    if (!replaced) {
      list.add(preset);
    }
    storage.store(list);
    logger().info("Saved tuning preset '{}' (id={})", preset.getName(), preset.getId());
  }

  /**
   * Returns an unmodifiable snapshot of all saved tuning presets,
   * ordered by insertion (oldest first).
   */
  public List<com.svenruppert.imagerag.domain.SearchTuningPreset> findAllTuningPresets() {
    return Collections.unmodifiableList(new ArrayList<>(root.getTuningPresets()));
  }

  /**
   * Removes the tuning preset with the given id.  No-op if not found.
   */
  public void deleteTuningPreset(java.util.UUID presetId) {
    boolean removed = root.getTuningPresets().removeIf(p -> presetId.equals(p.getId()));
    if (removed) {
      storage.store(root.getTuningPresets());
      logger().info("Deleted tuning preset id={}", presetId);
    }
  }

  public void shutdown() {
    storage.shutdown();
    logger().info("EclipseStore shut down");
  }

  // -------------------------------------------------------------------------
  // Schema migration helpers
  // -------------------------------------------------------------------------

  /**
   * EclipseStore reconstructs objects via {@code Unsafe.allocateInstance()}, bypassing
   * constructors. {@code EnumSet} has {@code final} internal fields ({@code elementType},
   * {@code universe}) that are set in the constructor — after Unsafe reconstruction they
   * are {@code null}, causing {@link NullPointerException} on iteration.
   *
   * <p>This method replaces any broken {@code EnumSet}-backed flags field with a safe
   * {@link HashSet} copy and re-persists the affected assessments.
   */
  private void migrateEnumSets() {
    int migrated = 0;
    for (var entry : root.getAssessments().entrySet()) {
      var assessment = entry.getValue();
      Set<SensitivityFlag> flags = assessment.getFlags();
      if (flags == null) {
        assessment.setFlags(new HashSet<>());
        storage.store(assessment);
        migrated++;
        continue;
      }
      // Detect broken EnumSet by attempting to iterate; replace on failure
      try {
        flags.size(); // cheap probe — iterates nothing but accesses 'universe'
        // Also check by iterating (size() alone may not trigger the NPE in all JVMs)
        for (@SuppressWarnings("unused") var ignored : flags) break;
      } catch (NullPointerException e) {
        // 'universe' is null — rebuild as HashSet (we can't recover the values)
        logger().warn("Rebuilding broken EnumSet flags for assessment imageId={}",
                      assessment.getImageId());
        assessment.setFlags(new HashSet<>());
        storage.store(assessment);
        migrated++;
      }
    }
    if (migrated > 0) {
      logger().info("Migrated {} assessments from broken EnumSet to HashSet", migrated);
    }
  }
}
