package com.svenruppert.imagerag.domain;

import com.svenruppert.imagerag.domain.enums.SourceCategory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Category confidence record for a single image.
 *
 * <p>Stores the model's confidence for the primary assigned category as well as
 * a ranked list of alternative candidates.  This data is produced during semantic
 * derivation (ingestion / reprocessing) or during a taxonomy-maintenance analysis
 * pass and is used to:
 * <ul>
 *   <li>Identify images with low classification confidence for targeted re-analysis.</li>
 *   <li>Provide a confidence boost signal during search ranking.</li>
 *   <li>Drive taxonomy-maintenance proposals (reclassify if top alternative has
 *       higher confidence than current primary).</li>
 * </ul>
 *
 * <p>Scores are model-produced values in [0.0, 1.0].  They reflect relative model
 * preference rather than statistically calibrated probabilities.
 *
 * <p>Not declared final — EclipseStore Unsafe reconstruction compatibility.
 */
public class CategoryConfidence {

  /**
   * The image this confidence record belongs to.
   */
  private java.util.UUID imageId;

  /**
   * Primary (winning) category, matches {@link SemanticAnalysis#getSourceCategory()}.
   */
  private SourceCategory primaryCategory;

  /**
   * Model confidence for the primary category assignment.
   * 1.0 = model is certain; 0.0 = no signal.  Values below 0.5 indicate the
   * classification is ambiguous and the image is a good candidate for review.
   */
  private double primaryScore;

  /**
   * Up to 5 alternative category candidates in descending score order.
   * Does not include the primary category.
   */
  private List<CategoryCandidate> alternatives;

  /**
   * When this confidence record was produced.
   */
  private Instant assessedAt;

  /**
   * Semantic model version that produced this assessment.
   */
  private String modelVersion;

  public CategoryConfidence() {
    this.alternatives = new ArrayList<>();
  }

  public CategoryConfidence(java.util.UUID imageId,
                            SourceCategory primaryCategory,
                            double primaryScore,
                            List<CategoryCandidate> alternatives,
                            Instant assessedAt,
                            String modelVersion) {
    this.imageId = imageId;
    this.primaryCategory = primaryCategory;
    this.primaryScore = Math.max(0.0, Math.min(1.0, primaryScore));
    this.alternatives = alternatives != null ? alternatives : new ArrayList<>();
    this.assessedAt = assessedAt;
    this.modelVersion = modelVersion;
  }

  // ── Accessors ──────────────────────────────────────────────────────────────

  public java.util.UUID getImageId() {
    return imageId;
  }

  public void setImageId(java.util.UUID imageId) {
    this.imageId = imageId;
  }

  public SourceCategory getPrimaryCategory() {
    return primaryCategory;
  }

  public void setPrimaryCategory(SourceCategory primaryCategory) {
    this.primaryCategory = primaryCategory;
  }

  public double getPrimaryScore() {
    return primaryScore;
  }

  public void setPrimaryScore(double primaryScore) {
    this.primaryScore = Math.max(0.0, Math.min(1.0, primaryScore));
  }

  public List<CategoryCandidate> getAlternatives() {
    if (alternatives == null) alternatives = new ArrayList<>();
    return alternatives;
  }

  public void setAlternatives(List<CategoryCandidate> alternatives) {
    this.alternatives = alternatives != null ? alternatives : new ArrayList<>();
  }

  public Instant getAssessedAt() {
    return assessedAt;
  }

  public void setAssessedAt(Instant assessedAt) {
    this.assessedAt = assessedAt;
  }

  public String getModelVersion() {
    return modelVersion;
  }

  public void setModelVersion(String modelVersion) {
    this.modelVersion = modelVersion;
  }

  // ── Helpers ────────────────────────────────────────────────────────────────

  /**
   * Returns {@code true} when the primary confidence is below {@code threshold},
   * indicating the classification is ambiguous.
   */
  public boolean isAmbiguous(double threshold) {
    return primaryScore < threshold;
  }

  /**
   * Returns the best alternative candidate whose score is strictly higher than
   * the primary score, if any.  A non-empty result signals a potential
   * reclassification opportunity.
   */
  public java.util.Optional<CategoryCandidate> getBetterAlternative() {
    return getAlternatives().stream()
        .filter(c -> c.getScore() > primaryScore)
        .findFirst();
  }
}
