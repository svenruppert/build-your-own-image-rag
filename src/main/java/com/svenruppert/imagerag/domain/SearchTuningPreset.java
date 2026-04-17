package com.svenruppert.imagerag.domain;

import com.svenruppert.imagerag.domain.enums.RetrievalMode;
import com.svenruppert.imagerag.domain.enums.SimilarityFunction;

import java.time.Instant;
import java.util.UUID;

/**
 * Persisted snapshot of a named Search Tuning Lab configuration.
 * <p>Only the <em>algorithmic</em> parameters are saved — not the transient
 * session state such as {@link com.svenruppert.imagerag.dto.FeedbackSession} or
 * the resolved {@link com.svenruppert.imagerag.domain.enums.QueryIntentType}.
 * <p>Persisted via EclipseStore in
 * {@link com.svenruppert.imagerag.persistence.AppDataRoot#getTuningPresets()}.
 * Follows the same no-arg-constructor convention as
 * {@link com.svenruppert.imagerag.domain.SavedSearchView}.
 */
public class SearchTuningPreset {

  private UUID id;
  private String name;
  private String query;

  // ── Retrieval parameters ──────────────────────────────────────────────────
  private RetrievalMode retrievalMode;
  private SimilarityFunction similarityFunction;
  private double semanticWeight;
  private double bm25Weight;
  private double confidenceWeight;

  // ── Feedback parameters (state captured, not the session itself) ──────────
  private boolean feedbackEnabled;
  private double feedbackWeight;

  // ── Cutoff & output ───────────────────────────────────────────────────────
  private double scoreCutoff;
  private int maxResults;

  // ── Miscellaneous ─────────────────────────────────────────────────────────
  private boolean queryIntentEnabled;
  private Instant savedAt;

  /**
   * No-arg constructor required for EclipseStore Unsafe reconstruction.
   */
  public SearchTuningPreset() {
  }

  /**
   * Convenience factory — captures the current UI config into a named preset.
   *
   * @param name  user-chosen preset name
   * @param query the query string at save time (may be empty for QBE-centric presets)
   * @param cfg   the active {@link SearchTuningConfig}
   */
  public static SearchTuningPreset from(String name, String query, SearchTuningConfig cfg) {
    SearchTuningPreset p = new SearchTuningPreset();
    p.id = UUID.randomUUID();
    p.name = name;
    p.query = query != null ? query : "";
    p.retrievalMode = cfg.getRetrievalMode();
    p.similarityFunction = cfg.getSimilarityFunction();
    p.semanticWeight = cfg.getSemanticWeight();
    p.bm25Weight = cfg.getBm25Weight();
    p.confidenceWeight = cfg.getConfidenceWeight();
    p.feedbackEnabled = cfg.isFeedbackEnabled();
    p.feedbackWeight = cfg.getFeedbackWeight();
    p.scoreCutoff = cfg.getScoreCutoff();
    p.maxResults = cfg.getMaxResults();
    p.queryIntentEnabled = cfg.isQueryIntentEnabled();
    p.savedAt = Instant.now();
    return p;
  }

  /**
   * Restores a {@link SearchTuningConfig} from this preset.
   * Transient fields (feedbackSession, queryByExampleImageId) remain at their defaults.
   */
  public SearchTuningConfig toConfig() {
    return new SearchTuningConfig()
        .setRetrievalMode(retrievalMode != null ? retrievalMode : RetrievalMode.HYBRID)
        .setSimilarityFunction(similarityFunction != null
                                   ? similarityFunction : SimilarityFunction.COSINE)
        .setSemanticWeight(semanticWeight)
        .setBm25Weight(bm25Weight)
        .setConfidenceWeight(confidenceWeight)
        .setFeedbackEnabled(feedbackEnabled)
        .setFeedbackWeight(feedbackWeight)
        .setScoreCutoff(scoreCutoff)
        .setMaxResults(maxResults)
        .setQueryIntentEnabled(queryIntentEnabled);
  }

  // ── Accessors ─────────────────────────────────────────────────────────────

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getQuery() {
    return query;
  }

  public RetrievalMode getRetrievalMode() {
    return retrievalMode;
  }

  public SimilarityFunction getSimilarityFunction() {
    return similarityFunction;
  }

  public double getSemanticWeight() {
    return semanticWeight;
  }

  public double getBm25Weight() {
    return bm25Weight;
  }

  public double getConfidenceWeight() {
    return confidenceWeight;
  }

  public boolean isFeedbackEnabled() {
    return feedbackEnabled;
  }

  public double getFeedbackWeight() {
    return feedbackWeight;
  }

  public double getScoreCutoff() {
    return scoreCutoff;
  }

  public int getMaxResults() {
    return maxResults;
  }

  public boolean isQueryIntentEnabled() {
    return queryIntentEnabled;
  }

  public Instant getSavedAt() {
    return savedAt;
  }

  @Override
  public String toString() {
    return "SearchTuningPreset{name='" + name + "', mode=" + retrievalMode
        + ", sem=" + semanticWeight + ", bm25=" + bm25Weight + ", cutoff=" + scoreCutoff + "}";
  }
}
