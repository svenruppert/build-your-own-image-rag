package com.svenruppert.imagerag.domain;

import com.svenruppert.imagerag.domain.enums.CategoryGroup;
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
 *
 * <p>Category filtering uses coarse {@link CategoryGroup} buckets so that, for
 * example, filtering by "Nature" returns images classified as LANDSCAPE, MOUNTAIN,
 * FOREST, FLOWER, etc.  The mapping is provided by {@link CategoryRegistry}.
 */
public class ImageOverviewFilter {

  /**
   * null = show all category groups
   */
  private CategoryGroup category;

  /**
   * Optional fine-grained category within the selected group.
   * When set, filtering uses this exact {@link SourceCategory} instead of the coarse group.
   * null = no specific-category constraint.
   */
  private SourceCategory specificCategory;

  /**
   * null = show all risk levels
   */
  private RiskLevel risk;

  /**
   * null = show all, {@code true} = approved/visible only,
   * {@code false} = locked/blocked only.
   */
  private Boolean approved;

  // ── Accessors ─────────────────────────────────────────────────────────────

  public CategoryGroup getCategory() {
    return category;
  }

  public void setCategory(CategoryGroup category) {
    this.category = category;
  }

  public SourceCategory getSpecificCategory() {
    return specificCategory;
  }

  public void setSpecificCategory(SourceCategory specificCategory) {
    this.specificCategory = specificCategory;
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

  /**
   * Returns {@code true} when no filter constraint is set.
   */
  public boolean isEmpty() {
    return category == null && specificCategory == null && risk == null && approved == null;
  }

  /**
   * Resets all constraints to "show all".
   */
  public void reset() {
    category = null;
    specificCategory = null;
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
    // Fine-grained check takes precedence over coarse-group check.
    if (specificCategory != null) {
      SourceCategory assetCat = ps.findAnalysis(asset.getId())
          .map(SemanticAnalysis::getSourceCategory)
          .orElse(null);
      if (!Objects.equals(specificCategory, assetCat)) return false;
    } else if (category != null) {
      CategoryGroup assetGroup = ps.findAnalysis(asset.getId())
          .map(a -> CategoryRegistry.getGroup(a.getSourceCategory()))
          .orElse(null);
      if (!Objects.equals(category, assetGroup)) return false;
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
