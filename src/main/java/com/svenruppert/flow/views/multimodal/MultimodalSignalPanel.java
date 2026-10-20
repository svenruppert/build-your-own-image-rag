package com.svenruppert.flow.views.multimodal;

import com.svenruppert.imagerag.domain.ImageAsset;
import com.svenruppert.imagerag.dto.MultimodalSignal;
import com.svenruppert.imagerag.service.ImageCatalogService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.svenruppert.flow.views.multimodal.MultimodalUi.sectionLabel;
import static com.svenruppert.flow.views.multimodal.MultimodalUi.sep;

/**
 * Left panel of the multimodal search: builds the list of input signals (text,
 * image example, OCR terms, category filter) and shows the active signals.
 */
final class MultimodalSignalPanel
    extends VerticalLayout {

  private final ImageCatalogService catalog;
  private final List<MultimodalSignal> signals = new ArrayList<>();
  private final VerticalLayout signalsList = new VerticalLayout();

  private final TextArea textInput = new TextArea();
  private final TextField imageIdInput = new TextField();
  private final TextArea ocrInput = new TextArea();
  private final TextField categoryInput = new TextField();
  private final NumberField textWeight = new NumberField();
  private final NumberField imageWeight = new NumberField();
  private final NumberField ocrWeight = new NumberField();

  MultimodalSignalPanel(ImageCatalogService catalog) {
    this.catalog = catalog;
    MultimodalUi.stylePanel(this, "280px");
    build();
  }

  /**
   * The active signals in insertion order (live list).
   */
  List<MultimodalSignal> signals() {
    return signals;
  }

  private void build() {
    // Text signal
    textInput.setLabel(getTranslation("multimodal.signal.text"));
    textInput.setPlaceholder(getTranslation("multimodal.signal.text.placeholder"));
    textInput.setWidthFull();
    textInput.setMaxHeight("80px");

    textWeight.setLabel(getTranslation("multimodal.signal.weight"));
    textWeight.setValue(0.7);
    textWeight.setMin(0.1);
    textWeight.setMax(1.0);
    textWeight.setStep(0.1);
    textWeight.setWidthFull();

    Button addText = new Button(getTranslation("multimodal.signal.add.text"), e -> {
      String q = textInput.getValue();
      if (!q.isBlank()) {
        addSignal(MultimodalSignal.text(q.trim(), textWeight.getValue()));
        textInput.clear();
      }
    });
    addText.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);

    // Image example signal
    imageIdInput.setLabel(getTranslation("multimodal.signal.image.id"));
    imageIdInput.setPlaceholder(getTranslation("multimodal.signal.image.placeholder"));
    imageIdInput.setWidthFull();

    imageWeight.setLabel(getTranslation("multimodal.signal.weight"));
    imageWeight.setValue(0.5);
    imageWeight.setMin(0.1);
    imageWeight.setMax(1.0);
    imageWeight.setStep(0.1);
    imageWeight.setWidthFull();

    Button addImage = new Button(getTranslation("multimodal.signal.add.image"), e -> {
      UUID id = resolveImageId(imageIdInput.getValue());
      if (id != null) {
        addSignal(MultimodalSignal.imageExample(id, imageWeight.getValue()));
        imageIdInput.clear();
      } else {
        Notification.show(getTranslation("multimodal.signal.image.not.found", imageIdInput.getValue()), 2500, Notification.Position.BOTTOM_START);
      }
    });
    addImage.addThemeVariants(ButtonVariant.LUMO_SMALL);

    // OCR terms signal
    ocrInput.setLabel(getTranslation("multimodal.signal.ocr"));
    ocrInput.setPlaceholder(getTranslation("multimodal.signal.ocr.placeholder"));
    ocrInput.setWidthFull();
    ocrInput.setMaxHeight("60px");

    ocrWeight.setLabel(getTranslation("multimodal.signal.weight"));
    ocrWeight.setValue(0.6);
    ocrWeight.setMin(0.1);
    ocrWeight.setMax(1.0);
    ocrWeight.setStep(0.1);
    ocrWeight.setWidthFull();

    Button addOcr = new Button(getTranslation("multimodal.signal.add.ocr"), e -> {
      String t = ocrInput.getValue();
      if (!t.isBlank()) {
        addSignal(MultimodalSignal.ocrTerms(t.trim(), ocrWeight.getValue()));
        ocrInput.clear();
      }
    });
    addOcr.addThemeVariants(ButtonVariant.LUMO_SMALL);

    // Category filter signal
    categoryInput.setLabel(getTranslation("multimodal.signal.category"));
    categoryInput.setPlaceholder(getTranslation("multimodal.signal.category.placeholder"));
    categoryInput.setWidthFull();

    Button addCat = new Button(getTranslation("multimodal.signal.add.category"), e -> {
      String cat = categoryInput.getValue();
      if (!cat.isBlank()) {
        addSignal(MultimodalSignal.categoryFilter(cat.trim()));
        categoryInput.clear();
      }
    });
    addCat.addThemeVariants(ButtonVariant.LUMO_SMALL);

    // Active signals list
    signalsList.setPadding(false);
    signalsList.setSpacing(false);
    signalsList.setWidthFull();
    Span signalsHeading = new Span(getTranslation("multimodal.signals.active"));
    signalsHeading.getStyle()
        .set("font-weight", "600").set("font-size", "var(--lumo-font-size-xs)")
        .set("text-transform", "uppercase").set("letter-spacing", "0.05em")
        .set("color", "var(--lumo-secondary-text-color)").set("display", "block")
        .set("margin-top", "0.5rem");

    Button clearAll = new Button(getTranslation("multimodal.signals.clear"), e -> {
      signals.clear();
      refreshSignalsList();
    });
    clearAll.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY,
                              ButtonVariant.LUMO_ERROR);

    add(
        sectionLabel(getTranslation("multimodal.signal.text.section")),
        textInput, textWeight, addText,
        sep(),
        sectionLabel(getTranslation("multimodal.signal.image.section")),
        imageIdInput, imageWeight, addImage,
        sep(),
        sectionLabel(getTranslation("multimodal.signal.ocr.section")),
        ocrInput, ocrWeight, addOcr,
        sep(),
        sectionLabel(getTranslation("multimodal.signal.category.section")),
        categoryInput, addCat,
        sep(),
        signalsHeading, clearAll,
        signalsList
    );
  }

  private void addSignal(MultimodalSignal sig) {
    signals.add(sig);
    refreshSignalsList();
  }

  private void refreshSignalsList() {
    signalsList.removeAll();
    for (int i = 0; i < signals.size(); i++) {
      final int idx = i;
      MultimodalSignal sig = signals.get(i);
      Span label = new Span(sig.displayLabel());
      label.getStyle()
          .set("font-size", "var(--lumo-font-size-xs)").set("flex", "1")
          .set("overflow", "hidden").set("text-overflow", "ellipsis")
          .set("white-space", "nowrap")
          .set("color", sig.type().getColor());
      Button remove = new Button("×", e -> {
        signals.remove(idx);
        refreshSignalsList();
      });
      remove.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_SMALL,
                              ButtonVariant.LUMO_ERROR);
      HorizontalLayout row = new HorizontalLayout(label, remove);
      row.setAlignItems(FlexComponent.Alignment.CENTER);
      row.setWidthFull();
      row.setSpacing(false);
      row.getStyle().set("gap", "0.3rem").set("padding", "0.1rem 0");
      signalsList.add(row);
    }
  }

  private UUID resolveImageId(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return UUID.fromString(value.trim());
    } catch (IllegalArgumentException ignored) {
    }
    // Try filename fragment
    return catalog.findAllImages().stream()
        .filter(a -> a.getOriginalFilename() != null
            && a.getOriginalFilename().toLowerCase().contains(value.toLowerCase()))
        .map(ImageAsset::getId)
        .findFirst().orElse(null);
  }
}
