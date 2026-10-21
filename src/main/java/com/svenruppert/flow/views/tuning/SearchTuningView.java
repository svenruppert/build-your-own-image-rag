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
package com.svenruppert.flow.views.tuning;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.MainLayout;
import com.svenruppert.flow.views.shared.Formats;
import com.svenruppert.flow.views.shared.ViewServices;
import com.svenruppert.flow.views.shared.WhyNotFoundDialog;
import com.svenruppert.imagerag.domain.SearchResultItem;
import com.svenruppert.imagerag.domain.SearchTuningConfig;
import com.svenruppert.imagerag.domain.SearchTuningPreset;
import com.svenruppert.imagerag.domain.enums.FeedbackType;
import com.svenruppert.imagerag.domain.enums.QueryIntentType;
import com.svenruppert.imagerag.dto.FeedbackSession;
import com.svenruppert.imagerag.dto.SearchStrategyPlan;
import com.svenruppert.imagerag.dto.TuningRun;
import com.svenruppert.imagerag.dto.TuningSearchResponse;
import com.svenruppert.imagerag.dto.TuningSearchResult;
import com.svenruppert.imagerag.service.ImageCatalogService;
import com.svenruppert.imagerag.service.SearchService;
import com.svenruppert.imagerag.service.SearchStrategyAutopilot;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;

import java.util.UUID;

/**
 * Search Tuning Lab — interactive retrieval experimentation workbench.
 * <p>Three-column layout in the upper workbench area:
 * <pre>
 * ┌──────────────────────────────────────────────────────────────────────────┐
 * │  Header (title + description)                                            │
 * ├─────────────────┬──────────────────────┬────────────────────────────────┤
 * │  Query Panel    │  Tuning Panel         │  Retrieval Pipeline Inspector   │
 * │  (~240 px)      │  (~280 px)            │  (flex:1)                      │
 * │  Query textarea │  Mode (Select)        │  10-step pipeline timeline      │
 * │  Run button     │  Similarity (Select)  │  ─ Feedback session (Details)  │
 * │  QBE anchor row │  Channel weights      │                                │
 * │  Presets row    │  Relevance feedback   │                                │
 * │                 │  Query intent         │                                │
 * │                 │  Score cutoff         │                                │
 * ├─────────────────┴──────────────────────┴────────────────────────────────┤
 * │  Run summary bar  (full width)                                           │
 * ├──────────────────────────────────────────────────────────────────────────┤
 * │  Result cards  (full width, with per-result feedback + QBE buttons)      │
 * └──────────────────────────────────────────────────────────────────────────┘
 * </pre>
 * Components: {@link TuningQueryPanel}, {@link TuningParameterPanel},
 * {@link TuningInspectorComponent}, {@link FeedbackSessionPanel},
 * {@link TuningResultCards} and {@link TuningPresetDialogs}.
 */
