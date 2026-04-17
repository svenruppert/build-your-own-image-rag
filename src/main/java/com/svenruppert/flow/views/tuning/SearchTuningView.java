package com.svenruppert.flow.views.tuning;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.MainLayout;
import com.svenruppert.imagerag.bootstrap.ServiceRegistry;
import com.svenruppert.imagerag.domain.CategoryRegistry;
import com.svenruppert.imagerag.domain.ImageAsset;
import com.svenruppert.imagerag.domain.SearchResultItem;
import com.svenruppert.imagerag.domain.SearchTuningConfig;
import com.svenruppert.imagerag.domain.SearchTuningPreset;
import com.svenruppert.imagerag.domain.enums.FeedbackType;
import com.svenruppert.imagerag.domain.enums.QueryIntentType;
import com.svenruppert.imagerag.domain.enums.RetrievalMode;
import com.svenruppert.imagerag.domain.enums.SimilarityFunction;
import com.svenruppert.imagerag.dto.FeedbackSession;
import com.svenruppert.imagerag.dto.ScoreBreakdown;
import com.svenruppert.imagerag.dto.TuningRun;
import com.svenruppert.imagerag.dto.TuningSearchResponse;
import com.svenruppert.imagerag.dto.TuningSearchResult;
import com.svenruppert.imagerag.persistence.PersistenceService;
import com.svenruppert.imagerag.service.ImageStorageService;
import com.svenruppert.imagerag.service.PreviewService;
import com.svenruppert.imagerag.service.SearchService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * Search Tuning Lab — interactive retrieval experimentation workbench.
 *
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
 */
