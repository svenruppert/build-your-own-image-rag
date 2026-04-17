package com.svenruppert.flow.views.taxonomy;

import com.svenruppert.imagerag.service.TaxonomyAnalysisProgress;
import com.svenruppert.imagerag.service.TaxonomyAnalysisProgress.StepStatus;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Left-side taxonomy-analysis step inspector.
 * <p>Visualises the seven stages of a taxonomy-analysis run as a vertical timeline of
 * numbered step rows, each with a colour-coded status indicator, a title, and a
 * one-line summary.  Mirrors the pattern established by
 * {@link com.svenruppert.flow.views.search.SearchInspectorComponent}.
 * <h3>Steps</h3>
 * <ol>
 *   <li>Scope Definition — scope mode and image set are resolved</li>
 *   <li>Image Loading — non-archived images matching the scope are loaded</li>
 *   <li>Taxonomy Loading — deprecated-category metadata is fetched</li>
 *   <li>Image Analysis — per-image category check (main loop, shows X/Y)</li>
 *   <li>Generating Suggestions — suggestion list is consolidated</li>
 *   <li>Dry-Run Preview — (skipped for live runs)</li>
 *   <li>Completed — final state</li>
 * </ol>
 * <h3>Step states</h3>
 * <ul>
 *   <li>PENDING — grey — not yet started</li>
 *   <li>ACTIVE — blue — currently running</li>
 *   <li>COMPLETED — green — finished successfully</li>
 *   <li>SKIPPED — very-light — intentionally not executed</li>
 *   <li>FAILED — red — ended in error</li>
 * </ul>
 * <p>Call {@link #update(TaxonomyAnalysisProgress)} from inside a {@code ui.access()} block
 * whenever the progress state changes.  Call {@link #reset()} before each new analysis run.
 */
public class TaxonomyAnalysisInspectorComponent
    extends VerticalLayout {

  // ── Status colours ────────────────────────────────────────────────────────
  private static final String COLOR_PENDING = "var(--lumo-contrast-30pct)";
  private static final String COLOR_ACTIVE = "var(--lumo-primary-color)";
  private static final String COLOR_COMPLETED = "var(--lumo-success-color)";
  private static final String COLOR_SKIPPED = "var(--lumo-contrast-15pct)";
  private static final String COLOR_FAILED = "var(--lumo-error-color)";

  // ── Step rows ─────────────────────────────────────────────────────────────
  private final Map<String, StepRow> rows = new LinkedHashMap<>();

  public TaxonomyAnalysisInspectorComponent() {
    setPadding(false);
    setSpacing(false);
    getStyle()
        .set("border", "1px solid var(--lumo-contrast-10pct)")
        .set("border-radius", "var(--lumo-border-radius-l)")
        .set("padding", "0.5rem 0.75rem")
        .set("background", "var(--lumo-base-color)")
        .set("width", "100%");

    H4 heading = new H4("Analysis Steps");
    heading.getStyle()
        .set("margin", "0 0 0.4rem 0")
        .set("font-size", "var(--lumo-font-size-s)")
        .set("color", "var(--lumo-secondary-text-color)")
        .set("text-transform", "uppercase")
        .set("letter-spacing", "0.05em");
    add(heading);

    String[] labels = TaxonomyAnalysisProgress.ALL_STEPS;
    for (int i = 0; i < labels.length; i++) {
      StepRow row = new StepRow(String.valueOf(i + 1), labels[i], "Waiting\u2026");
      rows.put(labels[i], row);
      add(row);
    }

    reset();
  }

  // ── Public API ────────────────────────────────────────────────────────────

  /**
   * Resets all steps to PENDING.  Call at the start of each new analysis run.
   */
  public void reset() {
    rows.values().forEach(r -> r.update(StepStatus.PENDING, "Waiting\u2026", null));
  }

  /**
   * Applies the current state of {@code progress} to all step rows.
   * Must be called inside a {@code ui.access()} block.
   */
  public void update(TaxonomyAnalysisProgress progress) {
    Map<String, StepStatus> statuses = progress.getStepStatuses();
    Map<String, String> summaries = progress.getStepSummaries();

    for (String stepLabel : TaxonomyAnalysisProgress.ALL_STEPS) {
      StepRow row = rows.get(stepLabel);
      StepStatus status = statuses.getOrDefault(stepLabel, StepStatus.PENDING);
      String summary = summaries.getOrDefault(stepLabel, "Waiting\u2026");

      // For the analysis step, append the numeric counter if active
      if (TaxonomyAnalysisProgress.STEP_ANALYSIS.equals(stepLabel)
          && status == StepStatus.ACTIVE
          && progress.getTotalImages() > 0) {
        summary = String.format("Analysing %d / %d images \u2014 %s elapsed",
                                progress.getAnalyzedImages(),
                                progress.getTotalImages(),
                                progress.getElapsedFormatted());
      }

      row.update(status, summary, null);
    }
  }

  // ── StepRow inner component ───────────────────────────────────────────────

  private static final class StepRow
      extends Div {

    private final Div indicator;
    private final Span titleSpan;
    private final Span summarySpan;
    private final Div right;
    private Details detailsPanel = null;

    StepRow(String number, String title, String defaultSummary) {
      getStyle()
          .set("display", "flex")
          .set("gap", "0.75rem")
          .set("padding", "0.4rem 0.25rem")
          .set("border-bottom", "1px solid var(--lumo-contrast-5pct)")
          .set("align-items", "flex-start");

      indicator = new Div();
      indicator.getStyle()
          .set("width", "22px")
          .set("height", "22px")
          .set("border-radius", "50%")
          .set("background", COLOR_PENDING)
          .set("flex-shrink", "0")
          .set("margin-top", "2px")
          .set("display", "flex")
          .set("align-items", "center")
          .set("justify-content", "center")
          .set("font-size", "10px")
          .set("color", "white")
          .set("font-weight", "700");
      indicator.setText(number);

      right = new Div();
      right.getStyle().set("flex", "1").set("min-width", "0");

      titleSpan = new Span(title);
      titleSpan.getStyle()
          .set("font-weight", "600")
          .set("font-size", "var(--lumo-font-size-s)")
          .set("display", "block");

      summarySpan = new Span(defaultSummary);
      summarySpan.getStyle()
          .set("font-size", "var(--lumo-font-size-xs)")
          .set("color", "var(--lumo-secondary-text-color)")
          .set("display", "block")
          .set("white-space", "pre-wrap");

      right.add(titleSpan, summarySpan);
      add(indicator, right);
    }

    void update(StepStatus status, String summary, String detailsText) {
      applyStatusStyle(status);
      summarySpan.setText(summary != null ? summary : "");

      if (detailsPanel != null) {
        right.remove(detailsPanel);
        detailsPanel = null;
      }
      if (detailsText != null && !detailsText.isBlank()) {
        detailsPanel = new Details("Show details");
        Paragraph p = new Paragraph(detailsText);
        p.getStyle()
            .set("font-size", "var(--lumo-font-size-xs)")
            .set("white-space", "pre-wrap")
            .set("color", "var(--lumo-secondary-text-color)")
            .set("margin", "0");
        detailsPanel.add(p);
        detailsPanel.setOpened(false);
        right.add(detailsPanel);
      }
    }

    private void applyStatusStyle(StepStatus status) {
      String color = switch (status) {
        case PENDING -> COLOR_PENDING;
        case ACTIVE -> COLOR_ACTIVE;
        case COMPLETED -> COLOR_COMPLETED;
        case SKIPPED -> COLOR_SKIPPED;
        case FAILED -> COLOR_FAILED;
      };
      indicator.getStyle().set("background", color);

      boolean dimmed = status == StepStatus.PENDING || status == StepStatus.SKIPPED;
      titleSpan.getStyle().set("color",
                               dimmed ? "var(--lumo-disabled-text-color)" : "");
      summarySpan.getStyle().set("color",
                                 dimmed ? "var(--lumo-disabled-text-color)" : "var(--lumo-secondary-text-color)");
    }
  }
}
