package com.svenruppert.imagerag.persistence;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.imagerag.domain.*;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;

import com.svenruppert.imagerag.domain.enums.RiskLevel;
import com.svenruppert.imagerag.domain.enums.SearchMode;
import com.svenruppert.imagerag.domain.enums.SensitivityFlag;
import com.svenruppert.imagerag.domain.enums.SourceCategory;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class PersistenceService
    implements HasLogger {

  private final EmbeddedStorageManager storage;
  private final AppDataRoot root;

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
      existingRoot.getApprovedImageIds(); // lazy init if null
      existingRoot.getRecentSearches();   // lazy init if null (added later)
      existingRoot.getHashIndex();        // lazy init if null (added later)
      storage.store(existingRoot);        // persist updated field references
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

  // -------------------------------------------------------------------------
  // Save operations
  // -------------------------------------------------------------------------

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

  public void saveVectorEntry(UUID imageId, VectorEntry entry) {
    root.getVectorEntries().put(imageId, entry);
    storage.store(root.getVectorEntries());
  }

  // -------------------------------------------------------------------------
  // Delete — removes all data associated with an image across all collections
  // -------------------------------------------------------------------------

  /**
   * Permanently removes all persisted data for the given image.
   * Caller is responsible for also removing the image file and the vector-index entry.
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
    storage.store(root.getImages());
    storage.store(root.getMetadata());
    storage.store(root.getLocations());
    storage.store(root.getAnalyses());
    storage.store(root.getAssessments());
    storage.store(root.getVectorEntries());
    storage.store(root.getApprovedImageIds());
    logger().info("Deleted all persisted data for imageId={}", imageId);
  }

  // -------------------------------------------------------------------------
  // Privacy / approval
  // -------------------------------------------------------------------------

  /**
   * Mark an image as approved so it appears in search results.
   */
  public void approveImage(UUID imageId) {
    root.getApprovedImageIds().add(imageId);
    storage.store(root.getApprovedImageIds());
  }

  // -------------------------------------------------------------------------
  // In-place updates for user corrections
  // -------------------------------------------------------------------------

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
   * Remove approval — image will no longer appear in search results.
   */
  public void unapproveImage(UUID imageId) {
    root.getApprovedImageIds().remove(imageId);
    storage.store(root.getApprovedImageIds());
  }

  /**
   * Returns {@code true} if the image has been approved for search.
   */
  public boolean isApproved(UUID imageId) {
    return root.getApprovedImageIds().contains(imageId);
  }

  // -------------------------------------------------------------------------
  // Find / query
  // -------------------------------------------------------------------------

  public Optional<ImageAsset> findImage(UUID imageId) {
    return Optional.ofNullable(root.getImages().get(imageId));
  }

  public List<ImageAsset> findAllImages() {
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

  public List<UUID> findAllIndexedImageIds() {
    return new ArrayList<>(root.getVectorEntries().keySet());
  }

  // -------------------------------------------------------------------------
  // Hash-based duplicate detection
  // -------------------------------------------------------------------------

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

  // -------------------------------------------------------------------------
  // Recent search history
  // -------------------------------------------------------------------------

  private static final int MAX_RECENT_SEARCHES = 20;

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
