package com.svenruppert.imagerag.domain;

import com.svenruppert.imagerag.domain.enums.RiskLevel;
import com.svenruppert.imagerag.domain.enums.SourceCategory;
import com.svenruppert.imagerag.persistence.PersistenceService;

import java.util.List;
import java.util.Objects;

/**
 * Reusable filter model for the image overview and tile view.
 *
 * <p>All filter fields are optional (null = "show all").  Multiple non-null filters
 * are ANDed together.  Use {@link #matches} to test a single {@link ImageAsset} or
 * {@link #apply} to filter a full list.
 */
public class ImageOverviewFilter {

  /** null = show all categories */
  private SourceCategory category;

  /** null = show all risk levels */
  private RiskLevel risk;

  /**
   * null = show all, {@code true} = approved/visible only,
   * {@code false} = locked/blocked only.
   */
  private Boolean approved;

  // ── Accessors ─────────────────────────────────────────────────────────────

  public SourceCategory getCategory() {
    return category;
  }

  public void setCategory(SourceCategory category) {
    this.category = category;
  }

  public RiskLevel getRisk() {
    return risk;
  }

  public void setRisk(RiskLevel risk) {
    this.risk = risk;
  }

  public Boolean getApproved() {
    return approved;
  }

  public void setApproved(Boolean approved) {
    this.approved = approved;
  }

  /** Returns {@code true} when no filter constraint is set. */
  public boolean isEmpty() {
    return category == null && risk == null && approved == null;
  }

  /** Resets all constraints to "show all". */
  public void reset() {
    category = null;
    risk = null;
    approved = null;
  }

  // ── Filtering logic ───────────────────────────────────────────────────────

  /**
   * Tests whether the given {@link ImageAsset} passes all active filter constraints.
   *
   * @param asset the asset to test
   * @param ps    persistence service used to look up derived attributes
   * @return {@code true} if the asset matches all active constraints
   */
  public boolean matches(ImageAsset asset, PersistenceService ps) {
    if (category != null) {
      SourceCategory assetCat = ps.findAnalysis(asset.getId())
          .map(SemanticAnalysis::getSourceCategory)
          .orElse(null);
      if (!Objects.equals(category, assetCat)) return false;
    }

    if (risk != null) {
      RiskLevel assetRisk = ps.findAssessment(asset.getId())
          .map(SensitivityAssessment::getRiskLevel)
          .orElse(null);
      if (!Objects.equals(risk, assetRisk)) return false;
    }

    if (approved != null) {
      boolean isApproved = ps.isApproved(asset.getId());
      if (!approved.equals(isApproved)) return false;
    }

    return true;
  }

  /**
   * Filters a list of images, returning only those that pass all active constraints.
   */
  public List<ImageAsset> apply(List<ImageAsset> images, PersistenceService ps) {
    if (isEmpty()) return images;
    return images.stream()
        .filter(a -> matches(a, ps))
        .toList();
  }
}
