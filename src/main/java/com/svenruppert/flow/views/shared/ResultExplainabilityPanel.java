package com.svenruppert.flow.views.shared;

import com.svenruppert.imagerag.domain.enums.RetrievalMode;
import com.svenruppert.imagerag.domain.enums.SimilarityFunction;
import com.svenruppert.imagerag.dto.MultimodalSearchResult;
import com.svenruppert.imagerag.dto.ScoreBreakdown;
import com.svenruppert.imagerag.dto.SearchStrategyPlan;
import com.svenruppert.imagerag.dto.TuningSearchResult;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.util.Map;

/**
 * Reusable Vaadin component that renders a structured, human-readable explanation
 * of why a search result appears where it does.
 * <p>The panel is designed to be embedded inside result cards (collapsible section)
 * or shown in a standalone dialog.  It renders:
 * <ul>
 *   <li>Score bar (semantic / BM25 / feedback colour-coded segments)</li>
 *   <li>Numeric score breakdown</li>
 *   <li>Retrieval mode and similarity function used</li>
 *   <li>Rank positions in each channel</li>
 *   <li>Autopilot plan summary (if applicable)</li>
 *   <li>Multimodal signal attribution (if applicable)</li>
 * </ul>
 * <p>All text is English-only (the panel targets the expert/technical audience)
 * and uses compact monospace formatting for scores.
 */
