package com.svenruppert.flow.views.search;

import com.svenruppert.imagerag.domain.SearchPlan;
import com.svenruppert.imagerag.domain.SearchResultItem;
import com.svenruppert.imagerag.domain.enums.SearchMode;

import java.util.List;

/**
 * Volatile holder for the in-progress background search of the {@link SearchView}.
 * <p>Written by the background executor thread; read by the Vaadin UI progress handler.
 * All fields are {@code volatile} to ensure visibility across threads without locking.
 * {@link #stepProgress} drives real-time inspector updates while the search runs.
 */
class SearchRunState {
  /**
   * i18n key for the current status label (e.g. "search.status.analyzing").
   */
  volatile String status = "search.status.init";
  volatile SearchPlan plan = null;
  volatile List<SearchResultItem> results = null;
  volatile String error = null;
  volatile boolean done = false;
  /**
   * Whether this run stops after LLM analysis (no vector search).
   */
  volatile boolean transformOnly = false;
  volatile SearchMode mode = SearchMode.TRANSFORM_AND_EXECUTE;
  /**
   * Original user query; null for refine runs (which are not persisted to history).
   */
  volatile String originalQuery = null;
  /**
   * Effective score threshold used for this search run.
   * Copied from the UI score field at the moment the run starts; passed to the
   * search plan so the service applies exactly the threshold the user configured.
   */
  volatile double minScore = 0.45;
  /**
   * Step progress counter for live inspector updates.
   * 0 = starting, 1 = LLM started, 2 = LLM done, 3 = search started, 4 = search done.
   */
  volatile int stepProgress = 0;
  /**
   * Number of vector candidates returned by the vector index before RRF fusion.
   */
  volatile int vectorCandidates = 0;
  /**
   * Number of BM25 candidates returned by the keyword index before RRF fusion.
   */
  volatile int keywordCandidates = 0;
}
