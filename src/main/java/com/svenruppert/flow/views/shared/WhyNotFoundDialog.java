package com.svenruppert.flow.views.shared;

import com.svenruppert.imagerag.bootstrap.ServiceRegistry;
import com.svenruppert.imagerag.domain.ImageAsset;
import com.svenruppert.imagerag.domain.SearchTuningConfig;
import com.svenruppert.imagerag.dto.WhyNotFoundAnalysis;
import com.svenruppert.imagerag.service.WhyNotFoundService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;

import java.util.UUID;

/**
 * Dialog that shows the "Why not found?" diagnostic analysis for a specific image.
 * <p>Can be opened from:
 * <ul>
 *   <li>The Search Tuning Lab — per-result action button (pre-filled with current query + config)</li>
 *   <li>The Multimodal Search view — per-result action button</li>
 *   <li>Manually — by entering an image UUID and a query</li>
 * </ul>
 * <p>Renders a {@link WhyNotFoundAnalysis} as a structured, human-readable panel
 * with the verdict, exclusion reasons, score signals, and diagnostic notes.
 * All visible strings are internationalised via Vaadin's {@code getTranslation()}.
 */
public class WhyNotFoundDialog
    extends Dialog {

  private final WhyNotFoundService whyNotFoundService;
  private final SearchTuningConfig config;

  private final TextField queryField   = new TextField();
  private final TextField imageIdField = new TextField();
  private final VerticalLayout resultPane = new VerticalLayout();

  // ── Constructor ──────────────────────────────────────────────────────────

  public WhyNotFoundDialog(SearchTuningConfig config) {
    this.whyNotFoundService = ServiceRegistry.getInstance().getWhyNotFoundService();
    this.config = config;

    setHeaderTitle(getTranslation("wnf.title"));
    setWidth("660px");
    setMaxHeight("90vh");

    // Input row
    queryField.setLabel(getTranslation("wnf.query.label"));
    queryField.setWidthFull();
    queryField.setPlaceholder(getTranslation("wnf.query.placeholder"));

    imageIdField.setLabel(getTranslation("wnf.image.id.label"));
    imageIdField.setWidthFull();
    imageIdField.setPlaceholder("xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx");

    Button analyzeBtn = new Button(getTranslation("wnf.analyze"), e -> triggerAnalyze());
    analyzeBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    HorizontalLayout inputRow = new HorizontalLayout(queryField, imageIdField, analyzeBtn);
    inputRow.setWidthFull();
    inputRow.setAlignItems(com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.END);
    inputRow.expand(queryField, imageIdField);

    // Result pane
    resultPane.setPadding(false);
    resultPane.setSpacing(false);
    resultPane.setWidthFull();
    resultPane.getStyle().set("margin-top", "0.5rem");

    VerticalLayout content = new VerticalLayout(inputRow, resultPane);
    content.setPadding(false);
    content.setSpacing(false);
    content.setWidthFull();
    add(content);

    Button closeBtn = new Button(getTranslation("common.close"), e -> close());
    getFooter().add(closeBtn);
  }

  // ── Static factory ───────────────────────────────────────────────────────

  /**
   * Opens the dialog pre-filled for a specific image and query.
   * The analysis is triggered immediately so the result is visible on open.
   *
   * @param imageId  the image to investigate
   * @param filename the image filename (for the dialog title)
   * @param query    the query that did not return this image
   * @param config   the retrieval configuration active during that search
   */
  public static WhyNotFoundDialog openFor(UUID imageId, String filename,
                                          String query, SearchTuningConfig config) {
    WhyNotFoundDialog dlg = new WhyNotFoundDialog(config);
    dlg.imageIdField.setValue(imageId != null ? imageId.toString() : "");
    dlg.queryField.setValue(query != null ? query : "");
    String label = filename != null ? filename : (imageId != null ? imageId.toString() : "");
    dlg.setHeaderTitle(dlg.getTranslation("wnf.title.for", label));
    dlg.analyze(imageId, query);
    dlg.open();
    return dlg;
  }

  // ── Analysis trigger ─────────────────────────────────────────────────────

  private void triggerAnalyze() {
    String query = queryField.getValue();
    String idStr  = imageIdField.getValue();
    if (query == null || query.isBlank()) {
      Notification.show(getTranslation("wnf.query.required"),
                        2500, Notification.Position.MIDDLE);
      return;
    }
    UUID imageId;
    try {
      imageId = UUID.fromString(idStr.trim());
    } catch (IllegalArgumentException ex) {
      imageId = resolveByFilenamePrefix(idStr.trim());
      if (imageId == null) {
        Notification.show(getTranslation("wnf.image.id.invalid", idStr),
                          2500, Notification.Position.MIDDLE);
        return;
      }
    }
    analyze(imageId, query);
  }

  // ── Analysis rendering ───────────────────────────────────────────────────

  private void analyze(UUID imageId, String query) {
    if (imageId == null || query == null || query.isBlank()) return;

    resultPane.removeAll();

    WhyNotFoundAnalysis analysis;
    try {
      analysis = whyNotFoundService.analyze(imageId, query, config);
    } catch (Exception e) {
      Span err = new Span(getTranslation("wnf.error", e.getMessage()));
      err.getStyle().set("color", "var(--lumo-error-color)")
          .set("font-size", "var(--lumo-font-size-s)");
      resultPane.add(err);
      return;
    }

    // ── Verdict badge ────────────────────────────────────────────────────
    Span verdict = new Span(analysis.verdict());
    verdict.getStyle()
        .set("display", "block")
        .set("font-weight", "700")
        .set("font-size", "var(--lumo-font-size-s)")
        .set("padding", "0.4rem 0.6rem")
        .set("border-radius", "var(--lumo-border-radius-s)")
        .set("background", analysis.isDefinitivelyExcluded()
            ? "var(--lumo-error-color-10pct)" : "var(--lumo-warning-color-10pct)")
        .set("color", analysis.isDefinitivelyExcluded()
            ? "var(--lumo-error-text-color)" : "var(--lumo-warning-text-color)")
        .set("margin-bottom", "0.5rem");
    resultPane.add(verdict);

    // ── Score signals ────────────────────────────────────────────────────
    resultPane.add(sectionHeading(getTranslation("wnf.section.scores")));
    resultPane.add(buildScoreGrid(analysis));

    // ── Exclusion reasons ────────────────────────────────────────────────
    if (!analysis.exclusionReasons().isEmpty()) {
      resultPane.add(sectionHeading(getTranslation("wnf.section.exclusions")));
      for (String reason : analysis.exclusionReasons()) {
        resultPane.add(bullet("\u2717", reason, "var(--lumo-error-color)"));
      }
    }

    // ── Diagnostic notes ─────────────────────────────────────────────────
    if (!analysis.diagnosticNotes().isEmpty()) {
      resultPane.add(sectionHeading(getTranslation("wnf.section.notes")));
      for (String note : analysis.diagnosticNotes()) {
        resultPane.add(bullet("\u2139", note, "var(--lumo-secondary-text-color)"));
      }
    }
  }

  private Div buildScoreGrid(WhyNotFoundAnalysis a) {
    Div grid = new Div();
    grid.getStyle()
        .set("display", "grid")
        .set("grid-template-columns", "auto 1fr")
        .set("gap", "0.2rem 0.8rem")
        .set("font-size", "var(--lumo-font-size-xs)")
        .set("font-family", "monospace")
        .set("background", "var(--lumo-contrast-5pct)")
        .set("padding", "0.4rem 0.6rem")
        .set("border-radius", "var(--lumo-border-radius-s)")
        .set("margin-bottom", "0.4rem");

    addGridRow(grid, getTranslation("wnf.score.semantic"),      String.format("%.3f", a.semanticScore()));
    addGridRow(grid, getTranslation("wnf.score.bm25"),          String.format("%.3f", a.bm25Score()));
    addGridRow(grid, getTranslation("wnf.score.estimated"),     String.format("%.3f", a.estimatedFinalScore()));
    addGridRow(grid, getTranslation("wnf.score.cutoff"),        String.format("%.2f", a.scoreCutoff()));
    addGridRow(grid, getTranslation("wnf.score.above.threshold"),
               a.aboveThreshold() ? "\u2713 " + getTranslation("wnf.yes") : "\u2717 " + getTranslation("wnf.no"));
    addGridRow(grid, getTranslation("wnf.score.approved"),
               a.approved()  ? "\u2713 " + getTranslation("wnf.yes") : "\u2717 " + getTranslation("wnf.no"));
    addGridRow(grid, getTranslation("wnf.score.archived"),
               a.archived()  ? "\u2713 " + getTranslation("wnf.archived.yes") : getTranslation("wnf.archived.no"));
    addGridRow(grid, getTranslation("wnf.score.vector"),
               a.vectorAvailable() ? "\u2713 " + getTranslation("wnf.yes") : "\u2717 " + getTranslation("wnf.no"));
    return grid;
  }

  // ── Rendering helpers ────────────────────────────────────────────────────

  private static H4 sectionHeading(String text) {
    H4 h = new H4(text);
    h.getStyle()
        .set("font-size", "var(--lumo-font-size-xs)")
        .set("text-transform", "uppercase")
        .set("letter-spacing", "0.05em")
        .set("color", "var(--lumo-secondary-text-color)")
        .set("margin", "0.6rem 0 0.2rem 0");
    return h;
  }

  private static Div bullet(String icon, String text, String color) {
    Div row = new Div();
    row.getStyle()
        .set("display", "flex").set("gap", "0.4rem").set("align-items", "flex-start")
        .set("font-size", "var(--lumo-font-size-xs)");
    Span ic = new Span(icon);
    ic.getStyle().set("color", color).set("flex-shrink", "0");
    Span tx = new Span(text);
    tx.getStyle().set("color", "var(--lumo-body-text-color)");
    row.add(ic, tx);
    return row;
  }

  private static void addGridRow(Div grid, String label, String value) {
    Span l = new Span(label + ":");
    l.getStyle().set("color", "var(--lumo-secondary-text-color)").set("white-space", "nowrap");
    Span v = new Span(value);
    v.getStyle().set("color", "var(--lumo-body-text-color)");
    grid.add(l, v);
  }

  // ── Helpers ──────────────────────────────────────────────────────────────

  private UUID resolveByFilenamePrefix(String prefix) {
    if (prefix == null || prefix.isBlank()) return null;
    String lower = prefix.toLowerCase();
    return ServiceRegistry.getInstance().getPersistenceService().findAllImages().stream()
        .filter(a -> a.getOriginalFilename() != null
            && a.getOriginalFilename().toLowerCase().contains(lower))
        .map(ImageAsset::getId)
        .findFirst()
        .orElse(null);
  }
}
