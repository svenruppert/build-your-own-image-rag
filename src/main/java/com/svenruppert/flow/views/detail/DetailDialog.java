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
package com.svenruppert.flow.views.detail;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.functional.result.Unit;
import com.svenruppert.functional.result.Result;
import com.svenruppert.flow.views.shared.Formats;
import com.svenruppert.flow.views.shared.ImagePreviewFactory;
import com.svenruppert.flow.views.shared.MarkdownRenderer;
import com.svenruppert.flow.views.shared.RiskBadge;
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
    extends Dialog
    implements HasLogger {

  private static final DateTimeFormatter DATE_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
  private final ViewServices services;
  private final ImagePreviewFactory previewFactory;

  public DetailDialog(ImageAsset asset,
                      SemanticAnalysis analysis,
                      SensitivityAssessment assessment,
                      LocationSummary location) {
    setHeaderTitle(getTranslation("detail.title", asset.getOriginalFilename()));
    setWidth("860px");
    setMaxHeight("90vh");
    setCloseOnOutsideClick(true);

    this.services = ViewServices.current();
    this.previewFactory = services.imagePreviews();
    Optional<OcrResult> ocrOpt = services.catalog().findOcrResult(asset.getId());

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
    Button reprocessBtn = new Button(getTranslation("detail.reprocess"));
    reprocessBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    reprocessBtn.getElement().setAttribute("title",
                                           getTranslation("detail.reprocess.tooltip"));
    reprocessBtn.addClickListener(e -> reprocessAndClose(asset));

    Button closeBtn = new Button(getTranslation("common.close"), e -> close());
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
    form.addFormItem(new Span(asset.getMimeType()), getTranslation("detail.field.mime.type"));
    form.addFormItem(new Span(Formats.fileSize(asset.getFileSize())), getTranslation("detail.field.file.size"));
    form.addFormItem(new Span(asset.getWidth() + " \u00d7 " + asset.getHeight()), getTranslation("detail.field.dimensions"));
    form.addFormItem(new Span(asset.getUploadedAt() != null
                                  ? DATE_FMT.format(asset.getUploadedAt()) : "\u2014"), getTranslation("detail.field.uploaded"));
    form.addFormItem(new Span(String.valueOf(asset.isExifPresent())), getTranslation("detail.field.exif.present"));
    form.addFormItem(new Span(String.valueOf(asset.isGpsPresent())), getTranslation("detail.field.gps.present"));
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
      layout.add(new Paragraph(getTranslation("detail.analysis.none")));
      return layout;
    }

    FormLayout form = new FormLayout();
    form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 2));

    // ── Primary category — editable via taxonomy-aware chooser ───────────────
    Span primaryCatLabel = new Span(
        CategoryRegistry.getUserLabel(analysis.getSourceCategory())
            + " (" + CategoryRegistry.getGroupLabel(analysis.getSourceCategory()) + ")");
    primaryCatLabel.getStyle().set("font-weight", "500");

    Button changePrimaryBtn = new Button(getTranslation("detail.analysis.primary.change"));
    changePrimaryBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
    changePrimaryBtn.getElement().setAttribute("title", getTranslation("detail.analysis.primary.change.tooltip"));
    changePrimaryBtn.addClickListener(e ->
        new CategoryTreeChooserDialog(chosen -> {
          if (chosen.equals(analysis.getSourceCategory())) return;
          services.moderation()
              .changeSourceCategory(analysis.getImageId(), chosen);
          // Remove chosen from secondaries if it was there — keeps assignments consistent.
          var normalized = CategoryAssignmentNormalizer.normalizeSecondaries(
              chosen, analysis.getSecondaryCategories());
          if (!normalized.equals(analysis.getSecondaryCategories())) {
            services.moderation()
                .changeSecondaryCategories(analysis.getImageId(), normalized);
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
    form.addFormItem(primaryRow, getTranslation("detail.field.primary.category"));

    form.addFormItem(new Span(Formats.nvl(analysis.getSeasonHint())), getTranslation("detail.field.season"));
    form.addFormItem(new Span(analysis.getSceneType() != null ? analysis.getSceneType() : "\u2014"), getTranslation("detail.field.scene.type"));
    form.addFormItem(new Span(bool(analysis.getContainsPerson())), getTranslation("detail.field.contains.person"));
    form.addFormItem(new Span(bool(analysis.getContainsVehicle())), getTranslation("detail.field.contains.vehicle"));
    form.addFormItem(new Span(bool(analysis.getContainsLicensePlateHint())), getTranslation("detail.field.license.plate"));
    form.addFormItem(new Span(bool(analysis.getContainsReadableText())), getTranslation("detail.field.readable.text"));
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
            services.moderation()
                .changeSecondaryCategories(analysis.getImageId(), cats);
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
              getTranslation("detail.analysis.confidence.alternative.tooltip"));
          altRow.add(chip);
        }
        Paragraph altHeading = new Paragraph(
            getTranslation("detail.analysis.confidence.alternatives") + " "
                + getTranslation("detail.analysis.confidence.alternatives.note"));
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
      layout.add(new H4(getTranslation("detail.col.tags")), tags);
    }

    if (analysis.getSummary() != null) {
      layout.add(new H4(getTranslation("detail.analysis.full.description")));
      layout.add(MarkdownRenderer.render(analysis.getSummary()));
    }

    return layout;
  }

  private VerticalLayout buildPrivacyTab(SensitivityAssessment assessment) {
    VerticalLayout layout = new VerticalLayout();
    layout.setPadding(true);
    layout.setSpacing(true);

    if (assessment == null) {
      layout.add(new Paragraph(getTranslation("detail.privacy.none")));
      return layout;
    }

    FormLayout form = new FormLayout();
    form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 2));

    Span riskBadge = RiskBadge.badge(assessment.getRiskLevel());
    form.addFormItem(riskBadge, getTranslation("detail.field.risk.level"));
    form.addFormItem(new Span(String.valueOf(assessment.isReviewRequired())), getTranslation("detail.field.review.required"));
    form.addFormItem(new Span(Formats.nvl(assessment.getRecommendedStorageMode())), getTranslation("detail.field.storage.mode"));
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
      layout.add(new H4(getTranslation("detail.privacy.flags")), flagRow);
    }

    if (assessment.getNotes() != null) {
      layout.add(new Paragraph(getTranslation("detail.privacy.notes", assessment.getNotes())));
    }

    return layout;
  }

  private VerticalLayout buildLocationTab(LocationSummary location) {
    VerticalLayout layout = new VerticalLayout();
    layout.setPadding(true);
    layout.setSpacing(true);

    if (location == null) {
      layout.add(new Paragraph(getTranslation("detail.location.none")));
      return layout;
    }

    FormLayout form = new FormLayout();
    form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 2));
    form.addFormItem(new Span(location.getCity() != null ? location.getCity() : "\u2014"), getTranslation("detail.field.city"));
    form.addFormItem(new Span(location.getRegion() != null ? location.getRegion() : "\u2014"), getTranslation("detail.field.region"));
    form.addFormItem(new Span(location.getCountry() != null ? location.getCountry() : "\u2014"), getTranslation("detail.field.country"));
    if (location.getLatitude() != null && location.getLongitude() != null) {
      form.addFormItem(new Span(String.format("%.5f, %.5f",
                                              location.getLatitude(), location.getLongitude())),
                       getTranslation("detail.field.coordinates"));
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
      layout.add(new Paragraph(getTranslation("detail.provenance.ocr.none")));
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
        services.moderation()
            .changeSecondaryCategories(analysis.getImageId(), updated);
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

  private String bool(Boolean value) {
    if (value == null) return "\u2014";
    return value ? getTranslation("search.yes") : getTranslation("search.no");
  }

  private void reprocessAndClose(ImageAsset asset) {
    switch (services.reprocessing().reprocess(asset.getId())) {
      case Result.Success<Unit, String> done -> {
        Notification n = Notification.show(
            getTranslation("detail.reprocess.started", asset.getOriginalFilename()),
            5000, Notification.Position.BOTTOM_END);
        n.addThemeVariants(NotificationVariant.LUMO_PRIMARY);
        close();
      }
      case Result.Failure<Unit, String>(String error) -> {
        logger().warn("Reprocessing {} not started: {}", asset.getId(), error);
        Notification n = Notification.show(
            getTranslation("detail.reprocess.failed", error), 5000, Notification.Position.MIDDLE);
        n.addThemeVariants(NotificationVariant.LUMO_ERROR);
      }
    }
  }
}
