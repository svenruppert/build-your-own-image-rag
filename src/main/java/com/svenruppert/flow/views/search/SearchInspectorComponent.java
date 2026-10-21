/*
 * Copyright © 2026 Sven Ruppert (sven.ruppert@gmail.com)
 *
 * Licensed under the EUPL, Version 1.2 (the "Licence");
 * you may not use this file except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *     https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */
package com.svenruppert.flow.views.search;

import com.svenruppert.flow.views.shared.Formats;
import com.svenruppert.imagerag.domain.SearchPlan;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * Interactive, step-by-step inspector for the Search Workbench.
 * <p>Visualises the search pipeline as a vertical timeline of eight explicit steps, each with
 * a colour-coded status indicator, a title, a one-line summary, and optional collapsible
 * details.  This makes the search process transparent and didactically understandable —
 * the user can see what happened at each stage, not just the final result.
 * <h3>Steps</h3>
 * <ol>
 *   <li>User Query — the raw text the user typed</li>
 *   <li>Query Understanding — the language-model analysis phase</li>
 *   <li>Transformed Query — the rich embedding text produced by the LLM</li>
 *   <li>Derived Parameters — structured filters and user-adjusted plan</li>
 *   <li>Search Preparation — embedding computation for the vector index</li>
 *   <li>Candidate Retrieval — raw cosine-similarity search against the image index</li>
 *   <li>Filtering — score threshold cutoff and structural filter application</li>
 *   <li>Result Generation — final ranked result assembly</li>
 * </ol>
 * <h3>Step states</h3>
 * <ul>
 *   <li>PENDING — grey circle — not yet started</li>
 *   <li>ACTIVE — blue circle — currently running</li>
 *   <li>COMPLETED — green circle — finished successfully</li>
 *   <li>SKIPPED — very-light circle — intentionally not executed (Transform Only mode)</li>
 *   <li>FAILED — red circle — ended in error</li>
 * </ul>
 * <p>UI update methods are designed to be called repeatedly from the polling handler —
 * they are idempotent and simply overwrite the previous state.
 */