@Route(value = SearchTuningView.PATH, layout = MainLayout.class)
public class SearchTuningView
    extends VerticalLayout
    implements BeforeEnterObserver, HasLogger, HasDynamicTitle {

  public static final String PATH = "tuning";

  private final SearchService searchService;
  private final ImageCatalogService catalog;
  private final SearchStrategyAutopilot autopilot;

  private final FeedbackSession feedbackSession = new FeedbackSession();
  private final TuningQueryPanel queryPanel;
  private final TuningParameterPanel parameterPanel = new TuningParameterPanel();
  private final TuningInspectorComponent inspector = new TuningInspectorComponent();
  private final FeedbackSessionPanel feedbackPanel = new FeedbackSessionPanel(this::removeFeedback);
  private final TuningResultCards resultCards;
  private final Div summaryBar = new Div();
  private final VerticalLayout resultsArea = new VerticalLayout();

  // ── Run state ─────────────────────────────────────────────────────────────
  private volatile boolean running = false;
  private TuningRun currentRun = null;
  private TuningRun previousRun = null;

  public SearchTuningView() {
    ViewServices services = ViewServices.current();
    this.searchService = services.search();
    this.catalog = services.catalog();
    this.autopilot = services.searchStrategyAutopilot();

    TuningPresetDialogs presetDialogs = new TuningPresetDialogs(
        services.searchHistory(),
        this::currentQuery,
        // Presets store the controls only, without the feedback session.
        () -> buildConfig(null),
        this::applyPreset);
    this.queryPanel = new TuningQueryPanel(catalog, this::runTuning,
                                           presetDialogs::openSave, presetDialogs::openLoad);
    this.resultCards = new TuningResultCards(this, catalog, services.imagePreviews(),
                                             feedbackSession, this::markFeedback,
                                             this::useAsExample, this::openWhyNotFound);

    setSpacing(false);
    setPadding(true);
    setWidthFull();

    add(
        buildHeader(),
        buildTopWorkbench(),
        buildSummaryBar(),
        buildResultsArea()
    );
  }

  @Override
  public void beforeEnter(BeforeEnterEvent event) { /* nothing on entry */ }

  // ── Layout ────────────────────────────────────────────────────────────────

  private Component buildHeader() {
    H2 title = new H2(getTranslation("tuning.title"));
    Paragraph desc = new Paragraph(getTranslation("tuning.description"));
    desc.getStyle().set("color", "var(--lumo-secondary-text-color)");
    VerticalLayout h = new VerticalLayout(title, desc);
    h.setSpacing(false);
    h.setPadding(false);
    h.getStyle().set("margin-bottom", "0.25rem");
    return h;
  }

  private Component buildTopWorkbench() {
    HorizontalLayout workbench = new HorizontalLayout(queryPanel, parameterPanel, buildInspectorPanel());
    workbench.setWidthFull();
    workbench.setAlignItems(FlexComponent.Alignment.START);
    workbench.setSpacing(true);
    workbench.setPadding(false);
    workbench.getStyle().set("margin-bottom", "0.5rem");
    return workbench;
  }

  private VerticalLayout buildInspectorPanel() {
    Details feedbackDetails = new Details(
        getTranslation("tuning.feedback.session"), feedbackPanel);
    feedbackDetails.setOpened(false);
    feedbackDetails.setWidthFull();

    VerticalLayout panel = new VerticalLayout(inspector, feedbackDetails);
    panel.setWidthFull();
    panel.getStyle().set("flex", "1").set("min-width", "260px");
    panel.setSpacing(true);
    panel.setPadding(false);
    return panel;
  }

  private Component buildSummaryBar() {
    summaryBar.setWidthFull();
    summaryBar.getStyle()
        .set("background", "var(--lumo-contrast-5pct)")
        .set("border-radius", "var(--lumo-border-radius-m)")
        .set("padding", "0.4rem 0.75rem")
        .set("font-size", "var(--lumo-font-size-s)")
        .set("color", "var(--lumo-secondary-text-color)")
        .set("min-height", "32px");
    summaryBar.setText(getTranslation("tuning.no.run.yet"));
    return summaryBar;
  }

  private Component buildResultsArea() {
    resultsArea.setSpacing(false);
    resultsArea.setPadding(false);
    resultsArea.setWidthFull();
    return resultsArea;
  }

  // ── Tuning execution ──────────────────────────────────────────────────────

  private String currentQuery() {
    return queryPanel.query();
  }

  private SearchTuningConfig buildConfig(FeedbackSession snap) {
    return parameterPanel.buildConfig(snap, queryPanel.qbeImageId());
  }

  private void runTuning() {
    if (running) {
      Notification n = Notification.show(getTranslation("tuning.already.running"),
                                         3000, Notification.Position.BOTTOM_START);
      n.addThemeVariants(NotificationVariant.LUMO_CONTRAST);
      return;
    }
    String query = currentQuery();
    if (query == null || query.isBlank()) {
      Notification n = Notification.show(getTranslation("tuning.query.empty"),
                                         3000, Notification.Position.BOTTOM_START);
      n.addThemeVariants(NotificationVariant.LUMO_ERROR);
      return;
    }

    SearchTuningConfig cfg = buildConfig(feedbackSession.snapshot());

    // Apply autopilot if enabled — it may override mode, similarity, and weights
    if (parameterPanel.isAutopilotEnabled()) {
      SearchStrategyPlan plan = autopilot.analyze(query, cfg);
      if (plan.hasRecommendations()) {
        parameterPanel.applyAutopilotPlan(plan, cfg);
      }
    } else {
      parameterPanel.clearAutopilotPlan();
    }

    running = true;
    queryPanel.setRunning(true);
    summaryBar.setText(getTranslation("tuning.running"));
    inspector.reset();
    inspector.markRunning(query);
    resultsArea.removeAll();

    UI ui = UI.getCurrent();
    long startMs = System.currentTimeMillis();

    Thread.ofVirtual().name("tuning-search").start(() -> {
      try {
        TuningSearchResponse response = searchService.searchWithTuning(query, cfg);
        long elapsed = System.currentTimeMillis() - startMs;

        TuningRun run = new TuningRun(
            query, cfg, response.results(),
            response.vectorCandidates(),
            response.keywordCandidates(),
            response.detectedIntent(),
            response.feedbackEntriesUsed(),
            elapsed);

        ui.access(() -> {
          // currentRun is owned by the UI thread, so it is read inside ui.access().
          TuningRun prevRun = currentRun;
          inspector.showRun(run);
          renderResults(run, prevRun);
          renderSummaryBar(run);
          previousRun = prevRun;
          currentRun = run;
          queryPanel.setRunning(false);
          running = false;
        });
      } catch (RuntimeException ex) {
        // Worker-thread boundary: no Vaadin error handler runs here, so every failure is
        // logged here and shown to the user.
        logger().error("Tuning run failed: {}", ex.getMessage(), ex);
        ui.access(() -> {
          String msg = getTranslation("tuning.error", ex.getMessage());
          summaryBar.setText(msg);
          queryPanel.setRunning(false);
          running = false;
          Notification n = Notification.show(msg, 5000, Notification.Position.BOTTOM_START);
          n.addThemeVariants(NotificationVariant.LUMO_ERROR);
        });
      }
    });
  }

  // ── Rendering ─────────────────────────────────────────────────────────────

  private void renderSummaryBar(TuningRun run) {
    SearchTuningConfig cfg = run.getConfig();
    summaryBar.removeAll();

    StringBuilder sb = new StringBuilder();
    sb.append(getTranslation("tuning.summary.config",
                             cfg.getRetrievalMode().getLabel(),
                             cfg.getSimilarityFunction().getLabel(),
                             Formats.decimal(cfg.getSemanticWeight()),
                             Formats.decimal(cfg.getBm25Weight()),
                             Formats.decimal(cfg.getConfidenceWeight()),
                             Formats.decimal(cfg.getScoreCutoff()),
                             run.getResults().size(),
                             run.getExecutionMs()));

    QueryIntentType intent = run.getDetectedIntent();
    if (intent != null) {
      sb.append("  │  ").append(
          getTranslation("tuning.summary.intent", intent.getLabel()));
    }
    int fb = run.getFeedbackEntriesUsed();
    if (fb > 0) {
      sb.append("  │  ").append(
          getTranslation("tuning.summary.feedback", fb));
    }
    summaryBar.setText(sb.toString());
  }

  private void renderResults(TuningRun current, TuningRun previous) {
    resultsArea.removeAll();
    if (current.getResults().isEmpty()) {
      Paragraph empty = new Paragraph(getTranslation("tuning.no.results"));
      empty.getStyle().set("color", "var(--lumo-secondary-text-color)");
      resultsArea.add(empty);
      return;
    }
    for (int i = 0; i < current.getResults().size(); i++) {
      TuningSearchResult tsr = current.getResults().get(i);
      int delta = TuningRun.rankDelta(tsr.item().getImageId(), current, previous);
      resultsArea.add(resultCards.card(i + 1, tsr, delta));
    }
  }

  // ── Per-result actions ────────────────────────────────────────────────────

  private void useAsExample(SearchResultItem item) {
    queryPanel.setQbeImageId(item.getImageId());
    Notification.show(
        getTranslation("tuning.qbe.set", TuningResultCards.displayTitle(item)),
        2500, Notification.Position.BOTTOM_START);
  }

  /**
   * Launches the diagnostic dialog for this result in the current search context.
   */
  private void openWhyNotFound(SearchResultItem item) {
    String query = currentQuery();
    if (query == null || query.isBlank()) {
      Notification.show(getTranslation("tuning.wnf.no.query"),
                        2000, Notification.Position.BOTTOM_START);
      return;
    }
    WhyNotFoundDialog.openFor(item.getImageId(), TuningResultCards.displayTitle(item), query,
                              buildConfig(feedbackSession.snapshot()));
  }

  private void markFeedback(SearchResultItem item, FeedbackType type) {
    float[] vector = catalog.findRawVector(item.getImageId())
        .orElse(new float[0]);
    feedbackSession.mark(item.getImageId(), TuningResultCards.displayTitle(item), type, vector);
    feedbackPanel.refresh(feedbackSession);
    Notification.show(
        getTranslation("tuning.feedback.marked",
                       resultCards.feedbackLabel(type),
                       TuningResultCards.displayTitle(item)),
        2000, Notification.Position.BOTTOM_START);
    if (currentRun != null) renderResults(currentRun, previousRun);
  }

  private void removeFeedback(UUID imageId) {
    feedbackSession.remove(imageId);
    feedbackPanel.refresh(feedbackSession);
    if (currentRun != null) renderResults(currentRun, previousRun);
  }

  private void applyPreset(SearchTuningPreset preset) {
    if (preset.getQuery() != null && !preset.getQuery().isBlank()) {
      queryPanel.setQuery(preset.getQuery());
    }
    parameterPanel.applyConfig(preset.toConfig());
  }

  @Override
  public String getPageTitle() {
    return getTranslation("tuning.page.title");
  }
}
