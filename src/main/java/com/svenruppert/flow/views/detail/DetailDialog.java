package com.svenruppert.flow.views.detail;

import com.svenruppert.flow.views.shared.ImagePreviewFactory;
import com.svenruppert.flow.views.shared.MarkdownRenderer;
import com.svenruppert.flow.views.shared.ViewServices;
import com.svenruppert.imagerag.domain.CategoryAssignmentNormalizer;
import com.svenruppert.imagerag.domain.*;
import com.svenruppert.imagerag.domain.enums.SourceCategory;
import com.svenruppert.imagerag.service.PreviewService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.TabSheet;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Optional;

public class DetailDialog
    extends Dialog {

  private static final DateTimeFormatter DATE_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
  private final ViewServices services;
  private final ImagePreviewFactory previewFactory;

  public DetailDialog(ImageAsset asset,
                      SemanticAnalysis analysis,
                      SensitivityAssessment assessment,
                      LocationSummary location) {
    setHeaderTitle("Image Details: " + asset.getOriginalFilename());
    setWidth("860px");
    setMaxHeight("90vh");
    setCloseOnOutsideClick(true);

    this.services = ViewServices.current();
    this.previewFactory = services.imagePreviews();
    Optional<OcrResult> ocrOpt = services.persistence().findOcrResult(asset.getId());

    TabSheet tabs = new TabSheet();
    tabs.setWidthFull();

    // ── Tab 1: Overview ───────────────────────────────────────────────────────
    tabs.add(getTranslation("detail.tab.overview"),
             buildOverviewTab(asset));

    // ── Tab 2: Analysis ───────────────────────────────────────────────────────
    tabs.add(getTranslation("detail.tab.analysis"),
             buildAnalysisTab(analysis));

    // ── Tab 3: Privacy ────────────────────────────────────────────────────────
    tabs.add(getTranslation("detail.tab.privacy"),
             buildPrivacyTab(assessment));

    // ── Tab 4: Location ───────────────────────────────────────────────────────
    tabs.add(getTranslation("detail.tab.location"),
             buildLocationTab(location));

    // ── Tab 5: Provenance ─────────────────────────────────────────────────────
    tabs.add(getTranslation("detail.tab.provenance"),
             buildProvenanceTab(analysis, ocrOpt.orElse(null)));

    add(tabs);

    // Footer: Reprocess + Close
    Button reprocessBtn = new Button("Reprocess");
    reprocessBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    reprocessBtn.getElement().setAttribute("title",
                                           "Re-run the full AI pipeline: vision \u2192 semantic \u2192 sensitivity \u2192 embedding. "
                                               + "Existing analysis data is overwritten. "
                                               + "Visibility is re-evaluated: SAFE images are auto-approved; "
                                               + "any other risk level locks the image and requires manual approval in the Overview.");
    reprocessBtn.addClickListener(e -> reprocessAndClose(asset));

    Button closeBtn = new Button("Close", e -> close());
    closeBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    getFooter().add(reprocessBtn, closeBtn);
  }

  // ---------------------------------------------------------------------------
  // Tab content builders
  // ---------------------------------------------------------------------------

  private VerticalLayout buildOverviewTab(ImageAsset asset) {
    VerticalLayout layout = new VerticalLayout();
    layout.setPadding(true);
    layout.setSpacing(true);

    // Thumbnail
    Div wrapper = new Div(previewFactory.image(
        asset,
        PreviewService.PreviewSize.DETAIL,
        "100%",
        "240px",
        "contain",
        null));
    wrapper.getStyle()
        .set("text-align", "center")
        .set("background", "var(--lumo-contrast-5pct)")
        .set("border-radius", "8px")
        .set("padding", "8px");
    layout.add(wrapper);

    // File info form
    FormLayout form = new FormLayout();
    form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 2));
    form.addFormItem(new Span(asset.getId().toString()), "ID");
    form.addFormItem(new Span(asset.getMimeType()), "MIME Type");
    form.addFormItem(new Span(formatSize(asset.getFileSize())), "File Size");
    form.addFormItem(new Span(asset.getWidth() + " \u00d7 " + asset.getHeight()), "Dimensions");
    form.addFormItem(new Span(asset.getUploadedAt() != null
                                  ? DATE_FMT.format(asset.getUploadedAt()) : "\u2014"), "Uploaded");
    form.addFormItem(new Span(String.valueOf(asset.isExifPresent())), "EXIF Present");
    form.addFormItem(new Span(String.valueOf(asset.isGpsPresent())), "GPS Present");
    form.addFormItem(new Span(asset.getSha256() != null
                                  ? asset.getSha256().substring(0, 16) + "\u2026" : "\u2014"), "SHA-256");
    layout.add(form);
    return layout;
  }

  private VerticalLayout buildAnalysisTab(SemanticAnalysis analysis) {
    VerticalLayout layout = new VerticalLayout();
    layout.setPadding(true);
    layout.setSpacing(true);

    if (analysis == null) {
      layout.add(new Paragraph("\u2014 No analysis available \u2014"));
      return layout;
    }

    FormLayout form = new FormLayout();
    form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 2));

    // ── Primary category — editable via taxonomy-aware chooser ───────────────
    Span primaryCatLabel = new Span(
        CategoryRegistry.getUserLabel(analysis.getSourceCategory())
            + " (" + CategoryRegistry.getGroupLabel(analysis.getSourceCategory()) + ")");
    primaryCatLabel.getStyle().set("font-weight", "500");

    Button changePrimaryBtn = new Button("Change");
    changePrimaryBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
    changePrimaryBtn.getElement().setAttribute("title", "Change the primary category");
    changePrimaryBtn.addClickListener(e ->
        new CategoryTreeChooserDialog(chosen -> {
          if (chosen.equals(analysis.getSourceCategory())) return;
          services.persistence()
              .updateSourceCategory(analysis.getImageId(), chosen);
          // Remove chosen from secondaries if it was there — keeps assignments consistent.
          var normalized = CategoryAssignmentNormalizer.normalizeSecondaries(
              chosen, analysis.getSecondaryCategories());
          if (!normalized.equals(analysis.getSecondaryCategories())) {
            services.persistence()
                .updateSecondaryCategories(analysis.getImageId(), normalized);
            analysis.setSecondaryCategories(normalized);
          }
          analysis.setSourceCategory(chosen);
          primaryCatLabel.setText(
              CategoryRegistry.getUserLabel(chosen)
                  + " (" + CategoryRegistry.getGroupLabel(chosen) + ")");
        }).open());

    HorizontalLayout primaryRow = new HorizontalLayout(primaryCatLabel, changePrimaryBtn);
    primaryRow.setAlignItems(FlexComponent.Alignment.CENTER);
    primaryRow.setSpacing(true);
    form.addFormItem(primaryRow, "Primary Category");

    form.addFormItem(new Span(str(analysis.getSeasonHint())), "Season");
    form.addFormItem(new Span(analysis.getSceneType() != null ? analysis.getSceneType() : "\u2014"), "Scene Type");
    form.addFormItem(new Span(bool(analysis.getContainsPerson())), "Contains Person");
    form.addFormItem(new Span(bool(analysis.getContainsVehicle())), "Contains Vehicle");
    form.addFormItem(new Span(bool(analysis.getContainsLicensePlateHint())), "License Plate");
    form.addFormItem(new Span(bool(analysis.getContainsReadableText())), "Readable Text");
    layout.add(form);

    // ── Secondary categories — displayed as removable badges + "Add" button ──
    layout.add(new H4(getTranslation("detail.analysis.secondary.categories")));
    Div secCatsContainer = new Div();
    secCatsContainer.getStyle().set("display", "flex").set("flex-wrap", "wrap")
        .set("gap", "var(--lumo-space-xs)").set("align-items", "center");
    refreshSecondaryBadges(secCatsContainer, analysis);

    Button addSecBtn = new Button("+", e ->
        new CategoryTreeChooserDialog(chosen -> {
          var cats = new ArrayList<>(analysis.getSecondaryCategories());
          if (!cats.contains(chosen) && !chosen.equals(analysis.getSourceCategory())) {
            cats.add(chosen);
            services.persistence()
                .updateSecondaryCategories(analysis.getImageId(), cats);
            analysis.setSecondaryCategories(cats);
            refreshSecondaryBadges(secCatsContainer, analysis);
          }
        }).open());
    addSecBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
    addSecBtn.getElement().setAttribute("title",
                                        getTranslation("detail.analysis.secondary.add"));

    HorizontalLayout secRow = new HorizontalLayout(secCatsContainer, addSecBtn);
    secRow.setAlignItems(com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.CENTER);
    secRow.setSpacing(true);
    layout.add(secRow);

    // ── Category confidence ───────────────────────────────────────────────────
    CategoryConfidence cc = analysis.getCategoryConfidence();
    if (cc != null) {
      layout.add(new H4(getTranslation("detail.analysis.confidence.heading")));
      Span primaryScore = new Span(String.format("%.0f%%", cc.getPrimaryScore() * 100));
      primaryScore.getStyle().set("font-weight", "bold");
      String color = cc.getPrimaryScore() >= 0.7 ? "var(--lumo-success-color)"
          : cc.getPrimaryScore() >= 0.5 ? "var(--lumo-warning-color)"
            : "var(--lumo-error-color)";
      primaryScore.getStyle().set("color", color);
      var paragraph = new Paragraph(getTranslation("detail.analysis.confidence.primary",
                                                   CategoryRegistry.getUserLabel(cc.getPrimaryCategory())) + ": ");
      paragraph.add(primaryScore);
      layout.add(paragraph);
      if (!cc.getAlternatives().isEmpty()) {
        Div altRow = new Div();
        altRow.getStyle().set("display", "flex").set("flex-wrap", "wrap")
            .set("gap", "var(--lumo-space-xs)");
        for (CategoryCandidate alt : cc.getAlternatives()) {
          Span chip = new Span(CategoryRegistry.getUserLabel(alt.getCategory())
                                   + " " + String.format("(%.0f%%)", alt.getScore() * 100));
          chip.getElement().getThemeList().add("badge contrast");
          chip.getElement().setAttribute("title",
              "Predicted alternative — not an assigned category");
          altRow.add(chip);
        }
        Paragraph altHeading = new Paragraph(
            getTranslation("detail.analysis.confidence.alternatives") + " (predicted, not assigned):");
        altHeading.getStyle().set("font-size", "var(--lumo-font-size-s)")
            .set("color", "var(--lumo-secondary-text-color)");
        layout.add(altHeading, altRow);
      }
    }

    if (analysis.getTags() != null && !analysis.getTags().isEmpty()) {
      HorizontalLayout tags = new HorizontalLayout();
      tags.getStyle().set("flex-wrap", "wrap");
      for (String tag : analysis.getTags()) {
        Span badge = new Span(tag);
        badge.getElement().getThemeList().add("badge");
        tags.add(badge);
      }
      layout.add(new H4("Tags"), tags);
    }

    if (analysis.getSummary() != null) {
      layout.add(new H4("Full Description"));
      layout.add(MarkdownRenderer.render(analysis.getSummary()));
    }

    return layout;
  }

  private VerticalLayout buildPrivacyTab(SensitivityAssessment assessment) {
    VerticalLayout layout = new VerticalLayout();
    layout.setPadding(true);
    layout.setSpacing(true);

    if (assessment == null) {
      layout.add(new Paragraph("\u2014 No sensitivity assessment available \u2014"));
      return layout;
    }

    FormLayout form = new FormLayout();
    form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 2));

    Span riskBadge = new Span(assessment.getRiskLevel().name());
    riskBadge.getElement().getThemeList().add("badge");
    switch (assessment.getRiskLevel()) {
      case SAFE -> riskBadge.getElement().getThemeList().add("success");
      case REVIEW -> riskBadge.getElement().getThemeList().add("contrast");
      case SENSITIVE -> riskBadge.getElement().getThemeList().add("error");
      default -> throw new RuntimeException("Should never reach..");
    }
    form.addFormItem(riskBadge, "Risk Level");
    form.addFormItem(new Span(String.valueOf(assessment.isReviewRequired())), "Review Required");
    form.addFormItem(new Span(str(assessment.getRecommendedStorageMode())), "Storage Mode");
    layout.add(form);

    if (assessment.getFlags() != null && !assessment.getFlags().isEmpty()) {
      HorizontalLayout flagRow = new HorizontalLayout();
      flagRow.getStyle().set("flex-wrap", "wrap");
      for (var flag : assessment.getFlags()) {
        Span f = new Span(flag.name().replace("_", " ").toLowerCase());
        f.getElement().getThemeList().add("badge");
        f.getElement().getThemeList().add("contrast");
        flagRow.add(f);
      }
      layout.add(new H4("Flags"), flagRow);
    }

    if (assessment.getNotes() != null) {
      layout.add(new Paragraph("Notes: " + assessment.getNotes()));
    }

    return layout;
  }

  private VerticalLayout buildLocationTab(LocationSummary location) {
    VerticalLayout layout = new VerticalLayout();
    layout.setPadding(true);
    layout.setSpacing(true);

    if (location == null) {
      layout.add(new Paragraph("\u2014 No location data available \u2014"));
      return layout;
    }

    FormLayout form = new FormLayout();
    form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 2));
    form.addFormItem(new Span(location.getCity() != null ? location.getCity() : "\u2014"), "City");
    form.addFormItem(new Span(location.getRegion() != null ? location.getRegion() : "\u2014"), "Region");
    form.addFormItem(new Span(location.getCountry() != null ? location.getCountry() : "\u2014"), "Country");
    if (location.getLatitude() != null && location.getLongitude() != null) {
      form.addFormItem(new Span(String.format("%.5f, %.5f",
                                              location.getLatitude(), location.getLongitude())),
                       "Coordinates");
    }
    layout.add(form);
    return layout;
  }

  private VerticalLayout buildProvenanceTab(SemanticAnalysis analysis, OcrResult ocr) {
    VerticalLayout layout = new VerticalLayout();
    layout.setPadding(true);
    layout.setSpacing(true);

    FormLayout form = new FormLayout();
    form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 2));

    String visionModel = "\u2014";
    String semanticModel = "\u2014";
    String analysisTime = "\u2014";
    String visionPromptVersion = "\u2014";
    String semanticPromptVersion = "\u2014";

    if (analysis != null) {
      if (analysis.getVisionModel() != null) {
        visionModel = analysis.getVisionModel();
      }
      if (analysis.getSemanticModel() != null) {
        semanticModel = analysis.getSemanticModel();
      }
      if (analysis.getAnalysisTimestamp() != null) {
        analysisTime = DATE_FMT.format(analysis.getAnalysisTimestamp());
      }
      if (analysis.getVisionPromptVersion() != null) {
        visionPromptVersion = analysis.getVisionPromptVersion();
      }
      if (analysis.getSemanticPromptVersion() != null) {
        semanticPromptVersion = analysis.getSemanticPromptVersion();
      }
    }

    form.addFormItem(new Span(visionModel),
                     getTranslation("detail.provenance.vision.model"));
    form.addFormItem(new Span(visionPromptVersion),
                     getTranslation("detail.provenance.vision.prompt.version"));
    form.addFormItem(new Span(semanticModel),
                     getTranslation("detail.provenance.semantic.model"));
    form.addFormItem(new Span(semanticPromptVersion),
                     getTranslation("detail.provenance.semantic.prompt.version"));
    form.addFormItem(new Span(analysisTime),
                     getTranslation("detail.provenance.analysis.time"));
    layout.add(form);

    // OCR text section
    layout.add(new H4(getTranslation("detail.provenance.ocr.text")));
    if (ocr != null && ocr.isTextFound() && ocr.getExtractedText() != null) {
      Div ocrBox = new Div();
      ocrBox.getStyle()
          .set("background", "var(--lumo-contrast-5pct)")
          .set("border-radius", "6px")
          .set("padding", "12px")
          .set("font-family", "monospace")
          .set("white-space", "pre-wrap")
          .set("word-break", "break-word")
          .set("max-height", "200px")
          .set("overflow-y", "auto");
      ocrBox.setText(ocr.getExtractedText());
      layout.add(ocrBox);
    } else {
      layout.add(new Paragraph("\u2014 No text extracted \u2014"));
    }

    return layout;
  }

  // ---------------------------------------------------------------------------
  // Secondary category helpers
  // ---------------------------------------------------------------------------

  /**
   * Clears and repopulates {@code container} with one badge per secondary category.
   * Each badge has an "×" button that removes the category from the analysis and persists.
   */
  private void refreshSecondaryBadges(Div container, SemanticAnalysis analysis) {
    container.removeAll();
    var cats = analysis.getSecondaryCategories();
    if (cats.isEmpty()) {
      Span none = new Span("—");
      none.getStyle().set("color", "var(--lumo-secondary-text-color)")
          .set("font-size", "var(--lumo-font-size-s)");
      container.add(none);
      return;
    }
    for (SourceCategory sc : cats) {
      Span label = new Span(CategoryRegistry.getUserLabel(sc));
      label.getStyle().set("font-size", "var(--lumo-font-size-s)");

      Button removeBtn = new Button("×");
      removeBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY,
                                 ButtonVariant.LUMO_ERROR);
      removeBtn.getStyle().set("min-width", "unset").set("padding", "0 4px");
      removeBtn.addClickListener(e -> {
        var updated = new ArrayList<>(analysis.getSecondaryCategories());
        updated.remove(sc);
        services.persistence()
            .updateSecondaryCategories(analysis.getImageId(), updated);
        analysis.setSecondaryCategories(updated);
        refreshSecondaryBadges(container, analysis);
      });

      Div badge = new Div(label, removeBtn);
      badge.getElement().getThemeList().add("badge");
      badge.getElement().getThemeList().add("contrast");
      badge.getStyle().set("display", "flex").set("align-items", "center")
          .set("gap", "2px").set("padding-right", "2px");
      container.add(badge);
    }
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private String formatSize(long bytes) {
    if (bytes < 1024) return bytes + " B";
    if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
    return String.format("%.1f MB", bytes / (1024.0 * 1024));
  }

  private String str(Object value) {
    return value != null ? value.toString() : "\u2014";
  }

  private String bool(Boolean value) {
    if (value == null) return "\u2014";
    return value ? "Yes" : "No";
  }

  private void reprocessAndClose(ImageAsset asset) {
    try {
      services.reprocessing().reprocess(asset.getId());
      Notification n = Notification.show(
          "Reprocessing started for: " + asset.getOriginalFilename()
              + ". Results appear in the Overview once complete.",
          5000, Notification.Position.BOTTOM_END);
      n.addThemeVariants(NotificationVariant.LUMO_PRIMARY);
      close();
    } catch (Exception ex) {
      Notification n = Notification.show(
          "Reprocessing failed: " + ex.getMessage(), 5000, Notification.Position.MIDDLE);
      n.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
  }
}
