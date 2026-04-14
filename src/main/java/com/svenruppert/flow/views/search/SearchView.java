package com.svenruppert.flow.views.search;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.MainLayout;
import com.svenruppert.flow.views.detail.DetailDialog;
import com.svenruppert.flow.views.shared.ViewModeToggle;
import com.svenruppert.imagerag.bootstrap.ServiceRegistry;
import com.svenruppert.imagerag.domain.*;
import com.svenruppert.imagerag.domain.enums.RiskLevel;
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
 * Semantic image search view.
 *
 * <p>Features:
 * <ul>
 *   <li>Simple search bar for natural language queries.</li>
 *   <li>Recent search history: chips rendered below the search bar let the user
 *       repeat any of the last {@code N} queries with a single click.</li>
 *   <li>Collapsible <em>Advanced Parameters</em> panel: shows the LLM-derived search
 *       plan after a first search and lets the user edit individual fields before
 *       re-running (skipping LLM re-analysis).</li>
 *   <li>Collapsible <em>Search Transparency</em> panel after results arrive: shows
 *       original query, final embedding text, applied filters, and result count.</li>
 *   <li>Results in table or tile/card view with cached thumbnail previews.
 *       The view toggle is a segmented control ({@link ViewModeToggle}).</li>
 *   <li>Tile cards show a rank badge (#1 / #2 / …), category, and a risk icon.</li>
 *   <li>Double-click on a tile opens the detail dialog.</li>
 * </ul>
 *
 * <p>Background searches use client-side polling (no {@code @Push} required).
 */
@PageTitle("Search Images")
@Route(value = SearchView.PATH, layout = MainLayout.class)
public class SearchView
    extends VerticalLayout
    implements HasLogger {

  public static final String PATH = "search";

  private static final int POLL_INTERVAL_MS = 800;

  // ── Background search execution ────────────────────────────────────────────
  // Uses the shared search executor from ServiceRegistry so that the
  // ProcessingSettings.searchParallelism setting is actually effective.
  // Do NOT hold a local executor reference — the shared pool is sized and
  // managed by ServiceRegistry.updateSearchParallelism().

  // ── Primary search bar ────────────────────────────────────────────────────
  private final TextArea queryField   = new TextArea();
  private final Button   searchButton = new Button();

  // ── Recent search history ─────────────────────────────────────────────────
  private final Div recentSearchBar = new Div();

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
  private final Button                 refineBtn        = new Button();

  // ── Search transparency panel ─────────────────────────────────────────────
  private final Details        transparencySection = new Details();
  private final VerticalLayout transparencyContent = new VerticalLayout();

  // ── Results ───────────────────────────────────────────────────────────────
  private final Grid<SearchResultItem> resultsGrid = new Grid<>(SearchResultItem.class, false);
  private final Div tileContainer = new Div();
  private boolean tileMode = false;
  private List<SearchResultItem> lastResults = List.of();

  // ── Runtime state ─────────────────────────────────────────────────────────
  /**
   * The last derived (or user-edited) search plan.
   * Written in the Vaadin UI thread only; safe to read from the same thread.
   */
  private SearchPlan currentPlan = null;
  private volatile SearchRunState currentSearch;
  private Registration pollRegistration;

  // ── Boolean select values ─────────────────────────────────────────────────
  private static final String BOOL_ANY = "any";
  private static final String BOOL_YES = "yes";
  private static final String BOOL_NO  = "no";

  public SearchView() {
    setSizeFull();
    setPadding(true);
    setSpacing(true);

    add(new H2(getTranslation("search.title")));
    add(new Paragraph(getTranslation("search.description")));

    // ── Search bar ─────────────────────────────────────────────────────────
    queryField.setLabel(getTranslation("search.query.label"));
    queryField.setWidthFull();
    queryField.setPlaceholder(getTranslation("search.query.placeholder"));
    queryField.setMinHeight("80px");

    searchButton.setText(getTranslation("search.button"));
    searchButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    searchButton.addClickListener(e -> triggerSearch(false));

    HorizontalLayout searchBar = new HorizontalLayout(queryField, searchButton);
    searchBar.setWidthFull();
    searchBar.setAlignItems(Alignment.END);
    searchBar.setFlexGrow(1, queryField);
    add(searchBar);

    // ── Recent search history ──────────────────────────────────────────────
    buildRecentSearchBar();
    add(recentSearchBar);

    // ── Advanced panel ─────────────────────────────────────────────────────
    add(buildAdvancedSection());

    // ── Progress ───────────────────────────────────────────────────────────
    progressBar.setIndeterminate(true);
    progressBar.setWidthFull();
    progressBar.setVisible(false);
    statusLabel.getStyle()
        .set("font-size", "var(--lumo-font-size-s)")
        .set("color", "var(--lumo-secondary-text-color)");
    statusLabel.setVisible(false);
    add(progressBar, statusLabel);

    // ── Transparency panel ─────────────────────────────────────────────────
    add(buildTransparencySection());

    // ── View toggle (segmented control) ───────────────────────────────────
    viewToggle = new ViewModeToggle(false, this::onTileModeChanged);
    viewToggle.setVisible(false);
    add(viewToggle);

    // ── Results grid ───────────────────────────────────────────────────────
    configureResultsGrid();
    add(resultsGrid);
    setFlexGrow(1, resultsGrid);

    // ── Tile container ─────────────────────────────────────────────────────
    tileContainer.getStyle()
        .set("display", "grid")
        .set("grid-template-columns", "repeat(auto-fill, minmax(220px, 1fr))")
        .set("gap", "1rem")
        .set("overflow-y", "auto")
        .set("padding", "0.5rem")
        .set("width", "100%")
        .set("height", "100%");
    tileContainer.setVisible(false);
    add(tileContainer);
    setFlexGrow(1, tileContainer);
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

    for (RecentSearchEntry entry : recent) {
      Button chip = new Button(entry.getQuery());
      chip.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY,
                            ButtonVariant.LUMO_CONTRAST);
      chip.getStyle().set("font-size", "var(--lumo-font-size-xs)");
      chip.addClickListener(e -> {
        queryField.setValue(entry.getQuery());
        triggerSearch(false);
      });
      recentSearchBar.add(chip);
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

  // -------------------------------------------------------------------------
  // Advanced search parameters panel
  // -------------------------------------------------------------------------

  private Details buildAdvancedSection() {
    // Embedding text
    embeddingArea.setLabel(getTranslation("search.advanced.embedding"));
    embeddingArea.setWidthFull();
    embeddingArea.setMaxHeight("100px");

    // Boolean triples
    configureBoolSelect(personSelect,  getTranslation("search.advanced.person"));
    configureBoolSelect(vehicleSelect, getTranslation("search.advanced.vehicle"));
    configureBoolSelect(plateSelect,   getTranslation("search.advanced.plate"));

    // Season
    seasonSelect.setLabel(getTranslation("search.advanced.season"));
    seasonSelect.setItems(SeasonHint.values());
    seasonSelect.setItemLabelGenerator(s -> s == null
        ? getTranslation("search.filter.any") : s.name());
    seasonSelect.setPlaceholder(getTranslation("search.filter.any"));

    // Category
    categorySelect.setLabel(getTranslation("search.advanced.category"));
    categorySelect.setItems(SourceCategory.values());
    categorySelect.setItemLabelGenerator(c -> c == null
        ? getTranslation("search.filter.any") : c.name());
    categorySelect.setPlaceholder(getTranslation("search.filter.any"));

    // Privacy level
    privacySelect.setLabel(getTranslation("search.advanced.privacy"));
    privacySelect.setItems(RiskLevel.values());
    privacySelect.setItemLabelGenerator(r -> r == null
        ? getTranslation("search.filter.any") : r.name());
    privacySelect.setPlaceholder(getTranslation("search.filter.any"));

    // Refine button
    refineBtn.setText(getTranslation("search.advanced.refine"));
    refineBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
    refineBtn.addClickListener(e -> triggerSearch(true));

    HorizontalLayout boolRow = new HorizontalLayout(personSelect, vehicleSelect, plateSelect);
    boolRow.setSpacing(true);

    HorizontalLayout enumRow = new HorizontalLayout(seasonSelect, categorySelect, privacySelect);
    enumRow.setSpacing(true);

    VerticalLayout content = new VerticalLayout(embeddingArea, boolRow, enumRow, refineBtn);
    content.setSpacing(true);
    content.setPadding(false);

    advancedSection.setSummaryText(getTranslation("search.advanced.header"));
    advancedSection.add(content);
    advancedSection.setOpened(false);
    return advancedSection;
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
  // Transparency panel
  // -------------------------------------------------------------------------

  private Details buildTransparencySection() {
    transparencyContent.setPadding(false);
    transparencyContent.setSpacing(false);
    transparencySection.setSummaryText(getTranslation("search.transparency.header"));
    transparencySection.add(transparencyContent);
    transparencySection.setOpened(false);
    transparencySection.setVisible(false);
    return transparencySection;
  }

  private void populateTransparency(SearchPlan plan, int resultCount) {
    transparencyContent.removeAll();

    if (plan.getOriginalQuery() != null) {
      addTransparencyRow(getTranslation("search.transparency.original"), plan.getOriginalQuery());
    }

    addTransparencyRow(getTranslation("search.transparency.embedding"), plan.getEmbeddingText());

    // Applied filters
    StringBuilder filters = new StringBuilder();
    if (plan.getContainsPerson() != null) {
      filters.append(getTranslation("search.advanced.person")).append(": ")
          .append(plan.getContainsPerson() ? getTranslation("search.yes")
                                           : getTranslation("search.no")).append("  ");
    }
    if (plan.getContainsVehicle() != null) {
      filters.append(getTranslation("search.advanced.vehicle")).append(": ")
          .append(plan.getContainsVehicle() ? getTranslation("search.yes")
                                            : getTranslation("search.no")).append("  ");
    }
    if (plan.getContainsLicensePlate() != null) {
      filters.append(getTranslation("search.advanced.plate")).append(": ")
          .append(plan.getContainsLicensePlate() ? getTranslation("search.yes")
                                                 : getTranslation("search.no")).append("  ");
    }
    if (plan.getSeasonHint() != null) {
      filters.append(getTranslation("search.advanced.season")).append(": ")
          .append(plan.getSeasonHint().name()).append("  ");
    }
    if (plan.getSourceCategory() != null) {
      filters.append(getTranslation("search.advanced.category")).append(": ")
          .append(plan.getSourceCategory().name()).append("  ");
    }
    if (!filters.isEmpty()) {
      addTransparencyRow(getTranslation("search.transparency.filters"),
                         filters.toString().trim());
    }

    if (plan.getExplanation() != null) {
      addTransparencyRow(getTranslation("search.plan.explanation"), plan.getExplanation());
    }

    addTransparencyRow(getTranslation("search.transparency.count"), String.valueOf(resultCount));

    transparencySection.setVisible(true);
    transparencySection.setOpened(true);
  }

  private void addTransparencyRow(String label, String value) {
    HorizontalLayout row = new HorizontalLayout();
    row.setSpacing(true);
    Span lbl = new Span(label + ":");
    lbl.getStyle().set("font-weight", "600").set("min-width", "160px").set("flex-shrink", "0");
    Span val = new Span(value);
    val.getStyle().set("color", "var(--lumo-secondary-text-color)").set("white-space", "pre-wrap");
    row.add(lbl, val);
    transparencyContent.add(row);
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
  // Search triggering
  // -------------------------------------------------------------------------

  /**
   * Triggers a search.
   *
   * @param useEditedPlan {@code true} → collect the user-edited plan from the advanced
   *                      panel and search directly with it (no LLM re-analysis);
   *                      {@code false} → full analyze + search from the query field
   */
  private void triggerSearch(boolean useEditedPlan) {
    String query = queryField.getValue();
    if (!useEditedPlan && (query == null || query.isBlank())) {
      Notification.show(getTranslation("search.error.empty"), 3000, Notification.Position.MIDDLE);
      return;
    }

    // Persist to recent history (full searches only, not refinements)
    if (!useEditedPlan && query != null && !query.isBlank()) {
      ServiceRegistry.getInstance().getPersistenceService().addRecentSearch(query);
      refreshRecentChips();
    }

    SearchPlan planToUse = null;
    if (useEditedPlan && currentPlan != null) {
      planToUse = collectPlanFromAdvancedPanel();
    }

    SearchRunState state = new SearchRunState();
    currentSearch = state;

    searchButton.setEnabled(false);
    refineBtn.setEnabled(false);
    progressBar.setVisible(true);
    statusLabel.setText(getTranslation("search.status.init"));
    statusLabel.setVisible(true);
    transparencySection.setVisible(false);
    resultsGrid.setVisible(false);
    tileContainer.setVisible(false);
    viewToggle.setVisible(false);

    getUI().ifPresent(ui -> ui.setPollInterval(POLL_INTERVAL_MS));

    final SearchPlan fixedPlan = planToUse;
    if (fixedPlan != null) {
      ServiceRegistry.getInstance().getSearchExecutor().submit(() -> runSearchWithPlan(state, fixedPlan));
    } else {
      ServiceRegistry.getInstance().getSearchExecutor().submit(() -> runSearch(state, query));
    }
  }

  /**
   * Collects current values from the advanced panel editable fields and returns
   * a new {@link SearchPlan} (preserving originalQuery from {@link #currentPlan}).
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
  // Background search tasks
  // -------------------------------------------------------------------------

  /** Full flow: LLM analyze then vector search. */
  private void runSearch(SearchRunState state, String query) {
    try {
      state.status = "search.status.analyzing";
      logger().info("Starting query understanding for: {}", query);
      SearchPlan plan = ServiceRegistry.getInstance()
          .getQueryUnderstandingService()
          .understand(query);
      state.plan = plan;
      logger().info("Query plan: embedding='{}', season={}, category={}",
                    plan.getEmbeddingText(), plan.getSeasonHint(), plan.getSourceCategory());

      state.status = "search.status.vector";
      List<SearchResultItem> results = ServiceRegistry.getInstance()
          .getSearchService()
          .search(plan);
      state.results = results;
      logger().info("Search returned {} results", results.size());
    } catch (Exception ex) {
      logger().error("Search failed: {}", ex.getMessage(), ex);
      state.error = ex.getMessage();
    } finally {
      state.done = true;
    }
  }

  /** Refined flow: skip LLM, use the supplied plan directly. */
  private void runSearchWithPlan(SearchRunState state, SearchPlan plan) {
    try {
      state.status = "search.status.vector";
      state.plan = plan;
      logger().info("Refine search with edited plan: embedding='{}'", plan.getEmbeddingText());
      List<SearchResultItem> results = ServiceRegistry.getInstance()
          .getSearchService()
          .search(plan);
      state.results = results;
      logger().info("Refine search returned {} results", results.size());
    } catch (Exception ex) {
      logger().error("Refine search failed: {}", ex.getMessage(), ex);
      state.error = ex.getMessage();
    } finally {
      state.done = true;
    }
  }

  // -------------------------------------------------------------------------
  // Poll handler — runs on the UI thread
  // -------------------------------------------------------------------------

  private void onPoll() {
    SearchRunState s = currentSearch;
    if (s == null) {
      return;
    }

    statusLabel.setText(getTranslation(s.status));
    if (!s.done) {
      return;
    }

    // Search finished
    getUI().ifPresent(ui -> ui.setPollInterval(-1));
    currentSearch = null;
    progressBar.setVisible(false);
    statusLabel.setVisible(false);
    searchButton.setEnabled(true);
    refineBtn.setEnabled(true);

    if (s.error != null) {
      Notification n = Notification.show(
          getTranslation("search.error.failed", s.error), 5000, Notification.Position.MIDDLE);
      n.addThemeVariants(NotificationVariant.LUMO_ERROR);
      return;
    }

    // Populate the advanced panel with derived (editable) plan values
    if (s.plan != null) {
      currentPlan = s.plan;
      populateAdvancedPanel(s.plan);
      populateTransparency(s.plan, s.results != null ? s.results.size() : 0);
    }

    renderResults(s.results != null ? s.results : List.of());
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
    advancedSection.setOpened(true);
  }

  private String boolToSelect(Boolean value) {
    if (Boolean.TRUE.equals(value))  return BOOL_YES;
    if (Boolean.FALSE.equals(value)) return BOOL_NO;
    return BOOL_ANY;
  }

  // -------------------------------------------------------------------------
  // Grid configuration
  // -------------------------------------------------------------------------

  private void configureResultsGrid() {
    resultsGrid.setWidthFull();
    resultsGrid.setHeightFull();
    resultsGrid.setVisible(false);

    resultsGrid.addComponentColumn(r -> buildResultThumb(r, "60px"))
        .setHeader(getTranslation("search.col.preview"))
        .setWidth("80px").setFlexGrow(0);

    resultsGrid.addColumn(r -> String.format("%.3f", r.getScore()))
        .setHeader(getTranslation("search.col.score")).setWidth("80px").setFlexGrow(0)
        .setSortable(true);

    resultsGrid.addColumn(SearchResultItem::getTitle)
        .setHeader(getTranslation("search.col.filename")).setFlexGrow(2).setSortable(true);

    resultsGrid.addColumn(r -> r.getSourceCategory() != null ? r.getSourceCategory().name() : "—")
        .setHeader(getTranslation("search.col.category")).setFlexGrow(1);

    resultsGrid.addColumn(r -> r.getSeasonHint() != null ? r.getSeasonHint().name() : "—")
        .setHeader(getTranslation("search.col.season")).setFlexGrow(1);

    resultsGrid.addComponentColumn(r -> {
      Boolean p = r.getContainsPerson();
      return new Span(p == null ? "—" : (p ? getTranslation("search.yes")
                                           : getTranslation("search.no")));
    }).setHeader(getTranslation("search.col.persons")).setFlexGrow(1);

    resultsGrid.addComponentColumn(r -> riskBadge(r.getRiskLevel()))
        .setHeader(getTranslation("search.col.risk")).setFlexGrow(1);

    resultsGrid.addColumn(SearchResultItem::getSummary)
        .setHeader(getTranslation("search.col.description")).setFlexGrow(4);

    resultsGrid.addComponentColumn(r -> {
      Button btn = new Button(getTranslation("search.col.details"));
      btn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
      btn.addClickListener(e -> openDetailDialog(r));
      return btn;
    }).setHeader("").setFlexGrow(1);
  }

  private Span riskBadge(RiskLevel risk) {
    if (risk == null) return new Span("—");
    Span badge = new Span(risk.name());
    badge.getElement().getThemeList().add("badge");
    switch (risk) {
      case SAFE      -> badge.getElement().getThemeList().add("success");
      case REVIEW    -> badge.getElement().getThemeList().add("contrast");
      case SENSITIVE -> badge.getElement().getThemeList().add("error");
      default        -> throw new IllegalStateException("Unexpected risk level: " + risk);
    }
    return badge;
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
  // Tile view for search results
  // -------------------------------------------------------------------------

  private void renderTiles(List<SearchResultItem> results) {
    tileContainer.removeAll();
    for (int i = 0; i < results.size(); i++) {
      tileContainer.add(buildResultTile(results.get(i), i + 1));
    }
  }

  private Div buildResultTile(SearchResultItem r, int rank) {
    Div card = new Div();
    card.getStyle()
        .set("border", "1px solid var(--lumo-contrast-10pct)")
        .set("border-radius", "var(--lumo-border-radius-l)")
        .set("overflow", "hidden")
        .set("background", "var(--lumo-base-color)")
        .set("display", "flex")
        .set("flex-direction", "column")
        .set("cursor", "default");

    // Double-click → detail dialog
    card.getElement().addEventListener("dblclick", e -> openDetailDialog(r));

    // Thumbnail
    Div thumbWrapper = new Div(buildResultThumb(r, "150px"));
    thumbWrapper.getStyle()
        .set("background", "var(--lumo-contrast-5pct)")
        .set("display", "flex").set("align-items", "center").set("justify-content", "center")
        .set("min-height", "120px").set("overflow", "hidden");
    card.add(thumbWrapper);

    Div info = new Div();
    info.getStyle().set("padding", "0.5rem").set("display", "flex")
        .set("flex-direction", "column").set("gap", "0.25rem");

    // ── Rank badge ─────────────────────────────────────────────────────────
    Span rankBadge = new Span("#" + rank + "  " + String.format("%.3f", r.getScore()));
    rankBadge.getElement().getThemeList().add("badge");
    if (rank == 1) {
      rankBadge.getElement().getThemeList().add("success");
    } else {
      rankBadge.getElement().getThemeList().add("contrast");
    }
    info.add(rankBadge);

    // ── Filename ───────────────────────────────────────────────────────────
    Span name = new Span(r.getTitle() != null ? r.getTitle() : "—");
    name.getStyle().set("font-weight", "600").set("font-size", "var(--lumo-font-size-s)")
        .set("overflow", "hidden").set("text-overflow", "ellipsis")
        .set("white-space", "nowrap").set("display", "block");
    name.getElement().setAttribute("title", r.getTitle() != null ? r.getTitle() : "");
    info.add(name);

    // ── Category ───────────────────────────────────────────────────────────
    if (r.getSourceCategory() != null) {
      Span catSpan = new Span(r.getSourceCategory().name());
      catSpan.getStyle()
          .set("font-size", "var(--lumo-font-size-xs)")
          .set("color", "var(--lumo-secondary-text-color)");
      info.add(catSpan);
    }

    // ── Risk icon ──────────────────────────────────────────────────────────
    info.add(riskIcon(r.getRiskLevel()));

    // ── Description excerpt ────────────────────────────────────────────────
    if (r.getSummary() != null) {
      String excerpt = r.getSummary().length() > 80
          ? r.getSummary().substring(0, 80) + "…" : r.getSummary();
      Span desc = new Span(excerpt);
      desc.getStyle().set("font-size", "var(--lumo-font-size-xs)")
          .set("color", "var(--lumo-secondary-text-color)").set("white-space", "normal");
      info.add(desc);
    }

    // ── Details button (explicit action; double-click is the shortcut) ─────
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

  /**
   * Returns a coloured icon representing the risk level, with a tooltip.
   * SAFE = green check, REVIEW = orange question mark, SENSITIVE = red exclamation.
   */
  private com.vaadin.flow.component.Component riskIcon(RiskLevel risk) {
    if (risk == null) {
      Span dash = new Span("—");
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
      if (asset == null) return new Span("—");

      PreviewService previews = ServiceRegistry.getInstance().getPreviewService();
      Path originalPath = ServiceRegistry.getInstance()
          .getImageStorageService().resolvePath(asset.getId());

      if (!Files.exists(originalPath)) return new Span("—");

      StreamResource res = previews.getTilePreview(asset.getId(), originalPath,
                                                   asset.getStoredFilename());
      if (res == null) {
        // Fallback: stream original
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
    return new Span("—");
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
  // Shared volatile search state
  // -------------------------------------------------------------------------

  private static class SearchRunState {
    volatile String                 status  = "search.status.init";
    volatile SearchPlan             plan    = null;
    volatile List<SearchResultItem> results = null;
    volatile String                 error   = null;
    volatile boolean                done    = false;
  }
}
