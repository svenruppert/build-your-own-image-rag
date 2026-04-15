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

  /**
   * OCR extraction results, keyed by imageId.
   * Not declared final for EclipseStore Unsafe reconstruction compatibility.
   */
  private Map<UUID, OcrResult> ocrResults;

  /**
   * User-saved search configurations.
   * Not declared final for EclipseStore Unsafe reconstruction compatibility.
   */
  private List<SavedSearchView> savedSearchViews;

  /**
   * Audit log of critical state-changing actions (newest first, capped at 500).
   * Not declared final for EclipseStore Unsafe reconstruction compatibility.
   */
  private List<AuditEntry> auditLog;

  /**
   * Raw embedding vectors keyed by imageId.
   *
   * <p>This is the EclipseStore-managed durable store for the
   * {@code GIGAMAP_JVECTOR} vector-search backend — conceptually the "GigaMap"
   * that persists the float[] embeddings so the JVector HNSW index can be
   * rebuilt at startup <em>without</em> calling the embedding model again.
   *
   * <p>The {@code IN_MEMORY} backend does not use this map.
   * Not declared final for EclipseStore Unsafe reconstruction compatibility.
   */
  private Map<UUID, float[]> rawVectorStore;

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

  /**
   * Returns the OCR results map, lazily initialising it if null.
   */
  public Map<UUID, OcrResult> getOcrResults() {
    if (ocrResults == null) {
      ocrResults = new HashMap<>();
    }
    return ocrResults;
  }

  /**
   * Returns the saved search views list, lazily initialising it if null.
   */
  public List<SavedSearchView> getSavedSearchViews() {
    if (savedSearchViews == null) {
      savedSearchViews = new ArrayList<>();
    }
    return savedSearchViews;
  }

  /**
   * Returns the audit log list, lazily initialising it if null.
   */
  public List<AuditEntry> getAuditLog() {
    if (auditLog == null) {
      auditLog = new ArrayList<>();
    }
    return auditLog;
  }

  /**
   * Returns the raw-vector store, lazily initialising it if null.
   * Used exclusively by the {@code GIGAMAP_JVECTOR} backend.
   * The map is owned by EclipseStore and persisted as part of the data root.
   */
  public Map<UUID, float[]> getRawVectorStore() {
    if (rawVectorStore == null) {
      rawVectorStore = new HashMap<>();
    }
    return rawVectorStore;
  }
}
