package com.svenruppert.flow.views.search;

import com.svenruppert.imagerag.domain.SearchPlan;
import com.svenruppert.imagerag.domain.enums.CategoryGroup;
import com.svenruppert.imagerag.domain.enums.RiskLevel;
import com.svenruppert.imagerag.domain.enums.SeasonHint;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextArea;

/**
 * Collapsible "Advanced" panel of the {@link SearchView}: shows the search plan derived
 * by query understanding as editable fields and offers "Refine Search", which runs the
 * vector search with the edited plan instead of asking the LLM again.
 */
class SearchPlanEditor
    extends Details {

  /**
   * Score threshold used when the score field is empty.
   */
  static final double FALLBACK_THRESHOLD = 0.45;

  private static final String BOOL_ANY = "any";
  private static final String BOOL_YES = "yes";
  private static final String BOOL_NO = "no";

  private final TextArea embeddingArea = new TextArea();
  private final Select<String> personSelect = new Select<>();
  private final Select<String> vehicleSelect = new Select<>();
  private final Select<String> plateSelect = new Select<>();
  private final Select<SeasonHint> seasonSelect = new Select<>();
  private final Select<CategoryGroup> categorySelect = new Select<>();
  private final Select<RiskLevel> privacySelect = new Select<>();
  /**
   * Score threshold: minimum similarity for a result to be kept.
   */
  private final NumberField scoreField = new NumberField();
  private final Button refineBtn = new Button();

  /**
   * @param defaultMinScore initial value of the score threshold field
   * @param onRefine        invoked when the user clicks "Refine Search"
   */
  SearchPlanEditor(double defaultMinScore, Runnable onRefine) {
    embeddingArea.setLabel(getTranslation("search.advanced.embedding"));
    embeddingArea.setWidthFull();
    embeddingArea.setMaxHeight("100px");

    configureBoolSelect(personSelect, getTranslation("search.advanced.person"));
    configureBoolSelect(vehicleSelect, getTranslation("search.advanced.vehicle"));
    configureBoolSelect(plateSelect, getTranslation("search.advanced.plate"));

    seasonSelect.setLabel(getTranslation("search.advanced.season"));
    seasonSelect.setItems(SeasonHint.values());
    seasonSelect.setItemLabelGenerator(s -> s == null
        ? getTranslation("search.filter.any") : s.name());
    seasonSelect.setPlaceholder(getTranslation("search.filter.any"));

    categorySelect.setLabel(getTranslation("search.advanced.category"));
    categorySelect.setItems(CategoryGroup.values());
    categorySelect.setItemLabelGenerator(c -> c == null
        ? getTranslation("search.filter.any") : c.getLabel());
    categorySelect.setPlaceholder(getTranslation("search.filter.any"));

    privacySelect.setLabel(getTranslation("search.advanced.privacy"));
    privacySelect.setItems(RiskLevel.values());
    privacySelect.setItemLabelGenerator(r -> r == null
        ? getTranslation("search.filter.any") : r.name());
    privacySelect.setPlaceholder(getTranslation("search.filter.any"));

    // Score threshold — minimum similarity for a result to appear
    scoreField.setLabel(getTranslation("search.advanced.threshold"));
    scoreField.setMin(0.0);
    scoreField.setMax(1.0);
    scoreField.setStep(0.05);
    scoreField.setValue(defaultMinScore);
    scoreField.setWidth("130px");

    // "Refine Search" — skip LLM and use the edited plan directly
    refineBtn.setText(getTranslation("search.advanced.refine"));
    refineBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
    refineBtn.addClickListener(e -> onRefine.run());

    HorizontalLayout boolRow = new HorizontalLayout(personSelect, vehicleSelect, plateSelect);
    boolRow.setSpacing(true);

    HorizontalLayout enumRow = new HorizontalLayout(seasonSelect, categorySelect, privacySelect, scoreField);
    enumRow.setSpacing(true);

    VerticalLayout content = new VerticalLayout(embeddingArea, boolRow, enumRow, refineBtn);
    content.setSpacing(true);
    content.setPadding(false);

    setSummaryText(getTranslation("search.advanced.header"));
    add(content);
    setOpened(false);
  }

  /**
   * Shows the values of {@code plan} in the editable fields and opens the panel.
   */
  void populate(SearchPlan plan) {
    embeddingArea.setValue(plan.getEmbeddingText() != null ? plan.getEmbeddingText() : "");
    personSelect.setValue(boolToSelect(plan.getContainsPerson()));
    vehicleSelect.setValue(boolToSelect(plan.getContainsVehicle()));
    plateSelect.setValue(boolToSelect(plan.getContainsLicensePlate()));
    seasonSelect.setValue(plan.getSeasonHint());
    categorySelect.setValue(plan.getCategoryGroup());
    privacySelect.setValue(plan.getPrivacyLevel());
    scoreField.setValue(plan.getMinScore() != null ? plan.getMinScore() : FALLBACK_THRESHOLD);
    setOpened(true);
  }

  /**
   * Collects the edited values into a new {@link SearchPlan}.
   *
   * @param basePlan      the plan the user started from; may be null
   * @param fallbackQuery query field content, used when there is no base plan
   */
  SearchPlan collect(SearchPlan basePlan, String fallbackQuery) {
    SearchPlan p = new SearchPlan();
    p.setOriginalQuery(basePlan != null ? basePlan.getOriginalQuery() : fallbackQuery);
    p.setEmbeddingText(embeddingArea.getValue().isBlank()
                           ? (basePlan != null ? basePlan.getEmbeddingText() : fallbackQuery)
                           : embeddingArea.getValue().trim());
    p.setContainsPerson(boolFromSelect(personSelect));
    p.setContainsVehicle(boolFromSelect(vehicleSelect));
    p.setContainsLicensePlate(boolFromSelect(plateSelect));
    p.setSeasonHint(seasonSelect.getValue());
    p.setCategoryGroup(categorySelect.getValue());
    p.setPrivacyLevel(privacySelect.getValue());
    p.setExplanation(basePlan != null ? basePlan.getExplanation() : null);
    p.setMinScore(effectiveThreshold());
    return p;
  }

  /**
   * Reads the score threshold, clamps it to [0.0, 1.0] and falls back to
   * {@link #FALLBACK_THRESHOLD} when the field is empty.
   */
  double effectiveThreshold() {
    Double raw = scoreField.getValue();
    if (raw == null) return FALLBACK_THRESHOLD;
    return Math.max(0.0, Math.min(1.0, raw));
  }

  void setRefineEnabled(boolean enabled) {
    refineBtn.setEnabled(enabled);
  }

  private void configureBoolSelect(Select<String> sel, String label) {
    sel.setLabel(label);
    sel.setItems(BOOL_ANY, BOOL_YES, BOOL_NO);
    sel.setItemLabelGenerator(v -> switch (v) {
      case BOOL_YES -> getTranslation("search.yes");
      case BOOL_NO -> getTranslation("search.no");
      default -> getTranslation("search.filter.any");
    });
    sel.setValue(BOOL_ANY);
  }

  private Boolean boolFromSelect(Select<String> sel) {
    return switch (sel.getValue()) {
      case BOOL_YES -> Boolean.TRUE;
      case BOOL_NO -> Boolean.FALSE;
      default -> null;
    };
  }

  private String boolToSelect(Boolean value) {
    if (Boolean.TRUE.equals(value)) return BOOL_YES;
    if (Boolean.FALSE.equals(value)) return BOOL_NO;
    return BOOL_ANY;
  }
}
