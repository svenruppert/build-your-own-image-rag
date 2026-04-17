package com.svenruppert.flow.views.multimodal;

import com.svenruppert.flow.MainLayout;
import com.svenruppert.flow.views.shared.ResultExplainabilityPanel;
import com.svenruppert.flow.views.shared.WhyNotFoundDialog;
import com.svenruppert.imagerag.bootstrap.ServiceRegistry;
import com.svenruppert.imagerag.domain.SearchTuningConfig;
import com.svenruppert.imagerag.domain.ImageAsset;
import com.svenruppert.imagerag.domain.enums.RetrievalMode;
import com.svenruppert.imagerag.domain.enums.SimilarityFunction;
import com.svenruppert.imagerag.dto.MultimodalSearchConfig;
import com.svenruppert.imagerag.dto.MultimodalSearchResult;
import com.svenruppert.imagerag.dto.MultimodalSignal;
import com.svenruppert.imagerag.dto.SearchStrategyPlan;
import com.svenruppert.imagerag.persistence.PersistenceService;
import com.svenruppert.imagerag.service.SearchService;
import com.svenruppert.imagerag.service.SearchStrategyAutopilot;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Multimodal Search view — lets users combine multiple input signals
 * (text query, image example, OCR terms, category filter) in a single search.
 * <p>Layout:
 * <ul>
 *   <li><b>Left panel</b> (280px) — signal builder: add/remove signals</li>
 *   <li><b>Middle panel</b> (240px) — retrieval configuration (mode, weights, cutoff)</li>
 *   <li><b>Right panel</b> (flex) — results with explainability panels</li>
 * </ul>
 */
