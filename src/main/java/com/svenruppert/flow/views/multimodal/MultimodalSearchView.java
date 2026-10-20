package com.svenruppert.flow.views.multimodal;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.MainLayout;
import com.svenruppert.flow.views.shared.ViewServices;
import com.svenruppert.imagerag.domain.enums.RetrievalMode;
import com.svenruppert.imagerag.domain.enums.SimilarityFunction;
import com.svenruppert.imagerag.dto.MultimodalSearchConfig;
import com.svenruppert.imagerag.dto.MultimodalSearchResult;
import com.svenruppert.imagerag.dto.MultimodalSignal;
import com.svenruppert.imagerag.dto.SearchStrategyPlan;
import com.svenruppert.imagerag.service.SearchService;
import com.svenruppert.imagerag.service.SearchStrategyAutopilot;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
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
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.svenruppert.flow.views.multimodal.MultimodalUi.panel;
import static com.svenruppert.flow.views.multimodal.MultimodalUi.sep;

/**
 * Multimodal Search view — lets users combine multiple input signals
 * (text query, image example, OCR terms, category filter) in a single search.
 * <p>Layout:
 * <ul>
 *   <li><b>Left panel</b> (280px) — signal builder: add/remove signals
 *       ({@link MultimodalSignalPanel})</li>
 *   <li><b>Middle panel</b> (240px) — retrieval configuration (mode, weights, cutoff)</li>
 *   <li><b>Right panel</b> (flex) — results with explainability panels
 *       ({@link MultimodalResultCard})</li>
 * </ul>
 */
@Route(value = MultimodalSearchView.PATH, layout = MainLayout.class)
public class MultimodalSearchView
    extends VerticalLayout
    implements HasDynamicTitle, HasLogger {

  public static final String PATH = "multimodal";

  // ── Services ─────────────────────────────────────────────────────────────
  private final SearchService searchService;
  private final SearchStrategyAutopilot autopilot;

  // ── Signal builder ───────────────────────────────────────────────────────
  private final MultimodalSignalPanel signalPanel;

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
    ViewServices services = ViewServices.current();
    this.searchService = services.search();
    this.autopilot = services.searchStrategyAutopilot();

    setSizeFull();
    setPadding(true);
    setSpacing(true);

    add(buildHeader());

    signalPanel = new MultimodalSignalPanel(services.catalog());
    HorizontalLayout workbench = new HorizontalLayout(
        signalPanel,
        buildConfigPanel(),
        buildResultsPanel());
    workbench.setSizeFull();
    workbench.setAlignItems(FlexComponent.Alignment.START);
    workbench.setFlexGrow(1, workbench.getComponentAt(2));
    addAndExpand(workbench);
  }

  @Override
  protected void onAttach(AttachEvent event) {
    super.onAttach(event);
    autopilotToggle.setLabel(getTranslation("multimodal.autopilot.enable"));
  }

  // ── Header ───────────────────────────────────────────────────────────────

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

  // ── Middle panel: config ──────────────────────────────────────────────────

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

  // ── Right panel: results ──────────────────────────────────────────────────

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

  // ── Search execution ──────────────────────────────────────────────────────

  private void runSearch() {
    List<MultimodalSignal> signals = signalPanel.signals();
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
      } catch (RuntimeException e) {
        // Worker-thread boundary: no Vaadin error handler runs here, so every failure is
        // logged here and shown to the user.
        logger().error("Multimodal search failed: {}", e.getMessage(), e);
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

  // ── Result rendering ──────────────────────────────────────────────────────

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
      resultArea.add(new MultimodalResultCard(r, cfg, autopilotPlan));
    }
  }

  @Override
  public String getPageTitle() {
    return getTranslation("multimodal.page.title");
  }
}
