package com.svenruppert.flow.views.detail;

import com.svenruppert.flow.views.shared.ImagePreviewFactory;
import com.svenruppert.flow.views.shared.ViewServices;
import com.svenruppert.imagerag.domain.*;
import com.svenruppert.imagerag.domain.enums.SourceCategory;
import com.svenruppert.imagerag.service.PreviewService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.util.List;

/**
 * Side-by-side comparison of two {@link ImageAsset} instances.
 * <p>Each column shows: a DETAIL-sized preview, primary category, risk level, summary,
 * tags, and the model / prompt version used to produce the analysis.  Differences
 * between the two assets are not highlighted — this is purely an informational
 * side-by-side view to help operators decide between two similar images.
 */
public class ComparisonDialog
    extends Dialog {

  public ComparisonDialog(ImageAsset left, ImageAsset right) {
    setHeaderTitle(getTranslation("overview.compare.title"));
    setWidth("1100px");
    setMaxHeight("90vh");
    setCloseOnOutsideClick(true);

    ViewServices services = ViewServices.current();
    ImagePreviewFactory previewFactory = services.imagePreviews();

    SemanticAnalysis leftAnalysis = services.persistence().findAnalysis(left.getId()).orElse(null);
    SemanticAnalysis rightAnalysis = services.persistence().findAnalysis(right.getId()).orElse(null);
    SensitivityAssessment leftAssess = services.persistence().findAssessment(left.getId()).orElse(null);
    SensitivityAssessment rightAssess = services.persistence().findAssessment(right.getId()).orElse(null);
    LocationSummary leftLoc = services.persistence().findLocation(left.getId()).orElse(null);
    LocationSummary rightLoc = services.persistence().findLocation(right.getId()).orElse(null);

    HorizontalLayout columns = new HorizontalLayout(
        buildColumn(left, leftAnalysis, leftAssess, leftLoc, previewFactory),
        buildDivider(),
        buildColumn(right, rightAnalysis, rightAssess, rightLoc, previewFactory)
    );
    columns.setWidthFull();
    columns.setSpacing(false);
    columns.getStyle().set("gap", "0");
    add(columns);

    Button closeBtn = new Button(getTranslation("common.close"), e -> close());
    closeBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    getFooter().add(closeBtn);
  }

  // ── Column builder ─────────────────────────────────────────────────────────

  private VerticalLayout buildColumn(ImageAsset asset,
                                     SemanticAnalysis analysis,
                                     SensitivityAssessment assessment,
                                     LocationSummary location,
                                     ImagePreviewFactory previewFactory) {
    VerticalLayout col = new VerticalLayout();
    col.setWidth("50%");
    col.setPadding(true);
    col.setSpacing(true);

    // ── Filename header ────────────────────────────────────────────────────
    H4 filename = new H4(asset.getOriginalFilename());
    filename.getStyle()
        .set("overflow", "hidden")
        .set("text-overflow", "ellipsis")
        .set("white-space", "nowrap")
        .set("margin", "0");
    filename.getElement().setAttribute("title", asset.getOriginalFilename());
    col.add(filename);

    // ── Preview ────────────────────────────────────────────────────────────
    col.add(buildPreview(asset, previewFactory));

    // ── Primary category ───────────────────────────────────────────────────
    if (analysis != null && analysis.getSourceCategory() != null) {
      String catLabel = CategoryRegistry.getUserLabel(analysis.getSourceCategory())
          + " · " + CategoryRegistry.getGroupLabel(analysis.getSourceCategory());
      addRow(col, getTranslation("overview.col.category"), catLabel);
    } else {
      addRow(col, getTranslation("overview.col.category"), "—");
    }

    // ── Secondary categories ───────────────────────────────────────────────
    if (analysis != null) {
      List<SourceCategory> secondary = analysis.getSecondaryCategories();
      if (!secondary.isEmpty()) {
        HorizontalLayout tags = new HorizontalLayout();
        tags.setSpacing(true);
        tags.getStyle().set("flex-wrap", "wrap");
        for (SourceCategory sc : secondary) {
          Span badge = new Span(CategoryRegistry.getUserLabel(sc));
          badge.getElement().getThemeList().add("badge");
          badge.getElement().getThemeList().add("contrast");
          tags.add(badge);
        }
        Span label = new Span(getTranslation("detail.analysis.secondary.categories") + ":");
        label.getStyle()
            .set("font-size", "var(--lumo-font-size-s)")
            .set("font-weight", "500")
            .set("color", "var(--lumo-secondary-text-color)");
        col.add(label, tags);
      }
    }

    // ── Risk level ─────────────────────────────────────────────────────────
    if (assessment != null) {
      Span riskBadge = new Span(assessment.getRiskLevel().name());
      riskBadge.getElement().getThemeList().add("badge");
      switch (assessment.getRiskLevel()) {
        case SAFE -> riskBadge.getElement().getThemeList().add("success");
        case REVIEW -> riskBadge.getElement().getThemeList().add("contrast");
        case SENSITIVE -> riskBadge.getElement().getThemeList().add("error");
        default -> throw new IllegalStateException("Unexpected value: " + assessment.getRiskLevel());
      }
      Span riskLabel = new Span(getTranslation("overview.col.risk") + ":");
      riskLabel.getStyle().set("font-size", "var(--lumo-font-size-s)").set("font-weight", "500");
      col.add(new HorizontalLayout(riskLabel, riskBadge));
    }

    // ── Location ───────────────────────────────────────────────────────────
    if (location != null) {
      addRow(col, getTranslation("overview.col.location"), location.toHumanReadable());
    }

    // ── Tags ───────────────────────────────────────────────────────────────
    if (analysis != null && analysis.getTags() != null && !analysis.getTags().isEmpty()) {
      HorizontalLayout tagRow = new HorizontalLayout();
      tagRow.setSpacing(true);
      tagRow.getStyle().set("flex-wrap", "wrap");
      for (String tag : analysis.getTags()) {
        Span badge = new Span(tag);
        badge.getElement().getThemeList().add("badge");
        tagRow.add(badge);
      }
      Span label = new Span(getTranslation("detail.col.tags") + ":");
      label.getStyle().set("font-size", "var(--lumo-font-size-s)").set("font-weight", "500");
      col.add(label, tagRow);
    }

    // ── Summary ────────────────────────────────────────────────────────────
    if (analysis != null && analysis.getSummary() != null) {
      H4 summaryHeader = new H4(getTranslation("overview.col.description"));
      summaryHeader.getStyle().set("margin-bottom", "2px");
      Div summaryBox = new Div();
      summaryBox.getStyle()
          .set("background", "var(--lumo-contrast-5pct)")
          .set("border-radius", "6px")
          .set("padding", "8px 12px")
          .set("font-size", "var(--lumo-font-size-s)")
          .set("white-space", "pre-wrap")
          .set("word-break", "break-word")
          .set("max-height", "120px")
          .set("overflow-y", "auto");
      summaryBox.setText(analysis.getSummary());
      col.add(summaryHeader, summaryBox);
    }

    // ── Model provenance ───────────────────────────────────────────────────
    if (analysis != null) {
      String modelInfo = nvl(analysis.getVisionModel());
      String promptVer = nvl(analysis.getVisionPromptVersion());
      addRow(col, getTranslation("detail.provenance.vision.model"), modelInfo);
      addRow(col, getTranslation("detail.provenance.vision.prompt.version"), promptVer);
    }

    return col;
  }

  // ── Preview ────────────────────────────────────────────────────────────────

  private Div buildPreview(ImageAsset asset, ImagePreviewFactory previewFactory) {
    Div wrapper = new Div();
    wrapper.getStyle()
        .set("background", "var(--lumo-contrast-5pct)")
        .set("border-radius", "8px")
        .set("overflow", "hidden")
        .set("display", "flex")
        .set("align-items", "center")
        .set("justify-content", "center")
        .set("height", "220px");
    wrapper.add(previewFactory.image(
        asset,
        PreviewService.PreviewSize.DETAIL,
        "100%",
        "220px",
        "contain",
        null));
    return wrapper;
  }

  // ── Helpers ────────────────────────────────────────────────────────────────

  private void addRow(VerticalLayout col, String label, String value) {
    HorizontalLayout row = new HorizontalLayout();
    row.setSpacing(true);
    Span labelSpan = new Span(label + ":");
    labelSpan.getStyle()
        .set("font-weight", "500")
        .set("font-size", "var(--lumo-font-size-s)")
        .set("color", "var(--lumo-secondary-text-color)")
        .set("flex-shrink", "0")
        .set("min-width", "120px");
    Span valueSpan = new Span(value);
    valueSpan.getStyle().set("font-size", "var(--lumo-font-size-s)");
    row.add(labelSpan, valueSpan);
    col.add(row);
  }

  private Div buildDivider() {
    Div div = new Div();
    div.getStyle()
        .set("width", "1px")
        .set("background", "var(--lumo-contrast-10pct)")
        .set("align-self", "stretch")
        .set("flex-shrink", "0");
    return div;
  }

  private String nvl(String s) {
    return s != null ? s : "—";
  }
}
