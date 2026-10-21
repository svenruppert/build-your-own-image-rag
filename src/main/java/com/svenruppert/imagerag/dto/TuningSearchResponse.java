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

import java.util.List;

/**
 * Return value of
 * {@link com.svenruppert.imagerag.service.SearchService#searchWithTuning}.
 * <p>Carries the ranked result list together with diagnostic information that
 * cannot be derived from the final result set alone:
 * <ul>
 *   <li>Pre-fusion candidate counts — shown in the retrieval pipeline inspector</li>
 *   <li>The detected query intent — displayed in the inspector and summary bar</li>
 *   <li>The number of feedback entries that actively influenced scoring</li>
 * </ul>
 *
 * @param results             ranked results with per-result score breakdowns
 * @param vectorCandidates    candidates returned by vector/HNSW search before fusion
 *                            (0 if the retrieval mode skips vector search)
 * @param keywordCandidates   candidates returned by BM25 keyword search before fusion
 *                            (0 if the mode skips BM25)
 * @param detectedIntent      the intent resolved for this run; {@code null} if detection
 *                            was disabled in the config
 * @param feedbackEntriesUsed number of feedback examples that influenced scoring;
 *                            0 if feedback was disabled or the session was empty
 */
public record TuningSearchResponse(
    List<TuningSearchResult> results,
    int vectorCandidates,
    int keywordCandidates,
    QueryIntentType detectedIntent,
    int feedbackEntriesUsed
) { }