public class SearchInspectorComponent
    extends VerticalLayout {

  // ── Status colours ────────────────────────────────────────────────────────────
  private static final String COLOR_PENDING = "var(--lumo-contrast-30pct)";
  private static final String COLOR_ACTIVE = "var(--lumo-primary-color)";
  private static final String COLOR_COMPLETED = "var(--lumo-success-color)";
  private static final String COLOR_SKIPPED = "var(--lumo-contrast-15pct)";
  private static final String COLOR_FAILED = "var(--lumo-error-color)";
  // ── Step rows (8 steps) ───────────────────────────────────────────────────────
  private final StepRow stepQuery;
  private final StepRow stepLlm;
  private final StepRow stepTransformed;
  private final StepRow stepParams;
  private final StepRow stepSearchPrep;
  private final StepRow stepRetrieval;
  private final StepRow stepFiltering;
  private final StepRow stepResults;

  public SearchInspectorComponent() {
    setPadding(false);
    setSpacing(false);
    getStyle()
        .set("border", "1px solid var(--lumo-contrast-10pct)")
        .set("border-radius", "var(--lumo-border-radius-l)")
        .set("padding", "0.5rem 0.75rem")
        .set("background", "var(--lumo-base-color)")
        .set("width", "100%");

    stepQuery = new StepRow("1", getTranslation("search.inspector.step.query"),
                            getTranslation("search.inspector.waiting.query"));
    stepLlm = new StepRow("2", getTranslation("search.inspector.step.understanding"), getTranslation("search.inspector.waiting"));
    stepTransformed = new StepRow("3", getTranslation("search.inspector.step.transformed"), getTranslation("search.inspector.waiting"));
    stepParams = new StepRow("4", getTranslation("search.inspector.step.params"), getTranslation("search.inspector.waiting"));
    stepSearchPrep = new StepRow("5", getTranslation("search.inspector.step.preparation"), getTranslation("search.inspector.waiting"));
    stepRetrieval = new StepRow("6", getTranslation("search.inspector.step.retrieval"), getTranslation("search.inspector.waiting"));
    stepFiltering = new StepRow("7", getTranslation("search.inspector.step.filtering"), getTranslation("search.inspector.waiting"));
    stepResults = new StepRow("8", getTranslation("search.inspector.step.results"), getTranslation("search.inspector.waiting"));

    add(stepQuery, stepLlm, stepTransformed, stepParams,
        stepSearchPrep, stepRetrieval, stepFiltering, stepResults);
    reset();
  }

  /**
   * Resets all steps to PENDING and clears all detail content.
   * Must be called at the start of each new search run.
   */
  public void reset() {
    String waiting = getTranslation("search.inspector.waiting");
    stepQuery.update(StepStatus.PENDING, getTranslation("search.inspector.waiting.query"), null);
    stepLlm.update(StepStatus.PENDING, waiting, null);
    stepTransformed.update(StepStatus.PENDING, waiting, null);
    stepParams.update(StepStatus.PENDING, waiting, null);
    stepSearchPrep.update(StepStatus.PENDING, waiting, null);
    stepRetrieval.update(StepStatus.PENDING, waiting, null);
    stepFiltering.update(StepStatus.PENDING, waiting, null);
    stepResults.update(StepStatus.PENDING, waiting, null);
  }

  // ── Public update API (called from the UI polling handler) ───────────────────

  /**
   * Marks step 1 (User Query) as COMPLETED with the query text.
   */
  public void setQueryInput(String query) {
    stepQuery.update(StepStatus.COMPLETED,
                     getTranslation("search.inspector.query", Formats.truncate(query, 80)),
                     query);
  }

  /**
   * Marks step 2 (Query Understanding) as ACTIVE.
   */
  public void setLlmActive() {
    stepLlm.update(StepStatus.ACTIVE, getTranslation("search.inspector.llm.active"), null);
  }

  /**
   * Marks steps 2, 3, and 4 as COMPLETED after the LLM has produced a {@link SearchPlan}.
   * Idempotent — safe to call on every poll tick once the plan is available.
   */
  public void setLlmCompleted(SearchPlan plan) {
    // Step 2 — LLM explanation
    String explanation = plan.getExplanation() != null ? plan.getExplanation() : getTranslation("search.inspector.llm.understood");
    stepLlm.update(StepStatus.COMPLETED, Formats.truncate(explanation, 100), plan.getExplanation());

    // Step 3 — Transformed embedding text
    String embText = plan.getEmbeddingText() != null ? plan.getEmbeddingText() : "\u2014";
    stepTransformed.update(StepStatus.COMPLETED, Formats.truncate(embText, 100), embText);

    // Step 4 — Derived parameters (from LLM) + any user refinements
    String paramSummary = buildParamSummary(plan);
    stepParams.update(StepStatus.COMPLETED, paramSummary, buildParamDetails(plan));
  }

  /**
   * Marks step 5 (Search Preparation) as ACTIVE.
   * Called right before the background thread submits the search request.
   */
  public void setSearchPrepActive() {
    stepSearchPrep.update(StepStatus.ACTIVE, getTranslation("search.inspector.embedding.active"), null);
  }

  /**
   * Marks steps 5-8 as COMPLETED once the search has returned results.
   * <p>Step 6 (Candidate Retrieval) shows both vector and BM25 candidate counts,
   * making the hybrid fusion step transparent.
   * <p>Step 7 (Filtering) shows the effective score threshold so the user can
   * understand exactly how strict the similarity cutoff was.
   *
   * @param resultCount  number of results after all filters
   * @param threshold    effective minimum score threshold that was applied
   * @param vectorCands  number of candidates from the vector (semantic) channel
   * @param keywordCands number of candidates from the BM25 (keyword) channel
   */
  public void setSearchCompleted(int resultCount, double threshold,
                                 int vectorCands, int keywordCands) {
    stepSearchPrep.update(StepStatus.COMPLETED, getTranslation("search.inspector.embedding.done"), null);
    String retrievalSummary = getTranslation("search.inspector.retrieval.summary",
                                             vectorCands, keywordCands);
    String retrievalDetail = getTranslation("search.inspector.retrieval.detail",
                                            vectorCands, keywordCands);
    stepRetrieval.update(StepStatus.COMPLETED, retrievalSummary, retrievalDetail);
    stepFiltering.update(StepStatus.COMPLETED,
                         getTranslation("search.inspector.filtering.summary", threshold, resultCount),
                         getTranslation("search.inspector.filtering.detail", threshold, resultCount));
    stepResults.update(StepStatus.COMPLETED,
                       getTranslation("search.inspector.results.matched", resultCount),
                       null);
  }

  /**
   * Marks steps 5-8 as SKIPPED.
   * Called in TRANSFORM_ONLY mode after LLM analysis completes.
   */
  public void skipExecutionSteps() {
    String note = getTranslation("search.inspector.skipped.transform");
    stepSearchPrep.update(StepStatus.SKIPPED, note, null);
    stepRetrieval.update(StepStatus.SKIPPED, note, null);
    stepFiltering.update(StepStatus.SKIPPED, note, null);
    stepResults.update(StepStatus.SKIPPED, getTranslation("search.inspector.not.executed.transform"), null);
  }

  /**
   * Marks the appropriate step as FAILED and marks all subsequent steps as SKIPPED.
   *
   * @param error        the error message to display
   * @param stepProgress the last reached step progress value from the run state
   *                     (values &ge; 3 indicate the LLM completed; failure was in search execution)
   */
  public void setFailed(String error, int stepProgress) {
    String skippedNote = getTranslation("search.inspector.not.reached");
    if (stepProgress >= 3) {
      // LLM completed; failure occurred during search preparation or retrieval
      stepSearchPrep.update(StepStatus.FAILED,
                            getTranslation("search.inspector.search.failed", Formats.truncate(error, 80)),
                            error);
      stepRetrieval.update(StepStatus.SKIPPED, skippedNote, null);
      stepFiltering.update(StepStatus.SKIPPED, skippedNote, null);
      stepResults.update(StepStatus.SKIPPED, skippedNote, null);
    } else {
      // Failure during or before LLM analysis
      stepLlm.update(StepStatus.FAILED,
                     getTranslation("search.inspector.llm.failed", Formats.truncate(error, 80)),
                     error);
      stepTransformed.update(StepStatus.SKIPPED, skippedNote, null);
      stepParams.update(StepStatus.SKIPPED, skippedNote, null);
      stepSearchPrep.update(StepStatus.SKIPPED, skippedNote, null);
      stepRetrieval.update(StepStatus.SKIPPED, skippedNote, null);
      stepFiltering.update(StepStatus.SKIPPED, skippedNote, null);
      stepResults.update(StepStatus.SKIPPED, skippedNote, null);
    }
  }

  // ── Helpers ──────────────────────────────────────────────────────────────────

  private String yesNo(boolean value) {
    return getTranslation(value ? "search.inspector.yes" : "search.inspector.no");
  }

  private String buildParamSummary(SearchPlan plan) {
    StringBuilder sb = new StringBuilder();
    if (plan.getSeasonHint() != null) {
      sb.append(getTranslation("search.inspector.param.season")).append(": ")
          .append(plan.getSeasonHint().name()).append("  ");
    }
    if (plan.getCategoryGroup() != null) {
      sb.append(getTranslation("search.inspector.param.category")).append(": ").append(plan.getCategoryGroup().getLabel()).append("  ");
    }
    if (plan.getContainsPerson() != null) {
      sb.append(getTranslation("search.inspector.param.person")).append(": ").append(yesNo(plan.getContainsPerson())).append("  ");
    }
    if (plan.getContainsVehicle() != null) {
      sb.append(getTranslation("search.inspector.param.vehicle")).append(": ").append(yesNo(plan.getContainsVehicle())).append("  ");
    }
    if (plan.getContainsLicensePlate() != null) {
      sb.append(getTranslation("search.inspector.param.plate")).append(": ").append(yesNo(plan.getContainsLicensePlate())).append("  ");
    }
    if (plan.getMinScore() != null) {
      sb.append(getTranslation("search.inspector.param.threshold")).append(": ").append(Formats.decimal(plan.getMinScore())).append("  ");
    }
    return sb.isEmpty() ? getTranslation("search.inspector.param.none") : sb.toString().trim();
  }

  private String buildParamDetails(SearchPlan plan) {
    StringBuilder sb = new StringBuilder();
    if (plan.getContainsPerson() != null) {
      sb.append(getTranslation("detail.field.contains.person")).append(": ").append(yesNo(plan.getContainsPerson())).append("\n");
    }
    if (plan.getContainsVehicle() != null) {
      sb.append(getTranslation("detail.field.contains.vehicle")).append(": ").append(yesNo(plan.getContainsVehicle())).append("\n");
    }
    if (plan.getContainsLicensePlate() != null) {
      sb.append(getTranslation("detail.field.license.plate")).append(": ").append(yesNo(plan.getContainsLicensePlate())).append("\n");
    }
    if (plan.getSeasonHint() != null) {
      sb.append(getTranslation("search.inspector.param.season")).append(": ")
          .append(plan.getSeasonHint().name()).append("\n");
    }
    if (plan.getCategoryGroup() != null) {
      sb.append(getTranslation("search.inspector.param.category.group")).append(": ").append(plan.getCategoryGroup().getLabel()).append("\n");
    }
    if (plan.getPrivacyLevel() != null) {
      sb.append(getTranslation("search.inspector.param.privacy")).append(": ").append(plan.getPrivacyLevel().name()).append("\n");
    }
    if (plan.getMinScore() != null) {
      sb.append(getTranslation("search.inspector.param.score.threshold")).append(": ").append(Formats.decimal(plan.getMinScore())).append("\n");
    }
    return sb.isEmpty() ? null : sb.toString().trim();
  }

  /**
   * Step status values.
   */
  public enum StepStatus {
    PENDING, ACTIVE, COMPLETED, SKIPPED, FAILED
  }

  // ── StepRow — inner component for a single pipeline step ─────────────────────

  /**
   * A single row in the inspector timeline.  Contains a round status indicator,
   * a title, a one-line summary, and an optional collapsible details panel.
   * <p>All content is updated via {@link #update(StepStatus, String, String)}, which
   * replaces the existing summary and detail content in one call.
   */
  private static class StepRow
      extends Div {

    private final Div indicator;
    private final Span titleSpan;
    private final Span summarySpan;
    private final Div right;
    private Details detailsPanel = null;
    private StepStatus currentStatus = StepStatus.PENDING;

    StepRow(String number, String title, String defaultSummary) {
      getStyle()
          .set("display", "flex")
          .set("gap", "0.75rem")
          .set("padding", "0.4rem 0.25rem")
          .set("border-bottom", "1px solid var(--lumo-contrast-5pct)")
          .set("align-items", "flex-start");

      // Round status indicator with step number
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

      // Right side: title, summary, optional details
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

    /**
     * Updates all visual elements atomically.
     *
     * @param status      new step status (drives indicator colour and text dimming)
     * @param summary     one-line summary text; replaces existing summary
     * @param detailsText if non-blank, adds/replaces a collapsible details panel;
     *                    pass {@code null} to remove any existing panel
     */
    void update(StepStatus status, String summary, String detailsText) {
      this.currentStatus = status;
      applyStatusStyle(status);
      summarySpan.setText(summary != null ? summary : "");

      // Replace existing details panel
      if (detailsPanel != null) {
        right.remove(detailsPanel);
        detailsPanel = null;
      }
      if (detailsText != null && !detailsText.isBlank()) {
        detailsPanel = new Details(getTranslation("search.inspector.show.details"));
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

    StepStatus getCurrentStatus() {
      return currentStatus;
    }
  }
}
