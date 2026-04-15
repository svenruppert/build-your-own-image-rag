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
