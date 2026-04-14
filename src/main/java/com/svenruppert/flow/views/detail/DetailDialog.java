package com.svenruppert.flow.views.detail;

import com.svenruppert.imagerag.bootstrap.ServiceRegistry;
import com.svenruppert.imagerag.domain.ImageAsset;
import com.svenruppert.imagerag.domain.LocationSummary;
import com.svenruppert.imagerag.domain.SemanticAnalysis;
import com.svenruppert.imagerag.domain.SensitivityAssessment;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.server.StreamResource;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class DetailDialog
    extends Dialog {

  private static final DateTimeFormatter DATE_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

  public DetailDialog(ImageAsset asset,
                      SemanticAnalysis analysis,
                      SensitivityAssessment assessment,
                      LocationSummary location) {
    setHeaderTitle("Image Details: " + asset.getOriginalFilename());
    setWidth("860px");
    setMaxHeight("90vh");
    setCloseOnOutsideClick(true);

    VerticalLayout content = new VerticalLayout();
    content.setPadding(false);
    content.setSpacing(true);

    // ── Thumbnail preview ─────────────────────────────────────────────────────
    try {
      Path imgPath = ServiceRegistry.getInstance()
          .getImageStorageService().resolvePath(asset.getId());
      if (Files.exists(imgPath)) {
        StreamResource res = new StreamResource(
            asset.getStoredFilename(),
            () -> {
              try {
                return Files.newInputStream(imgPath);
              } catch (Exception ex) {
                return InputStream.nullInputStream();
              }
            });
        Image preview = new Image(res, asset.getOriginalFilename());
        preview.setMaxHeight("280px");
        preview.setMaxWidth("100%");
        preview.getStyle()
            .set("object-fit", "contain")
            .set("border-radius", "8px")
            .set("display", "block")
            .set("margin", "0 auto 8px");
        Div previewWrapper = new Div(preview);
        previewWrapper.getStyle()
            .set("text-align", "center")
            .set("background", "var(--lumo-contrast-5pct)")
            .set("border-radius", "8px")
            .set("padding", "8px");
        content.add(previewWrapper);
      }
    } catch (Exception ignored) {
      // Thumbnail is optional — proceed without it
    }

    // ── Technical metadata ────────────────────────────────────────────────────
    content.add(new H3("Technical Metadata"));
    FormLayout techForm = new FormLayout();
    techForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 2));

    techForm.addFormItem(new Span(asset.getId().toString()), "ID");
    techForm.addFormItem(new Span(asset.getMimeType()), "MIME Type");
    techForm.addFormItem(new Span(formatSize(asset.getFileSize())), "File Size");
    techForm.addFormItem(new Span(asset.getWidth() + " × " + asset.getHeight()), "Dimensions");
    techForm.addFormItem(new Span(asset.getUploadedAt() != null
                                      ? DATE_FMT.format(asset.getUploadedAt()) : "—"), "Uploaded");
    techForm.addFormItem(new Span(String.valueOf(asset.isExifPresent())), "EXIF Present");
    techForm.addFormItem(new Span(String.valueOf(asset.isGpsPresent())), "GPS Present");
    techForm.addFormItem(new Span(asset.getSha256() != null
                                      ? asset.getSha256().substring(0, 16) + "…" : "—"), "SHA-256");
    content.add(techForm);

    // ── Location ──────────────────────────────────────────────────────────────
    if (location != null) {
      content.add(new H3("Location"));
      FormLayout locForm = new FormLayout();
      locForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 2));
      locForm.addFormItem(new Span(location.getCity() != null ? location.getCity() : "—"), "City");
      locForm.addFormItem(new Span(location.getRegion() != null ? location.getRegion() : "—"), "Region");
      locForm.addFormItem(new Span(location.getCountry() != null ? location.getCountry() : "—"), "Country");
      if (location.getLatitude() != null && location.getLongitude() != null) {
        locForm.addFormItem(new Span(String.format("%.5f, %.5f",
                                                   location.getLatitude(), location.getLongitude())),
                            "Coordinates");
      }
      content.add(locForm);
    }

    // ── Semantic Analysis ─────────────────────────────────────────────────────
    if (analysis != null) {
      content.add(new H3("Semantic Analysis"));
      FormLayout semForm = new FormLayout();
      semForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 2));

      semForm.addFormItem(new Span(str(analysis.getSourceCategory())), "Category");
      semForm.addFormItem(new Span(str(analysis.getSeasonHint())), "Season");
      semForm.addFormItem(new Span(analysis.getSceneType() != null ? analysis.getSceneType() : "—"), "Scene Type");
      semForm.addFormItem(new Span(bool(analysis.getContainsPerson())), "Contains Person");
      semForm.addFormItem(new Span(bool(analysis.getContainsVehicle())), "Contains Vehicle");
      semForm.addFormItem(new Span(bool(analysis.getContainsLicensePlateHint())), "License Plate");
      semForm.addFormItem(new Span(bool(analysis.getContainsReadableText())), "Readable Text");
      content.add(semForm);

      if (analysis.getTags() != null && !analysis.getTags().isEmpty()) {
        HorizontalLayout tags = new HorizontalLayout();
        tags.getStyle().set("flex-wrap", "wrap");
        for (String tag : analysis.getTags()) {
          Span badge = new Span(tag);
          badge.getElement().getThemeList().add("badge");
          tags.add(badge);
        }
        content.add(new H4("Tags"), tags);
      }

      if (analysis.getSummary() != null) {
        content.add(new H4("Full Description"));
        Paragraph para = new Paragraph(analysis.getSummary());
        para.getStyle().set("white-space", "pre-wrap");
        content.add(para);
      }
    }

    // ── Sensitivity Assessment ────────────────────────────────────────────────
    if (assessment != null) {
      content.add(new H3("Privacy & Sensitivity"));
      FormLayout privForm = new FormLayout();
      privForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 2));

      Span riskBadge = new Span(assessment.getRiskLevel().name());
      riskBadge.getElement().getThemeList().add("badge");
      switch (assessment.getRiskLevel()) {
        case SAFE -> riskBadge.getElement().getThemeList().add("success");
        case REVIEW -> riskBadge.getElement().getThemeList().add("contrast");
        case SENSITIVE -> riskBadge.getElement().getThemeList().add("error");
        default -> throw new RuntimeException("Should never reach..");
      }
      privForm.addFormItem(riskBadge, "Risk Level");
      privForm.addFormItem(new Span(String.valueOf(assessment.isReviewRequired())), "Review Required");
      privForm.addFormItem(new Span(str(assessment.getRecommendedStorageMode())), "Storage Mode");
      content.add(privForm);

      if (assessment.getFlags() != null && !assessment.getFlags().isEmpty()) {
        HorizontalLayout flagRow = new HorizontalLayout();
        flagRow.getStyle().set("flex-wrap", "wrap");
        for (var flag : assessment.getFlags()) {
          Span f = new Span(flag.name().replace("_", " ").toLowerCase());
          f.getElement().getThemeList().add("badge");
          f.getElement().getThemeList().add("contrast");
          flagRow.add(f);
        }
        content.add(new H4("Flags"), flagRow);
      }

      if (assessment.getNotes() != null) {
        content.add(new Paragraph("Notes: " + assessment.getNotes()));
      }
    }

    add(content);

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

  private String formatSize(long bytes) {
    if (bytes < 1024) return bytes + " B";
    if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
    return String.format("%.1f MB", bytes / (1024.0 * 1024));
  }

  private String str(Object value) {
    return value != null ? value.toString() : "—";
  }

  private String bool(Boolean value) {
    if (value == null) return "—";
    return value ? "Yes" : "No";
  }

  private void reprocessAndClose(ImageAsset asset) {
    try {
      ServiceRegistry.getInstance().getReprocessingService().reprocess(asset.getId());
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