@Route(value = MultimodalSearchView.PATH, layout = MainLayout.class)
@PageTitle("Multimodal Search")
public class MultimodalSearchView
    extends VerticalLayout {

  public static final String PATH = "multimodal";

  // ── Services ─────────────────────────────────────────────────────────────
  private final SearchService searchService;
  private final PersistenceService persistenceService;
  private final SearchStrategyAutopilot autopilot = new SearchStrategyAutopilot();

  // ── Signal builder state ─────────────────────────────────────────────────
  private final List<MultimodalSignal> signals = new ArrayList<>();
  private final VerticalLayout signalsList = new VerticalLayout();

  // ── Signal input controls ────────────────────────────────────────────────
  private final TextArea textInput = new TextArea();
  private final TextField imageIdInput = new TextField();
  private final TextArea ocrInput = new TextArea();
  private final TextField categoryInput = new TextField();
  private final NumberField textWeight = new NumberField();
  private final NumberField imageWeight = new NumberField();
  private final NumberField ocrWeight = new NumberField();

  // ── Retrieval config ─────────────────────────────────────────────────────
  private final Select<RetrievalMode> modeSelect = new Select<>();
  private final Select<SimilarityFunction> similaritySelect = new Select<>();
  private final NumberField semWeight = new NumberField();
  private final NumberField bm25Weight = new NumberField();
  private final NumberField cutoff = new NumberField();
  private final NumberField maxResults = new NumberField();
  private final Checkbox autopilotToggle = new Checkbox();

  // ── Results ──────────────────────────────────────────────────────────────
  private final Div resultArea = new Div();
  private final ProgressBar progressBar = new ProgressBar();
  private final Span statusSpan = new Span();
  private final AtomicBoolean running = new AtomicBoolean(false);

  // ── Autopilot state ───────────────────────────────────────────────────────
  private SearchStrategyPlan lastAutopilotPlan = null;

  // ── Constructor ──────────────────────────────────────────────────────────

  public MultimodalSearchView() {
    ServiceRegistry reg = ServiceRegistry.getInstance();
    this.searchService = reg.getSearchService();
    this.persistenceService = reg.getPersistenceService();

    setSizeFull();
    setPadding(true);
    setSpacing(true);

    add(buildHeader());

    HorizontalLayout workbench = new HorizontalLayout(
        buildSignalPanel(),
        buildConfigPanel(),
        buildResultsPanel());
    workbench.setSizeFull();
    workbench.setAlignItems(FlexComponent.Alignment.START);
    workbench.setFlexGrow(1, workbench.getComponentAt(2));
    addAndExpand(workbench);
  }

  private static VerticalLayout panel(String width) {
    VerticalLayout p = new VerticalLayout();
    p.setPadding(true);
    p.setSpacing(false);
    p.getStyle()
        .set("border", "1px solid var(--lumo-contrast-10pct)")
        .set("border-radius", "var(--lumo-border-radius-l)")
        .set("background", "var(--lumo-base-color)")
        .set("flex-shrink", "0")
        .set("min-width", width).set("max-width", width)
        .set("overflow-y", "auto");
    return p;
  }

  // ── Header ───────────────────────────────────────────────────────────────

  private static Span sectionLabel(String text) {
    Span s = new Span(text);
    s.getStyle()
        .set("font-size", "var(--lumo-font-size-xxs)").set("font-weight", "700")
        .set("text-transform", "uppercase").set("letter-spacing", "0.05em")
        .set("color", "var(--lumo-secondary-text-color)").set("display", "block")
        .set("margin-top", "0.5rem");
    return s;
  }

  // ── Left panel: signal builder ────────────────────────────────────────────

  private static Div sep() {
    Div d = new Div();
    d.getStyle()
        .set("height", "1px").set("background", "var(--lumo-contrast-10pct)")
        .set("margin", "0.4rem 0");
    return d;
  }

  // ── Middle panel: config ──────────────────────────────────────────────────

  @Override
  protected void onAttach(AttachEvent event) {
    super.onAttach(event);
    autopilotToggle.setLabel(getTranslation("multimodal.autopilot.enable"));
  }

  // ── Right panel: results ──────────────────────────────────────────────────

  private Component buildHeader() {
    H3 title = new H3(getTranslation("multimodal.title"));
    Paragraph desc = new Paragraph(getTranslation("multimodal.description"));
    desc.getStyle().set("color", "var(--lumo-secondary-text-color)")
        .set("font-size", "var(--lumo-font-size-s)").set("margin", "0");
    VerticalLayout h = new VerticalLayout(title, desc);
    h.setPadding(false);
    h.setSpacing(false);
    return h;
  }

  // ── Search execution ──────────────────────────────────────────────────────

  private Component buildSignalPanel() {
    // Text signal
    textInput.setLabel(getTranslation("multimodal.signal.text"));
    textInput.setPlaceholder("e.g. winter landscape");
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
    imageIdInput.setPlaceholder("UUID or filename fragment");
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
        Notification.show("Image not found: " + imageIdInput.getValue(), 2500, Notification.Position.BOTTOM_START);
      }
    });
    addImage.addThemeVariants(ButtonVariant.LUMO_SMALL);

    // OCR terms signal
    ocrInput.setLabel(getTranslation("multimodal.signal.ocr"));
    ocrInput.setPlaceholder("e.g. \"ABC 123\" parking");
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
    categoryInput.setPlaceholder("e.g. LANDSCAPE or Nature");
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

    VerticalLayout panel = panel("280px");
    panel.add(
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
    return panel;
  }

  // ── Result rendering ──────────────────────────────────────────────────────

  private Component buildConfigPanel() {
    modeSelect.setLabel(getTranslation("tuning.mode"));
    modeSelect.setItems(RetrievalMode.values());
    modeSelect.setItemLabelGenerator(RetrievalMode::getLabel);
    modeSelect.setValue(RetrievalMode.HYBRID);
    modeSelect.setWidthFull();

    similaritySelect.setLabel(getTranslation("tuning.similarity"));
    similaritySelect.setItems(SimilarityFunction.values());
    similaritySelect.setItemLabelGenerator(SimilarityFunction::getLabel);
    similaritySelect.setValue(SimilarityFunction.COSINE);
    similaritySelect.setWidthFull();

    semWeight.setLabel(getTranslation("tuning.weight.semantic"));
    semWeight.setValue(1.0);
    semWeight.setMin(0.1);
    semWeight.setMax(4.0);
    semWeight.setStep(0.1);
    semWeight.setWidthFull();

    bm25Weight.setLabel(getTranslation("tuning.weight.bm25"));
    bm25Weight.setValue(1.0);
    bm25Weight.setMin(0.1);
    bm25Weight.setMax(4.0);
    bm25Weight.setStep(0.1);
    bm25Weight.setWidthFull();

    cutoff.setLabel(getTranslation("tuning.cutoff"));
    cutoff.setValue(0.45);
    cutoff.setMin(0.0);
    cutoff.setMax(1.0);
    cutoff.setStep(0.05);
    cutoff.setWidthFull();

    maxResults.setLabel(getTranslation("tuning.max.results"));
    maxResults.setValue(20.0);
    maxResults.setMin(1);
    maxResults.setMax(100);
    maxResults.setStep(1);
    maxResults.setWidthFull();

    autopilotToggle.setValue(false);

    Button runBtn = new Button(getTranslation("multimodal.run"), e -> runSearch());
    runBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    runBtn.setWidthFull();

    VerticalLayout panel = panel("240px");
    panel.add(
        modeSelect, similaritySelect,
        sep(),
        semWeight, bm25Weight,
        sep(),
        cutoff, maxResults,
        sep(),
        autopilotToggle,
        runBtn
    );
    return panel;
  }

  private Component buildResultsPanel() {
    progressBar.setVisible(false);
    statusSpan.getStyle().set("font-size", "var(--lumo-font-size-xs)")
        .set("color", "var(--lumo-secondary-text-color)");

    resultArea.setWidthFull();
    resultArea.getStyle().set("overflow-y", "auto");

    VerticalLayout panel = new VerticalLayout(progressBar, statusSpan, resultArea);
    panel.setPadding(false);
    panel.setSpacing(false);
    panel.setSizeFull();
    return panel;
  }

  // ── Signal management ─────────────────────────────────────────────────────

  private void runSearch() {
    if (signals.isEmpty()) {
      Notification.show(getTranslation("multimodal.no.signals"), 2500, Notification.Position.BOTTOM_START);
      return;
    }
    if (running.getAndSet(true)) {
      Notification.show(getTranslation("tuning.already.running"), 2500, Notification.Position.BOTTOM_START);
      return;
    }

    progressBar.setIndeterminate(true);
    progressBar.setVisible(true);
    statusSpan.setText(getTranslation("tuning.running"));
    resultArea.removeAll();

    // Build config
    SearchStrategyPlan plan = null;
    if (autopilotToggle.getValue()) {
      String firstText = signals.stream()
          .filter(s -> s.type() == MultimodalSignal.SignalType.TEXT)
          .map(MultimodalSignal::textContent)
          .filter(t -> t != null && !t.isBlank())
          .findFirst().orElse("");
      plan = autopilot.analyze(firstText, semWeight.getValue(), bm25Weight.getValue(), signals);
      lastAutopilotPlan = plan;
      // Apply autopilot recommendations
      modeSelect.setValue(plan.recommendedMode());
      similaritySelect.setValue(plan.recommendedSimilarity());
      semWeight.setValue(plan.recommendedSemanticWeight());
      bm25Weight.setValue(plan.recommendedBm25Weight());
    }

    final SearchStrategyPlan finalPlan = plan;
    final MultimodalSearchConfig cfg = MultimodalSearchConfig.builder()
        .signals(List.copyOf(signals))
        .retrievalMode(modeSelect.getValue())
        .similarityFunction(similaritySelect.getValue())
        .semanticWeight(semWeight.getValue())
        .bm25Weight(bm25Weight.getValue())
        .scoreCutoff(cutoff.getValue())
        .maxResults(maxResults.getValue().intValue())
        .autopilotPlan(finalPlan)
        .build();

    var ui = getUI().orElse(null);
    if (ui == null) {
      running.set(false);
      return;
    }

    Thread.ofVirtual().name("multimodal-search").start(() -> {
      List<MultimodalSearchResult> results;
      try {
        results = searchService.multimodalSearch(cfg);
      } catch (Exception e) {
        ui.access(() -> {
          progressBar.setVisible(false);
          statusSpan.setText(getTranslation("tuning.error", e.getMessage()));
          running.set(false);
        });
        return;
      }
      ui.access(() -> {
        progressBar.setVisible(false);
        renderResults(results, cfg, finalPlan);
        running.set(false);
      });
    });
  }

  private void renderResults(List<MultimodalSearchResult> results, MultimodalSearchConfig cfg,
                             SearchStrategyPlan autopilotPlan) {
    resultArea.removeAll();

    // Autopilot summary
    if (autopilotPlan != null && autopilotPlan.hasRecommendations()) {
      Div apBox = new Div();
      apBox.getStyle()
          .set("background", "var(--lumo-primary-color-10pct)")
          .set("border-radius", "var(--lumo-border-radius-s)")
          .set("padding", "0.4rem 0.6rem").set("margin-bottom", "0.5rem")
          .set("font-size", "var(--lumo-font-size-xs)");
      apBox.add(new Span(getTranslation("multimodal.autopilot.label", autopilotPlan.shortSummary())));
      for (String reason : autopilotPlan.reasons()) {
        Div row = new Div(new Span("• " + reason));
        row.getStyle().set("color", "var(--lumo-secondary-text-color)");
        apBox.add(row);
      }
      resultArea.add(apBox);
    }

    if (results.isEmpty()) {
      statusSpan.setText(getTranslation("tuning.no.results"));
      return;
    }
    statusSpan.setText(getTranslation("multimodal.results.count", results.size()));

    for (MultimodalSearchResult r : results) {
      resultArea.add(buildResultCard(r, cfg, autopilotPlan));
    }
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private Div buildResultCard(MultimodalSearchResult r, MultimodalSearchConfig cfg,
                              SearchStrategyPlan autopilotPlan) {
    Div card = new Div();
    card.getStyle()
        .set("border", "1px solid var(--lumo-contrast-10pct)")
        .set("border-radius", "var(--lumo-border-radius-m)")
        .set("padding", "0.5rem 0.75rem")
        .set("margin-bottom", "0.4rem")
        .set("background", "var(--lumo-base-color)");

    // Header row
    String titleText = r.item().getTitle() != null ? r.item().getTitle() : r.item().getImageId().toString();
    Span title = new Span(titleText);
    title.getStyle().set("font-weight", "600").set("font-size", "var(--lumo-font-size-s)");

    Span score = new Span(String.format("%.3f", r.item().getScore()));
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
      card.add(header, sum);
    } else {
      card.add(header);
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
      card.add(tags);
    }

    // Explainability panel (collapsible)
    ResultExplainabilityPanel expPanel = ResultExplainabilityPanel.forMultimodalResult(
        r, cfg.getRetrievalMode(), cfg.getSimilarityFunction(), autopilotPlan);
    Details details = new Details(getTranslation("multimodal.explain"), expPanel);
    details.getStyle().set("margin-top", "0.3rem").set("font-size", "var(--lumo-font-size-xs)");
    card.add(details);

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
    card.add(wnfBtn);

    return card;
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
    return persistenceService.findAllImages().stream()
        .filter(a -> a.getOriginalFilename() != null
            && a.getOriginalFilename().toLowerCase().contains(value.toLowerCase()))
        .map(ImageAsset::getId)
        .findFirst().orElse(null);
  }
}
