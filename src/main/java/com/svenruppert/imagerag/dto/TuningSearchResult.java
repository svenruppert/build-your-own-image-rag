package com.svenruppert.imagerag.dto;

import com.svenruppert.imagerag.domain.SearchResultItem;

/**
 * A single result entry from a Search Tuning Lab run.
 *
 * <p>Extends the standard {@link SearchResultItem} with tuning-specific data:
 * per-channel rank information and a full {@link ScoreBreakdown} that explains
 * exactly how the final score was derived.
 *
 * @param item       The standard result item (imageId, title, summary, score, …).
 * @param breakdown  Score contributions broken down by retrieval channel.
 * @param vectorRank 0-based rank in the vector search results before fusion.
 *                   {@code -1} if the image did not appear in the vector results
 *                   (e.g., BM25-only mode, or image outside the top-N vector pool).
 * @param bm25Rank   0-based rank in the BM25 keyword search results before fusion.
 *                   {@code -1} if the image did not appear in the keyword results.
 */
public record TuningSearchResult(
    SearchResultItem item,
    ScoreBreakdown breakdown,
    int vectorRank,
    int bm25Rank
) {

  /**
   * {@code true} if this result came from the semantic channel.
   */
  public boolean isInVectorResults() {
    return vectorRank >= 0;
  }

  /**
   * {@code true} if this result came from the BM25 channel.
   */
  public boolean isInBm25Results() {
    return bm25Rank >= 0;
  }

  /**
   * {@code true} if the result appeared in both channels (truly hybrid).
   */
  public boolean isInBothChannels() {
    return vectorRank >= 0 && bm25Rank >= 0;
  }
}
