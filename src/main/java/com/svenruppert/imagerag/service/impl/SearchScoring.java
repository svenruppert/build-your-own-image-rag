/*
 * Copyright © 2026 Sven Ruppert (sven.ruppert@gmail.com)
 *
 * Licensed under the EUPL, Version 1.2 (the "Licence");
 * you may not use this file except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *     https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */
package com.svenruppert.imagerag.service.impl;

import com.svenruppert.imagerag.settings.SettingsService;
import com.svenruppert.imagerag.settings.Settings;
import com.svenruppert.imagerag.domain.CategoryConfidence;
import com.svenruppert.imagerag.domain.ImageAsset;
import com.svenruppert.imagerag.domain.SearchTuningConfig;
import com.svenruppert.imagerag.domain.enums.QueryIntentType;
import com.svenruppert.imagerag.dto.KeywordSearchHit;
import com.svenruppert.imagerag.dto.VectorSearchHit;
import com.svenruppert.imagerag.persistence.PersistenceService;
import com.svenruppert.imagerag.service.QueryIntentResolver;

import java.util.List;
import java.util.UUID;

/**
 * Scoring rules shared by every search path and by the why-not-found diagnosis,
 * so that a diagnosis always reproduces the score the real search computes.
 */
final class SearchScoring {


  private static final QueryIntentResolver INTENT_RESOLVER = new QueryIntentResolver();

  private SearchScoring() {
  }

  /**
   * Number of vector candidates retrieved per query ({@link Settings#SEARCH_VECTOR_CANDIDATES}).
   */
  static int vectorCandidates() {
    return SettingsService.getInstance().get(Settings.SEARCH_VECTOR_CANDIDATES);
  }

  /**
   * Number of BM25 candidates retrieved per query ({@link Settings#SEARCH_KEYWORD_CANDIDATES}).
   */
  static int keywordCandidates() {
    return SettingsService.getInstance().get(Settings.SEARCH_KEYWORD_CANDIDATES);
  }

  /**
   * Confidence-boost weight of the standard search ({@link Settings#SEARCH_CONFIDENCE_WEIGHT}).
   */
  static double standardConfidenceWeight() {
    return SettingsService.getInstance().get(Settings.SEARCH_CONFIDENCE_WEIGHT);
  }

  /**
   * RRF rank constant k ({@link Settings#SEARCH_RRF_K}).
   */
  static int rrfK() {
    return SettingsService.getInstance().get(Settings.SEARCH_RRF_K);
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
   * Image ids of vector hits in rank order.
   */
  static List<UUID> vectorIds(List<VectorSearchHit> hits) {
    return hits.stream().map(VectorSearchHit::imageId).toList();
  }

  /**
   * Image ids of keyword hits in rank order.
   */
  static List<UUID> keywordIds(List<KeywordSearchHit> hits) {
    return hits.stream().map(KeywordSearchHit::imageId).toList();
  }

  /**
   * Channel weights after optional query-intent adjustment.
   */
  record EffectiveWeights(QueryIntentType intent, double semantic, double keyword) {
  }
}
