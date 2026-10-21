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
package com.svenruppert.flow.views.overview;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.MainLayout;
import com.svenruppert.flow.views.shared.CategoryChooserComponent;
import com.svenruppert.flow.views.shared.ImagePreviewFactory;
import com.svenruppert.flow.views.shared.ViewModeToggle;
import com.svenruppert.flow.views.shared.ViewServices;
import com.svenruppert.imagerag.domain.ImageAsset;
import com.svenruppert.imagerag.domain.ImageOverviewFilter;
import com.svenruppert.imagerag.domain.enums.RiskLevel;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;

import java.util.List;

/**
 * Image archive overview — table and tile views.
 * <p>Features in this view:
 * <ul>
 *   <li>Filter bar: category, risk level, visibility (any / approved / locked).</li>
 *   <li>Table view ({@link OverviewImageGrid}): editable risk and category per row.</li>
 *   <li>Tile view ({@link OverviewTiles}): cached JPEG thumbnails via
 *       {@link ImagePreviewFactory}, with all key metadata (category, location, risk icon,
 *       visibility icon), approve/lock toggle, delete, and a details button.  Double-click
 *       on any tile opens the detail dialog.</li>
 *   <li>Batch actions on the selected rows ({@link OverviewBatchToolbar}).</li>
 *   <li>Full-height layout — content fills browser viewport.</li>
 * </ul>
 */
@Route(value = OverviewView.PATH, layout = MainLayout.class)
public class OverviewView
    extends VerticalLayout
    implements BeforeEnterObserver, HasLogger, HasDynamicTitle {

  public static final String PATH = "overview";
  private static final String VIS_ALL = "all";
  private static final String VIS_APPROVED = "approved";
  private static final String VIS_LOCKED = "locked";

  private final ViewServices services;
  private final OverviewImageGrid grid;
  private final OverviewTiles tiles;
  private final OverviewBatchToolbar batchToolbar;
  // ── Filter state ──────────────────────────────────────────────────────────
  private final ImageOverviewFilter filter = new ImageOverviewFilter();
  private final Select<RiskLevel> filterRisk = new Select<>();
  private final Select<String> filterVisibility = new Select<>();
  private CategoryChooserComponent filterCategory;
  private boolean tileMode = false;
  private List<ImageAsset> currentFilteredImages = List.of();
  private List<ImageAsset> allImages = List.of();

  public OverviewView() {
    this.services = ViewServices.current();
    ImagePreviewFactory previewFactory = services.imagePreviews();
    OverviewImageActions actions = new OverviewImageActions(this, services, this::loadData);

    // Natural page scrolling — do not setSizeFull() so content can grow beyond the viewport.
    setWidthFull();
    setPadding(true);
    setSpacing(true);

    add(new H2(getTranslation("overview.title")));

    // ── Toolbar ───────────────────────────────────────────────────────────
    Button refreshBtn = new Button(getTranslation("overview.refresh"), e -> loadData());
    refreshBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

    ViewModeToggle viewToggle = new ViewModeToggle(false, this::setTileMode);

    HorizontalLayout toolbar = new HorizontalLayout(refreshBtn, viewToggle);
    toolbar.setAlignItems(Alignment.CENTER);
    add(toolbar);

    // ── Filter bar ────────────────────────────────────────────────────────
    add(buildFilterBar());

    // ── Table view + batch action toolbar (hidden until items selected) ───
    grid = new OverviewImageGrid(services, previewFactory, actions, this::loadData);
    batchToolbar = new OverviewBatchToolbar(services, grid::getSelectedItems, this::loadData);
    grid.addSelectionListener(e -> batchToolbar.onSelectionChanged(e.getAllSelectedItems().size()));
    add(batchToolbar);
    add(grid);

    // ── Tile container + load more button (hidden until toggled) ───────────
    tiles = new OverviewTiles(services, previewFactory, actions);
    add(tiles);
    add(tiles.loadMoreButton());
  }

  // -------------------------------------------------------------------------
  // Filter bar
  // -------------------------------------------------------------------------

  private HorizontalLayout buildFilterBar() {
    // Cascading category chooser: group → specific category
    filterCategory = new CategoryChooserComponent(
        getTranslation("overview.filter.category"),
        getTranslation("overview.filter.category.specific"),
        getTranslation("overview.filter.all"));
    filterCategory.addChangeListener(() -> {
      filter.setCategory(filterCategory.getSelectedGroup());
      filter.setSpecificCategory(filterCategory.getSelectedCategory());
      applyFilter();
    });

    // Risk filter
    filterRisk.setLabel(getTranslation("overview.filter.risk"));
    filterRisk.setItems(RiskLevel.values());
    filterRisk.setPlaceholder(getTranslation("overview.filter.all"));
    filterRisk.setItemLabelGenerator(r -> r == null ? getTranslation("overview.filter.all") : r.name());
    filterRisk.addValueChangeListener(e -> {
      filter.setRisk(e.getValue());
      applyFilter();
    });

    // Visibility filter
    filterVisibility.setLabel(getTranslation("overview.filter.visibility"));
    filterVisibility.setItems(VIS_ALL, VIS_APPROVED, VIS_LOCKED);
    filterVisibility.setItemLabelGenerator(v -> switch (v) {
      case VIS_APPROVED -> getTranslation("overview.filter.approved");
      case VIS_LOCKED -> getTranslation("overview.filter.locked");
      default -> getTranslation("overview.filter.all");
    });
    filterVisibility.setValue(VIS_ALL);
    filterVisibility.addValueChangeListener(e -> {
      filter.setApproved(switch (e.getValue()) {
        case VIS_APPROVED -> Boolean.TRUE;
        case VIS_LOCKED -> Boolean.FALSE;
        default -> null;
      });
      applyFilter();
    });

    Button resetBtn = new Button(getTranslation("overview.filter.reset"), e -> resetFilters());
    resetBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

    HorizontalLayout bar = new HorizontalLayout(filterCategory, filterRisk, filterVisibility, resetBtn);
    bar.setAlignItems(Alignment.END);
    bar.setSpacing(true);
    return bar;
  }

  private void resetFilters() {
    filter.reset();
    filterCategory.reset();
    filterRisk.setValue(null);
    filterVisibility.setValue(VIS_ALL);
    applyFilter();
  }

  private void applyFilter() {
    currentFilteredImages = filter.apply(allImages, services.catalog());
    grid.setItems(currentFilteredImages);
    if (tileMode) {
      tiles.render(currentFilteredImages);  // full reset + first page
    }
  }

  // -------------------------------------------------------------------------
  // View mode toggle callback
  // -------------------------------------------------------------------------

  private void setTileMode(boolean tileMode) {
    this.tileMode = tileMode;
    grid.setVisible(!tileMode);
    tiles.setVisible(tileMode);
    tiles.loadMoreButton().setVisible(false);
    if (tileMode) {
      currentFilteredImages = filter.apply(allImages, services.catalog());
      tiles.render(currentFilteredImages);
    }
  }

  // -------------------------------------------------------------------------
  // Data loading
  // -------------------------------------------------------------------------

  private void loadData() {
    allImages = services.catalog().findAllImages();
    applyFilter();  // tiles.render() inside handles the full reset
  }

  @Override
  public void beforeEnter(BeforeEnterEvent event) {
    loadData();
  }

  @Override
  public String getPageTitle() {
    return getTranslation("overview.page.title");
  }
}
