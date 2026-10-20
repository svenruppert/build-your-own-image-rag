package com.svenruppert.imagerag.service.impl;

import com.svenruppert.imagerag.domain.CategoryConfidence;
import com.svenruppert.imagerag.domain.ImageAsset;
import com.svenruppert.imagerag.domain.SearchTuningConfig;
import com.svenruppert.imagerag.domain.enums.QueryIntentType;
import com.svenruppert.imagerag.persistence.PersistenceService;
import com.svenruppert.imagerag.service.QueryIntentResolver;

import java.util.UUID;

/**
 * Scoring rules shared by every search path and by the why-not-found diagnosis,
 * so that a diagnosis always reproduces the score the real search computes.
 */
final class SearchScoring {

  /**
   * Number of vector candidates retrieved per query.
   */
  static final int VECTOR_CANDIDATES = 50;
  /**
   * Number of BM25 candidates retrieved per query.
   */
  static final int KEYWORD_CANDIDATES = VECTOR_CANDIDATES * 2;
  /**
   * Confidence-boost weight used by the standard (non-tuning) search.
   */
  static final double STANDARD_CONFIDENCE_WEIGHT = 0.15;

  private static final QueryIntentResolver INTENT_RESOLVER = new QueryIntentResolver();

  private SearchScoring() {
  }

  /**
   * Applies the category-confidence boost {@code base * (1 + weight * primaryScore)},
   * capped at 1.0. Without confidence data or with a non-positive weight the base
   * score is returned unchanged.
   */
  static double boosted(double base, double weight, CategoryConfidence confidence) {
    return Math.min(1.0, base * (1.0 + boostFraction(weight, confidence)));
  }

  /**
   * The boost fraction {@code weight * primaryScore}, or {@code 0.0} when not applicable.
   */
  static double boostFraction(double weight, CategoryConfidence confidence) {
    return (weight > 0 && confidence != null) ? weight * confidence.getPrimaryScore() : 0.0;
  }

  /**
   * Resolves the query intent if enabled and returns the channel weights the
   * tuning search uses.
   */
  static EffectiveWeights effectiveWeights(String query, SearchTuningConfig config) {
    if (!config.isQueryIntentEnabled()) {
      return new EffectiveWeights(null, config.getSemanticWeight(), config.getBm25Weight());
    }
    QueryIntentType intent = INTENT_RESOLVER.resolve(query);
    double[] adjusted = INTENT_RESOLVER.adjustedWeights(
        intent, config.getSemanticWeight(), config.getBm25Weight());
    return new EffectiveWeights(intent, adjusted[0], adjusted[1]);
  }

  /**
   * An image is searchable when it is approved and not archived.
   */
  static boolean isSearchable(PersistenceService persistence, UUID imageId) {
    return persistence.isApproved(imageId)
        && persistence.findImage(imageId).map(asset -> !asset.isDeleted()).orElse(false);
  }

  /**
   * Returns {@code true} when the image exists and is archived.
   */
  static boolean isArchived(PersistenceService persistence, UUID imageId) {
    return persistence.findImage(imageId).map(ImageAsset::isDeleted).orElse(false);
  }

  /**
   * Channel weights after optional query-intent adjustment.
   */
  record EffectiveWeights(QueryIntentType intent, double semantic, double keyword) {
  }
}
