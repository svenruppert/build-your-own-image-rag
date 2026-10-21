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
package com.svenruppert.imagerag.dto;

import com.svenruppert.imagerag.domain.enums.QueryIntentType;
import com.svenruppert.imagerag.domain.enums.RetrievalMode;
import com.svenruppert.imagerag.domain.enums.SimilarityFunction;

import java.util.List;

/**
 * The output of {@link com.svenruppert.imagerag.service.SearchStrategyAutopilot}.
 * <p>Contains both the recommended strategy (mode, weights, similarity function)
 * and a human-readable explanation of why those values were chosen.  The explanation
 * is shown in the Search Tuning Lab and the explainability panel so the user always
 * knows what the autopilot decided and why.
 * <p>A plan can be applied to a {@link com.svenruppert.imagerag.domain.SearchTuningConfig}
 * via the autopilot or overridden manually by the user.
 */
public record SearchStrategyPlan(
    /** Detected query intent (may be UNKNOWN). */
    QueryIntentType detectedIntent,
    /** Recommended retrieval mode. */
    RetrievalMode recommendedMode,
    /** Recommended similarity function. */
    SimilarityFunction recommendedSimilarity,
    /** Recommended semantic-channel weight (may override user config). */
    double recommendedSemanticWeight,
    /** Recommended BM25-channel weight (may override user config). */
    double recommendedBm25Weight,
    /**
     * Ordered list of reasoning signals the autopilot considered.
     * Each entry is a human-readable sentence, e.g.
     * "Query is OCR-heavy → boosting BM25 weight to 2.5×".
     */
    List<String> reasons,
    /**
     * Confidence score (0–1) that the autopilot's recommendation is appropriate.
     * Higher = more certainty.
     */
    double confidence
) {

  /**
   * Summary line for display in compact UI areas.
   */
  public String shortSummary() {
    return recommendedMode.getLabel()
        + " · Sem " + String.format("%.2f", recommendedSemanticWeight)
        + " · BM25 " + String.format("%.2f", recommendedBm25Weight)
        + (detectedIntent != null && detectedIntent != QueryIntentType.UNKNOWN
        ? " · " + detectedIntent.getLabel() : "");
  }

  /**
   * True if the autopilot recommends overriding the current mode or weights.
   */
  public boolean hasRecommendations() {
    return !reasons.isEmpty();
  }
}
