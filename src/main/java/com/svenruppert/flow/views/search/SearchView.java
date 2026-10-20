package com.svenruppert.flow.views.search;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.MainLayout;
import com.svenruppert.flow.views.shared.ViewServices;
import com.svenruppert.imagerag.domain.SearchPlan;
import com.svenruppert.imagerag.domain.enums.SearchMode;
import com.svenruppert.imagerag.dto.SearchResult;
import com.svenruppert.imagerag.settings.Settings;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Search Workbench — semantic image search with step-by-step transparency.
 * <h3>Two explicit modes</h3>
 * <ul>
 *   <li><b>Transform Only</b> — runs LLM query understanding and stops.  The user can
 *       inspect the transformation result in the {@link SearchInspectorComponent} and
 *       optionally adjust the derived parameters in the {@link SearchPlanEditor} before
 *       executing.</li>
 *   <li><b>Search</b> — full pipeline: LLM understanding → vector search → results.</li>
 * </ul>
 * <h3>Iterative workflow</h3>
 * <ol>
 *   <li>Enter a natural language query.</li>
 *   <li>Click <em>Transform Only</em> to see how the system interprets the query.</li>
 *   <li>Optionally edit the derived parameters in the Advanced panel.</li>
 *   <li>Click <em>Search</em> (or <em>Refine Search</em> if parameters were edited)
 *       to run the actual search.</li>
 * </ol>
 * <h3>Components</h3>
 * {@link RecentSearchBar} (history chips), {@link SearchPlanEditor} (advanced panel),
 * {@link SearchResultsPanel} (facets, grid and tiles) and {@link SearchActionBar}
 * (save, load, CSV export).
 * <p>History is persisted <em>after</em> each run completes so that the entry always
 * captures the original query together with the LLM-transformed final query.
 * <p>Background searches are pushed to the UI via {@code ui.access()} (Push(AUTOMATIC)
 * in {@link com.svenruppert.flow.AppShell}). While a search is in flight a server-side
 * scheduler ticks every {@link #POLL_INTERVAL_MS} ms and calls {@link #onPoll()} on the
 * UI thread to reflect intermediate inspector state.
 */
@Route(value = SearchView.PATH, layout = MainLayout.class)
public class SearchView
    extends VerticalLayout
    implements HasLogger, HasDynamicTitle {

  public static final String PATH = "search";

  private static final int POLL_INTERVAL_MS = 800;

  private final ViewServices services;

  private final TextArea queryField = new TextArea();
  /**
   * Runs only LLM query transformation; skips vector search.
   */
  private final Button transformBtn = new Button();
  /**
   * Runs the full pipeline: LLM transformation + vector search + results.
   */
  private final Button searchBtn = new Button();
  private final SearchInspectorComponent inspector = new SearchInspectorComponent();
  private final ProgressBar progressBar = new ProgressBar();
  private final Span statusLabel = new Span();
  private final RecentSearchBar recentSearchBar;
  private final SearchPlanEditor planEditor;
  private final SearchResultsPanel resultsPanel;

  private SearchPlan currentPlan = null;
  private volatile SearchRunState currentSearch;

  // ── Push-driven UI refresh scheduler (replaces former client-side polling) ──
  private ScheduledExecutorService progressScheduler;
  private ScheduledFuture<?> progressTask;

  public SearchView() {
    this.services = ViewServices.current();
    this.recentSearchBar = new RecentSearchBar(services.searchHistory(), this::loadQueryForEditing);
    this.planEditor = new SearchPlanEditor(services.settings().get(Settings.SEARCH_MIN_SCORE),
                                           this::triggerRefine);
    this.resultsPanel = new SearchResultsPanel(services.catalog(), services.imagePreviews());

    // Natural page scroll — do NOT call setSizeFull() so the page can grow with content.
    setWidthFull();
    setPadding(true);
    setSpacing(true);

    add(new H2(getTranslation("search.title")));
    add(new Paragraph(getTranslation("search.description")));

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

    progressBar.setIndeterminate(true);
    progressBar.setWidthFull();
    progressBar.setVisible(false);
    statusLabel.getStyle()
        .set("font-size", "var(--lumo-font-size-s)")
        .set("color", "var(--lumo-secondary-text-color)");
    statusLabel.setVisible(false);

    // Left column: search input → history → advanced → progress
    VerticalLayout leftCol = new VerticalLayout(
        searchBar, recentSearchBar, planEditor, progressBar, statusLabel);
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

    HorizontalLayout topArea = new HorizontalLayout(leftCol, rightCol);
    topArea.setWidthFull();
    topArea.setAlignItems(Alignment.START);
    topArea.setSpacing(true);
    add(topArea);

    // Results area — full width, below the two columns
    add(resultsPanel);

    add(new SearchActionBar(services.searchHistory(),
                            queryField::getValue,
                            () -> currentPlan,
                            resultsPanel::lastResults,
                            this::runSavedQuery));
  }

  private void loadQueryForEditing(String query) {
    queryField.setValue(query);
    queryField.focus();
  }

  private void runSavedQuery(String query) {
    queryField.setValue(query);
    triggerSearchAndExecute();
  }

  // -------------------------------------------------------------------------
  // Progress push lifecycle
  // -------------------------------------------------------------------------

  @Override
  protected void onAttach(AttachEvent event) {
    super.onAttach(event);
    // No client-side polling anymore: runSearch() pushes state changes directly
    // via ui.access() (see startProgressPush()). Relies on @Push(AUTOMATIC).
  }

  @Override
  protected void onDetach(DetachEvent event) {
    super.onDetach(event);
    stopProgressPush();
    // The shared search executor is NOT shut down here — it is managed by ServiceRegistry.
  }

  /**
   * Starts the server-side scheduler that calls {@link #onPoll()} via
   * {@code ui.access()} while a search is in flight.
   */
  private void startProgressPush() {
    stopProgressPush();
    getUI().ifPresent(ui -> {
      progressScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "search-view-progress");
        t.setDaemon(true);
        return t;
      });
      progressTask = progressScheduler.scheduleAtFixedRate(
          () -> ui.access(this::onPoll),
          POLL_INTERVAL_MS, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
    });
  }

  private void stopProgressPush() {
    if (progressTask != null) {
      progressTask.cancel(false);
      progressTask = null;
    }
    if (progressScheduler != null) {
      // shutdown(), not shutdownNow(): onPoll() calls this method on the scheduler's own
      // thread, and interrupting that thread makes the following EclipseStore write fail.
      progressScheduler.shutdown();
      progressScheduler = null;
    }
  }

  // -------------------------------------------------------------------------
  // Search triggering — three entry points, one shared implementation
  // -------------------------------------------------------------------------

  /**
   * TRANSFORM ONLY — runs LLM query understanding and stops before vector search.
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
   * REFINE — skips LLM and executes vector search with the user-edited parameters.
   * Does not add a history entry (refinements are not separate search sessions).
   */
  private void triggerRefine() {
    if (currentPlan == null) {
      return;
    }
    SearchPlan edited = planEditor.collect(currentPlan, queryField.getValue());
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
    state.originalQuery = (prebuiltPlan == null) ? query : null; // only persist on fresh searches
    state.transformOnly = transformOnly;
    state.mode = transformOnly ? SearchMode.TRANSFORM_ONLY
        : SearchMode.TRANSFORM_AND_EXECUTE;
    state.minScore = planEditor.effectiveThreshold();
    currentSearch = state;

    setAllButtonsEnabled(false);
    progressBar.setVisible(true);
    statusLabel.setText(getTranslation("search.status.init"));
    statusLabel.setVisible(true);
    resultsPanel.hideResults();

    startProgressPush();

    if (prebuiltPlan != null) {
      services.searchExecutor()
          .submit(() -> runSearchWithPlan(state, prebuiltPlan));
    } else {
      services.searchExecutor()
          .submit(() -> runSearch(state, query));
    }
  }

  // -------------------------------------------------------------------------
  // Background search tasks (run on shared executor thread)
  // -------------------------------------------------------------------------

  /**
   * Full flow: LLM query analysis then (optionally) vector search.
   * Sets {@link SearchRunState#stepProgress} at each stage so the progress handler
   * can update the inspector in real time.
   */
  private void runSearch(SearchRunState state, String query) {
    try {
      state.status = "search.status.analyzing";
      state.stepProgress = 1;  // LLM analysis started
      logger().info("Starting query understanding for: {}", query);
      SearchPlan plan = services
          .queryUnderstanding()
          .understand(query);
      state.plan = plan;
      state.stepProgress = 2;  // LLM analysis done
      logger().info("Query plan: embedding='{}', season={}, categoryGroup={}",
                    plan.getEmbeddingText(), plan.getSeasonHint(), plan.getCategoryGroup());

      if (state.transformOnly) {
        // Stop here — vector search intentionally skipped
        logger().info("Transform Only mode: stopping after LLM analysis");
        return;
      }

      // Apply the user-selected score threshold before executing the search
      plan.setMinScore(state.minScore);

      state.status = "search.status.vector";
      state.stepProgress = 3;  // search execution started
      SearchResult sr = services
          .search()
          .search(plan);
      state.results = sr.items();
      state.vectorCandidates = sr.vectorCandidates();
      state.keywordCandidates = sr.keywordCandidates();
      state.stepProgress = 4;  // vector search done
      logger().info("Search returned {} results (vec={}, bm25={})",
                    sr.items().size(), sr.vectorCandidates(), sr.keywordCandidates());
    } catch (RuntimeException ex) {
      // Worker-thread boundary: the failure is reported to the UI through the poll state.
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
      SearchResult sr = services
          .search()
          .search(plan);
      state.results = sr.items();
      state.vectorCandidates = sr.vectorCandidates();
      state.keywordCandidates = sr.keywordCandidates();
      state.stepProgress = 4;
      logger().info("Refine search returned {} results (vec={}, bm25={})",
                    sr.items().size(), sr.vectorCandidates(), sr.keywordCandidates());
    } catch (RuntimeException ex) {
      // Worker-thread boundary: the failure is reported to the UI through the poll state.
      logger().error("Refine search failed: {}", ex.getMessage(), ex);
      state.error = ex.getMessage();
    } finally {
      state.done = true;
    }
  }

  // -------------------------------------------------------------------------
  // Progress handler — runs on the Vaadin UI thread
  // -------------------------------------------------------------------------

  private void onPoll() {
    SearchRunState s = currentSearch;
    if (s == null) {
      return;
    }

    // Live inspector step updates (idempotent — safe to call on every tick)
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
    stopProgressPush();
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

    if (p >= 4 && s.results != null) {
      inspector.setSearchCompleted(s.results.size(), s.minScore,
                                   s.vectorCandidates, s.keywordCandidates);
    }
    if (s.transformOnly) {
      inspector.skipExecutionSteps();
    }

    // Populate advanced panel with editable plan values
    if (s.plan != null) {
      currentPlan = s.plan;
      planEditor.populate(s.plan);
    }

    // Show results only when the full search pipeline ran
    if (!s.transformOnly) {
      resultsPanel.showResults(s.results != null ? s.results : List.of());
    }

    // Persist to history after completion — captures original + final LLM query.
    // Only fresh searches (not refinements) are stored; originalQuery is null for refine.
    if (s.originalQuery != null && !s.originalQuery.isBlank()) {
      String finalQuery = s.plan != null ? s.plan.getEmbeddingText() : null;
      try {
        services.searchHistory()
            .addRecentSearch(s.originalQuery, finalQuery, s.mode);
        recentSearchBar.refresh();
      } catch (RuntimeException e) {
        // The history is a convenience; a failed write must not hide the search results.
        logger().warn("Could not store search in history: {}", e.getMessage(), e);
      }
    }
  }

  private void setAllButtonsEnabled(boolean enabled) {
    transformBtn.setEnabled(enabled);
    searchBtn.setEnabled(enabled);
    planEditor.setRefineEnabled(enabled);
  }

  @Override
  public String getPageTitle() {
    return getTranslation("search.page.title");
  }
}