public class ResultExplainabilityPanel
    extends VerticalLayout {

  // ── Construction helpers ─────────────────────────────────────────────────

  private ResultExplainabilityPanel() {
    setPadding(false);
    setSpacing(false);
    setWidthFull();
    getStyle()
        .set("background", "var(--lumo-contrast-5pct)")
        .set("border-radius", "var(--lumo-border-radius-s)")
        .set("padding", "0.5rem 0.6rem")
        .set("font-family", "monospace")
        .set("font-size", "var(--lumo-font-size-xs)");
  }

  // ── Static factory for TuningSearchResult ───────────────────────────────

  /**
   * Creates a panel from a tuning-lab result with optional autopilot plan.
   *
   * @param tsr        the tuning result (contains ScoreBreakdown and channel ranks)
   * @param mode       retrieval mode that was active
   * @param similarity similarity function that was active
   * @param autopilot  the search-strategy plan the autopilot produced (may be null)
   */
  public static ResultExplainabilityPanel forTuningResult(
      TuningSearchResult tsr,
      RetrievalMode mode,
      SimilarityFunction similarity,
      SearchStrategyPlan autopilot) {

    ResultExplainabilityPanel panel = new ResultExplainabilityPanel();
    panel.add(buildScoreBar(tsr.breakdown()));
    panel.add(buildScoreText(tsr.breakdown()));
    panel.add(buildChannelLine(mode, similarity, tsr.vectorRank(), tsr.bm25Rank()));
    if (autopilot != null && autopilot.hasRecommendations()) {
      panel.add(buildAutopilotLine(autopilot));
    }
    return panel;
  }

  /**
   * Creates a panel from a multimodal search result.
   *
   * @param result     the multimodal result (contains ScoreBreakdown + signal attribution)
   * @param mode       retrieval mode that was active
   * @param similarity similarity function used
   * @param autopilot  the autopilot plan (may be null)
   */
  public static ResultExplainabilityPanel forMultimodalResult(
      MultimodalSearchResult result,
      RetrievalMode mode,
      SimilarityFunction similarity,
      SearchStrategyPlan autopilot) {

    ResultExplainabilityPanel panel = new ResultExplainabilityPanel();
    panel.add(buildScoreBar(result.breakdown()));
    panel.add(buildScoreText(result.breakdown()));
    panel.add(buildChannelLine(mode, similarity, result.vectorRank(), result.bm25Rank()));

    // Signal attribution
    Map<String, Double> contribs = result.signalContributions();
    if (!contribs.isEmpty()) {
      panel.add(buildSignalAttributionLine(contribs));
    }

    if (autopilot != null && autopilot.hasRecommendations()) {
      panel.add(buildAutopilotLine(autopilot));
    }
    return panel;
  }

  // ── Rendering helpers ────────────────────────────────────────────────────

  private static Div buildScoreBar(ScoreBreakdown bd) {
    Div barWrap = new Div();
    barWrap.getStyle()
        .set("display", "flex").set("height", "6px")
        .set("border-radius", "3px").set("overflow", "hidden")
        .set("background", "var(--lumo-contrast-10pct)")
        .set("margin-bottom", "0.35rem").set("width", "100%");

    double semW = Math.round(bd.semanticFraction() * 80);
    double bm25W = Math.round(bd.bm25Fraction() * 80);
    double fbW = 0;
    if (bd.hasFeedback()) {
      double base = Math.max(bd.scoreBeforeBoost(), 0.001);
      fbW = Math.round(Math.min(Math.abs(bd.feedbackContrib()) / base, 0.15) * 80);
    }

    appendBarSeg(barWrap, semW, "var(--lumo-primary-color)");
    appendBarSeg(barWrap, bm25W, "var(--lumo-success-color)");
    if (bd.hasFeedback()) {
      appendBarSeg(barWrap, fbW, bd.feedbackContrib() > 0 ? "#f59e0b" : "var(--lumo-error-color)");
    }
    return barWrap;
  }

  private static void appendBarSeg(Div parent, double width, String color) {
    if (width <= 0) return;
    Div seg = new Div();
    seg.getStyle().set("width", width + "%").set("background", color).set("flex-shrink", "0");
    parent.add(seg);
  }

  private static Span buildScoreText(ScoreBreakdown bd) {
    StringBuilder sb = new StringBuilder();
    sb.append(String.format("Score %.3f", bd.finalScore()));
    if (bd.semanticContrib() > 0.0001) sb.append(String.format("  │  Sem %.3f", bd.semanticContrib()));
    if (bd.bm25Contrib() > 0.0001) sb.append(String.format("  │  BM25 %.3f", bd.bm25Contrib()));
    if (bd.confidenceBoost() > 0.001) sb.append(String.format("  │  Conf +%.0f%%", bd.confidenceBoost() * 100));
    if (bd.hasFeedback()) sb.append(String.format("  │  Feedback %+.3f", bd.feedbackContrib()));
    return styledSpan(sb.toString(), "var(--lumo-body-text-color)");
  }

  private static Span buildChannelLine(
      RetrievalMode mode, SimilarityFunction sim, int vRank, int bm25Rank) {
    StringBuilder sb = new StringBuilder();
    sb.append("Mode: ").append(mode.getLabel());
    sb.append("  │  Similarity: ").append(sim.getLabel());
    if (vRank >= 0) sb.append(String.format("  │  Vector rank: %d", vRank + 1));
    if (bm25Rank >= 0) sb.append(String.format("  │  BM25 rank: %d", bm25Rank + 1));
    if (vRank < 0) sb.append("  │  Not in vector hits");
    if (bm25Rank < 0) sb.append("  │  Not in BM25 hits");
    return styledSpan(sb.toString(), "var(--lumo-secondary-text-color)");
  }

  private static Span buildAutopilotLine(SearchStrategyPlan plan) {
    String text = "Autopilot: " + plan.shortSummary()
        + (plan.confidence() > 0 ? String.format(" (confidence %.0f%%)", plan.confidence() * 100) : "");
    return styledSpan(text, "#f59e0b"); // amber — autopilot highlight
  }

  private static Span buildSignalAttributionLine(Map<String, Double> contribs) {
    StringBuilder sb = new StringBuilder("Signals:");
    contribs.forEach((key, val) -> sb.append(String.format("  %s %.0f%%", key, val * 100)));
    return styledSpan(sb.toString(), "var(--lumo-success-color)");
  }

  private static Span styledSpan(String text, String color) {
    Span s = new Span(text);
    s.getStyle()
        .set("display", "block")
        .set("color", color)
        .set("white-space", "pre-wrap")
        .set("overflow-x", "auto");
    return s;
  }
}
