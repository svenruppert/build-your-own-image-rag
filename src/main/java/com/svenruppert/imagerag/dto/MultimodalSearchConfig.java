package com.svenruppert.imagerag.dto;

import com.svenruppert.imagerag.domain.SearchTuningConfig;
import com.svenruppert.imagerag.domain.enums.RetrievalMode;
import com.svenruppert.imagerag.domain.enums.SimilarityFunction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Configuration for a multimodal search that combines multiple input signals.
 * <p>Extends the retrieval parameters from {@link SearchTuningConfig} and adds
 * a list of {@link MultimodalSignal} instances. The search engine combines the
 * signals as follows:
 * <ul>
 *   <li>TEXT signals are embedded and their vectors averaged (weighted).</li>
 *   <li>IMAGE_EXAMPLE signals contribute their stored raw vectors (weighted).</li>
 *   <li>The combined vector is the weighted sum of all TEXT+IMAGE contributions,
 *       normalised to unit length before vector search.</li>
 *   <li>OCR_TERMS signals are appended to the BM25 query string.</li>
 *   <li>CATEGORY_FILTER signals restrict results post-fusion.</li>
 * </ul>
 */
public final class MultimodalSearchConfig {

  // ── Retrieval parameters (mirrors SearchTuningConfig) ──────────────────
  private final RetrievalMode retrievalMode;
  private final SimilarityFunction similarityFunction;
  private final double semanticWeight;
  private final double bm25Weight;
  private final double confidenceWeight;
  private final double scoreCutoff;
  private final int maxResults;

  // ── Multimodal signals ──────────────────────────────────────────────────
  private final List<MultimodalSignal> signals;

  /**
   * Whether the search-strategy autopilot influenced this run.
   */
  private final SearchStrategyPlan appliedAutopilotPlan;

  private MultimodalSearchConfig(Builder b) {
    this.retrievalMode = b.retrievalMode;
    this.similarityFunction = b.similarityFunction;
    this.semanticWeight = b.semanticWeight;
    this.bm25Weight = b.bm25Weight;
    this.confidenceWeight = b.confidenceWeight;
    this.scoreCutoff = b.scoreCutoff;
    this.maxResults = b.maxResults;
    this.signals = Collections.unmodifiableList(new ArrayList<>(b.signals));
    this.appliedAutopilotPlan = b.appliedAutopilotPlan;
  }

  // ── Accessors ───────────────────────────────────────────────────────────

  public static Builder builder() {
    return new Builder();
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

  public double getScoreCutoff() {
    return scoreCutoff;
  }

  public int getMaxResults() {
    return maxResults;
  }

  public List<MultimodalSignal> getSignals() {
    return signals;
  }

  public SearchStrategyPlan getAppliedAutopilotPlan() {
    return appliedAutopilotPlan;
  }

  /**
   * Signals of a specific type.
   */
  public List<MultimodalSignal> getSignals(MultimodalSignal.SignalType type) {
    return signals.stream().filter(s -> s.type() == type).toList();
  }

  // ── Builder ─────────────────────────────────────────────────────────────

  public boolean hasAutopilotPlan() {
    return appliedAutopilotPlan != null;
  }

  public static final class Builder {
    private final List<MultimodalSignal> signals = new ArrayList<>();
    private RetrievalMode retrievalMode = RetrievalMode.HYBRID;
    private SimilarityFunction similarityFunction = SimilarityFunction.COSINE;
    private double semanticWeight = 1.0;
    private double bm25Weight = 1.0;
    private double confidenceWeight = 0.15;
    private double scoreCutoff = 0.45;
    private int maxResults = 20;
    private SearchStrategyPlan appliedAutopilotPlan = null;

    public Builder retrievalMode(RetrievalMode m) {
      this.retrievalMode = m;
      return this;
    }

    public Builder similarityFunction(SimilarityFunction f) {
      this.similarityFunction = f;
      return this;
    }

    public Builder semanticWeight(double w) {
      this.semanticWeight = w;
      return this;
    }

    public Builder bm25Weight(double w) {
      this.bm25Weight = w;
      return this;
    }

    public Builder confidenceWeight(double w) {
      this.confidenceWeight = w;
      return this;
    }

    public Builder scoreCutoff(double c) {
      this.scoreCutoff = c;
      return this;
    }

    public Builder maxResults(int n) {
      this.maxResults = n;
      return this;
    }

    public Builder autopilotPlan(SearchStrategyPlan plan) {
      this.appliedAutopilotPlan = plan;
      return this;
    }

    public Builder addSignal(MultimodalSignal s) {
      this.signals.add(s);
      return this;
    }

    public Builder signals(List<MultimodalSignal> list) {
      this.signals.addAll(list);
      return this;
    }

    /**
     * Populate retrieval params from an existing tuning config.
     */
    public Builder fromTuningConfig(SearchTuningConfig cfg) {
      this.retrievalMode = cfg.getRetrievalMode();
      this.similarityFunction = cfg.getSimilarityFunction();
      this.semanticWeight = cfg.getSemanticWeight();
      this.bm25Weight = cfg.getBm25Weight();
      this.confidenceWeight = cfg.getConfidenceWeight();
      this.scoreCutoff = cfg.getScoreCutoff();
      this.maxResults = cfg.getMaxResults();
      return this;
    }

    public MultimodalSearchConfig build() {
      if (signals.isEmpty()) throw new IllegalStateException("At least one signal is required");
      return new MultimodalSearchConfig(this);
    }
  }
}
