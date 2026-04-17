package com.svenruppert.imagerag.domain;

import com.svenruppert.imagerag.domain.enums.SourceCategory;

/**
 * A single alternative category candidate produced during classification.
 * <p>Stored as part of {@link CategoryConfidence} to preserve the top-N alternatives
 * from an image classification pass.  This information is used by the taxonomy
 * maintenance workflow and can improve search ranking when a query matches
 * alternative categories.
 * <p>Scores are model-produced confidence values in [0.0, 1.0].  They reflect the
 * model's relative preference rather than a statistically calibrated probability.
 */
public class CategoryCandidate {

  private SourceCategory category;

  /**
   * Model-produced confidence value in [0.0, 1.0].
   * Higher means the model considers this a better fit.
   */
  private double score;

  public CategoryCandidate() {
  }

  public CategoryCandidate(SourceCategory category, double score) {
    this.category = category;
    this.score = Math.max(0.0, Math.min(1.0, score));
  }

  public SourceCategory getCategory() {
    return category;
  }

  public void setCategory(SourceCategory category) {
    this.category = category;
  }

  public double getScore() {
    return score;
  }

  public void setScore(double score) {
    this.score = Math.max(0.0, Math.min(1.0, score));
  }

  @Override
  public String toString() {
    return category + "(" + String.format("%.2f", score) + ")";
  }
}
