package com.svenruppert.imagerag.persistence;

import com.svenruppert.imagerag.domain.*;

import java.util.*;

public class AppDataRoot {

  private final Map<UUID, ImageAsset> images = new HashMap<>();
  private final Map<UUID, ImageMetadataInfo> metadata = new HashMap<>();
  private final Map<UUID, LocationSummary> locations = new HashMap<>();
  private final Map<UUID, SemanticAnalysis> analyses = new HashMap<>();
  private final Map<UUID, SensitivityAssessment> assessments = new HashMap<>();
  private final Map<UUID, VectorEntry> vectorEntries = new HashMap<>();

  /**
   * Images whose search visibility has been approved.
   * SAFE images are auto-approved by the ingestion pipeline;
   * REVIEW / SENSITIVE images require explicit user approval.
   * <p>
   * NOTE: NOT declared final — EclipseStore reconstructs objects via Unsafe,
   * bypassing constructors. A final initialised field would be null when loading
   * data persisted before this field was added. The lazy getter below handles that.
   */
  private Set<UUID> approvedImageIds;

  /**
   * Most-recent-first list of search queries entered by the user.
   * Capped at 20 entries.  Not declared final for the same Unsafe reason as above.
   */
  private List<RecentSearchEntry> recentSearches;

  /**
   * SHA-256 hash → imageId lookup map for O(1) duplicate detection at upload time.
   * Not declared final for the same Unsafe reason as above.
   * Rebuilt from {@link #images} on startup via
   * {@link com.svenruppert.imagerag.persistence.PersistenceService#rebuildHashIndex}.
   */
  private Map<String, UUID> hashIndex;

  public Map<UUID, ImageAsset> getImages() {
    return images;
  }

  public Map<UUID, ImageMetadataInfo> getMetadata() {
    return metadata;
  }

  public Map<UUID, LocationSummary> getLocations() {
    return locations;
  }

  public Map<UUID, SemanticAnalysis> getAnalyses() {
    return analyses;
  }

  public Map<UUID, SensitivityAssessment> getAssessments() {
    return assessments;
  }

  public Map<UUID, VectorEntry> getVectorEntries() {
    return vectorEntries;
  }

  /**
   * Returns the approved-image set, lazily initialising it if it is null
   * (= first access after migrating from a version that didn't have this field).
   */
  public Set<UUID> getApprovedImageIds() {
    if (approvedImageIds == null) {
      approvedImageIds = new HashSet<>();
    }
    return approvedImageIds;
  }

  /**
   * Returns the recent-search list, lazily initialising it if it is null
   * (= first access after migrating from a version that didn't have this field).
   */
  public List<RecentSearchEntry> getRecentSearches() {
    if (recentSearches == null) {
      recentSearches = new ArrayList<>();
    }
    return recentSearches;
  }

  /**
   * Returns the hash → imageId index, lazily initialising it when first accessed.
   * The caller ({@link com.svenruppert.imagerag.persistence.PersistenceService})
   * is responsible for populating this map from existing image assets on startup.
   */
  public Map<String, UUID> getHashIndex() {
    if (hashIndex == null) {
      hashIndex = new HashMap<>();
    }
    return hashIndex;
  }
}
