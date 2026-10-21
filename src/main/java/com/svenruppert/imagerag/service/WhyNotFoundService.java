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
package com.svenruppert.imagerag.service;

import com.svenruppert.imagerag.domain.SearchTuningConfig;
import com.svenruppert.imagerag.dto.WhyNotFoundAnalysis;

import java.util.UUID;

/**
 * Diagnostic service that explains why a specific image did not appear in a
 * given search result.
 * <p>The analysis re-runs the relevant retrieval pipeline sub-steps for the
 * target image and collects concrete exclusion reasons.  Results are returned
 * as a structured {@link WhyNotFoundAnalysis} — never as a vague generic message.
 */
public interface WhyNotFoundService {

  /**
   * Analyses why {@code imageId} was absent from the results for {@code query}
   * using the given retrieval configuration.
   * <p>The method is synchronous and may call the vector index and keyword index
   * to compute scores.  It does <em>not</em> call any LLM.
   *
   * @param imageId the image to investigate
   * @param query   the query that produced the search result
   * @param config  the retrieval configuration active during the search
   * @return a fully populated {@link WhyNotFoundAnalysis}
   */
  WhyNotFoundAnalysis analyze(UUID imageId, String query, SearchTuningConfig config);
}