@Route(value = SearchTuningView.PATH, layout = MainLayout.class)
@PageTitle("Search Tuning Lab")
public class SearchTuningView extends VerticalLayout
    implements BeforeEnterObserver, HasLogger {

  public static final String PATH = "tuning";

  // ── Services ──────────────────────────────────────────────────────────────
  private final SearchService      searchService;
  private final PersistenceService persistenceService;

  // ── Query input ───────────────────────────────────────────────────────────
  private final TextArea queryField = new TextArea();

  // ── Mode / similarity — Select for compact display ────────────────────────
  private final Select<RetrievalMode>      modeGroup       = new Select<>();
  private final Select<SimilarityFunction> similarityGroup = new Select<>();

  // ── Weight controls ───────────────────────────────────────────────────────
  private final NumberField semanticWeightField   = new NumberField();
  private final NumberField bm25WeightField       = new NumberField();
  private final NumberField confidenceWeightField = new NumberField();
  private final NumberField scoreCutoffField      = new NumberField();
  private final NumberField maxResultsField       = new NumberField();

  // ── Relevance feedback controls ───────────────────────────────────────────
  private final Checkbox    feedbackToggle      = new Checkbox();
  private final NumberField feedbackWeightField = new NumberField();

  // ── Query-intent toggle ───────────────────────────────────────────────────
  private final Checkbox intentToggle = new Checkbox();

  // ── Query-by-Example: currently selected anchor image ─────────────────────
  private volatile UUID qbeImageId    = null;
  private final    Span qbeStatusSpan = new Span();

  // ── Run button + progress ─────────────────────────────────────────────────
  private final Button      runBtn      = new Button();
  private final ProgressBar runProgress = new ProgressBar();

  // ── Summary bar + results area ────────────────────────────────────────────
  private final Div            summaryBar  = new Div();
  private final VerticalLayout resultsArea = new VerticalLayout();

  // ── Inspector — top-right, always visible ─────────────────────────────────
  private final TuningInspectorComponent inspector = new TuningInspectorComponent();

  // ── Feedback session ──────────────────────────────────────────────────────
  private final FeedbackSession      feedbackSession = new FeedbackSession();
  private final FeedbackSessionPanel feedbackPanel   = new FeedbackSessionPanel(this::removeFeedback);

  // ── Run state ─────────────────────────────────────────────────────────────
  private volatile boolean  running     = false;
  private          TuningRun currentRun  = null;
  private          TuningRun previousRun = null;

  public SearchTuningView() {
    this.searchService      = ServiceRegistry.getInstance().getSearchService();
    this.persistenceService = ServiceRegistry.getInstance().getPersistenceService();

    // ── Labels that need getTranslation() (not available in field initializers) ──
    runBtn.setText(getTranslation("tuning.run"));
    runBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    runBtn.setWidthFull();
    runBtn.addClickListener(e -> runTuning());

    feedbackToggle.setLabel(getTranslation("tuning.feedback.enable"));
    intentToggle.setLabel(getTranslation("tuning.intent.enable"));

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

  // ── Section 1: header ─────────────────────────────────────────────────────

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

  // ── Section 2: top workbench — three columns ──────────────────────────────

  private Component buildTopWorkbench() {
    VerticalLayout queryPanel  = buildQueryPanel();
    VerticalLayout tuningPanel = buildTuningPanel();
    VerticalLayout inspPanel   = buildInspectorPanel();

    HorizontalLayout workbench = new HorizontalLayout(queryPanel, tuningPanel, inspPanel);
    workbench.setWidthFull();
    workbench.setAlignItems(FlexComponent.Alignment.START);
    workbench.setSpacing(true);
    workbench.setPadding(false);
    workbench.getStyle().set("margin-bottom", "0.5rem");
    return workbench;
  }

  // ── Left column: query + run + QBE + presets ─────────────────────────────

  private VerticalLayout buildQueryPanel() {
    VerticalLayout panel = styledPanel("240px");

    // Query field
    queryField.setLabel(getTranslation("tuning.query"));
    queryField.setPlaceholder(getTranslation("tuning.query.placeholder"));
    queryField.setWidthFull();
    queryField.setMinHeight("70px");
    queryField.setMaxHeight("120px");

    // Progress bar (hidden by default)
    runProgress.setIndeterminate(true);
    runProgress.setVisible(false);
    runProgress.setWidthFull();

    // QBE status row
    qbeStatusSpan.getStyle()
        .set("font-size", "var(--lumo-font-size-xs)")
        .set("color", "var(--lumo-secondary-text-color)")
        .set("flex", "1");
    updateQbeStatus();

    Button clearQbeBtn = new Button(getTranslation("tuning.qbe.clear"));
    clearQbeBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
    clearQbeBtn.getStyle().set("flex-shrink", "0");
    clearQbeBtn.addClickListener(e -> { qbeImageId = null; updateQbeStatus(); });

    HorizontalLayout qbeRow = new HorizontalLayout(qbeStatusSpan, clearQbeBtn);
    qbeRow.setAlignItems(FlexComponent.Alignment.CENTER);
    qbeRow.setWidthFull();
    qbeRow.setSpacing(false);
    qbeRow.getStyle().set("gap", "0.3rem");

    // Presets row (save + load side by side)
    Button saveBtn = new Button(getTranslation("tuning.preset.save"));
    saveBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
    saveBtn.setWidthFull();
    saveBtn.addClickListener(e -> openSavePresetDialog());

    Button loadBtn = new Button(getTranslation("tuning.preset.load"));
    loadBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
    loadBtn.setWidthFull();
    loadBtn.addClickListener(e -> openLoadPresetDialog());

    HorizontalLayout presetsRow = new HorizontalLayout(saveBtn, loadBtn);
    presetsRow.setWidthFull();
    presetsRow.setSpacing(false);
    presetsRow.getStyle().set("gap", "0.3rem");

    panel.add(
        queryField,
        runBtn,
        runProgress,
        separator(),
        labelSpan("tuning.qbe.label"), qbeRow,
        separator(),
        labelSpan("tuning.presets"), presetsRow
    );
    return panel;
  }

  // ── Middle column: retrieval parameters ───────────────────────────────────

  private VerticalLayout buildTuningPanel() {
    VerticalLayout panel = styledPanel("280px");

    // Retrieval mode — compact Select
    modeGroup.setItems(RetrievalMode.values());
    modeGroup.setItemLabelGenerator(RetrievalMode::getLabel);
    modeGroup.setValue(RetrievalMode.HYBRID);
    modeGroup.setWidthFull();
    modeGroup.addValueChangeListener(e -> updateControlVisibility());
    panel.add(controlSection(getTranslation("tuning.mode"), modeGroup));

    // Similarity function — compact Select
    similarityGroup.setItems(SimilarityFunction.values());
    similarityGroup.setItemLabelGenerator(SimilarityFunction::getLabel);
    similarityGroup.setValue(SimilarityFunction.COSINE);
    similarityGroup.setWidthFull();
    panel.add(controlSection(getTranslation("tuning.similarity"), similarityGroup));

    // Channel weights: semantic + BM25 side by side, confidence on own row
    configureWeightField(semanticWeightField,   getTranslation("tuning.weight.semantic"),
        SearchTuningConfig.DEFAULT_SEMANTIC_WEIGHT,   0.0, 5.0, 0.1);
    configureWeightField(bm25WeightField,       getTranslation("tuning.weight.bm25"),
        SearchTuningConfig.DEFAULT_BM25_WEIGHT,       0.0, 5.0, 0.1);
    configureWeightField(confidenceWeightField, getTranslation("tuning.weight.confidence"),
        SearchTuningConfig.DEFAULT_CONFIDENCE_WEIGHT, 0.0, 1.0, 0.05);

    HorizontalLayout semBm25Row = new HorizontalLayout(semanticWeightField, bm25WeightField);
    semBm25Row.setWidthFull();
    semBm25Row.setSpacing(false);
    semBm25Row.getStyle().set("gap", "0.35rem");

    panel.add(controlSection(getTranslation("tuning.weights"), semBm25Row, confidenceWeightField));

    // Relevance feedback: toggle + weight field side by side
    configureWeightField(feedbackWeightField, getTranslation("tuning.feedback.weight"),
        SearchTuningConfig.DEFAULT_FEEDBACK_WEIGHT, 0.0, 2.0, 0.1);
    feedbackWeightField.setEnabled(false);
    feedbackToggle.setValue(false);
    feedbackToggle.addValueChangeListener(e ->
        feedbackWeightField.setEnabled(Boolean.TRUE.equals(e.getValue())));

    HorizontalLayout fbRow = new HorizontalLayout(feedbackToggle, feedbackWeightField);
    fbRow.setAlignItems(FlexComponent.Alignment.BASELINE);
    fbRow.setWidthFull();
    fbRow.setSpacing(false);
    fbRow.getStyle().set("gap", "0.5rem");
    panel.add(controlSection(getTranslation("tuning.feedback"), fbRow));

    // Query intent: toggle + compact hint
    intentToggle.setValue(false);
    Span intentHint = new Span(getTranslation("tuning.intent.hint.short"));
    intentHint.getStyle()
        .set("font-size", "var(--lumo-font-size-xs)")
        .set("color", "var(--lumo-secondary-text-color)");
    panel.add(controlSection(getTranslation("tuning.intent"), intentToggle, intentHint));

    // Score cutoff + max results side by side
    configureWeightField(scoreCutoffField, getTranslation("tuning.cutoff"),
        SearchTuningConfig.DEFAULT_SCORE_CUTOFF, 0.0, 1.0, 0.05);
    maxResultsField.setLabel(getTranslation("tuning.max.results"));
    maxResultsField.setValue((double) SearchTuningConfig.DEFAULT_MAX_RESULTS);
    maxResultsField.setMin(1);
    maxResultsField.setMax(100);
    maxResultsField.setStep(5);
    maxResultsField.setStepButtonsVisible(true);
    maxResultsField.setWidthFull();

    HorizontalLayout cutoffRow = new HorizontalLayout(scoreCutoffField, maxResultsField);
    cutoffRow.setWidthFull();
    cutoffRow.setSpacing(false);
    cutoffRow.getStyle().set("gap", "0.35rem");
    panel.add(controlSection(getTranslation("tuning.cutoff.section"), cutoffRow));

    return panel;
  }

  private void configureWeightField(NumberField f, String label, double def,
                                    double min, double max, double step) {
    f.setLabel(label);
    f.setValue(def);
    f.setMin(min);
    f.setMax(max);
    f.setStep(step);
    f.setStepButtonsVisible(true);
    f.setWidthFull();
  }

  private VerticalLayout controlSection(String heading, Component... components) {
    H4 h = new H4(heading);
    h.getStyle()
        .set("margin", "0.5rem 0 0.2rem 0")
        .set("font-size", "var(--lumo-font-size-xs)")
        .set("color", "var(--lumo-secondary-text-color)")
        .set("text-transform", "uppercase")
        .set("letter-spacing", "0.04em");
    VerticalLayout sec = new VerticalLayout(h);
    sec.add(components);
    sec.setSpacing(false);
    sec.setPadding(false);
    sec.getStyle()
        .set("border-bottom", "1px solid var(--lumo-contrast-5pct)")
        .set("padding-bottom", "0.4rem");
    return sec;
  }

  /** Styled panel card (border + background). */
  private static VerticalLayout styledPanel(String width) {
    VerticalLayout panel = new VerticalLayout();
    panel.setWidth(width);
    panel.setMinWidth(width);
    panel.getStyle()
        .set("flex-shrink", "0")
        .set("border", "1px solid var(--lumo-contrast-10pct)")
        .set("border-radius", "var(--lumo-border-radius-l)")
        .set("padding", "0.6rem 0.75rem")
        .set("background", "var(--lumo-base-color)");
    panel.setSpacing(false);
    panel.setPadding(false);
    return panel;
  }

  /** Thin horizontal separator line. */
  private static Div separator() {
    Div d = new Div();
    d.getStyle()
        .set("border-top", "1px solid var(--lumo-contrast-5pct)")
        .set("margin", "0.35rem 0")
        .set("width", "100%");
    return d;
  }

  /** Small uppercase section label for the query panel (no H4 overhead). */
  private Span labelSpan(String i18nKey) {
    Span s = new Span(getTranslation(i18nKey));
    s.getStyle()
        .set("font-size", "var(--lumo-font-size-xs)")
        .set("color", "var(--lumo-secondary-text-color)")
        .set("text-transform", "uppercase")
        .set("letter-spacing", "0.04em")
        .set("font-weight", "600")
        .set("margin-top", "0.1rem");
    return s;
  }

  private void updateControlVisibility() {
    RetrievalMode mode = modeGroup.getValue();
    boolean hasSemantic = mode != RetrievalMode.BM25_ONLY;
    boolean hasBm25     = mode != RetrievalMode.SEMANTIC_ONLY;
    semanticWeightField.setEnabled(hasSemantic);
    similarityGroup.setEnabled(hasSemantic);
    bm25WeightField.setEnabled(hasBm25);
  }

  private void updateQbeStatus() {
    if (qbeImageId == null) {
      qbeStatusSpan.setText(getTranslation("tuning.qbe.none"));
      qbeStatusSpan.getStyle().remove("color");
    } else {
      String label = persistenceService.findImage(qbeImageId)
          .map(ImageAsset::getOriginalFilename).orElse(qbeImageId.toString());
      if (label.length() > 26) label = label.substring(0, 23) + "\u2026";
      qbeStatusSpan.setText(getTranslation("tuning.qbe.active", label));
      qbeStatusSpan.getStyle().set("color", "var(--lumo-primary-color)");
    }
  }

  // ── Right column: inspector + feedback session ────────────────────────────

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

  // ── Section 3: full-width summary bar ─────────────────────────────────────

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

  // ── Section 4: full-width results area ────────────────────────────────────

  private Component buildResultsArea() {
    resultsArea.setSpacing(false);
    resultsArea.setPadding(false);
    resultsArea.setWidthFull();
    return resultsArea;
  }

  // ── Tuning execution ──────────────────────────────────────────────────────

  private void runTuning() {
    if (running) {
      Notification n = Notification.show(getTranslation("tuning.already.running"),
          3000, Notification.Position.BOTTOM_START);
      n.addThemeVariants(NotificationVariant.LUMO_CONTRAST);
      return;
    }
    String query = queryField.getValue();
    if (query == null || query.isBlank()) {
      Notification n = Notification.show(getTranslation("tuning.query.empty"),
          3000, Notification.Position.BOTTOM_START);
      n.addThemeVariants(NotificationVariant.LUMO_ERROR);
      return;
    }

    FeedbackSession snap   = feedbackSession.snapshot();
    SearchTuningConfig cfg = buildConfig(snap);
    running = true;
    runBtn.setEnabled(false);
    runProgress.setVisible(true);
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

        final TuningRun finalRun  = run;
        final TuningRun prevRun   = currentRun;

        ui.access(() -> {
          inspector.showRun(finalRun);
          renderResults(finalRun, prevRun);
          renderSummaryBar(finalRun);
          previousRun = prevRun;
          currentRun  = finalRun;
          runBtn.setEnabled(true);
          runProgress.setVisible(false);
          running = false;
        });
      } catch (Exception ex) {
        ui.access(() -> {
          String msg = getTranslation("tuning.error", ex.getMessage());
          summaryBar.setText(msg);
          runBtn.setEnabled(true);
          runProgress.setVisible(false);
          running = false;
          Notification n = Notification.show(msg, 5000, Notification.Position.BOTTOM_START);
          n.addThemeVariants(NotificationVariant.LUMO_ERROR);
        });
      }
    });
  }

  private SearchTuningConfig buildConfig(FeedbackSession snap) {
    return new SearchTuningConfig()
        .setRetrievalMode(modeGroup.getValue() != null
            ? modeGroup.getValue() : RetrievalMode.HYBRID)
        .setSimilarityFunction(similarityGroup.getValue() != null
            ? similarityGroup.getValue() : SimilarityFunction.COSINE)
        .setSemanticWeight(doubleValue(semanticWeightField,
            SearchTuningConfig.DEFAULT_SEMANTIC_WEIGHT))
        .setBm25Weight(doubleValue(bm25WeightField,
            SearchTuningConfig.DEFAULT_BM25_WEIGHT))
        .setConfidenceWeight(doubleValue(confidenceWeightField,
            SearchTuningConfig.DEFAULT_CONFIDENCE_WEIGHT))
        .setScoreCutoff(doubleValue(scoreCutoffField,
            SearchTuningConfig.DEFAULT_SCORE_CUTOFF))
        .setMaxResults((int) doubleValue(maxResultsField,
            SearchTuningConfig.DEFAULT_MAX_RESULTS))
        .setFeedbackEnabled(Boolean.TRUE.equals(feedbackToggle.getValue()))
        .setFeedbackWeight(doubleValue(feedbackWeightField,
            SearchTuningConfig.DEFAULT_FEEDBACK_WEIGHT))
        .setFeedbackSession(snap)
        .setQueryByExampleImageId(qbeImageId)
        .setQueryIntentEnabled(Boolean.TRUE.equals(intentToggle.getValue()));
  }

  private double doubleValue(NumberField f, double fallback) {
    Double v = f.getValue();
    return (v != null && !Double.isNaN(v)) ? v : fallback;
  }

  // ── Rendering ─────────────────────────────────────────────────────────────

  private void renderSummaryBar(TuningRun run) {
    SearchTuningConfig cfg = run.getConfig();
    summaryBar.removeAll();

    String semFmt  = String.format("%.2f", cfg.getSemanticWeight());
    String bFmt    = String.format("%.2f", cfg.getBm25Weight());
    String confFmt = String.format("%.2f", cfg.getConfidenceWeight());
    String cutFmt  = String.format("%.2f", cfg.getScoreCutoff());

    StringBuilder sb = new StringBuilder();
    sb.append(getTranslation("tuning.summary.config",
        cfg.getRetrievalMode().getLabel(),
        cfg.getSimilarityFunction().getLabel(),
        semFmt, bFmt, confFmt, cutFmt,
        run.getResults().size(),
        run.getExecutionMs()));

    QueryIntentType intent = run.getDetectedIntent();
    if (intent != null) {
      sb.append("  \u2502  ").append(
          getTranslation("tuning.summary.intent", intent.getLabel()));
    }
    int fb = run.getFeedbackEntriesUsed();
    if (fb > 0) {
      sb.append("  \u2502  ").append(
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
      resultsArea.add(buildResultCard(i + 1, tsr, delta));
    }
  }

  // ── Result card ───────────────────────────────────────────────────────────

  private Component buildResultCard(int rank, TuningSearchResult tsr, int delta) {
    SearchResultItem item = tsr.item();
    ScoreBreakdown   bd   = tsr.breakdown();

    // Thumbnail
    Div thumbWrap = new Div(buildThumbnail(item));
    thumbWrap.getStyle()
        .set("width", "160px").set("min-width", "160px").set("height", "120px")
        .set("flex-shrink", "0").set("overflow", "hidden")
        .set("background", "var(--lumo-contrast-5pct)")
        .set("display", "flex").set("align-items", "center").set("justify-content", "center");

    // Info panel
    Div info = new Div();
    info.getStyle()
        .set("flex", "1").set("padding", "0.6rem 0.75rem")
        .set("min-width", "0").set("overflow", "hidden")
        .set("display", "flex").set("flex-direction", "column").set("gap", "0.3rem");

    // Header row
    Span rankBadge = new Span("#" + rank);
    rankBadge.getStyle().set("font-weight", "700")
        .set("color", "var(--lumo-primary-color)").set("flex-shrink", "0");

    Span filename = new Span(item.getTitle() != null ? item.getTitle() : item.getImageId().toString());
    filename.getStyle()
        .set("font-weight", "600").set("font-size", "var(--lumo-font-size-s)")
        .set("flex", "1").set("overflow", "hidden")
        .set("text-overflow", "ellipsis").set("white-space", "nowrap");

    HorizontalLayout header = new HorizontalLayout(rankBadge, filename, buildDeltaBadge(delta));
    header.setWidthFull();
    header.setAlignItems(FlexComponent.Alignment.CENTER);
    header.setSpacing(false);
    header.getStyle().set("gap", "0.4rem");

    // Tag row
    HorizontalLayout tags = new HorizontalLayout();
    tags.setSpacing(false);
    tags.getStyle().set("gap", "0.3rem").set("flex-wrap", "wrap");

    if (item.getSourceCategory() != null) {
      tags.add(buildTag(CategoryRegistry.getUserLabel(item.getSourceCategory()),
          "var(--lumo-primary-color-10pct)", "var(--lumo-primary-color)"));
    }
    if (item.getSeasonHint() != null
        && item.getSeasonHint() != com.svenruppert.imagerag.domain.enums.SeasonHint.UNKNOWN) {
      tags.add(buildTag(item.getSeasonHint().name(),
          "var(--lumo-contrast-5pct)", "var(--lumo-contrast-60pct)"));
    }
    if (Boolean.TRUE.equals(item.getContainsPerson())) {
      tags.add(buildTag(getTranslation("tuning.tag.person"),
          "var(--lumo-contrast-5pct)", "var(--lumo-contrast-60pct)"));
    }
    if (tsr.isInBothChannels()) {
      tags.add(buildTag(getTranslation("tuning.tag.hybrid"),
          "var(--lumo-success-color-10pct)", "var(--lumo-success-color)"));
    } else if (tsr.isInVectorResults()) {
      tags.add(buildTag(getTranslation("tuning.tag.semantic"),
          "var(--lumo-primary-color-10pct)", "var(--lumo-primary-color)"));
    } else if (tsr.isInBm25Results()) {
      tags.add(buildTag(getTranslation("tuning.tag.bm25"),
          "var(--lumo-contrast-5pct)", "var(--lumo-contrast-70pct)"));
    }
    feedbackSession.getType(item.getImageId()).ifPresent(ft ->
        tags.add(buildTag(ft.getIcon() + "\u202f" + getTranslation(
                "tuning.feedback.type." + ft.name().toLowerCase()),
            ft.getBackground(), ft.getColor()))
    );

    info.add(header, tags, buildScoreBreakdown(bd, tsr));

    if (item.getSummary() != null && !item.getSummary().isBlank()) {
      String txt = item.getSummary();
      if (txt.length() > 140) txt = txt.substring(0, 137) + "\u2026";
      Div summary = new Div();
      summary.setText(txt);
      summary.getStyle()
          .set("font-size", "var(--lumo-font-size-xs)")
          .set("color", "var(--lumo-secondary-text-color)");
      info.add(summary);
    }

    info.add(buildActionRow(item));

    Div card = new Div();
    card.getStyle()
        .set("display", "flex").set("align-items", "stretch")
        .set("border", "1px solid var(--lumo-contrast-10pct)")
        .set("border-radius", "var(--lumo-border-radius-m)")
        .set("margin-bottom", "0.5rem")
        .set("background", "var(--lumo-base-color)")
        .set("width", "100%").set("overflow", "hidden");
    card.add(thumbWrap, info);
    return card;
  }

  /** Per-result action row: feedback buttons + QBE anchor button. */
  private Component buildActionRow(SearchResultItem item) {
    HorizontalLayout row = new HorizontalLayout();
    row.setSpacing(false);
    row.getStyle().set("gap", "0.3rem").set("flex-wrap", "wrap").set("margin-top", "0.1rem");

    for (FeedbackType ft : FeedbackType.values()) {
      String label = ft.getIcon() + "\u202f"
          + getTranslation("tuning.feedback.type." + ft.name().toLowerCase());
      Button btn = new Button(label);
      btn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
      btn.getStyle().set("font-size", "var(--lumo-font-size-xs)");
      btn.addClickListener(e -> markFeedback(item, ft));
      row.add(btn);
    }

    Span divider = new Span("|");
    divider.getStyle().set("color", "var(--lumo-contrast-30pct)").set("padding", "0 0.2rem");
    row.add(divider);

    Button qbeBtn = new Button(getTranslation("tuning.qbe.use"));
    qbeBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
    qbeBtn.getStyle().set("font-size", "var(--lumo-font-size-xs)");
    qbeBtn.addClickListener(e -> {
      qbeImageId = item.getImageId();
      updateQbeStatus();
      Notification.show(
          getTranslation("tuning.qbe.set",
              item.getTitle() != null ? item.getTitle() : item.getImageId().toString()),
          2500, Notification.Position.BOTTOM_START);
    });
    row.add(qbeBtn);
    return row;
  }

  // ── Feedback management ───────────────────────────────────────────────────

  private void markFeedback(SearchResultItem item, FeedbackType type) {
    float[] vector = persistenceService.findRawVector(item.getImageId())
        .orElse(new float[0]);
    feedbackSession.mark(item.getImageId(),
        item.getTitle() != null ? item.getTitle() : item.getImageId().toString(),
        type, vector);
    feedbackPanel.refresh(feedbackSession);
    Notification.show(
        getTranslation("tuning.feedback.marked",
            getTranslation("tuning.feedback.type." + type.name().toLowerCase()),
            item.getTitle() != null ? item.getTitle() : item.getImageId().toString()),
        2000, Notification.Position.BOTTOM_START);
    if (currentRun != null) renderResults(currentRun, previousRun);
  }

  private void removeFeedback(UUID imageId) {
    feedbackSession.remove(imageId);
    feedbackPanel.refresh(feedbackSession);
    if (currentRun != null) renderResults(currentRun, previousRun);
  }

  // ── Preset dialogs ────────────────────────────────────────────────────────

  private void openSavePresetDialog() {
    Dialog dlg = new Dialog();
    dlg.setHeaderTitle(getTranslation("tuning.preset.save.title"));

    TextField nameField = new TextField(getTranslation("tuning.preset.name"));
    nameField.setWidthFull();
    nameField.setPlaceholder(getTranslation("tuning.preset.name.placeholder"));

    Button saveBtn = new Button(getTranslation("tuning.preset.save"), e -> {
      String name = nameField.getValue();
      if (name == null || name.isBlank()) {
        nameField.setInvalid(true);
        nameField.setErrorMessage(getTranslation("tuning.preset.name.required"));
        return;
      }
      String q = queryField.getValue() != null ? queryField.getValue() : "";
      persistenceService.saveTuningPreset(
          SearchTuningPreset.from(name.trim(), q, buildConfig(null)));
      dlg.close();
      Notification.show(getTranslation("tuning.preset.saved", name.trim()),
          2500, Notification.Position.BOTTOM_START);
    });
    saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    Button cancelBtn = new Button(getTranslation("tuning.preset.cancel"), e -> dlg.close());
    dlg.add(nameField);
    dlg.getFooter().add(cancelBtn, saveBtn);
    dlg.open();
  }

  private void openLoadPresetDialog() {
    List<SearchTuningPreset> presets = persistenceService.findAllTuningPresets();
    if (presets.isEmpty()) {
      Notification.show(getTranslation("tuning.preset.none"),
          2500, Notification.Position.BOTTOM_START);
      return;
    }

    Dialog dlg = new Dialog();
    dlg.setHeaderTitle(getTranslation("tuning.preset.load.title"));
    dlg.setWidth("420px");

    Select<SearchTuningPreset> selector = new Select<>();
    selector.setItems(presets);
    selector.setItemLabelGenerator(SearchTuningPreset::getName);
    selector.setWidthFull();
    selector.setLabel(getTranslation("tuning.preset.select"));
    if (!presets.isEmpty()) selector.setValue(presets.get(presets.size() - 1));

    Button loadBtn = new Button(getTranslation("tuning.preset.load"), e -> {
      SearchTuningPreset p = selector.getValue();
      if (p == null) return;
      applyPreset(p);
      dlg.close();
      Notification.show(getTranslation("tuning.preset.loaded", p.getName()),
          2500, Notification.Position.BOTTOM_START);
    });
    loadBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    Button deleteBtn = new Button(getTranslation("tuning.preset.delete"), e -> {
      SearchTuningPreset p = selector.getValue();
      if (p == null) return;
      persistenceService.deleteTuningPreset(p.getId());
      dlg.close();
      Notification.show(getTranslation("tuning.preset.deleted", p.getName()),
          2500, Notification.Position.BOTTOM_START);
    });
    deleteBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);

    Button cancelBtn = new Button(getTranslation("tuning.preset.cancel"), e -> dlg.close());
    dlg.add(selector);
    dlg.getFooter().add(deleteBtn, cancelBtn, loadBtn);
    dlg.open();
  }

  private void applyPreset(SearchTuningPreset preset) {
    SearchTuningConfig cfg = preset.toConfig();
    if (preset.getQuery() != null && !preset.getQuery().isBlank()) {
      queryField.setValue(preset.getQuery());
    }
    modeGroup.setValue(cfg.getRetrievalMode());
    similarityGroup.setValue(cfg.getSimilarityFunction());
    semanticWeightField.setValue(cfg.getSemanticWeight());
    bm25WeightField.setValue(cfg.getBm25Weight());
    confidenceWeightField.setValue(cfg.getConfidenceWeight());
    feedbackToggle.setValue(cfg.isFeedbackEnabled());
    feedbackWeightField.setValue(cfg.getFeedbackWeight());
    intentToggle.setValue(cfg.isQueryIntentEnabled());
    scoreCutoffField.setValue(cfg.getScoreCutoff());
    maxResultsField.setValue((double) cfg.getMaxResults());
    updateControlVisibility();
  }

  // ── Thumbnail ─────────────────────────────────────────────────────────────

  private Component buildThumbnail(SearchResultItem item) {
    try {
      ImageStorageService storage  = ServiceRegistry.getInstance().getImageStorageService();
      PreviewService      previews = ServiceRegistry.getInstance().getPreviewService();
      ImageAsset          asset    = persistenceService.findImage(item.getImageId()).orElse(null);
      if (asset == null) return thumbPlaceholder();

      Path originalPath = storage.resolvePath(asset.getId());
      if (!Files.exists(originalPath)) return thumbPlaceholder();

      StreamResource res = previews.getTilePreview(
          asset.getId(), originalPath, asset.getStoredFilename());
      if (res == null) {
        res = new StreamResource(asset.getStoredFilename(), () -> {
          try { return Files.newInputStream(originalPath); }
          catch (Exception ex) { return InputStream.nullInputStream(); }
        });
      }
      Image img = new Image(res,
          item.getTitle() != null ? item.getTitle() : getTranslation("tuning.image.alt"));
      img.setWidth("100%");
      img.setHeight("100%");
      img.getStyle().set("object-fit", "cover").set("display", "block");
      return img;
    } catch (Exception e) {
      logger().debug("Could not load thumbnail for tuning result {}", item.getImageId(), e);
    }
    return thumbPlaceholder();
  }

  private static Span thumbPlaceholder() {
    Span ph = new Span("\u2014");
    ph.getStyle().set("font-size", "1.4rem").set("color", "var(--lumo-contrast-30pct)");
    return ph;
  }

  // ── Score breakdown ───────────────────────────────────────────────────────

  private Div buildScoreBreakdown(ScoreBreakdown bd, TuningSearchResult tsr) {
    Div container = new Div();
    container.getStyle()
        .set("background", "var(--lumo-contrast-5pct)")
        .set("border-radius", "var(--lumo-border-radius-s)")
        .set("padding", "0.35rem 0.5rem");

    double semWidth  = Math.round(bd.semanticFraction() * 80);
    double bm25Width = Math.round(bd.bm25Fraction()    * 80);

    Div barWrap = new Div();
    barWrap.getStyle()
        .set("display", "flex").set("height", "6px")
        .set("border-radius", "3px").set("overflow", "hidden")
        .set("background", "var(--lumo-contrast-10pct)")
        .set("margin-bottom", "0.3rem").set("width", "100%");

    if (semWidth > 0) {
      Div seg = new Div();
      seg.getStyle().set("width", semWidth + "%")
          .set("background", "var(--lumo-primary-color)").set("flex-shrink", "0");
      barWrap.add(seg);
    }
    if (bm25Width > 0) {
      Div seg = new Div();
      seg.getStyle().set("width", bm25Width + "%")
          .set("background", "var(--lumo-success-color)").set("flex-shrink", "0");
      barWrap.add(seg);
    }
    if (bd.hasFeedback()) {
      double base   = Math.max(bd.scoreBeforeBoost(), 0.001);
      double fbWidth = Math.round(Math.min(Math.abs(bd.feedbackContrib()) / base, 0.15) * 80);
      if (fbWidth > 0) {
        Div seg = new Div();
        seg.getStyle().set("width", fbWidth + "%")
            .set("background", bd.feedbackContrib() > 0
                ? "#f59e0b" : "var(--lumo-error-color)")
            .set("flex-shrink", "0");
        barWrap.add(seg);
      }
    }

    // Numeric breakdown — intentionally kept as compact technical notation
    StringBuilder nums = new StringBuilder();
    nums.append(String.format("Score\u202f%.3f", bd.finalScore()));
    if (bd.semanticContrib()  > 0)     nums.append(String.format("  \u2502  Sem\u202f%.3f", bd.semanticContrib()));
    if (bd.bm25Contrib()      > 0)     nums.append(String.format("  \u2502  BM25\u202f%.3f", bd.bm25Contrib()));
    if (bd.confidenceBoost()  > 0.001) nums.append(String.format("  \u2502  Conf\u202f+%.0f%%", bd.confidenceBoost() * 100));
    if (bd.hasFeedback())              nums.append(String.format("  \u2502  Fb\u202f%+.3f", bd.feedbackContrib()));
    if (tsr.vectorRank() >= 0)         nums.append(String.format("  \u2502  vRk\u202f%d", tsr.vectorRank() + 1));
    if (tsr.bm25Rank()   >= 0)         nums.append(String.format("  \u2502  kRk\u202f%d", tsr.bm25Rank()   + 1));

    Span numsSpan = new Span(nums.toString());
    numsSpan.getStyle()
        .set("font-size", "var(--lumo-font-size-xs)")
        .set("color", "var(--lumo-secondary-text-color)")
        .set("font-family", "monospace");

    container.add(barWrap, numsSpan);
    return container;
  }

  // ── Delta badge ───────────────────────────────────────────────────────────

  private Span buildDeltaBadge(int delta) {
    Span badge = new Span();
    badge.getStyle()
        .set("font-size", "var(--lumo-font-size-xs)").set("font-weight", "700")
        .set("padding", "1px 5px")
        .set("border-radius", "var(--lumo-border-radius-s)").set("flex-shrink", "0");

    if (delta == Integer.MAX_VALUE) {
      badge.setText(getTranslation("tuning.rank.new"));
      badge.getStyle().set("background", "var(--lumo-primary-color-10pct)")
          .set("color", "var(--lumo-primary-color)");
    } else if (delta == Integer.MIN_VALUE || delta == 0) {
      badge.setText(delta == 0 ? "=" : "\u2014");
      badge.getStyle().set("background", "var(--lumo-contrast-5pct)")
          .set("color", "var(--lumo-contrast-60pct)");
    } else if (delta > 0) {
      badge.setText("\u25b2" + delta);
      badge.getStyle().set("background", "var(--lumo-success-color-10pct)")
          .set("color", "var(--lumo-success-color)");
    } else {
      badge.setText("\u25bc" + Math.abs(delta));
      badge.getStyle().set("background", "var(--lumo-error-color-10pct)")
          .set("color", "var(--lumo-error-color)");
    }
    return badge;
  }

  // ── Tag chip ──────────────────────────────────────────────────────────────

  private static Span buildTag(String text, String bg, String color) {
    Span tag = new Span(text);
    tag.getStyle()
        .set("background", bg).set("color", color)
        .set("font-size", "var(--lumo-font-size-xxs)")
        .set("padding", "1px 5px")
        .set("border-radius", "var(--lumo-border-radius-s)")
        .set("white-space", "nowrap");
    return tag;
  }
}
