package com.svenruppert.imagerag.dto;

/**
 * Per-result score breakdown for a tuning-lab search result.
 * <p>All contributions are expressed so the user can understand the relative
 * influence of each ranking signal.
 *
 * @param semanticContrib normalized weighted-RRF contribution from the vector channel;
 *                        zero when mode is BM25_ONLY or image was absent from vector results
 * @param bm25Contrib     normalized weighted-RRF contribution from the BM25 channel;
 *                        zero when mode is SEMANTIC_ONLY or image was absent from keyword results
 * @param confidenceBoost absolute boost fraction from category confidence
 *                        (e.g. 0.08 = +8 %; 0 if confidence weight is 0)
 * @param feedbackContrib absolute score delta from relevance feedback
 *                        (positive = boost from GOOD / MORE_LIKE_THIS examples;
 *                        negative = penalty from BAD examples; 0 if feedback disabled)
 * @param finalScore      combined score clamped to [0, 1] after all signals applied
 */
public record ScoreBreakdown(
    double semanticContrib,
    double bm25Contrib,
    double confidenceBoost,
    double feedbackContrib,
    double finalScore
) {

  /**
   * Sum of the two channel contributions before any boosts.
   */
  public double scoreBeforeBoost() {
    return semanticContrib + bm25Contrib;
  }

  /**
   * Fraction of the pre-boost score that came from the semantic channel.
   * Returns 0.5 when both channels contribute equally (avoids division by zero).
   */
  public double semanticFraction() {
    double base = scoreBeforeBoost();
    return base > 0 ? semanticContrib / base : 0.5;
  }

  /**
   * Fraction of the pre-boost score that came from the BM25 channel.
   * Returns 0.5 when both channels contribute equally.
   */
  public double bm25Fraction() {
    double base = scoreBeforeBoost();
    return base > 0 ? bm25Contrib / base : 0.5;
  }

  /**
   * Returns {@code true} if feedback influenced this result's score meaningfully.
   */
  public boolean hasFeedback() {
    return Math.abs(feedbackContrib) > 0.001;
  }
}
