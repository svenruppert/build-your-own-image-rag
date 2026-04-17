package com.svenruppert.imagerag.service;

import com.svenruppert.imagerag.domain.SearchPlan;
import com.svenruppert.imagerag.domain.SearchResultItem;
import com.svenruppert.imagerag.domain.SearchTuningConfig;
import com.svenruppert.imagerag.dto.SearchResult;
import com.svenruppert.imagerag.dto.TuningSearchResponse;

import java.util.List;

public interface SearchService {

  List<SearchResultItem> search(String naturalLanguageQuery);

  /**
   * Executes a planned search and returns results with hybrid-retrieval diagnostics.
   */
  SearchResult search(SearchPlan plan);

  /**
   * Executes a tuning-lab search using the supplied configuration.
   *
   * <p>Unlike the regular {@link #search} methods, this path:
   * <ul>
   *   <li>Skips LLM query understanding — the raw {@code query} string is used
   *       directly as the embedding input and BM25 query text.</li>
   *   <li>Applies the user-configured retrieval mode, similarity function, and
   *       per-channel weights.</li>
   *   <li>Returns each result with a full {@link com.svenruppert.imagerag.dto.ScoreBreakdown}
   *       showing how the final score was derived.</li>
   *   <li>Does not apply structural filters (person / vehicle / season / privacy) —
   *       the tuning lab focuses on raw retrieval ranking, not filter behavior.</li>
   *   <li>The privacy gate ({@code isApproved}) is still enforced.</li>
   * </ul>
   *
   * <p>The returned {@link TuningSearchResponse} includes the pre-fusion candidate
   * counts from each channel so the retrieval inspector can show accurate pipeline
   * stats (e.g. "50 vector candidates", "100 keyword candidates").
   *
   * @param query  raw text to embed and/or search with BM25
   * @param config tuning parameters (mode, similarity function, weights, cutoff, max results)
   * @return response containing ranked results with score breakdowns and raw candidate counts
   */
  TuningSearchResponse searchWithTuning(String query, SearchTuningConfig config);
}
