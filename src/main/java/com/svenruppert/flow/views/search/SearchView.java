package com.svenruppert.flow.views.search;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.MainLayout;
import com.svenruppert.flow.views.detail.DetailDialog;
import com.svenruppert.flow.views.shared.ViewModeToggle;
import com.svenruppert.imagerag.bootstrap.ServiceRegistry;
import com.svenruppert.imagerag.domain.*;
import com.svenruppert.imagerag.domain.enums.RiskLevel;
import com.svenruppert.imagerag.domain.enums.SearchMode;
import com.svenruppert.imagerag.domain.enums.SeasonHint;
import com.svenruppert.imagerag.domain.enums.SourceCategory;
import com.svenruppert.imagerag.persistence.PersistenceService;
import com.svenruppert.imagerag.service.PreviewService;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import com.vaadin.flow.shared.Registration;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Search Workbench — semantic image search with step-by-step transparency.
 *
 * <h3>Two explicit modes</h3>
 * <ul>
 *   <li><b>Transform Only</b> — runs LLM query understanding and stops.  The user can
 *       inspect the transformation result in the {@link SearchInspectorComponent} and
 *       optionally adjust the derived parameters in the Advanced panel before executing.</li>
 *   <li><b>Search</b> — full pipeline: LLM understanding → vector search → results.</li>
 * </ul>
 *
 * <h3>Iterative workflow</h3>
 * The intended use pattern is:
 * <ol>
 *   <li>Enter a natural language query.</li>
 *   <li>Click <em>Transform Only</em> to see how the system interprets the query.</li>
 *   <li>Optionally edit the derived parameters in the Advanced panel.</li>
 *   <li>Click <em>Search</em> (or <em>Refine Search</em> if parameters were edited)
 *       to run the actual search.</li>
 * </ol>
 *
 * <h3>History</h3>
 * Up to {@value #MAX_VISIBLE_CHIPS} recent searches are shown as chips below the search
 * bar.  Clicking a chip copies the original query back into the search field without
 * re-executing — the user can then edit it or choose a mode.  A "More history" button
 * opens {@link SearchHistoryDialog} for full history management including multi-select
 * deletion.
 *
 * <p>History is persisted <em>after</em> each run completes so that the entry always
 * captures the original query together with the LLM-transformed final query.
 *
 * <p>Background searches use client-side polling (no {@code @Push} required).
 */
@PageTitle("Search Images")
@Route(value = SearchView.PATH, layout = MainLayout.class)
public class SearchView
    extends VerticalLayout
    implements HasLogger {

  public static final String PATH = "search";

  private static final int POLL_INTERVAL_MS  = 800;
  private static final int MAX_VISIBLE_CHIPS = 3;

  // ── Primary search bar ────────────────────────────────────────────────────
  private final TextArea queryField   = new TextArea();
  /** Runs only LLM query transformation; skips vector search. */
  private final Button   transformBtn = new Button();
  /** Runs the full pipeline: LLM transformation + vector search + results. */
  private final Button   searchBtn    = new Button();

  // ── Recent search history ─────────────────────────────────────────────────
  private final Div recentSearchBar = new Div();

  // ── Search process inspector ──────────────────────────────────────────────
  private final SearchInspectorComponent inspector = new SearchInspectorComponent();

  // ── View toggle (segmented control) ──────────────────────────────────────
  private ViewModeToggle viewToggle;

  // ── Progress ──────────────────────────────────────────────────────────────
  private final ProgressBar progressBar = new ProgressBar();
  private final Span        statusLabel = new Span();

  // ── Advanced / editable search plan panel ────────────────────────────────
  private final Details                advancedSection  = new Details();
  private final TextArea               embeddingArea    = new TextArea();
  private final Select<String>         personSelect     = new Select<>();
  private final Select<String>         vehicleSelect    = new Select<>();
  private final Select<String>         plateSelect      = new Select<>();
  private final Select<SeasonHint>     seasonSelect     = new Select<>();
  private final Select<SourceCategory> categorySelect   = new Select<>();
  private final Select<RiskLevel>      privacySelect    = new Select<>();
  /** Score threshold: minimum cosine similarity for a result to be kept. Default 0.45. */
  private final NumberField            scoreField       = new NumberField();
  private final Button                 refineBtn        = new Button();

  // ── Results ───────────────────────────────────────────────────────────────
  private final Grid<SearchResultItem> resultsGrid = new Grid<>(SearchResultItem.class, false);
  private final Div tileContainer = new Div();
  private boolean tileMode = false;
  private List<SearchResultItem> lastResults = List.of();

  // ── Runtime state ─────────────────────────────────────────────────────────
  private SearchPlan currentPlan = null;
  private volatile SearchRunState currentSearch;
  private Registration pollRegistration;

  // ── Boolean select values ─────────────────────────────────────────────────
  private static final String BOOL_ANY = "any";
  private static final String BOOL_YES = "yes";
  private static final String BOOL_NO  = "no";

  public SearchView() {
    // Natural page scroll — do NOT call setSizeFull() so the page can grow with content.
    setWidthFull();
    setPadding(true);
    setSpacing(true);

    add(new H2(getTranslation("search.title")));
    add(new Paragraph(getTranslation("search.description")));

    // ── Left column: controls (search bar + history + advanced panel + progress) ────
    // ── Right column: interactive step inspector ───────────────────────────────────

    // Search bar
    queryField.setLabel(getTranslation("search.query.label"));
    queryField.setWidthFull();
    queryField.setPlaceholder(getTranslation("search.query.placeholder"));
    queryField.setMinHeight("80px");

    // Transform Only — stops after LLM analysis; vector search is skipped
    transformBtn.setText(getTranslation("search.button.transform"));
    transformBtn.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
    transformBtn.getElement().setAttribute("title",
        getTranslation("search.button.transform.tooltip"));
    transformBtn.addClickListener(e -> triggerTransformOnly());

    // Search — full pipeline: LLM + vector search + results
    searchBtn.setText(getTranslation("search.button"));
    searchBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    searchBtn.addClickListener(e -> triggerSearchAndExecute());

    VerticalLayout btnCol = new VerticalLayout(transformBtn, searchBtn);
    btnCol.setPadding(false);
    btnCol.setSpacing(true);

    HorizontalLayout searchBar = new HorizontalLayout(queryField, btnCol);
    searchBar.setWidthFull();
    searchBar.setAlignItems(Alignment.END);
    searchBar.setFlexGrow(1, queryField);

    // Progress indicators
    progressBar.setIndeterminate(true);
    progressBar.setWidthFull();
    progressBar.setVisible(false);
    statusLabel.getStyle()
        .set("font-size", "var(--lumo-font-size-s)")
        .set("color", "var(--lumo-secondary-text-color)");
    statusLabel.setVisible(false);

    // Recent search history chip bar
    buildRecentSearchBar();

    // Advanced panel
    buildAdvancedSection();

    // Left column: search input → history → advanced → progress
    VerticalLayout leftCol = new VerticalLayout(
        searchBar, recentSearchBar, advancedSection, progressBar, statusLabel);
    leftCol.setPadding(false);
    leftCol.setSpacing(true);
    leftCol.setMinWidth("340px");
    leftCol.setWidth("50%");

    // Right column: step inspector
    inspector.setWidthFull();
    VerticalLayout rightCol = new VerticalLayout(inspector);
    rightCol.setPadding(false);
    rightCol.setSpacing(false);
    rightCol.setWidth("50%");
    rightCol.setMinWidth("300px");

    // Two-column top area
    HorizontalLayout topArea = new HorizontalLayout(leftCol, rightCol);
    topArea.setWidthFull();
    topArea.setAlignItems(Alignment.START);
    topArea.setSpacing(true);
    add(topArea);

    // ── Results area — full width, below the two columns ──────────────────

    // View toggle (segmented control)
    viewToggle = new ViewModeToggle(false, this::onTileModeChanged);
    viewToggle.setVisible(false);
    add(viewToggle);

    // Results grid
    configureResultsGrid();
    add(resultsGrid);

    // Tile container
    tileContainer.getStyle()
        .set("display", "grid")
        .set("grid-template-columns", "repeat(auto-fill, minmax(220px, 1fr))")
        .set("gap", "1rem")
        .set("padding", "0.5rem")
        .set("width", "100%");
    tileContainer.setVisible(false);
    add(tileContainer);
  }

  // -------------------------------------------------------------------------
  // Recent search history
  // -------------------------------------------------------------------------

  private void buildRecentSearchBar() {
    recentSearchBar.getStyle()
        .set("display", "flex")
        .set("flex-wrap", "wrap")
        .set("gap", "0.5rem")
        .set("align-items", "center")
        .set("padding", "0.25rem 0");
    refreshRecentChips();
  }

  private void refreshRecentChips() {
    recentSearchBar.removeAll();
    PersistenceService ps = ServiceRegistry.getInstance().getPersistenceService();
    List<RecentSearchEntry> recent = ps.getRecentSearches();
    if (recent.isEmpty()) {
      recentSearchBar.setVisible(false);
      return;
    }
    recentSearchBar.setVisible(true);

    Span label = new Span(getTranslation("search.history.label"));
    label.getStyle()
        .set("font-size", "var(--lumo-font-size-xs)")
        .set("color", "var(--lumo-secondary-text-color)")
        .set("flex-shrink", "0");
    recentSearchBar.add(label);

    // Show at most MAX_VISIBLE_CHIPS entries as clickable chips.
    // Clicking a chip copies the original query into the field — does NOT trigger search.
    int visible = Math.min(recent.size(), MAX_VISIBLE_CHIPS);
    for (int i = 0; i < visible; i++) {
      RecentSearchEntry entry = recent.get(i);
      Button chip = new Button(entry.getQuery());
      chip.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY,
                            ButtonVariant.LUMO_CONTRAST);
      chip.getStyle().set("font-size", "var(--lumo-font-size-xs)");
      chip.getElement().setAttribute("title",
          getTranslation("search.history.chip.tooltip"));
      chip.addClickListener(e -> {
        queryField.setValue(entry.getQuery());
        queryField.focus();
      });
      recentSearchBar.add(chip);
    }

    // "More history" button when there are entries beyond the visible limit
    if (recent.size() > MAX_VISIBLE_CHIPS) {
      int hidden = recent.size() - MAX_VISIBLE_CHIPS;
      Button moreBtn = new Button(getTranslation("search.history.more", hidden));
      moreBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
      moreBtn.getStyle().set("font-size", "var(--lumo-font-size-xs)");
      moreBtn.addClickListener(e -> openHistoryDialog());
      recentSearchBar.add(moreBtn);
    }

    Button clearBtn = new Button(getTranslation("search.history.clear"));
    clearBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY,
                              ButtonVariant.LUMO_ERROR);
    clearBtn.getStyle().set("font-size", "var(--lumo-font-size-xs)");
    clearBtn.addClickListener(e -> {
      ps.clearRecentSearches();
      refreshRecentChips();
    });
    recentSearchBar.add(clearBtn);
  }

  private void openHistoryDialog() {
    PersistenceService ps = ServiceRegistry.getInstance().getPersistenceService();
    new SearchHistoryDialog(
        ps,
        entry -> {
          // Load the original query back into the field for editing — do NOT execute
          queryField.setValue(entry.getQuery());
          queryField.focus();
        },
        this::refreshRecentChips
    ).open();
  }

  // -------------------------------------------------------------------------
  // Advanced search parameters panel
  // -------------------------------------------------------------------------

  private void buildAdvancedSection() {
    embeddingArea.setLabel(getTranslation("search.advanced.embedding"));
    embeddingArea.setWidthFull();
    embeddingArea.setMaxHeight("100px");

    configureBoolSelect(personSelect,  getTranslation("search.advanced.person"));
    configureBoolSelect(vehicleSelect, getTranslation("search.advanced.vehicle"));
    configureBoolSelect(plateSelect,   getTranslation("search.advanced.plate"));

    seasonSelect.setLabel(getTranslation("search.advanced.season"));
    seasonSelect.setItems(SeasonHint.values());
    seasonSelect.setItemLabelGenerator(s -> s == null
        ? getTranslation("search.filter.any") : s.name());
    seasonSelect.setPlaceholder(getTranslation("search.filter.any"));

    categorySelect.setLabel(getTranslation("search.advanced.category"));
    categorySelect.setItems(SourceCategory.values());
    categorySelect.setItemLabelGenerator(c -> c == null
        ? getTranslation("search.filter.any") : c.name());
    categorySelect.setPlaceholder(getTranslation("search.filter.any"));

    privacySelect.setLabel(getTranslation("search.advanced.privacy"));
    privacySelect.setItems(RiskLevel.values());
    privacySelect.setItemLabelGenerator(r -> r == null
        ? getTranslation("search.filter.any") : r.name());
    privacySelect.setPlaceholder(getTranslation("search.filter.any"));

    // Score threshold — minimum cosine similarity for a result to appear
    scoreField.setLabel(getTranslation("search.advanced.threshold"));
    scoreField.setMin(0.0);
    scoreField.setMax(1.0);
    scoreField.setStep(0.05);
    scoreField.setValue(0.45);  // matches server default MIN_SCORE = 0.45
    scoreField.setWidth("130px");

    // "Refine Search" — skip LLM and use the edited plan directly
    refineBtn.setText(getTranslation("search.advanced.refine"));
    refineBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
    refineBtn.addClickListener(e -> triggerRefine());

    HorizontalLayout boolRow = new HorizontalLayout(personSelect, vehicleSelect, plateSelect);
    boolRow.setSpacing(true);

    HorizontalLayout enumRow = new HorizontalLayout(seasonSelect, categorySelect, privacySelect, scoreField);
    enumRow.setSpacing(true);

    VerticalLayout content = new VerticalLayout(embeddingArea, boolRow, enumRow, refineBtn);
    content.setSpacing(true);
    content.setPadding(false);

    advancedSection.setSummaryText(getTranslation("search.advanced.header"));
    advancedSection.add(content);
    advancedSection.setOpened(false);
  }

  private void configureBoolSelect(Select<String> sel, String label) {
    sel.setLabel(label);
    sel.setItems(BOOL_ANY, BOOL_YES, BOOL_NO);
    sel.setItemLabelGenerator(v -> switch (v) {
      case BOOL_YES -> getTranslation("search.yes");
      case BOOL_NO  -> getTranslation("search.no");
      default       -> getTranslation("search.filter.any");
    });
    sel.setValue(BOOL_ANY);
  }

  // -------------------------------------------------------------------------
  // View mode toggle callback
  // -------------------------------------------------------------------------

  private void onTileModeChanged(boolean tiles) {
    tileMode = tiles;
    if (!lastResults.isEmpty()) {
      resultsGrid.setVisible(!tileMode);
      tileContainer.setVisible(tileMode);
      if (tileMode) {
        renderTiles(lastResults);
      }
    }
  }

  // -------------------------------------------------------------------------
  // Polling lifecycle
  // -------------------------------------------------------------------------

  @Override
  protected void onAttach(AttachEvent event) {
    super.onAttach(event);
    pollRegistration = event.getUI().addPollListener(e -> onPoll());
  }

  @Override
  protected void onDetach(DetachEvent event) {
    super.onDetach(event);
    if (pollRegistration != null) {
      pollRegistration.remove();
      pollRegistration = null;
    }
    event.getUI().setPollInterval(-1);
    // The shared search executor is NOT shut down here — it is managed by ServiceRegistry.
  }

  // -------------------------------------------------------------------------
  // Search triggering — three entry points, one shared implementation
  // -------------------------------------------------------------------------

  /**
   * TRANSFORM ONLY — runs LLM query understanding and stops before vector search.
   * The user can review the transformation in the inspector, then choose to search
   * or further edit the parameters.
   */
  private void triggerTransformOnly() {
    String query = queryField.getValue();
    if (query == null || query.isBlank()) {
      Notification.show(getTranslation("search.error.empty"), 3000, Notification.Position.MIDDLE);
      return;
    }
    startSearch(query, null, true);
  }

  /**
   * TRANSFORM AND EXECUTE — runs the full pipeline: LLM understanding then vector search.
   */
  private void triggerSearchAndExecute() {
    String query = queryField.getValue();
    if (query == null || query.isBlank()) {
      Notification.show(getTranslation("search.error.empty"), 3000, Notification.Position.MIDDLE);
      return;
    }
    startSearch(query, null, false);
  }

  /**
   * REFINE — skips LLM and executes vector search with the user-edited parameters
   * from the Advanced panel.  Does not add a history entry (refinements are not
   * separate search sessions).
   */
  private void triggerRefine() {
    if (currentPlan == null) {
      return;
    }
    SearchPlan edited = collectPlanFromAdvancedPanel();
    startSearch(edited.getOriginalQuery(), edited, false);
  }

  /**
   * Common search entry point.
   *
   * @param query         the original user query (used for history persistence)
   * @param prebuiltPlan  when non-null, skip LLM and use this plan directly (Refine path)
   * @param transformOnly when true, stop after LLM analysis and skip vector search
   */
  private void startSearch(String query, SearchPlan prebuiltPlan, boolean transformOnly) {
    inspector.reset();
    inspector.setQueryInput(query != null ? query : "");

    SearchRunState state = new SearchRunState();
    state.originalQuery  = (prebuiltPlan == null) ? query : null; // only persist on fresh searches
    state.transformOnly  = transformOnly;
    state.mode           = transformOnly ? SearchMode.TRANSFORM_ONLY
                                        : SearchMode.TRANSFORM_AND_EXECUTE;
    state.minScore       = effectiveThreshold();
    currentSearch = state;

    setAllButtonsEnabled(false);
    progressBar.setVisible(true);
    statusLabel.setText(getTranslation("search.status.init"));
    statusLabel.setVisible(true);
    resultsGrid.setVisible(false);
    tileContainer.setVisible(false);
    viewToggle.setVisible(false);

    getUI().ifPresent(ui -> ui.setPollInterval(POLL_INTERVAL_MS));

    if (prebuiltPlan != null) {
      ServiceRegistry.getInstance().getSearchExecutor()
          .submit(() -> runSearchWithPlan(state, prebuiltPlan));
    } else {
      ServiceRegistry.getInstance().getSearchExecutor()
          .submit(() -> runSearch(state, query));
    }
  }

  /**
   * Collects the current values from the Advanced panel editable fields and returns a
   * new {@link SearchPlan} (preserving {@code originalQuery} from {@link #currentPlan}).
   */
  private SearchPlan collectPlanFromAdvancedPanel() {
    SearchPlan p = new SearchPlan();
    p.setOriginalQuery(currentPlan != null ? currentPlan.getOriginalQuery() : queryField.getValue());
    p.setEmbeddingText(embeddingArea.getValue().isBlank()
                       ? (currentPlan != null ? currentPlan.getEmbeddingText() : queryField.getValue())
                       : embeddingArea.getValue().trim());
    p.setContainsPerson(boolFromSelect(personSelect));
    p.setContainsVehicle(boolFromSelect(vehicleSelect));
    p.setContainsLicensePlate(boolFromSelect(plateSelect));
    p.setSeasonHint(seasonSelect.getValue());
    p.setSourceCategory(categorySelect.getValue());
    p.setPrivacyLevel(privacySelect.getValue());
    p.setExplanation(currentPlan != null ? currentPlan.getExplanation() : null);
    p.setMinScore(effectiveThreshold());
    return p;
  }

  private Boolean boolFromSelect(Select<String> sel) {
    return switch (sel.getValue()) {
      case BOOL_YES -> Boolean.TRUE;
      case BOOL_NO  -> Boolean.FALSE;
      default       -> null;
    };
  }

  // -------------------------------------------------------------------------
  // Background search tasks (run on shared executor thread)
  // -------------------------------------------------------------------------

  /**
   * Full flow: LLM query analysis then (optionally) vector search.
   * Sets {@link SearchRunState#stepProgress} at each stage so the polling handler
   * can update the inspector in real time.
   */
  private void runSearch(SearchRunState state, String query) {
    try {
      state.status = "search.status.analyzing";
      state.stepProgress = 1;  // LLM analysis started
      logger().info("Starting query understanding for: {}", query);
      SearchPlan plan = ServiceRegistry.getInstance()
          .getQueryUnderstandingService()
          .understand(query);
      state.plan = plan;
      state.stepProgress = 2;  // LLM analysis done
      logger().info("Query plan: embedding='{}', season={}, category={}",
                    plan.getEmbeddingText(), plan.getSeasonHint(), plan.getSourceCategory());

      if (state.transformOnly) {
        // Stop here — vector search intentionally skipped
        logger().info("Transform Only mode: stopping after LLM analysis");
        return;
      }

      // Apply the user-selected score threshold before executing the search
      plan.setMinScore(state.minScore);

      state.status = "search.status.vector";
      state.stepProgress = 3;  // search execution started
      List<SearchResultItem> results = ServiceRegistry.getInstance()
          .getSearchService()
          .search(plan);
      state.results = results;
      state.stepProgress = 4;  // vector search done
      logger().info("Search returned {} results", results.size());
    } catch (Exception ex) {
      logger().error("Search failed: {}", ex.getMessage(), ex);
      state.error = ex.getMessage();
    } finally {
      state.done = true;
    }
  }

  /**
   * Refined flow: skip LLM, use the supplied plan directly, always executes vector search.
   */
  private void runSearchWithPlan(SearchRunState state, SearchPlan plan) {
    try {
      // LLM was skipped; mark steps 1-2 as if completed so inspector shows them correctly
      state.status = "search.status.vector";
      state.plan = plan;
      state.stepProgress = 2;  // plan is already available (user edited it)
      logger().info("Refine search with edited plan: embedding='{}'", plan.getEmbeddingText());
      state.stepProgress = 3;
      List<SearchResultItem> results = ServiceRegistry.getInstance()
          .getSearchService()
          .search(plan);
      state.results = results;
      state.stepProgress = 4;
      logger().info("Refine search returned {} results", results.size());
    } catch (Exception ex) {
      logger().error("Refine search failed: {}", ex.getMessage(), ex);
      state.error = ex.getMessage();
    } finally {
      state.done = true;
    }
  }

  // -------------------------------------------------------------------------
  // Poll handler — runs on the Vaadin UI thread
  // -------------------------------------------------------------------------

  private void onPoll() {
    SearchRunState s = currentSearch;
    if (s == null) {
      return;
    }

    // Live inspector step updates (idempotent — safe to call every 800 ms)
    int p = s.stepProgress;
    if (p >= 1) {
      inspector.setLlmActive();
    }
    if (p >= 2 && s.plan != null) {
      inspector.setLlmCompleted(s.plan);
    }
    if (p >= 3) {
      inspector.setSearchPrepActive();
    }

    statusLabel.setText(getTranslation(s.status));
    if (!s.done) {
      return;
    }

    // ── Search finished ────────────────────────────────────────────────────
    getUI().ifPresent(ui -> ui.setPollInterval(-1));
    currentSearch = null;
    progressBar.setVisible(false);
    statusLabel.setVisible(false);
    setAllButtonsEnabled(true);

    if (s.error != null) {
      Notification n = Notification.show(
          getTranslation("search.error.failed", s.error), 5000, Notification.Position.MIDDLE);
      n.addThemeVariants(NotificationVariant.LUMO_ERROR);
      inspector.setFailed(s.error, s.stepProgress);
      return;
    }

    // Final inspector states
    if (p >= 4 && s.results != null) {
      inspector.setSearchCompleted(s.results.size(), s.minScore);
    }
    if (s.transformOnly) {
      inspector.skipExecutionSteps();
    }

    // Populate advanced panel with editable plan values
    if (s.plan != null) {
      currentPlan = s.plan;
      populateAdvancedPanel(s.plan);
    }

    // Persist to history after completion — captures original + final LLM query.
    // Only fresh searches (not refinements) are stored; originalQuery is null for refine.
    if (s.originalQuery != null && !s.originalQuery.isBlank()) {
      String finalQuery = s.plan != null ? s.plan.getEmbeddingText() : null;
      ServiceRegistry.getInstance().getPersistenceService()
          .addRecentSearch(s.originalQuery, finalQuery, s.mode);
      refreshRecentChips();
    }

    // Show results only when the full search pipeline ran
    if (!s.transformOnly) {
      renderResults(s.results != null ? s.results : List.of());
    }
  }

  // -------------------------------------------------------------------------
  // Advanced panel population
  // -------------------------------------------------------------------------

  private void populateAdvancedPanel(SearchPlan plan) {
    embeddingArea.setValue(plan.getEmbeddingText() != null ? plan.getEmbeddingText() : "");
    personSelect.setValue(boolToSelect(plan.getContainsPerson()));
    vehicleSelect.setValue(boolToSelect(plan.getContainsVehicle()));
    plateSelect.setValue(boolToSelect(plan.getContainsLicensePlate()));
    seasonSelect.setValue(plan.getSeasonHint());
    categorySelect.setValue(plan.getSourceCategory());
    privacySelect.setValue(plan.getPrivacyLevel());
    scoreField.setValue(plan.getMinScore() != null ? plan.getMinScore() : 0.45);
    advancedSection.setOpened(true);
  }

  private String boolToSelect(Boolean value) {
    if (Boolean.TRUE.equals(value))  return BOOL_YES;
    if (Boolean.FALSE.equals(value)) return BOOL_NO;
    return BOOL_ANY;
  }

  /**
   * Reads the score threshold from the UI field, clamps it to [0.0, 1.0], and
   * falls back to 0.45 (the server default) when the field is empty.
   */
  private double effectiveThreshold() {
    Double raw = scoreField.getValue();
    if (raw == null) return 0.45;
    return Math.max(0.0, Math.min(1.0, raw));
  }

  private void setAllButtonsEnabled(boolean enabled) {
    transformBtn.setEnabled(enabled);
    searchBtn.setEnabled(enabled);
    refineBtn.setEnabled(enabled);
  }

  // -------------------------------------------------------------------------
  // Grid configuration
  // -------------------------------------------------------------------------

  private void configureResultsGrid() {
    resultsGrid.setWidthFull();
    // Do not call setHeightFull() — the grid auto-sizes with its content so the page
    // can scroll naturally (goal L: results area fully usable, no squeezing).
    resultsGrid.setAllRowsVisible(true);
    resultsGrid.setVisible(false);

    // Thumbnail — 90px height, 110px column width (goal N: larger thumbnails)
    resultsGrid.addComponentColumn(r -> buildResultThumb(r, "90px"))
        .setHeader(getTranslation("search.col.preview"))
        .setWidth("110px").setFlexGrow(0);

    resultsGrid.addColumn(r -> String.format("%.3f", r.getScore()))
        .setHeader(getTranslation("search.col.score")).setWidth("80px").setFlexGrow(0)
        .setSortable(true);

    // Filename (goal M) and Description (goal M) columns removed.

    resultsGrid.addColumn(r -> r.getSourceCategory() != null ? r.getSourceCategory().name() : "\u2014")
        .setHeader(getTranslation("search.col.category")).setFlexGrow(1);

    resultsGrid.addColumn(r -> r.getSeasonHint() != null ? r.getSeasonHint().name() : "\u2014")
        .setHeader(getTranslation("search.col.season")).setFlexGrow(1);

    resultsGrid.addComponentColumn(r -> {
      Boolean pp = r.getContainsPerson();
      return new Span(pp == null ? "\u2014" : (pp ? getTranslation("search.yes")
                                                  : getTranslation("search.no")));
    }).setHeader(getTranslation("search.col.persons")).setFlexGrow(1);

    // Risk as icon (goal O: icons instead of text badges in table)
    resultsGrid.addComponentColumn(r -> riskIcon(r.getRiskLevel()))
        .setHeader(getTranslation("search.col.risk")).setWidth("70px").setFlexGrow(0);

    resultsGrid.addComponentColumn(r -> {
      Button btn = new Button(getTranslation("search.col.details"));
      btn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
      btn.addClickListener(e -> openDetailDialog(r));
      return btn;
    }).setHeader("").setFlexGrow(1);
  }

  // -------------------------------------------------------------------------
  // Results rendering
  // -------------------------------------------------------------------------

  private void renderResults(List<SearchResultItem> results) {
    lastResults = results;
    if (results.isEmpty()) {
      Notification.show(getTranslation("search.notfound"), 3000, Notification.Position.MIDDLE);
      resultsGrid.setVisible(false);
      tileContainer.setVisible(false);
      viewToggle.setVisible(false);
    } else {
      viewToggle.setVisible(true);
      if (tileMode) {
        renderTiles(results);
        tileContainer.setVisible(true);
        resultsGrid.setVisible(false);
      } else {
        resultsGrid.setItems(results);
        resultsGrid.setVisible(true);
        tileContainer.setVisible(false);
      }
      Notification n = Notification.show(
          getTranslation("search.found", results.size()), 3000, Notification.Position.BOTTOM_END);
      n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }
  }

  // -------------------------------------------------------------------------
  // Tile view for search results — fixed-height cards
  // -------------------------------------------------------------------------

  private void renderTiles(List<SearchResultItem> results) {
    tileContainer.removeAll();
    for (int i = 0; i < results.size(); i++) {
      tileContainer.add(buildResultTile(results.get(i), i + 1));
    }
  }

  private Div buildResultTile(SearchResultItem r, int rank) {
    Div card = new Div();
    // Fixed height: all cards stay the same size regardless of content length.
    // The info section below the thumbnail scrolls if its content overflows.
    card.getStyle()
        .set("border", "1px solid var(--lumo-contrast-10pct)")
        .set("border-radius", "var(--lumo-border-radius-l)")
        .set("overflow", "hidden")
        .set("background", "var(--lumo-base-color)")
        .set("display", "flex")
        .set("flex-direction", "column")
        .set("height", "360px")
        .set("cursor", "default");

    card.getElement().addEventListener("dblclick", e -> openDetailDialog(r));

    // Thumbnail — fixed height, does not shrink
    Div thumbWrapper = new Div(buildResultThumb(r, "150px"));
    thumbWrapper.getStyle()
        .set("background", "var(--lumo-contrast-5pct)")
        .set("display", "flex")
        .set("align-items", "center")
        .set("justify-content", "center")
        .set("height", "150px")
        .set("flex-shrink", "0")
        .set("overflow", "hidden");
    card.add(thumbWrapper);

    // Info section — takes remaining height, scrolls on overflow
    Div info = new Div();
    info.getStyle()
        .set("padding", "0.5rem")
        .set("display", "flex")
        .set("flex-direction", "column")
        .set("gap", "0.25rem")
        .set("flex", "1")
        .set("min-height", "0")
        .set("overflow-y", "auto");

    // Rank badge
    Span rankBadge = new Span("#" + rank + "  " + String.format("%.3f", r.getScore()));
    rankBadge.getElement().getThemeList().add("badge");
    if (rank == 1) {
      rankBadge.getElement().getThemeList().add("success");
    } else {
      rankBadge.getElement().getThemeList().add("contrast");
    }
    info.add(rankBadge);

    // Category — high-signal compact label
    if (r.getSourceCategory() != null) {
      Span catSpan = new Span(r.getSourceCategory().name());
      catSpan.getStyle()
          .set("font-size", "var(--lumo-font-size-xs)")
          .set("color", "var(--lumo-secondary-text-color)");
      info.add(catSpan);
    }

    // Season hint — compact context
    if (r.getSeasonHint() != null) {
      Span seasonSpan = new Span(r.getSeasonHint().name());
      seasonSpan.getStyle()
          .set("font-size", "var(--lumo-font-size-xs)")
          .set("color", "var(--lumo-secondary-text-color)");
      info.add(seasonSpan);
    }

    // Risk icon
    info.add(riskIcon(r.getRiskLevel()));

    // Details button (filename and description available in dialog)
    Button detailBtn = new Button(getTranslation("search.col.details"));
    detailBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);
    detailBtn.addClickListener(e -> openDetailDialog(r));
    info.add(detailBtn);

    card.add(info);
    return card;
  }

  // -------------------------------------------------------------------------
  // Risk iconography
  // -------------------------------------------------------------------------

  private com.vaadin.flow.component.Component riskIcon(RiskLevel risk) {
    if (risk == null) {
      Span dash = new Span("\u2014");
      dash.getStyle().set("font-size", "var(--lumo-font-size-xs)");
      return dash;
    }
    Icon icon = switch (risk) {
      case SAFE      -> VaadinIcon.CHECK_CIRCLE.create();
      case REVIEW    -> VaadinIcon.QUESTION_CIRCLE.create();
      case SENSITIVE -> VaadinIcon.EXCLAMATION_CIRCLE_O.create();
    };
    String color = switch (risk) {
      case SAFE      -> "var(--lumo-success-color)";
      case REVIEW    -> "var(--lumo-warning-color, orange)";
      case SENSITIVE -> "var(--lumo-error-color)";
    };
    icon.setSize("16px");
    icon.setColor(color);
    icon.getElement().setAttribute("title",
        getTranslation("overview.risk.tooltip." + risk.name().toLowerCase()));
    return icon;
  }

  // -------------------------------------------------------------------------
  // Thumbnail helpers
  // -------------------------------------------------------------------------

  private com.vaadin.flow.component.Component buildResultThumb(SearchResultItem r, String height) {
    try {
      PersistenceService ps = ServiceRegistry.getInstance().getPersistenceService();
      ImageAsset asset = ps.findImage(r.getImageId()).orElse(null);
      if (asset == null) return new Span("\u2014");

      PreviewService previews = ServiceRegistry.getInstance().getPreviewService();
      Path originalPath = ServiceRegistry.getInstance()
          .getImageStorageService().resolvePath(asset.getId());
      if (!Files.exists(originalPath)) return new Span("\u2014");

      StreamResource res = previews.getTilePreview(asset.getId(), originalPath,
                                                   asset.getStoredFilename());
      if (res == null) {
        res = new StreamResource(asset.getStoredFilename(), () -> {
          try {
            return Files.newInputStream(originalPath);
          } catch (Exception ex) {
            return InputStream.nullInputStream();
          }
        });
      }

      Image thumb = new Image(res, asset.getOriginalFilename());
      thumb.setHeight(height);
      thumb.setWidth("100%");
      thumb.getStyle().set("object-fit", "cover").set("border-radius", "4px");
      return thumb;
    } catch (Exception e) {
      logger().debug("Could not load thumbnail for search result {}", r.getImageId(), e);
    }
    return new Span("\u2014");
  }

  private void openDetailDialog(SearchResultItem result) {
    PersistenceService ps = ServiceRegistry.getInstance().getPersistenceService();
    ImageAsset asset = ps.findImage(result.getImageId()).orElse(null);
    if (asset == null) {
      Notification.show(getTranslation("search.image.notfound"), 3000, Notification.Position.MIDDLE);
      return;
    }
    SemanticAnalysis analysis  = ps.findAnalysis(result.getImageId()).orElse(null);
    SensitivityAssessment assm = ps.findAssessment(result.getImageId()).orElse(null);
    LocationSummary location   = ps.findLocation(result.getImageId()).orElse(null);
    new DetailDialog(asset, analysis, assm, location).open();
  }

  // -------------------------------------------------------------------------
  // Shared volatile search run state
  // -------------------------------------------------------------------------

  /**
   * Volatile holder for the in-progress background search.
   *
   * <p>Written by the background executor thread; read by the Vaadin UI polling handler.
   * All fields are {@code volatile} to ensure visibility across threads without locking.
   * {@link #stepProgress} drives real-time inspector updates during polling.
   */
  private static class SearchRunState {
    /** i18n key for the current status label (e.g. "search.status.analyzing"). */
    volatile String                 status       = "search.status.init";
    volatile SearchPlan             plan         = null;
    volatile List<SearchResultItem> results      = null;
    volatile String                 error        = null;
    volatile boolean                done         = false;
    /** Whether this run stops after LLM analysis (no vector search). */
    volatile boolean                transformOnly = false;
    volatile SearchMode             mode         = SearchMode.TRANSFORM_AND_EXECUTE;
    /** Original user query; null for refine runs (which are not persisted to history). */
    volatile String                 originalQuery = null;
    /**
     * Effective score threshold used for this search run.
     * Copied from the UI scoreField at the moment the run starts; passed to the
     * search plan so the service applies exactly the threshold the user configured.
     */
    volatile double                 minScore = 0.45;
    /**
     * Step progress counter for live inspector updates.
     * 0 = starting, 1 = LLM started, 2 = LLM done, 3 = search started, 4 = search done.
     */
    volatile int                    stepProgress = 0;
  }
}
