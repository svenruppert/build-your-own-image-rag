package com.svenruppert.imagerag.domain;

import com.svenruppert.imagerag.domain.enums.RetrievalMode;
import com.svenruppert.imagerag.domain.enums.SimilarityFunction;
import com.svenruppert.imagerag.dto.FeedbackSession;

import java.util.UUID;

/**
 * All parameters that control a single Search Tuning Lab run.
 * <p>A new instance is built from the UI controls before each run via
 * {@code SearchTuningView.buildConfig()}.  The instance is embedded in
 * {@link com.svenruppert.imagerag.dto.TuningRun} so results always carry the
 * exact configuration that produced them.
 * <h3>Defaults</h3>
 * <ul>
 *   <li>Mode:              {@link RetrievalMode#HYBRID}</li>
 *   <li>Similarity:        {@link SimilarityFunction#COSINE}</li>
 *   <li>Semantic weight:   1.0 (equal-weight hybrid)</li>
 *   <li>BM25 weight:       1.0</li>
 *   <li>Confidence weight: 0.15 (matches the production search boost)</li>
 *   <li>Feedback:          disabled; weight 0.5</li>
 *   <li>Score cutoff:      0.45 (lower than production 0.65 to surface more candidates)</li>
 *   <li>Max results:       20</li>
 * </ul>
 * <h3>Transient fields</h3>
 * <p>{@link #feedbackSession} and {@link #queryByExampleImageId} are session-only
 * references that are <em>not</em> persisted in
 * {@link SearchTuningPreset}.  They are set by the view for each run.
 */
public class SearchTuningConfig {

  // ── Defaults ──────────────────────────────────────────────────────────────
  public static final double DEFAULT_SEMANTIC_WEIGHT = 1.0;
  public static final double DEFAULT_BM25_WEIGHT = 1.0;
  public static final double DEFAULT_CONFIDENCE_WEIGHT = 0.15;
  public static final double DEFAULT_FEEDBACK_WEIGHT = 0.5;
  public static final double DEFAULT_SCORE_CUTOFF = 0.45;
  public static final int DEFAULT_MAX_RESULTS = 20;

  // ── Persistent algorithmic parameters ────────────────────────────────────
  private RetrievalMode retrievalMode = RetrievalMode.HYBRID;
  private SimilarityFunction similarityFunction = SimilarityFunction.COSINE;
  private double semanticWeight = DEFAULT_SEMANTIC_WEIGHT;
  private double bm25Weight = DEFAULT_BM25_WEIGHT;
  private double confidenceWeight = DEFAULT_CONFIDENCE_WEIGHT;
  private double scoreCutoff = DEFAULT_SCORE_CUTOFF;
  private int maxResults = DEFAULT_MAX_RESULTS;

  // ── Relevance feedback ────────────────────────────────────────────────────
  private boolean feedbackEnabled = false;
  private double feedbackWeight = DEFAULT_FEEDBACK_WEIGHT;

  /**
   * Transient — not stored in SearchTuningPreset. Set by the view.
   */
  private transient FeedbackSession feedbackSession = null;

  // ── Query-by-Example ──────────────────────────────────────────────────────
  /**
   * If non-null, the vector of this image is used as the query vector instead
   * of embedding the text.  Transient — not stored in SearchTuningPreset.
   */
  private transient UUID queryByExampleImageId = null;

  // ── Query-intent detection ────────────────────────────────────────────────
  /**
   * When true, {@link com.svenruppert.imagerag.service.QueryIntentResolver}
   * classifies the query and adjusts channel weights accordingly.  The user
   * can still see and override the adjusted weights in the UI.
   */
  private boolean queryIntentEnabled = false;

  // ── Accessors — retrieval parameters ─────────────────────────────────────

  public RetrievalMode getRetrievalMode() {
    return retrievalMode;
  }

  public SearchTuningConfig setRetrievalMode(RetrievalMode m) {
    this.retrievalMode = m;
    return this;
  }

  public SimilarityFunction getSimilarityFunction() {
    return similarityFunction;
  }

  public SearchTuningConfig setSimilarityFunction(SimilarityFunction f) {
    this.similarityFunction = f;
    return this;
  }

  public double getSemanticWeight() {
    return semanticWeight;
  }

  public SearchTuningConfig setSemanticWeight(double w) {
    this.semanticWeight = Math.max(0.0, w);
    return this;
  }

  public double getBm25Weight() {
    return bm25Weight;
  }

  public SearchTuningConfig setBm25Weight(double w) {
    this.bm25Weight = Math.max(0.0, w);
    return this;
  }

  public double getConfidenceWeight() {
    return confidenceWeight;
  }

  public SearchTuningConfig setConfidenceWeight(double w) {
    this.confidenceWeight = Math.max(0.0, Math.min(1.0, w));
    return this;
  }

  public double getScoreCutoff() {
    return scoreCutoff;
  }

  public SearchTuningConfig setScoreCutoff(double c) {
    this.scoreCutoff = Math.max(0.0, Math.min(1.0, c));
    return this;
  }

  public int getMaxResults() {
    return maxResults;
  }

  public SearchTuningConfig setMaxResults(int n) {
    this.maxResults = Math.max(1, Math.min(100, n));
    return this;
  }

  // ── Accessors — feedback ──────────────────────────────────────────────────

  public boolean isFeedbackEnabled() {
    return feedbackEnabled;
  }

  public SearchTuningConfig setFeedbackEnabled(boolean b) {
    this.feedbackEnabled = b;
    return this;
  }

  public double getFeedbackWeight() {
    return feedbackWeight;
  }

  public SearchTuningConfig setFeedbackWeight(double w) {
    this.feedbackWeight = Math.max(0.0, Math.min(2.0, w));
    return this;
  }

  /**
   * The session snapshot to use in the search thread; may be {@code null}.
   */
  public FeedbackSession getFeedbackSession() {
    return feedbackSession;
  }

  public SearchTuningConfig setFeedbackSession(FeedbackSession s) {
    this.feedbackSession = s;
    return this;
  }

  // ── Accessors — QBE ──────────────────────────────────────────────────────

  public UUID getQueryByExampleImageId() {
    return queryByExampleImageId;
  }

  public SearchTuningConfig setQueryByExampleImageId(UUID id) {
    this.queryByExampleImageId = id;
    return this;
  }

  // ── Accessors — intent ────────────────────────────────────────────────────

  public boolean isQueryIntentEnabled() {
    return queryIntentEnabled;
  }

  public SearchTuningConfig setQueryIntentEnabled(boolean b) {
    this.queryIntentEnabled = b;
    return this;
  }

  // ── Diagnostics ───────────────────────────────────────────────────────────

  @Override
  public String toString() {
    return String.format(
        "TuningConfig{mode=%s, fn=%s, sem=%.2f, bm25=%.2f, conf=%.2f,"
            + " fb=%s(w=%.2f), qbe=%s, intent=%s, cutoff=%.2f, max=%d}",
        retrievalMode, similarityFunction, semanticWeight, bm25Weight, confidenceWeight,
        feedbackEnabled, feedbackWeight,
        queryByExampleImageId != null ? queryByExampleImageId : "none",
        queryIntentEnabled,
        scoreCutoff, maxResults);
  }
}
