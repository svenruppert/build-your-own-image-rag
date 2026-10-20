package com.svenruppert.flow.views.tuning;

import com.svenruppert.imagerag.domain.ImageAsset;
import com.svenruppert.imagerag.service.ImageCatalogService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.textfield.TextArea;

import java.util.UUID;

import static com.svenruppert.flow.views.tuning.TuningStyles.sectionLabel;
import static com.svenruppert.flow.views.tuning.TuningStyles.separator;

/**
 * Left column of the Search Tuning Lab: query field, run button with progress,
 * the query-by-example anchor and the preset buttons.
 */
class TuningQueryPanel
    extends VerticalLayout {

  private final ImageCatalogService catalog;
  private final TextArea queryField = new TextArea();
  private final Button runBtn = new Button();
  private final ProgressBar runProgress = new ProgressBar();
  private final Span qbeStatusSpan = new Span();

  /**
   * Currently selected query-by-example anchor image; null when none is set.
   */
  private UUID qbeImageId = null;

  TuningQueryPanel(ImageCatalogService catalog,
                   Runnable onRun,
                   Runnable onSavePreset,
                   Runnable onLoadPreset) {
    this.catalog = catalog;
    TuningStyles.stylePanel(this, "240px");

    runBtn.setText(getTranslation("tuning.run"));
    runBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    runBtn.setWidthFull();
    runBtn.addClickListener(e -> onRun.run());

    queryField.setLabel(getTranslation("tuning.query"));
    queryField.setPlaceholder(getTranslation("tuning.query.placeholder"));
    queryField.setWidthFull();
    queryField.setMinHeight("70px");
    queryField.setMaxHeight("120px");

    // Progress bar (hidden by default)
    runProgress.setIndeterminate(true);
    runProgress.setVisible(false);
    runProgress.setWidthFull();

    // QBE status row
    qbeStatusSpan.getStyle()
        .set("font-size", "var(--lumo-font-size-xs)")
        .set("color", "var(--lumo-secondary-text-color)")
        .set("flex", "1");
    updateQbeStatus();

    Button clearQbeBtn = new Button(getTranslation("tuning.qbe.clear"));
    clearQbeBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
    clearQbeBtn.getStyle().set("flex-shrink", "0");
    clearQbeBtn.addClickListener(e -> setQbeImageId(null));

    HorizontalLayout qbeRow = new HorizontalLayout(qbeStatusSpan, clearQbeBtn);
    qbeRow.setAlignItems(FlexComponent.Alignment.CENTER);
    qbeRow.setWidthFull();
    qbeRow.setSpacing(false);
    qbeRow.getStyle().set("gap", "0.3rem");

    // Presets row (save + load side by side)
    Button saveBtn = new Button(getTranslation("tuning.preset.save"));
    saveBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
    saveBtn.setWidthFull();
    saveBtn.addClickListener(e -> onSavePreset.run());

    Button loadBtn = new Button(getTranslation("tuning.preset.load"));
    loadBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
    loadBtn.setWidthFull();
    loadBtn.addClickListener(e -> onLoadPreset.run());

    HorizontalLayout presetsRow = new HorizontalLayout(saveBtn, loadBtn);
    presetsRow.setWidthFull();
    presetsRow.setSpacing(false);
    presetsRow.getStyle().set("gap", "0.3rem");

    add(
        queryField,
        runBtn,
        runProgress,
        separator(),
        sectionLabel(getTranslation("tuning.qbe.label")), qbeRow,
        separator(),
        sectionLabel(getTranslation("tuning.presets")), presetsRow
    );
  }

  String query() {
    return queryField.getValue();
  }

  void setQuery(String query) {
    queryField.setValue(query);
  }

  UUID qbeImageId() {
    return qbeImageId;
  }

  void setQbeImageId(UUID imageId) {
    qbeImageId = imageId;
    updateQbeStatus();
  }

  /**
   * Switches between the idle and the running state of the run button.
   */
  void setRunning(boolean running) {
    runBtn.setEnabled(!running);
    runProgress.setVisible(running);
  }

  private void updateQbeStatus() {
    if (qbeImageId == null) {
      qbeStatusSpan.setText(getTranslation("tuning.qbe.none"));
      qbeStatusSpan.getStyle().remove("color");
    } else {
      String label = catalog.findImage(qbeImageId)
          .map(ImageAsset::getOriginalFilename).orElse(qbeImageId.toString());
      if (label.length() > 26) label = label.substring(0, 23) + "…";
      qbeStatusSpan.setText(getTranslation("tuning.qbe.active", label));
      qbeStatusSpan.getStyle().set("color", "var(--lumo-primary-color)");
    }
  }
}
