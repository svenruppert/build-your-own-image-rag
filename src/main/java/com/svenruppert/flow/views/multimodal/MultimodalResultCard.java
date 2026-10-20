package com.svenruppert.flow.views.multimodal;

import com.svenruppert.flow.views.shared.Formats;
import com.svenruppert.flow.views.shared.ResultExplainabilityPanel;
import com.svenruppert.flow.views.shared.WhyNotFoundDialog;
import com.svenruppert.imagerag.domain.SearchTuningConfig;
import com.svenruppert.imagerag.dto.MultimodalSearchConfig;
import com.svenruppert.imagerag.dto.MultimodalSearchResult;
import com.svenruppert.imagerag.dto.MultimodalSignal;
import com.svenruppert.imagerag.dto.SearchStrategyPlan;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

/**
 * One multimodal search result: title, score, summary, signal attribution tags,
 * a collapsible explainability panel and a "Why not found?" action.
 */
final class MultimodalResultCard
    extends Div {

  MultimodalResultCard(MultimodalSearchResult r, MultimodalSearchConfig cfg,
                       SearchStrategyPlan autopilotPlan) {
    getStyle()
        .set("border", "1px solid var(--lumo-contrast-10pct)")
        .set("border-radius", "var(--lumo-border-radius-m)")
        .set("padding", "0.5rem 0.75rem")
        .set("margin-bottom", "0.4rem")
        .set("background", "var(--lumo-base-color)");

    // Header row
    String titleText = r.item().getTitle() != null ? r.item().getTitle() : r.item().getImageId().toString();
    Span title = new Span(titleText);
    title.getStyle().set("font-weight", "600").set("font-size", "var(--lumo-font-size-s)");

    Span score = new Span(Formats.score(r.item().getScore()));
    score.getStyle().set("font-size", "var(--lumo-font-size-xs)")
        .set("color", "var(--lumo-primary-color)").set("font-weight", "700")
        .set("background", "var(--lumo-primary-color-10pct)")
        .set("padding", "1px 5px").set("border-radius", "var(--lumo-border-radius-s)");

    HorizontalLayout header = new HorizontalLayout(title, score);
    header.setAlignItems(FlexComponent.Alignment.CENTER);
    header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
    header.setWidthFull();

    // Summary
    String summary = r.item().getSummary();
    if (summary != null && !summary.isBlank()) {
      Span sum = new Span(summary.length() > 120 ? summary.substring(0, 117) + "…" : summary);
      sum.getStyle().set("font-size", "var(--lumo-font-size-xs)")
          .set("color", "var(--lumo-secondary-text-color)").set("display", "block");
      add(header, sum);
    } else {
      add(header);
    }

    // Signal attribution tags
    if (!r.signalContributions().isEmpty()) {
      HorizontalLayout tags = new HorizontalLayout();
      tags.setSpacing(false);
      tags.getStyle().set("gap", "0.3rem").set("flex-wrap", "wrap").set("margin-top", "0.25rem");
      r.signalContributions().forEach((key, val) -> {
        Span tag = new Span(key + " " + String.format("%.0f%%", val * 100));
        tag.getStyle()
            .set("background", "var(--lumo-contrast-10pct)")
            .set("font-size", "var(--lumo-font-size-xxs)")
            .set("padding", "1px 4px").set("border-radius", "var(--lumo-border-radius-s)");
        tags.add(tag);
      });
      add(tags);
    }

    // Explainability panel (collapsible)
    ResultExplainabilityPanel expPanel = ResultExplainabilityPanel.forMultimodalResult(
        r, cfg.getRetrievalMode(), cfg.getSimilarityFunction(), autopilotPlan);
    Details details = new Details(getTranslation("multimodal.explain"), expPanel);
    details.getStyle().set("margin-top", "0.3rem").set("font-size", "var(--lumo-font-size-xs)");
    add(details);

    // "Why not found?" action — diagnostic for this result in the current multimodal context
    Button wnfBtn = new Button(getTranslation("tuning.wnf.button"));
    wnfBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
    wnfBtn.getStyle()
        .set("font-size", "var(--lumo-font-size-xs)")
        .set("margin-top", "0.25rem");
    wnfBtn.addClickListener(e -> {
      // Use the first text signal as the WNF query context
      String queryText = cfg.getSignals().stream()
          .filter(s -> s.type() == MultimodalSignal.SignalType.TEXT)
          .map(MultimodalSignal::textContent)
          .filter(t -> t != null && !t.isBlank())
          .findFirst()
          .orElse(r.item().getTitle() != null ? r.item().getTitle() : "");
      // Build a SearchTuningConfig from the multimodal config for the WNF analysis
      SearchTuningConfig wnfCfg = new SearchTuningConfig()
          .setRetrievalMode(cfg.getRetrievalMode())
          .setSimilarityFunction(cfg.getSimilarityFunction())
          .setSemanticWeight(cfg.getSemanticWeight())
          .setBm25Weight(cfg.getBm25Weight())
          .setScoreCutoff(cfg.getScoreCutoff());
      String imgTitle = r.item().getTitle() != null ? r.item().getTitle() : r.item().getImageId().toString();
      WhyNotFoundDialog.openFor(r.item().getImageId(), imgTitle, queryText, wnfCfg);
    });
    add(wnfBtn);
  }
}
