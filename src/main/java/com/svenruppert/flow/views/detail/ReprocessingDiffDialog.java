package com.svenruppert.flow.views.detail;

import com.svenruppert.imagerag.domain.ReprocessingDiff;
import com.svenruppert.imagerag.domain.enums.SourceCategory;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.util.List;
import java.util.Objects;

/**
 * Dialog that shows a user-readable diff between the before and after state of an
 * image that has been reprocessed.
 *
 * <p>Each tracked field is shown as an "old → new" row.  Unchanged fields are
 * displayed in a muted style; changed fields are highlighted so they stand out.
 */
public class ReprocessingDiffDialog
    extends Dialog {

  public ReprocessingDiffDialog(ReprocessingDiff diff) {
    setHeaderTitle(getTranslation("pipeline.diff.title"));
    setWidth("700px");
    setMaxHeight("80vh");
    setCloseOnOutsideClick(true);

    VerticalLayout content = new VerticalLayout();
    content.setPadding(true);
    content.setSpacing(true);

    if (diff == null) {
      content.add(new Paragraph(getTranslation("pipeline.diff.no.info")));
    } else if (!diff.anyChanged()) {
      Paragraph noChange = new Paragraph(getTranslation("pipeline.diff.no.changes"));
      noChange.getStyle().set("color", "var(--lumo-secondary-text-color)");
      content.add(noChange);
    } else {
      content.add(new H4(getTranslation("pipeline.diff.summary")));

      // Category
      addDiffRow(content, getTranslation("overview.col.category"),
                 labelOf(diff.previousCategory()),
                 labelOf(diff.newCategory()),
                 diff.categoryChanged());

      // Risk
      addDiffRow(content, getTranslation("pipeline.diff.risk.level"),
                 diff.previousRisk() != null ? diff.previousRisk().name() : "—",
                 diff.newRisk() != null ? diff.newRisk().name() : "—",
                 diff.riskChanged());

      // Tags
      addDiffRow(content, getTranslation("detail.col.tags"),
                 tagString(diff.previousTags()),
                 tagString(diff.newTags()),
                 diff.tagsChanged());

      // Vision model / provenance
      addDiffRow(content, getTranslation("detail.provenance.vision.model"),
                 nvl(diff.previousVisionModel()),
                 nvl(diff.newVisionModel()),
                 !Objects.equals(diff.previousVisionModel(), diff.newVisionModel()));

      addDiffRow(content, getTranslation("pipeline.diff.prompt.version"),
                 nvl(diff.previousPromptVersion()),
                 nvl(diff.newPromptVersion()),
                 !Objects.equals(diff.previousPromptVersion(), diff.newPromptVersion()));

      // OCR text (only show when changed and non-empty)
      if (diff.ocrChanged()) {
        content.add(new H4(getTranslation("pipeline.diff.ocr.changed")));
        content.add(buildTextBlock(diff.previousOcrText(), true));
        Span arrow = new Span(getTranslation("pipeline.diff.arrow.new"));
        arrow.getStyle().set("color", "var(--lumo-secondary-text-color)").set("font-size", "var(--lumo-font-size-s)");
        content.add(arrow);
        content.add(buildTextBlock(diff.newOcrText(), false));
      }

      // Summary / description (only show when changed and non-empty)
      if (diff.summaryChanged()) {
        content.add(new H4(getTranslation("pipeline.diff.description.changed")));
        content.add(buildTextBlock(diff.previousSummary(), true));
        Span arrow = new Span(getTranslation("pipeline.diff.arrow.new"));
        arrow.getStyle().set("color", "var(--lumo-secondary-text-color)").set("font-size", "var(--lumo-font-size-s)");
        content.add(arrow);
        content.add(buildTextBlock(diff.newSummary(), false));
      }
    }

    add(content);

    Button closeBtn = new Button(getTranslation("common.close"), e -> close());
    closeBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    getFooter().add(closeBtn);
  }

  // ── Helpers ────────────────────────────────────────────────────────────────

  private void addDiffRow(VerticalLayout container, String label,
                          String oldValue, String newValue, boolean changed) {
    HorizontalLayout row = new HorizontalLayout();
    row.setAlignItems(com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.CENTER);
    row.setSpacing(true);

    Span labelSpan = new Span(label + ":");
    labelSpan.setWidth("130px");
    labelSpan.getStyle()
        .set("font-weight", "500")
        .set("font-size", "var(--lumo-font-size-s)")
        .set("flex-shrink", "0");

    if (changed) {
      Span oldSpan = new Span(oldValue);
      oldSpan.getStyle()
          .set("text-decoration", "line-through")
          .set("color", "var(--lumo-error-color)")
          .set("font-size", "var(--lumo-font-size-s)");

      Span arrow = new Span("→");
      arrow.getStyle().set("color", "var(--lumo-secondary-text-color)");

      Span newSpan = new Span(newValue);
      newSpan.getStyle()
          .set("color", "var(--lumo-success-color)")
          .set("font-weight", "600")
          .set("font-size", "var(--lumo-font-size-s)");

      row.add(labelSpan, oldSpan, arrow, newSpan);
    } else {
      Span same = new Span(oldValue);
      same.getStyle()
          .set("color", "var(--lumo-secondary-text-color)")
          .set("font-size", "var(--lumo-font-size-s)");
      Span unchangedBadge = new Span(getTranslation("pipeline.diff.unchanged"));
      unchangedBadge.getElement().getThemeList().add("badge");
      unchangedBadge.getElement().getThemeList().add("contrast");
      row.add(labelSpan, same, unchangedBadge);
    }

    container.add(row);
  }

  private Div buildTextBlock(String text, boolean old) {
    Div box = new Div();
    box.getStyle()
        .set("background", old ? "var(--lumo-error-color-10pct, #fff0f0)"
            : "var(--lumo-success-color-10pct, #f0fff0)")
        .set("border-radius", "6px")
        .set("padding", "8px 12px")
        .set("font-size", "var(--lumo-font-size-s)")
        .set("white-space", "pre-wrap")
        .set("word-break", "break-word")
        .set("max-height", "120px")
        .set("overflow-y", "auto");
    box.setText(text != null ? text : "—");
    return box;
  }

  private String labelOf(SourceCategory cat) {
    if (cat == null) return "—";
    return com.svenruppert.imagerag.domain.CategoryRegistry.getUserLabel(cat)
        + " (" + com.svenruppert.imagerag.domain.CategoryRegistry.getGroupLabel(cat) + ")";
  }

  private String tagString(List<String> tags) {
    if (tags == null || tags.isEmpty()) return "—";
    return String.join(", ", tags);
  }

  private String nvl(String s) {
    return s != null ? s : "—";
  }
}
