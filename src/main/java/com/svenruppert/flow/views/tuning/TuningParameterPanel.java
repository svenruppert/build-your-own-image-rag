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
package com.svenruppert.flow.views.tuning;

import com.svenruppert.imagerag.domain.SearchTuningConfig;
import com.svenruppert.imagerag.domain.enums.RetrievalMode;
import com.svenruppert.imagerag.domain.enums.SimilarityFunction;
import com.svenruppert.imagerag.dto.FeedbackSession;
import com.svenruppert.imagerag.dto.SearchStrategyPlan;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.NumberField;

import java.util.List;
import java.util.UUID;

import static com.svenruppert.flow.views.tuning.TuningStyles.controlSection;

/**
 * Middle column of the Search Tuning Lab: retrieval mode, similarity function, channel
 * weights, relevance feedback, query intent, autopilot, score cutoff and result limit.
 * Turns the current control values into a {@link SearchTuningConfig}.
 */
class TuningParameterPanel
    extends VerticalLayout {

  private final Select<RetrievalMode> modeGroup = new Select<>();
  private final Select<SimilarityFunction> similarityGroup = new Select<>();
  private final NumberField semanticWeightField = new NumberField();
  private final NumberField bm25WeightField = new NumberField();
  private final NumberField confidenceWeightField = new NumberField();
  private final NumberField scoreCutoffField = new NumberField();
  private final NumberField maxResultsField = new NumberField();
  private final Checkbox feedbackToggle = new Checkbox();
  private final NumberField feedbackWeightField = new NumberField();
  private final Checkbox intentToggle = new Checkbox();
  private final Checkbox autopilotToggle = new Checkbox();
  private final Span autopilotPlanSpan = new Span();

  TuningParameterPanel() {
    TuningStyles.stylePanel(this, "280px");
    feedbackToggle.setLabel(getTranslation("tuning.feedback.enable"));
    intentToggle.setLabel(getTranslation("tuning.intent.enable"));

    // Retrieval mode — compact Select
    modeGroup.setItems(RetrievalMode.values());
    modeGroup.setItemLabelGenerator(RetrievalMode::getLabel);
    modeGroup.setValue(RetrievalMode.HYBRID);
    modeGroup.setWidthFull();
    modeGroup.addValueChangeListener(e -> updateControlVisibility());
    add(controlSection(getTranslation("tuning.mode"), modeGroup));

    // Similarity function — compact Select
    similarityGroup.setItems(SimilarityFunction.values());
    similarityGroup.setItemLabelGenerator(SimilarityFunction::getLabel);
    similarityGroup.setValue(SimilarityFunction.COSINE);
    similarityGroup.setWidthFull();
    add(controlSection(getTranslation("tuning.similarity"), similarityGroup));

    // Channel weights: semantic + BM25 side by side, confidence on own row
    configureWeightField(semanticWeightField, getTranslation("tuning.weight.semantic"),
                         SearchTuningConfig.DEFAULT_SEMANTIC_WEIGHT, 0.0, 5.0, 0.1);
    configureWeightField(bm25WeightField, getTranslation("tuning.weight.bm25"),
                         SearchTuningConfig.DEFAULT_BM25_WEIGHT, 0.0, 5.0, 0.1);
    configureWeightField(confidenceWeightField, getTranslation("tuning.weight.confidence"),
                         SearchTuningConfig.DEFAULT_CONFIDENCE_WEIGHT, 0.0, 1.0, 0.05);

    add(controlSection(getTranslation("tuning.weights"),
                       pairRow(semanticWeightField, bm25WeightField), confidenceWeightField));

    // Relevance feedback: toggle + weight field side by side
    configureWeightField(feedbackWeightField, getTranslation("tuning.feedback.weight"),
                         SearchTuningConfig.DEFAULT_FEEDBACK_WEIGHT, 0.0, 2.0, 0.1);
    feedbackWeightField.setEnabled(false);
    feedbackToggle.setValue(false);
    feedbackToggle.addValueChangeListener(e ->
                                              feedbackWeightField.setEnabled(Boolean.TRUE.equals(e.getValue())));

    // Stacked: side by side, the field does not fit into the 280 px panel.
    add(controlSection(getTranslation("tuning.feedback"), feedbackToggle, feedbackWeightField));

    // Query intent: toggle + compact hint
    intentToggle.setValue(false);
    Span intentHint = new Span(getTranslation("tuning.intent.hint.short"));
    intentHint.getStyle()
        .set("font-size", "var(--lumo-font-size-xs)")
        .set("color", "var(--lumo-secondary-text-color)");
    add(controlSection(getTranslation("tuning.intent"), intentToggle, intentHint));

    // Autopilot: toggle + plan hint
    autopilotToggle.setLabel(getTranslation("tuning.autopilot.enable"));
    autopilotToggle.setValue(false);
    autopilotPlanSpan.getStyle()
        .set("font-size", "var(--lumo-font-size-xs)")
        .set("color", "#f59e0b")
        .set("display", "block");
    Span autopilotHint = new Span(getTranslation("tuning.autopilot.hint"));
    autopilotHint.getStyle()
        .set("font-size", "var(--lumo-font-size-xs)")
        .set("color", "var(--lumo-secondary-text-color)");
    add(controlSection(getTranslation("tuning.autopilot"),
                       autopilotToggle, autopilotHint, autopilotPlanSpan));

    // Score cutoff + max results side by side
    configureWeightField(scoreCutoffField, getTranslation("tuning.cutoff"),
                         SearchTuningConfig.DEFAULT_SCORE_CUTOFF, 0.0, 1.0, 0.05);
    maxResultsField.setLabel(getTranslation("tuning.max.results"));
    maxResultsField.setValue((double) SearchTuningConfig.DEFAULT_MAX_RESULTS);
    maxResultsField.setMin(1);
    maxResultsField.setMax(100);
    maxResultsField.setStep(5);
    maxResultsField.setStepButtonsVisible(true);
    maxResultsField.setWidthFull();

    add(controlSection(getTranslation("tuning.cutoff.section"),
                       pairRow(scoreCutoffField, maxResultsField)));
  }

  /**
   * Places two number fields side by side so that both shrink to share the panel width;
   * without min-width 0 the fields keep their default width and overflow the panel.
   */
  private static HorizontalLayout pairRow(NumberField left, NumberField right) {
    for (NumberField field : List.of(left, right)) {
      field.getStyle().set("flex", "1 1 0").set("min-width", "0");
    }
    HorizontalLayout row = new HorizontalLayout(left, right);
    row.setWidthFull();
    row.setSpacing(false);
    row.getStyle().set("gap", "0.35rem");
    return row;
  }

  /**
   * Builds the tuning configuration from the current control values.
   *
   * @param snap       feedback session snapshot; may be null (presets store no feedback)
   * @param qbeImageId query-by-example anchor image; may be null
   */
  SearchTuningConfig buildConfig(FeedbackSession snap, UUID qbeImageId) {
    return new SearchTuningConfig()
        .setRetrievalMode(modeGroup.getValue() != null
                              ? modeGroup.getValue() : RetrievalMode.HYBRID)
        .setSimilarityFunction(similarityGroup.getValue() != null
                                   ? similarityGroup.getValue() : SimilarityFunction.COSINE)
        .setSemanticWeight(doubleValue(semanticWeightField,
                                       SearchTuningConfig.DEFAULT_SEMANTIC_WEIGHT))
        .setBm25Weight(doubleValue(bm25WeightField,
                                   SearchTuningConfig.DEFAULT_BM25_WEIGHT))
        .setConfidenceWeight(doubleValue(confidenceWeightField,
                                         SearchTuningConfig.DEFAULT_CONFIDENCE_WEIGHT))
        .setScoreCutoff(doubleValue(scoreCutoffField,
                                    SearchTuningConfig.DEFAULT_SCORE_CUTOFF))
        .setMaxResults((int) doubleValue(maxResultsField,
                                         SearchTuningConfig.DEFAULT_MAX_RESULTS))
        .setFeedbackEnabled(Boolean.TRUE.equals(feedbackToggle.getValue()))
        .setFeedbackWeight(doubleValue(feedbackWeightField,
                                       SearchTuningConfig.DEFAULT_FEEDBACK_WEIGHT))
        .setFeedbackSession(snap)
        .setQueryByExampleImageId(qbeImageId)
        .setQueryIntentEnabled(Boolean.TRUE.equals(intentToggle.getValue()));
  }

  boolean isAutopilotEnabled() {
    return Boolean.TRUE.equals(autopilotToggle.getValue());
  }

  /**
   * Applies the autopilot's recommendations to {@code cfg} and syncs the controls.
   */
  void applyAutopilotPlan(SearchStrategyPlan plan, SearchTuningConfig cfg) {
    cfg.setRetrievalMode(plan.recommendedMode());
    cfg.setSimilarityFunction(plan.recommendedSimilarity());
    cfg.setSemanticWeight(plan.recommendedSemanticWeight());
    cfg.setBm25Weight(plan.recommendedBm25Weight());
    modeGroup.setValue(plan.recommendedMode());
    similarityGroup.setValue(plan.recommendedSimilarity());
    semanticWeightField.setValue(plan.recommendedSemanticWeight());
    bm25WeightField.setValue(plan.recommendedBm25Weight());
    autopilotPlanSpan.setText(getTranslation("tuning.autopilot.plan",
                                             plan.shortSummary() + String.format(" (%.0f%%)", plan.confidence() * 100)));
    updateControlVisibility();
  }

  void clearAutopilotPlan() {
    autopilotPlanSpan.setText("");
  }

  /**
   * Shows the values of a stored configuration in the controls.
   */
  void applyConfig(SearchTuningConfig cfg) {
    modeGroup.setValue(cfg.getRetrievalMode());
    similarityGroup.setValue(cfg.getSimilarityFunction());
    semanticWeightField.setValue(cfg.getSemanticWeight());
    bm25WeightField.setValue(cfg.getBm25Weight());
    confidenceWeightField.setValue(cfg.getConfidenceWeight());
    feedbackToggle.setValue(cfg.isFeedbackEnabled());
    feedbackWeightField.setValue(cfg.getFeedbackWeight());
    intentToggle.setValue(cfg.isQueryIntentEnabled());
    scoreCutoffField.setValue(cfg.getScoreCutoff());
    maxResultsField.setValue((double) cfg.getMaxResults());
    updateControlVisibility();
  }

  private void updateControlVisibility() {
    RetrievalMode mode = modeGroup.getValue();
    boolean hasSemantic = mode != RetrievalMode.BM25_ONLY;
    boolean hasBm25 = mode != RetrievalMode.SEMANTIC_ONLY;
    semanticWeightField.setEnabled(hasSemantic);
    similarityGroup.setEnabled(hasSemantic);
    bm25WeightField.setEnabled(hasBm25);
  }

  private void configureWeightField(NumberField f, String label, double def,
                                    double min, double max, double step) {
    f.setLabel(label);
    f.setValue(def);
    f.setMin(min);
    f.setMax(max);
    f.setStep(step);
    f.setStepButtonsVisible(true);
    f.setWidthFull();
  }

  private double doubleValue(NumberField f, double fallback) {
    Double v = f.getValue();
    return (v != null && !Double.isNaN(v)) ? v : fallback;
  }
}
