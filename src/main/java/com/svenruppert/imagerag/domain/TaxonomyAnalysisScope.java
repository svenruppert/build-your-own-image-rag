package com.svenruppert.imagerag.domain;

import com.svenruppert.imagerag.domain.enums.CategoryGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Defines the scope (set of images) subject to a taxonomy-maintenance analysis pass.
 *
 * <p>The user selects a scope in the Taxonomy Maintenance view before triggering
 * analysis.  The {@link com.svenruppert.imagerag.service.TaxonomyAnalysisService} uses
 * this object to determine which images to include.
 */
public class TaxonomyAnalysisScope {

  private Mode mode = Mode.ALL;
  /**
   * For {@link Mode#CATEGORY_GROUP}: the target group.
   */
  private CategoryGroup targetGroup;
  /**
   * Whether to include all subgroups when {@link Mode#CATEGORY_GROUP} is active. Defaults true.
   */
  private boolean includeSubgroups = true;
  /**
   * For {@link Mode#MANUAL_SELECTION}: explicit image IDs to analyse.
   */
  private List<UUID> manualImageIds = new ArrayList<>();
  /**
   * For {@link Mode#LOW_CONFIDENCE}: images with primaryScore below this value are included.
   * Defaults to 0.6.
   */
  private double confidenceThreshold = 0.6;

  public static TaxonomyAnalysisScope all() {
    TaxonomyAnalysisScope s = new TaxonomyAnalysisScope();
    s.mode = Mode.ALL;
    return s;
  }

  // ── Fluent builders ────────────────────────────────────────────────────────

  public static TaxonomyAnalysisScope forGroup(CategoryGroup group, boolean includeSubgroups) {
    TaxonomyAnalysisScope s = new TaxonomyAnalysisScope();
    s.mode = Mode.CATEGORY_GROUP;
    s.targetGroup = group;
    s.includeSubgroups = includeSubgroups;
    return s;
  }

  public static TaxonomyAnalysisScope manualSelection(List<UUID> imageIds) {
    TaxonomyAnalysisScope s = new TaxonomyAnalysisScope();
    s.mode = Mode.MANUAL_SELECTION;
    s.manualImageIds = new ArrayList<>(imageIds);
    return s;
  }

  public static TaxonomyAnalysisScope missingCategory() {
    TaxonomyAnalysisScope s = new TaxonomyAnalysisScope();
    s.mode = Mode.MISSING_CATEGORY;
    return s;
  }

  public static TaxonomyAnalysisScope lowConfidence(double threshold) {
    TaxonomyAnalysisScope s = new TaxonomyAnalysisScope();
    s.mode = Mode.LOW_CONFIDENCE;
    s.confidenceThreshold = threshold;
    return s;
  }

  public Mode getMode() {
    return mode;
  }

  // ── Accessors ──────────────────────────────────────────────────────────────

  public void setMode(Mode mode) {
    this.mode = mode;
  }

  public CategoryGroup getTargetGroup() {
    return targetGroup;
  }

  public void setTargetGroup(CategoryGroup targetGroup) {
    this.targetGroup = targetGroup;
  }

  public boolean isIncludeSubgroups() {
    return includeSubgroups;
  }

  public void setIncludeSubgroups(boolean includeSubgroups) {
    this.includeSubgroups = includeSubgroups;
  }

  public List<UUID> getManualImageIds() {
    if (manualImageIds == null) manualImageIds = new ArrayList<>();
    return manualImageIds;
  }

  public void setManualImageIds(List<UUID> manualImageIds) {
    this.manualImageIds = manualImageIds != null ? manualImageIds : new ArrayList<>();
  }

  public double getConfidenceThreshold() {
    return confidenceThreshold;
  }

  public void setConfidenceThreshold(double confidenceThreshold) {
    this.confidenceThreshold = Math.max(0.0, Math.min(1.0, confidenceThreshold));
  }

  @Override
  public String toString() {
    return switch (mode) {
      case ALL -> "All images";
      case CATEGORY_GROUP -> "Group: " + (targetGroup != null ? targetGroup.getLabel() : "?")
          + (includeSubgroups ? " (incl. subgroups)" : "");
      case MANUAL_SELECTION -> manualImageIds.size() + " manually selected images";
      case MISSING_CATEGORY -> "Images with missing/unknown category";
      case LOW_CONFIDENCE -> "Images with confidence < " + String.format("%.2f", confidenceThreshold);
    };
  }

  /**
   * How the target image set is selected.
   */
  public enum Mode {
    /**
     * Analyse every image in the active (non-archived) dataset.
     */
    ALL,

    /**
     * Analyse a specific list of image UUIDs chosen by the user.
     */
    MANUAL_SELECTION,

    /**
     * Analyse all images whose primary or secondary category belongs to a group.
     */
    CATEGORY_GROUP,

    /**
     * Analyse images where the primary category is {@code UNKNOWN} or {@code MIXED},
     * or where no {@link CategoryConfidence} record exists.
     */
    MISSING_CATEGORY,

    /**
     * Analyse images whose stored {@link CategoryConfidence#getPrimaryScore()} is
     * below {@link #confidenceThreshold}.
     */
    LOW_CONFIDENCE
  }
}
