package com.svenruppert.imagerag.dto;

import com.svenruppert.imagerag.domain.SearchTuningConfig;
import com.svenruppert.imagerag.domain.enums.QueryIntentType;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Immutable snapshot of a complete Search Tuning Lab execution.
 * <p>Stored in the view as {@code currentRun} / {@code previousRun} so ranking
 * diffs can be computed between consecutive runs, and so the retrieval inspector
 * always reflects the exact parameters that produced the visible results.
 */
public class TuningRun {

  private final String query;
  private final SearchTuningConfig config;
  private final List<TuningSearchResult> results;
  private final int vectorCandidates;
  private final int keywordCandidates;
  private final long executionMs;
  private final Instant runAt;

  // ── Extended diagnostic fields ────────────────────────────────────────────

  /**
   * The intent resolved during this run, or {@code null} if intent detection was off.
   */
  private final QueryIntentType detectedIntent;

  /**
   * Number of feedback entries that actively influenced scoring in this run.
   * Zero if feedback was disabled or the session was empty.
   */
  private final int feedbackEntriesUsed;

  // ── Constructor ───────────────────────────────────────────────────────────

  public TuningRun(String query,
                   SearchTuningConfig config,
                   List<TuningSearchResult> results,
                   int vectorCandidates,
                   int keywordCandidates,
                   QueryIntentType detectedIntent,
                   int feedbackEntriesUsed,
                   long executionMs) {
    this.query = query;
    this.config = config;
    this.results = Collections.unmodifiableList(results);
    this.vectorCandidates = vectorCandidates;
    this.keywordCandidates = keywordCandidates;
    this.detectedIntent = detectedIntent;
    this.feedbackEntriesUsed = feedbackEntriesUsed;
    this.executionMs = executionMs;
    this.runAt = Instant.now();
  }

  // ── Static helpers ────────────────────────────────────────────────────────

  /**
   * Computes the rank delta of {@code imageId} relative to its position in
   * {@code previousRun}.
   * <ul>
   *   <li>Positive → moved up (current rank &lt; previous rank)</li>
   *   <li>Negative → moved down</li>
   *   <li>{@code Integer.MAX_VALUE} → appeared for the first time (NEW)</li>
   *   <li>{@code Integer.MIN_VALUE} → was in previous, absent now (DROPPED)</li>
   * </ul>
   */
  public static int rankDelta(UUID imageId, TuningRun current, TuningRun previous) {
    int curRank = current.rankOf(imageId);
    int prevRank = previous != null ? previous.rankOf(imageId) : -1;
    if (curRank < 0) return Integer.MIN_VALUE;
    if (prevRank < 0) return Integer.MAX_VALUE;
    return prevRank - curRank;   // positive = moved up
  }

  // ── Accessors ─────────────────────────────────────────────────────────────

  public String getQuery() {
    return query;
  }

  public SearchTuningConfig getConfig() {
    return config;
  }

  public List<TuningSearchResult> getResults() {
    return results;
  }

  public int getVectorCandidates() {
    return vectorCandidates;
  }

  public int getKeywordCandidates() {
    return keywordCandidates;
  }

  public long getExecutionMs() {
    return executionMs;
  }

  public Instant getRunAt() {
    return runAt;
  }

  public QueryIntentType getDetectedIntent() {
    return detectedIntent;
  }

  public int getFeedbackEntriesUsed() {
    return feedbackEntriesUsed;
  }

  /**
   * Returns the 0-based position of the given image in the results list,
   * or {@code -1} if it is absent.
   */
  public int rankOf(UUID imageId) {
    for (int i = 0; i < results.size(); i++) {
      if (imageId.equals(results.get(i).item().getImageId())) return i;
    }
    return -1;
  }
}
