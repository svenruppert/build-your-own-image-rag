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
package com.svenruppert.flow.views.taxonomy;

import com.svenruppert.imagerag.domain.CategoryConfidence;
import com.svenruppert.imagerag.domain.CategoryRegistry;
import com.svenruppert.imagerag.domain.SemanticAnalysis;
import com.svenruppert.imagerag.domain.TaxonomyAnalysisScope;
import com.svenruppert.imagerag.domain.enums.CategoryGroup;
import com.svenruppert.imagerag.domain.enums.SourceCategory;
import com.svenruppert.imagerag.service.ImageCatalogService;
import com.svenruppert.imagerag.settings.Settings;
import com.svenruppert.imagerag.settings.SettingsService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.NumberField;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Scope definition panel of the taxonomy maintenance view: analysis mode with its
 * mode-specific inputs, a live scope summary and the "analyze" / "dry run" buttons.
 */
class TaxonomyScopePanel
    extends VerticalLayout {

  private final ImageCatalogService catalog;

  private final Select<TaxonomyAnalysisScope.Mode> modeSelect = new Select<>();
  private final ComboBox<CategoryGroup> groupCombo = new ComboBox<>();
  private final NumberField thresholdField = new NumberField();
  private final Checkbox subgroupCheck = new Checkbox();
  private final Button selectImagesBtn = new Button();
  private final Span scopeSummary = new Span();
  private final Button analyzeBtn = new Button();
  private final Button dryRunBtn = new Button();

  /**
   * Currently selected image IDs for MANUAL_SELECTION mode.
   */
  private List<UUID> manualImageIds = new ArrayList<>();

  /**
   * @param catalog read access to images and analyses for the scope summary
   * @param onRun   invoked with {@code true} for a dry run, {@code false} for a live analysis
   */
  TaxonomyScopePanel(ImageCatalogService catalog, Consumer<Boolean> onRun) {
    this.catalog = catalog;

    // Mode selector
    modeSelect.setLabel(getTranslation("taxonomy.scope.mode"));
    modeSelect.setItems(TaxonomyAnalysisScope.Mode.values());
    modeSelect.setItemLabelGenerator(m -> getTranslation("taxonomy.scope.mode." + m.name()));
    modeSelect.setValue(TaxonomyAnalysisScope.Mode.ALL);
    modeSelect.addValueChangeListener(e -> onModeChanged(e.getValue()));

    // Category group (visible only in CATEGORY_GROUP mode)
    groupCombo.setLabel(getTranslation("taxonomy.scope.group"));
    groupCombo.setItems(CategoryGroup.values());
    groupCombo.setItemLabelGenerator(CategoryGroup::getLabel);
    groupCombo.setVisible(false);
    groupCombo.addValueChangeListener(e -> updateScopeSummary());

    // Include subgroups (visible only in CATEGORY_GROUP mode)
    subgroupCheck.setLabel(getTranslation("taxonomy.scope.include.subgroups"));
    subgroupCheck.setValue(true);
    subgroupCheck.setVisible(false);
    subgroupCheck.addValueChangeListener(e -> updateScopeSummary());

    // Confidence threshold (visible only in LOW_CONFIDENCE mode)
    thresholdField.setLabel(getTranslation("taxonomy.scope.threshold"));
    thresholdField.setValue(defaultConfidenceThreshold());
    thresholdField.setMin(0.0);
    thresholdField.setMax(1.0);
    thresholdField.setStep(0.05);
    thresholdField.setVisible(false);
    thresholdField.addValueChangeListener(e -> updateScopeSummary());

    // Manual image selection button (visible only in MANUAL_SELECTION mode)
    selectImagesBtn.setText(getTranslation("taxonomy.scope.select.images"));
    selectImagesBtn.addThemeVariants(ButtonVariant.LUMO_CONTRAST, ButtonVariant.LUMO_SMALL);
    selectImagesBtn.setVisible(false);
    selectImagesBtn.addClickListener(e -> openManualSelectionDialog());

    // Scope summary badge
    scopeSummary.getStyle()
        .set("font-size", "var(--lumo-font-size-s)")
        .set("color", "var(--lumo-secondary-text-color)")
        .set("align-self", "flex-end")
        .set("padding-bottom", "0.25rem");

    // Run buttons
    analyzeBtn.setText(getTranslation("taxonomy.analyze"));
    analyzeBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    analyzeBtn.addClickListener(e -> onRun.accept(false));

    dryRunBtn.setText(getTranslation("taxonomy.analyze.dryrun"));
    dryRunBtn.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
    dryRunBtn.addClickListener(e -> onRun.accept(true));

    HorizontalLayout scopeRow1 = new HorizontalLayout(
        modeSelect, groupCombo, subgroupCheck, thresholdField, selectImagesBtn);
    scopeRow1.setAlignItems(FlexComponent.Alignment.END);
    scopeRow1.setSpacing(true);

    HorizontalLayout scopeRow2 = new HorizontalLayout(
        scopeSummary, analyzeBtn, dryRunBtn);
    scopeRow2.setAlignItems(FlexComponent.Alignment.END);
    scopeRow2.setSpacing(true);

    add(scopeRow1, scopeRow2);
    setSpacing(false);
    setPadding(true);
    getStyle()
        .set("border", "1px solid var(--lumo-contrast-10pct)")
        .set("border-radius", "var(--lumo-border-radius-l)")
        .set("background", "var(--lumo-base-color)");
    setWidthFull();
  }

  /**
   * Default LOW_CONFIDENCE threshold ({@link Settings#TAXONOMY_CONFIDENCE_THRESHOLD}).
   */
  private static double defaultConfidenceThreshold() {
    return SettingsService.getInstance().get(Settings.TAXONOMY_CONFIDENCE_THRESHOLD);
  }

  private void onModeChanged(TaxonomyAnalysisScope.Mode mode) {
    boolean isGroup = mode == TaxonomyAnalysisScope.Mode.CATEGORY_GROUP;
    boolean isLowConf = mode == TaxonomyAnalysisScope.Mode.LOW_CONFIDENCE;
    boolean isManual = mode == TaxonomyAnalysisScope.Mode.MANUAL_SELECTION;

    groupCombo.setVisible(isGroup);
    subgroupCheck.setVisible(isGroup);
    thresholdField.setVisible(isLowConf);
    selectImagesBtn.setVisible(isManual);

    updateScopeSummary();
  }

  private void openManualSelectionDialog() {
    ManualImageSelectionDialog dialog = new ManualImageSelectionDialog(
        catalog, manualImageIds, selected -> {
      manualImageIds = new ArrayList<>(selected);
      updateScopeSummary();
    });
    dialog.open();
  }

  /**
   * Computes a human-readable scope summary (e.g. "~42 images in scope") and
   * updates the {@link #scopeSummary} label.  Runs on the UI thread; no I/O.
   */
  void updateScopeSummary() {
    TaxonomyAnalysisScope.Mode mode = modeSelect.getValue();
    if (mode == null) {
      scopeSummary.setText("");
      return;
    }

    String text = switch (mode) {
      case ALL -> {
        long count = catalog.findAllImages().size();
        yield getTranslation("taxonomy.scope.summary.all", count);
      }
      case MANUAL_SELECTION -> {
        int sel = manualImageIds.size();
        yield sel == 0
            ? getTranslation("taxonomy.scope.summary.manual.none")
            : getTranslation("taxonomy.scope.summary.manual", sel);
      }
      case CATEGORY_GROUP -> {
        CategoryGroup group = groupCombo.getValue();
        if (group == null) yield getTranslation("taxonomy.scope.summary.group.none");
        boolean incl = subgroupCheck.getValue() != null && subgroupCheck.getValue();
        long count = catalog.findAllImages().stream()
            .filter(a -> {
              Optional<SemanticAnalysis> an = catalog.findAnalysis(a.getId());
              if (an.isEmpty()) return false;
              boolean prim = CategoryRegistry.getGroup(an.get().getSourceCategory()) == group;
              boolean sec = incl && an.get().getSecondaryCategories().stream()
                  .anyMatch(sc -> CategoryRegistry.getGroup(sc) == group);
              return prim || sec;
            }).count();
        yield getTranslation("taxonomy.scope.summary.group", group.getLabel(), count);
      }
      case MISSING_CATEGORY -> {
        long count = catalog.findAllImages().stream()
            .filter(a -> {
              Optional<SemanticAnalysis> an = catalog.findAnalysis(a.getId());
              if (an.isEmpty()) return true;
              SourceCategory cat = an.get().getSourceCategory();
              return cat == null || cat == SourceCategory.UNKNOWN || cat == SourceCategory.MIXED;
            }).count();
        yield getTranslation("taxonomy.scope.summary.missing", count);
      }
      case LOW_CONFIDENCE -> {
        Double thr = thresholdField.getValue();
        double threshold = thr != null ? thr : defaultConfidenceThreshold();
        long count = catalog.findAllImages().stream()
            .filter(a -> {
              Optional<SemanticAnalysis> an = catalog.findAnalysis(a.getId());
              if (an.isEmpty()) return false;
              CategoryConfidence cc = an.get().getCategoryConfidence();
              return cc == null || cc.isAmbiguous(threshold);
            }).count();
        yield getTranslation("taxonomy.scope.summary.low.confidence", count, threshold);
      }
    };
    scopeSummary.setText(text);
  }

  /**
   * Builds the analysis scope from the current inputs.
   *
   * @return the scope, or {@code null} when the inputs are incomplete (the user was notified)
   */
  TaxonomyAnalysisScope buildScope() {
    TaxonomyAnalysisScope.Mode mode = modeSelect.getValue();
    if (mode == null) mode = TaxonomyAnalysisScope.Mode.ALL;

    return switch (mode) {
      case ALL -> TaxonomyAnalysisScope.all();

      case MANUAL_SELECTION -> {
        if (manualImageIds.isEmpty()) {
          TaxonomyNotifications.show(getTranslation("taxonomy.scope.manual.empty"), true);
          yield null;
        }
        yield TaxonomyAnalysisScope.manualSelection(manualImageIds);
      }

      case CATEGORY_GROUP -> {
        CategoryGroup group = groupCombo.getValue();
        if (group == null) {
          TaxonomyNotifications.show(getTranslation("taxonomy.scope.group.required"), true);
          yield null;
        }
        boolean incl = subgroupCheck.getValue() != null && subgroupCheck.getValue();
        yield TaxonomyAnalysisScope.forGroup(group, incl);
      }

      case MISSING_CATEGORY -> TaxonomyAnalysisScope.missingCategory();

      case LOW_CONFIDENCE -> {
        Double threshold = thresholdField.getValue();
        yield TaxonomyAnalysisScope.lowConfidence(threshold != null ? threshold : defaultConfidenceThreshold());
      }
    };
  }

  void setControlsEnabled(boolean enabled) {
    modeSelect.setEnabled(enabled);
    groupCombo.setEnabled(enabled);
    subgroupCheck.setEnabled(enabled);
    thresholdField.setEnabled(enabled);
    selectImagesBtn.setEnabled(enabled);
    analyzeBtn.setEnabled(enabled);
    dryRunBtn.setEnabled(enabled);
  }
}
