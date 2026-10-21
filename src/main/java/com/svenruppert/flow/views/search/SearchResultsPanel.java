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
package com.svenruppert.flow.views.search;

import com.svenruppert.flow.views.detail.DetailDialog;
import com.svenruppert.flow.views.shared.Formats;
import com.svenruppert.flow.views.shared.ImagePreviewFactory;
import com.svenruppert.flow.views.shared.RiskBadge;
import com.svenruppert.flow.views.shared.ViewModeToggle;
import com.svenruppert.imagerag.domain.CategoryRegistry;
import com.svenruppert.imagerag.domain.ImageAsset;
import com.svenruppert.imagerag.domain.LocationSummary;
import com.svenruppert.imagerag.domain.SearchResultItem;
import com.svenruppert.imagerag.domain.SemanticAnalysis;
import com.svenruppert.imagerag.domain.SensitivityAssessment;
import com.svenruppert.imagerag.service.ImageCatalogService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.util.List;

/**
 * Result area of the {@link SearchView}: facet chips, grid/tile toggle, result grid,
 * paged tile view with "Load more", and the detail dialog for a single result.
 */
class SearchResultsPanel
    extends VerticalLayout {

  private static final int PAGE_SIZE = 30;

  private final ImageCatalogService catalog;
  private final ImagePreviewFactory previewFactory;

  private final SearchFacetBar facetBar = new SearchFacetBar(this::onFacetChanged);
  private final ViewModeToggle viewToggle = new ViewModeToggle(false, this::onTileModeChanged);
  private final Grid<SearchResultItem> resultsGrid = new Grid<>(SearchResultItem.class, false);
  private final Div tileContainer = new Div();
  private final Button loadMoreTilesBtn = new Button();

  private boolean tileMode = false;
  private List<SearchResultItem> lastResults = List.of();
  /**
   * Subset of lastResults currently visible after facet filtering; null means show all.
   */
  private List<SearchResultItem> facetFilteredResults = null;
  /**
   * The list currently rendered in the tile container — either {@link #lastResults}
   * or {@link #facetFilteredResults}. Used by {@link #appendTiles} so "Load more"
   * always appends from the right list.
   */
  private List<SearchResultItem> currentTileSource = List.of();
  /**
   * Number of tile cards currently rendered; "Load more" only adds NEW tiles.
   */
  private int renderedTileCount = 0;

  SearchResultsPanel(ImageCatalogService catalog, ImagePreviewFactory previewFactory) {
    this.catalog = catalog;
    this.previewFactory = previewFactory;
    setWidthFull();
    setPadding(false);
    setSpacing(true);

    viewToggle.setVisible(false);
    configureResultsGrid();

    tileContainer.getStyle()
        .set("display", "grid")
        .set("grid-template-columns", "repeat(auto-fill, minmax(220px, 1fr))")
        .set("gap", "1rem")
        .set("padding", "0.5rem")
        .set("width", "100%");
    tileContainer.setVisible(false);

    loadMoreTilesBtn.setText(getTranslation("search.loadmore"));
    loadMoreTilesBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    loadMoreTilesBtn.setVisible(false);
    // Append the next page of tiles WITHOUT destroying already-rendered cards.
    loadMoreTilesBtn.addClickListener(e -> appendTiles(PAGE_SIZE));

    add(facetBar, viewToggle, resultsGrid, tileContainer, loadMoreTilesBtn);
  }

  /**
   * Hides the result views while a new search runs.
   */
  void hideResults() {
    resultsGrid.setVisible(false);
    tileContainer.setVisible(false);
    viewToggle.setVisible(false);
  }

  /**
   * The results of the last completed search (unfiltered).
   */
  List<SearchResultItem> lastResults() {
    return lastResults;
  }

  /**
   * Shows the results of a completed search and clears any active facet filter.
   */
  void showResults(List<SearchResultItem> results) {
    lastResults = results;
    facetFilteredResults = null;
    if (results.isEmpty()) {
      Notification.show(getTranslation("search.notfound"), 3000, Notification.Position.MIDDLE);
      resultsGrid.setVisible(false);
      tileContainer.setVisible(false);
      loadMoreTilesBtn.setVisible(false);
      viewToggle.setVisible(false);
      facetBar.setVisible(false);
    } else {
      facetBar.show(results);
      facetBar.setVisible(true);
      viewToggle.setVisible(true);
      renderResultsView(results);
      Notification n = Notification.show(
          getTranslation("search.found", results.size()), 3000, Notification.Position.BOTTOM_END);
      n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }
  }

  private void onFacetChanged(List<SearchResultItem> filtered) {
    facetFilteredResults = filtered;
    renderResultsView(filtered != null ? filtered : lastResults);
  }

  private void onTileModeChanged(boolean tiles) {
    tileMode = tiles;
    List<SearchResultItem> active = facetFilteredResults != null ? facetFilteredResults : lastResults;
    if (!active.isEmpty()) {
      renderResultsView(active);
    }
  }

  /**
   * Displays results in whichever mode (grid or tile) is currently active.
   */
  private void renderResultsView(List<SearchResultItem> items) {
    if (tileMode) {
      renderTiles(items);
      tileContainer.setVisible(true);
      resultsGrid.setVisible(false);
    } else {
      resultsGrid.setItems(items);
      resultsGrid.setVisible(true);
      tileContainer.setVisible(false);
      loadMoreTilesBtn.setVisible(false);
    }
  }

  private void configureResultsGrid() {
    resultsGrid.setWidthFull();
    // Do not call setHeightFull() — the grid auto-sizes with its content so the page
    // can scroll naturally.
    resultsGrid.setAllRowsVisible(true);
    resultsGrid.setVisible(false);

    resultsGrid.addComponentColumn(r -> buildResultThumb(r, "90px"))
        .setHeader(getTranslation("search.col.preview"))
        .setWidth("110px").setFlexGrow(0);

    resultsGrid.addColumn(r -> Formats.score(r.getScore()))
        .setHeader(getTranslation("search.col.score")).setWidth("80px").setFlexGrow(0)
        .setSortable(true);

    resultsGrid.addColumn(r -> CategoryRegistry.getUserLabel(r.getSourceCategory()))
        .setHeader(getTranslation("search.col.category")).setFlexGrow(1);

    resultsGrid.addColumn(r -> r.getSeasonHint() != null ? r.getSeasonHint().name() : "—")
        .setHeader(getTranslation("search.col.season")).setFlexGrow(1);

    resultsGrid.addComponentColumn(r -> {
      Boolean pp = r.getContainsPerson();
      return new Span(pp == null ? "—" : (pp ? getTranslation("search.yes")
                                               : getTranslation("search.no")));
    }).setHeader(getTranslation("search.col.persons")).setFlexGrow(1);

    resultsGrid.addComponentColumn(r -> RiskBadge.icon(r.getRiskLevel(), "16px"))
        .setHeader(getTranslation("search.col.risk")).setWidth("70px").setFlexGrow(0);

    resultsGrid.addComponentColumn(r -> {
      Button btn = new Button(getTranslation("search.col.details"));
      btn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
      btn.addClickListener(e -> openDetailDialog(r));
      return btn;
    }).setHeader("").setFlexGrow(1);
  }

  /**
   * Full reset: clears all tile cards and renders the first {@link #PAGE_SIZE} items.
   * For "Load more" use {@link #appendTiles} instead.
   */
  private void renderTiles(List<SearchResultItem> results) {
    currentTileSource = results;
    tileContainer.removeAll();
    renderedTileCount = 0;
    appendTiles(PAGE_SIZE);
  }

  /**
   * Appends up to {@code count} new tile cards from {@link #currentTileSource} starting
   * at {@link #renderedTileCount}. Does NOT clear existing cards.
   */
  private void appendTiles(int count) {
    int from = renderedTileCount;
    int to = Math.min(from + count, currentTileSource.size());
    for (int i = from; i < to; i++) {
      tileContainer.add(buildResultTile(currentTileSource.get(i), i + 1));
    }
    renderedTileCount = to;
    loadMoreTilesBtn.setVisible(renderedTileCount < currentTileSource.size());
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

    Div info = new Div();
    info.getStyle()
        .set("padding", "0.5rem")
        .set("display", "flex")
        .set("flex-direction", "column")
        .set("gap", "0.25rem")
        .set("flex", "1")
        .set("min-height", "0")
        .set("overflow-y", "auto");

    Span rankBadge = new Span("#" + rank + "  " + Formats.score(r.getScore()));
    rankBadge.getElement().getThemeList().add("badge");
    rankBadge.getElement().getThemeList().add(rank == 1 ? "success" : "contrast");
    info.add(rankBadge);

    // Category — user-friendly label with coarse group
    if (r.getSourceCategory() != null) {
      String catLabel = CategoryRegistry.getUserLabel(r.getSourceCategory())
          + " · " + CategoryRegistry.getGroupLabel(r.getSourceCategory());
      Span catSpan = new Span(catLabel);
      catSpan.getStyle()
          .set("font-size", "var(--lumo-font-size-xs)")
          .set("color", "var(--lumo-secondary-text-color)");
      info.add(catSpan);
    }

    info.add(RiskBadge.icon(r.getRiskLevel(), "16px"));

    // Details button (filename and description available in dialog)
    Button detailBtn = new Button(getTranslation("search.col.details"));
    detailBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);
    detailBtn.addClickListener(e -> openDetailDialog(r));
    info.add(detailBtn);

    card.add(info);
    return card;
  }

  private Component buildResultThumb(SearchResultItem r, String height) {
    ImageAsset asset = catalog.findImage(r.getImageId()).orElse(null);
    return previewFactory.tile(asset, height);
  }

  private void openDetailDialog(SearchResultItem result) {
    ImageAsset asset = catalog.findImage(result.getImageId()).orElse(null);
    if (asset == null) {
      Notification.show(getTranslation("search.image.notfound"), 3000, Notification.Position.MIDDLE);
      return;
    }
    SemanticAnalysis analysis = catalog.findAnalysis(result.getImageId()).orElse(null);
    SensitivityAssessment assm = catalog.findAssessment(result.getImageId()).orElse(null);
    LocationSummary location = catalog.findLocation(result.getImageId()).orElse(null);
    new DetailDialog(asset, analysis, assm, location).open();
  }
}
