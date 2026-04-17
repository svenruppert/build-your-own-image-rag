package com.svenruppert.flow.views.tuning;

import com.svenruppert.imagerag.domain.enums.QueryIntentType;
import com.svenruppert.imagerag.domain.enums.RetrievalMode;
import com.svenruppert.imagerag.domain.SearchTuningConfig;
import com.svenruppert.imagerag.dto.TuningRun;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tuning-lab retrieval pipeline inspector.
 *
 * <p>Shows 10 pipeline steps for the last tuning run as a vertical timeline, colour-coded
 * by status (PENDING / ACTIVE / COMPLETED / SKIPPED).  Call {@link #showRun(TuningRun)}
 * after a run completes to update the display.  Call {@link #reset()} before starting
 * a new run.
 *
 * <h3>Steps</h3>
 * <ol>
 *   <li>Query Input</li>
 *   <li>Query Intent (skipped when intent detection is off)</li>
 *   <li>Embedding Generation (skipped for BM25-only; uses stored vector in QBE mode)</li>
 *   <li>Vector Retrieval (skipped for BM25-only)</li>
 *   <li>BM25 Retrieval (skipped for semantic-only)</li>
 *   <li>Score Fusion</li>
 *   <li>Confidence Boost</li>
 *   <li>Relevance Feedback (skipped when feedback is off or session is empty)</li>
 *   <li>Score Filtering</li>
 *   <li>Result Assembly</li>
 * </ol>
 */
public class TuningInspectorComponent extends VerticalLayout {

  // ── Status colours — mirrors SearchInspectorComponent ────────────────────
  private static final String COLOR_PENDING   = "var(--lumo-contrast-30pct)";
  private static final String COLOR_ACTIVE    = "var(--lumo-primary-color)";
  private static final String COLOR_COMPLETED = "var(--lumo-success-color)";
  private static final String COLOR_SKIPPED   = "var(--lumo-contrast-15pct)";

  // ── Step constants ────────────────────────────────────────────────────────
  static final String STEP_QUERY      = "Query Input";
  static final String STEP_INTENT     = "Query Intent";
  static final String STEP_EMBED      = "Embedding";
  static final String STEP_VECTOR     = "Vector Retrieval";
  static final String STEP_BM25       = "BM25 Retrieval";
  static final String STEP_FUSION     = "Score Fusion";
  static final String STEP_CONFIDENCE = "Confidence Boost";
  static final String STEP_FEEDBACK   = "Relevance Feedback";
  static final String STEP_FILTER     = "Score Filtering";
  static final String STEP_RESULT     = "Result Assembly";

  static final String[] ALL_STEPS = {
      STEP_QUERY, STEP_INTENT, STEP_EMBED, STEP_VECTOR, STEP_BM25,
      STEP_FUSION, STEP_CONFIDENCE, STEP_FEEDBACK, STEP_FILTER, STEP_RESULT
  };

  // ── Step rows ─────────────────────────────────────────────────────────────
  private final Map<String, StepRow> rows = new LinkedHashMap<>();

  public TuningInspectorComponent() {
    setPadding(false);
    setSpacing(false);
    getStyle()
        .set("border", "1px solid var(--lumo-contrast-10pct)")
        .set("border-radius", "var(--lumo-border-radius-l)")
        .set("padding", "0.5rem 0.75rem")
        .set("background", "var(--lumo-base-color)")
        .set("width", "100%");

    H4 heading = new H4("Retrieval Pipeline");
    heading.getStyle()
        .set("margin", "0 0 0.4rem 0")
        .set("font-size", "var(--lumo-font-size-s)")
        .set("color", "var(--lumo-secondary-text-color)")
        .set("text-transform", "uppercase")
        .set("letter-spacing", "0.05em");
    add(heading);

    for (int i = 0; i < ALL_STEPS.length; i++) {
      StepRow row = new StepRow(String.valueOf(i + 1), ALL_STEPS[i], "Waiting\u2026");
      rows.put(ALL_STEPS[i], row);
      add(row);
    }
    reset();
  }

  // ── Public API ────────────────────────────────────────────────────────────

  /** Resets all steps to PENDING. Call before each new run. */
  public void reset() {
    rows.values().forEach(r -> r.update(Status.PENDING, "Waiting\u2026"));
  }

  /** Marks all steps ACTIVE immediately (spinner-like state during execution). */
  public void markRunning(String query) {
    rows.get(STEP_QUERY).update(Status.ACTIVE, truncate(query, 80));
    rows.get(STEP_INTENT).update(Status.ACTIVE, "Detecting intent\u2026");
    rows.get(STEP_EMBED).update(Status.ACTIVE, "Generating embedding\u2026");
    rows.get(STEP_VECTOR).update(Status.ACTIVE, "Retrieving vector candidates\u2026");
    rows.get(STEP_BM25).update(Status.ACTIVE, "Running keyword search\u2026");
    rows.get(STEP_FUSION).update(Status.ACTIVE, "Fusing scores\u2026");
    rows.get(STEP_CONFIDENCE).update(Status.ACTIVE, "Applying confidence boost\u2026");
    rows.get(STEP_FEEDBACK).update(Status.ACTIVE, "Applying relevance feedback\u2026");
    rows.get(STEP_FILTER).update(Status.ACTIVE, "Filtering by cutoff\u2026");
    rows.get(STEP_RESULT).update(Status.ACTIVE, "Assembling results\u2026");
  }

  /**
   * Populates all steps from a completed {@link TuningRun}.
   * Call inside {@code ui.access()} after the run finishes.
   */
  public void showRun(TuningRun run) {
    SearchTuningConfig cfg = run.getConfig();
    RetrievalMode      mode = cfg.getRetrievalMode();

    // Step 1 — Query Input
    rows.get(STEP_QUERY).update(Status.COMPLETED,
        "\u201c" + truncate(run.getQuery(), 80) + "\u201d");

    // Step 2 — Query Intent
    QueryIntentType intent = run.getDetectedIntent();
    if (!cfg.isQueryIntentEnabled() || intent == null) {
      rows.get(STEP_INTENT).update(Status.SKIPPED, "Intent detection disabled");
    } else {
      rows.get(STEP_INTENT).update(Status.COMPLETED,
          intent.getLabel() + " \u2014 " + intent.getHint());
    }

    // Step 3 — Embedding
    if (mode == RetrievalMode.BM25_ONLY) {
      rows.get(STEP_EMBED).update(Status.SKIPPED, "Skipped \u2014 BM25-only mode");
    } else if (cfg.getQueryByExampleImageId() != null) {
      rows.get(STEP_EMBED).update(Status.COMPLETED,
          "QBE mode \u2014 using stored vector for image " + cfg.getQueryByExampleImageId());
    } else {
      rows.get(STEP_EMBED).update(Status.COMPLETED,
          "Query embedded using " + cfg.getSimilarityFunction().getLabel());
    }

    // Step 4 — Vector Retrieval
    if (mode == RetrievalMode.BM25_ONLY) {
      rows.get(STEP_VECTOR).update(Status.SKIPPED, "Skipped \u2014 BM25-only mode");
    } else {
      rows.get(STEP_VECTOR).update(Status.COMPLETED,
          run.getVectorCandidates() + " candidates  |  "
              + cfg.getSimilarityFunction().getLabel());
    }

    // Step 5 — BM25 Retrieval
    if (mode == RetrievalMode.SEMANTIC_ONLY) {
      rows.get(STEP_BM25).update(Status.SKIPPED, "Skipped \u2014 semantic-only mode");
    } else {
      rows.get(STEP_BM25).update(Status.COMPLETED,
          run.getKeywordCandidates() + " candidates via keyword search");
    }

    // Step 6 — Score Fusion
    int totalCandidates = run.getVectorCandidates() + run.getKeywordCandidates();
    String fusionSummary = switch (mode) {
      case HYBRID        -> String.format("Hybrid RRF  |  Sem \u00d7%.2f  BM25 \u00d7%.2f  |  %d unique candidates",
                               cfg.getSemanticWeight(), cfg.getBm25Weight(), totalCandidates);
      case SEMANTIC_ONLY -> String.format("Semantic only  |  Sem \u00d7%.2f", cfg.getSemanticWeight());
      case BM25_ONLY     -> String.format("BM25 only  |  BM25 \u00d7%.2f", cfg.getBm25Weight());
    };
    rows.get(STEP_FUSION).update(Status.COMPLETED, fusionSummary);

    // Step 7 — Confidence Boost
    if (cfg.getConfidenceWeight() <= 0) {
      rows.get(STEP_CONFIDENCE).update(Status.SKIPPED, "Weight = 0 \u2014 boost disabled");
    } else {
      rows.get(STEP_CONFIDENCE).update(Status.COMPLETED,
          String.format("Weight %.2f \u2014 up to +%.0f%% for high-confidence images",
              cfg.getConfidenceWeight(), cfg.getConfidenceWeight() * 100));
    }

    // Step 8 — Relevance Feedback
    int fbEntries = run.getFeedbackEntriesUsed();
    if (!cfg.isFeedbackEnabled() || fbEntries == 0) {
      rows.get(STEP_FEEDBACK).update(Status.SKIPPED,
          cfg.isFeedbackEnabled() ? "Enabled \u2014 no feedback in session"
                                  : "Feedback disabled");
    } else {
      rows.get(STEP_FEEDBACK).update(Status.COMPLETED,
          String.format("%d feedback entr%s  |  weight %.2f",
              fbEntries, fbEntries == 1 ? "y" : "ies", cfg.getFeedbackWeight()));
    }

    // Step 9 — Score Filtering
    rows.get(STEP_FILTER).update(Status.COMPLETED,
        String.format("Cutoff %.2f \u2014 %d result(s) passed", cfg.getScoreCutoff(),
            run.getResults().size()));

    // Step 10 — Result Assembly
    rows.get(STEP_RESULT).update(Status.COMPLETED,
        run.getResults().size() + " result(s) \u2014 " + run.getExecutionMs() + " ms");
  }

  // ── Inner helpers ─────────────────────────────────────────────────────────

  private static String truncate(String s, int max) {
    if (s == null) return "";
    return s.length() > max ? s.substring(0, max - 1) + "\u2026" : s;
  }

  // ── StepRow inner component ───────────────────────────────────────────────

  enum Status { PENDING, ACTIVE, COMPLETED, SKIPPED }

  private static final class StepRow extends Div {

    private final Div  indicator;
    private final Span summarySpan;

    StepRow(String number, String title, String defaultSummary) {
      getStyle()
          .set("display", "flex")
          .set("gap", "0.75rem")
          .set("padding", "0.35rem 0.25rem")
          .set("border-bottom", "1px solid var(--lumo-contrast-5pct)")
          .set("align-items", "flex-start");

      indicator = new Div();
      indicator.getStyle()
          .set("width", "20px").set("height", "20px")
          .set("border-radius", "50%")
          .set("background", COLOR_PENDING)
          .set("flex-shrink", "0").set("margin-top", "2px")
          .set("display", "flex").set("align-items", "center")
          .set("justify-content", "center")
          .set("font-size", "10px").set("color", "white").set("font-weight", "700");
      indicator.setText(number);

      Div right = new Div();
      right.getStyle().set("flex", "1").set("min-width", "0");

      Span titleSpan = new Span(title);
      titleSpan.getStyle()
          .set("font-weight", "600").set("font-size", "var(--lumo-font-size-s)")
          .set("display", "block");

      summarySpan = new Span(defaultSummary);
      summarySpan.getStyle()
          .set("font-size", "var(--lumo-font-size-xs)")
          .set("color", "var(--lumo-secondary-text-color)")
          .set("display", "block");

      right.add(titleSpan, summarySpan);
      add(indicator, right);
    }

    void update(Status status, String summary) {
      String color = switch (status) {
        case PENDING   -> COLOR_PENDING;
        case ACTIVE    -> COLOR_ACTIVE;
        case COMPLETED -> COLOR_COMPLETED;
        case SKIPPED   -> COLOR_SKIPPED;
      };
      indicator.getStyle().set("background", color);
      summarySpan.setText(summary != null ? summary : "");
      boolean dimmed = status == Status.PENDING || status == Status.SKIPPED;
      summarySpan.getStyle().set("color",
          dimmed ? "var(--lumo-disabled-text-color)" : "var(--lumo-secondary-text-color)");
    }
  }
}
