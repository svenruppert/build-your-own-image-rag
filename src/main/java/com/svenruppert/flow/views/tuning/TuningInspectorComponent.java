package com.svenruppert.flow.views.tuning;

import com.svenruppert.imagerag.domain.SearchTuningConfig;
import com.svenruppert.imagerag.domain.enums.QueryIntentType;
import com.svenruppert.imagerag.domain.enums.RetrievalMode;
import com.svenruppert.imagerag.dto.TuningRun;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tuning-lab retrieval pipeline inspector.
 * <p>Shows 10 pipeline steps for the last tuning run as a vertical timeline,
 * colour-coded by status (PENDING / ACTIVE / COMPLETED / SKIPPED).
 * Call {@link #showRun(TuningRun)} after a run completes to update the display.
 * Call {@link #reset()} before starting a new run.
 * <p>All user-visible text is fully internationalised.  Step titles and the
 * section heading are applied in {@link #onAttach(AttachEvent)} so that the
 * correct locale is available.  Status summaries written by {@link #markRunning}
 * and {@link #showRun} use {@code getTranslation()} directly, since those
 * methods are always called from an attached, session-bound UI thread.
 * <h3>Pipeline steps</h3>
 * <ol>
 *   <li>Query Input</li>
 *   <li>Query Intent (skipped when intent detection is off)</li>
 *   <li>Embedding (skipped for BM25-only; uses stored vector in QBE mode)</li>
 *   <li>Vector Retrieval (skipped for BM25-only)</li>
 *   <li>BM25 Retrieval (skipped for semantic-only)</li>
 *   <li>Score Fusion</li>
 *   <li>Confidence Boost</li>
 *   <li>Relevance Feedback (skipped when feedback is off or session is empty)</li>
 *   <li>Score Filtering</li>
 *   <li>Result Assembly</li>
 * </ol>
 */
public class TuningInspectorComponent
    extends VerticalLayout {

  // ── Status colours ────────────────────────────────────────────────────────
  private static final String COLOR_PENDING = "var(--lumo-contrast-30pct)";
  private static final String COLOR_ACTIVE = "var(--lumo-primary-color)";
  private static final String COLOR_COMPLETED = "var(--lumo-success-color)";
  private static final String COLOR_SKIPPED = "var(--lumo-contrast-15pct)";
  // ── Stored UI elements (updated in onAttach) ──────────────────────────────
  private final H4 headingEl = new H4();
  private final Map<PipelineStep, StepRow> rows = new LinkedHashMap<>();

  public TuningInspectorComponent() {
    setPadding(false);
    setSpacing(false);
    getStyle()
        .set("border", "1px solid var(--lumo-contrast-10pct)")
        .set("border-radius", "var(--lumo-border-radius-l)")
        .set("padding", "0.5rem 0.75rem")
        .set("background", "var(--lumo-base-color)")
        .set("width", "100%");

    headingEl.getStyle()
        .set("margin", "0 0 0.4rem 0")
        .set("font-size", "var(--lumo-font-size-s)")
        .set("color", "var(--lumo-secondary-text-color)")
        .set("text-transform", "uppercase")
        .set("letter-spacing", "0.05em");
    add(headingEl);

    int i = 1;
    for (PipelineStep step : PipelineStep.values()) {
      StepRow row = new StepRow(String.valueOf(i++));
      rows.put(step, row);
      add(row);
    }
    // Titles and waiting text are applied in onAttach() once translations are available.
  }

  private static String truncate(String s, int max) {
    if (s == null) return "";
    return s.length() > max ? s.substring(0, max - 1) + "\u2026" : s;
  }

  // ── Lifecycle ─────────────────────────────────────────────────────────────

  @Override
  protected void onAttach(AttachEvent event) {
    super.onAttach(event);
    headingEl.setText(getTranslation("tuning.inspector.title"));
    for (PipelineStep step : PipelineStep.values()) {
      rows.get(step).setStepTitle(getTranslation(step.titleKey()));
    }
    reset();
  }

  // ── Public API ────────────────────────────────────────────────────────────

  /**
   * Resets all steps to PENDING. Call before each new run.
   */
  public void reset() {
    String waiting = getTranslation("tuning.inspector.waiting");
    rows.values().forEach(r -> r.update(Status.PENDING, waiting));
  }

  /**
   * Marks all steps ACTIVE with "running" summaries while the search executes.
   * Requires the component to be attached.
   */
  public void markRunning(String query) {
    rows.get(PipelineStep.QUERY).update(Status.ACTIVE, truncate(query, 80));
    for (PipelineStep step : PipelineStep.values()) {
      if (step != PipelineStep.QUERY) {
        rows.get(step).update(Status.ACTIVE, getTranslation(step.runningKey()));
      }
    }
  }

  /**
   * Populates all steps from a completed {@link TuningRun}.
   * Must be called inside {@code ui.access()} after the run finishes.
   */
  public void showRun(TuningRun run) {
    SearchTuningConfig cfg = run.getConfig();
    RetrievalMode mode = cfg.getRetrievalMode();

    // Step 1 — Query Input
    rows.get(PipelineStep.QUERY).update(Status.COMPLETED,
                                        "\u201c" + truncate(run.getQuery(), 80) + "\u201d");

    // Step 2 — Query Intent
    QueryIntentType intent = run.getDetectedIntent();
    if (!cfg.isQueryIntentEnabled() || intent == null) {
      rows.get(PipelineStep.INTENT).update(Status.SKIPPED,
                                           getTranslation("tuning.step.intent.disabled"));
    } else {
      rows.get(PipelineStep.INTENT).update(Status.COMPLETED,
                                           getTranslation("tuning.step.intent.done", intent.getLabel(), intent.getHint()));
    }

    // Step 3 — Embedding
    if (mode == RetrievalMode.BM25_ONLY) {
      rows.get(PipelineStep.EMBED).update(Status.SKIPPED,
                                          getTranslation("tuning.step.embed.bm25only"));
    } else if (cfg.getQueryByExampleImageId() != null) {
      rows.get(PipelineStep.EMBED).update(Status.COMPLETED,
                                          getTranslation("tuning.step.embed.qbe"));
    } else {
      rows.get(PipelineStep.EMBED).update(Status.COMPLETED,
                                          getTranslation("tuning.step.embed.done", cfg.getSimilarityFunction().getLabel()));
    }

    // Step 4 — Vector Retrieval
    if (mode == RetrievalMode.BM25_ONLY) {
      rows.get(PipelineStep.VECTOR).update(Status.SKIPPED,
                                           getTranslation("tuning.step.vector.bm25only"));
    } else {
      rows.get(PipelineStep.VECTOR).update(Status.COMPLETED,
                                           getTranslation("tuning.step.vector.done",
                                                          run.getVectorCandidates(), cfg.getSimilarityFunction().getLabel()));
    }

    // Step 5 — BM25 Retrieval
    if (mode == RetrievalMode.SEMANTIC_ONLY) {
      rows.get(PipelineStep.BM25).update(Status.SKIPPED,
                                         getTranslation("tuning.step.bm25.semonly"));
    } else {
      rows.get(PipelineStep.BM25).update(Status.COMPLETED,
                                         getTranslation("tuning.step.bm25.done", run.getKeywordCandidates()));
    }

    // Step 6 — Score Fusion
    int total = run.getVectorCandidates() + run.getKeywordCandidates();
    String semFmt = String.format("%.2f", cfg.getSemanticWeight());
    String bFmt = String.format("%.2f", cfg.getBm25Weight());
    String fusionText = switch (mode) {
      case HYBRID -> getTranslation("tuning.step.fusion.hybrid", semFmt, bFmt, total);
      case SEMANTIC_ONLY -> getTranslation("tuning.step.fusion.semantic", semFmt);
      case BM25_ONLY -> getTranslation("tuning.step.fusion.bm25only", bFmt);
    };
    rows.get(PipelineStep.FUSION).update(Status.COMPLETED, fusionText);

    // Step 7 — Confidence Boost
    if (cfg.getConfidenceWeight() <= 0) {
      rows.get(PipelineStep.CONFIDENCE).update(Status.SKIPPED,
                                               getTranslation("tuning.step.confidence.disabled"));
    } else {
      String confFmt = String.format("%.2f", cfg.getConfidenceWeight());
      String pctFmt = String.format("%.0f", cfg.getConfidenceWeight() * 100);
      rows.get(PipelineStep.CONFIDENCE).update(Status.COMPLETED,
                                               getTranslation("tuning.step.confidence.done", confFmt, pctFmt));
    }

    // Step 8 — Relevance Feedback
    int fb = run.getFeedbackEntriesUsed();
    if (!cfg.isFeedbackEnabled() || fb == 0) {
      rows.get(PipelineStep.FEEDBACK).update(Status.SKIPPED,
                                             cfg.isFeedbackEnabled()
                                                 ? getTranslation("tuning.step.feedback.empty")
                                                 : getTranslation("tuning.step.feedback.disabled"));
    } else {
      rows.get(PipelineStep.FEEDBACK).update(Status.COMPLETED,
                                             getTranslation("tuning.step.feedback.done",
                                                            fb, String.format("%.2f", cfg.getFeedbackWeight())));
    }

    // Step 9 — Score Filtering
    rows.get(PipelineStep.FILTER).update(Status.COMPLETED,
                                         getTranslation("tuning.step.filter.done",
                                                        String.format("%.2f", cfg.getScoreCutoff()), run.getResults().size()));

    // Step 10 — Result Assembly
    rows.get(PipelineStep.RESULT).update(Status.COMPLETED,
                                         getTranslation("tuning.step.result.done",
                                                        run.getResults().size(), run.getExecutionMs()));
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  // ── Pipeline step enum — stable internal keys; i18n key derived from name ─
  private enum PipelineStep {
    QUERY, INTENT, EMBED, VECTOR, BM25, FUSION, CONFIDENCE, FEEDBACK, FILTER, RESULT;

    /**
     * Translation key for the step's user-visible title.
     */
    String titleKey() {
      return "tuning.step." + name().toLowerCase();
    }

    /**
     * Translation key for the "running" status summary.
     */
    String runningKey() {
      return "tuning.step." + name().toLowerCase() + ".running";
    }
  }

  // ── Status enum ───────────────────────────────────────────────────────────

  enum Status { PENDING, ACTIVE, COMPLETED, SKIPPED }

  // ── StepRow inner component ───────────────────────────────────────────────

  private static final class StepRow
      extends Div {

    private final Div indicator;
    private final Span titleSpan;
    private final Span summarySpan;

    StepRow(String number) {
      getStyle()
          .set("display", "flex")
          .set("gap", "0.75rem")
          .set("padding", "0.3rem 0.25rem")
          .set("border-bottom", "1px solid var(--lumo-contrast-5pct)")
          .set("align-items", "flex-start");

      indicator = new Div();
      indicator.getStyle()
          .set("width", "18px").set("height", "18px")
          .set("border-radius", "50%")
          .set("background", COLOR_PENDING)
          .set("flex-shrink", "0").set("margin-top", "2px")
          .set("display", "flex").set("align-items", "center")
          .set("justify-content", "center")
          .set("font-size", "9px").set("color", "white").set("font-weight", "700");
      indicator.setText(number);

      Div right = new Div();
      right.getStyle().set("flex", "1").set("min-width", "0");

      titleSpan = new Span();
      titleSpan.getStyle()
          .set("font-weight", "600")
          .set("font-size", "var(--lumo-font-size-xs)")
          .set("display", "block");

      summarySpan = new Span();
      summarySpan.getStyle()
          .set("font-size", "var(--lumo-font-size-xs)")
          .set("color", "var(--lumo-secondary-text-color)")
          .set("display", "block");

      right.add(titleSpan, summarySpan);
      add(indicator, right);
    }

    /**
     * Updates the user-visible step title (called from {@code onAttach}).
     */
    void setStepTitle(String title) {
      titleSpan.setText(title != null ? title : "");
    }

    void update(Status status, String summary) {
      String color = switch (status) {
        case PENDING -> COLOR_PENDING;
        case ACTIVE -> COLOR_ACTIVE;
        case COMPLETED -> COLOR_COMPLETED;
        case SKIPPED -> COLOR_SKIPPED;
      };
      indicator.getStyle().set("background", color);
      summarySpan.setText(summary != null ? summary : "");
      boolean dimmed = status == Status.PENDING || status == Status.SKIPPED;
      summarySpan.getStyle().set("color",
                                 dimmed ? "var(--lumo-disabled-text-color)" : "var(--lumo-secondary-text-color)");
    }
  }
}
