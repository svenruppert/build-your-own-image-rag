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

import com.svenruppert.imagerag.domain.SearchResultItem;

import java.util.List;

/**
 * Wraps the result of a planned search, including the ranked result items
 * and diagnostic counts for the hybrid retrieval channels (vector + BM25).
 * These counts are surfaced in the SearchInspectorComponent for transparency.
 */
public record SearchResult(
    List<SearchResultItem> items,
    int vectorCandidates,
    int keywordCandidates
) {
  /**
   * Convenience zero-result instance.
   */
  public static SearchResult empty() {
    return new SearchResult(List.of(), 0, 0);
  }
}
