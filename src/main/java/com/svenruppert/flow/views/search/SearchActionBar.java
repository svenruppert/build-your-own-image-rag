package com.svenruppert.flow.views.search;

import com.svenruppert.dependencies.core.net.MediaType;
import com.svenruppert.imagerag.domain.SavedSearchView;
import com.svenruppert.imagerag.domain.SearchPlan;
import com.svenruppert.imagerag.domain.SearchResultItem;
import com.svenruppert.imagerag.service.SearchHistoryService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Action bar below the search results: save the current search, load a saved search,
 * and export the last results as CSV.
 */
class SearchActionBar
    extends HorizontalLayout {

  private final SearchHistoryService searchHistory;
  private final Supplier<String> currentQuery;
  private final Supplier<SearchPlan> currentPlan;
  private final Supplier<List<SearchResultItem>> lastResults;
  private final Consumer<String> onRunSavedQuery;

  /**
   * @param searchHistory   service storing saved searches
   * @param currentQuery    current content of the query field
   * @param currentPlan     plan of the last completed search; may supply null
   * @param lastResults     results of the last completed search
   * @param onRunSavedQuery puts a saved query into the field and runs the full search
   */
  SearchActionBar(SearchHistoryService searchHistory,
                  Supplier<String> currentQuery,
                  Supplier<SearchPlan> currentPlan,
                  Supplier<List<SearchResultItem>> lastResults,
                  Consumer<String> onRunSavedQuery) {
    this.searchHistory = searchHistory;
    this.currentQuery = currentQuery;
    this.currentPlan = currentPlan;
    this.lastResults = lastResults;
    this.onRunSavedQuery = onRunSavedQuery;

    Button saveBtn = new Button(getTranslation("search.save"), e -> openSaveSearchDialog());
    saveBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

    Button loadBtn = new Button(getTranslation("search.saved.title"), e -> openLoadSavedSearchDialog());
    loadBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

    add(saveBtn, loadBtn, buildCsvExportAnchor());
    setAlignItems(Alignment.CENTER);
    setSpacing(true);
  }

  /**
   * Escapes a value for a double-quoted CSV cell. Values starting with a spreadsheet
   * formula trigger are prefixed with an apostrophe so they are not evaluated.
   */
  static String escapeCsv(String value) {
    if (value == null) return "";
    String safe = !value.isEmpty() && "=+-@".indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
    return safe.replace("\"", "\"\"");
  }

  static String buildCsvContent(List<SearchResultItem> results) {
    StringBuilder sb = new StringBuilder();
    sb.append("Score,Filename,Category,Risk,Summary\n");
    for (SearchResultItem r : results) {
      sb.append(String.format(Locale.ROOT, "%.3f,\"%s\",\"%s\",\"%s\",\"%s\"\n",
                              r.getScore(),
                              escapeCsv(r.getTitle()),
                              r.getSourceCategory() != null ? r.getSourceCategory().name() : "",
                              r.getRiskLevel() != null ? r.getRiskLevel().name() : "",
                              escapeCsv(r.getSummary())));
    }
    return sb.toString();
  }

  private Anchor buildCsvExportAnchor() {
    DownloadHandler csvDownload = DownloadHandler.fromInputStream(event -> {
      byte[] csv = buildCsvContent(lastResults.get()).getBytes(StandardCharsets.UTF_8);
      return new DownloadResponse(new ByteArrayInputStream(csv), "search-results.csv",
                                  MediaType.TEXT_CSV.withCharsetUtf8(), csv.length);
    });
    Anchor anchor = new Anchor(csvDownload, getTranslation("search.export.csv"));
    anchor.getElement().setAttribute("download", true);
    anchor.getStyle().set("font-size", "var(--lumo-font-size-s)");
    return anchor;
  }

  private void openSaveSearchDialog() {
    Dialog dlg = new Dialog();
    dlg.setHeaderTitle(getTranslation("search.save"));

    TextField nameField = new TextField(getTranslation("search.saved.name"));
    nameField.setWidthFull();

    Button saveBtn = new Button(getTranslation("common.save"), e -> {
      String name = nameField.getValue();
      if (name == null || name.isBlank()) return;
      SavedSearchView view = new SavedSearchView(name, currentQuery.get());
      SearchPlan plan = currentPlan.get();
      if (plan != null) {
        view.setEmbeddingText(plan.getEmbeddingText());
      }
      searchHistory.saveSavedSearchView(view);
      Notification.show(getTranslation("search.saved.done", name), 3000, Notification.Position.BOTTOM_END)
          .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
      dlg.close();
    });
    saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    Button cancelBtn = new Button(getTranslation("common.cancel"), e -> dlg.close());
    cancelBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

    dlg.add(nameField);
    dlg.getFooter().add(cancelBtn, saveBtn);
    dlg.open();
  }

  private void openLoadSavedSearchDialog() {
    Dialog dlg = new Dialog();
    dlg.setHeaderTitle(getTranslation("search.saved.title"));
    dlg.setWidth("500px");

    Grid<SavedSearchView> savedGrid = new Grid<>(SavedSearchView.class, false);
    savedGrid.addColumn(SavedSearchView::getName)
        .setHeader(getTranslation("search.saved.name")).setFlexGrow(2);
    savedGrid.addColumn(SavedSearchView::getQuery)
        .setHeader(getTranslation("search.saved.col.query")).setFlexGrow(3);

    savedGrid.addComponentColumn(view -> {
      Button loadBtn = new Button(getTranslation("search.saved.load"), e -> {
        dlg.close();
        onRunSavedQuery.accept(view.getQuery() != null ? view.getQuery() : "");
      });
      loadBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);

      Button delBtn = new Button(getTranslation("search.saved.delete"), e -> {
        searchHistory.deleteSavedSearchView(view.getId());
        savedGrid.setItems(searchHistory.getSavedSearchViews());
      });
      delBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR,
                              ButtonVariant.LUMO_TERTIARY);

      HorizontalLayout actions = new HorizontalLayout(loadBtn, delBtn);
      actions.setSpacing(true);
      return actions;
    }).setHeader("").setWidth("200px").setFlexGrow(0);

    savedGrid.setItems(searchHistory.getSavedSearchViews());
    savedGrid.setHeight("300px");
    dlg.add(savedGrid);

    Button closeBtn = new Button(getTranslation("common.close"), e -> dlg.close());
    closeBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    dlg.getFooter().add(closeBtn);
    dlg.open();
  }
}
