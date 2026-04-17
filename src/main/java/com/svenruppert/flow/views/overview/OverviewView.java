package com.svenruppert.flow.views.overview;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.MainLayout;
import com.svenruppert.flow.views.detail.CategoryTreeChooserDialog;
import com.svenruppert.flow.views.detail.ComparisonDialog;
import com.svenruppert.flow.views.detail.DetailDialog;
import com.svenruppert.flow.views.shared.CategoryChooserComponent;
import com.svenruppert.flow.views.shared.ViewModeToggle;
import com.svenruppert.imagerag.bootstrap.ServiceRegistry;
import com.svenruppert.imagerag.domain.*;
import com.svenruppert.imagerag.domain.enums.RiskLevel;
import com.svenruppert.imagerag.domain.enums.SourceCategory;
import com.svenruppert.imagerag.persistence.PersistenceService;
import com.svenruppert.imagerag.service.PreviewService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static com.vaadin.flow.component.icon.VaadinIcon.*;

/**
 * Image archive overview — table and tile views.
 *
 * <p>Features in this view:
 * <ul>
 *   <li>Filter bar: category, risk level, visibility (any / approved / locked).</li>
 *   <li>Table view: editable risk and category dropdowns per row.</li>
 *   <li>Tile view: cached JPEG thumbnails via {@link PreviewService}, with all key
 *       metadata (category, location, risk icon, visibility icon), approve/lock toggle,
 *       delete, and a details button.  Double-click on any tile opens the detail
 *       dialog.</li>
 *   <li>Full-height layout — content fills browser viewport.</li>
 * </ul>
 */
@PageTitle("Image Overview")
@Route(value = OverviewView.PATH, layout = MainLayout.class)
public class OverviewView
    extends VerticalLayout
    implements BeforeEnterObserver, HasLogger {

  public static final String PATH = "overview";
  private static final int PAGE_SIZE = 30;
  private static final String VIS_ALL = "all";
  private static final String VIS_APPROVED = "approved";
  private static final String VIS_LOCKED = "locked";
  private final Grid<ImageAsset> grid = new Grid<>(ImageAsset.class, false);
  private final Div tileContainer = new Div();
  private final Button loadMoreTilesBtn = new Button();
  private final Span selectedCountLabel = new Span();
  private final Button compareBtn = new Button();
  private final HorizontalLayout batchToolbar = new HorizontalLayout();
  // ── Filter state ──────────────────────────────────────────────────────────
  private final ImageOverviewFilter filter = new ImageOverviewFilter();
  private final Select<RiskLevel> filterRisk = new Select<>();
  private final Select<String> filterVisibility = new Select<>();
  private boolean tileMode = false;
  private ViewModeToggle viewToggle;
  private List<ImageAsset> currentFilteredImages = List.of();
  /**
   * Number of tile cards currently rendered in {@link #tileContainer}.
   * Incremented by {@link #appendTiles} so "Load more" only renders NEW tiles
   * instead of destroying and recreating all existing ones.
   */
  private int renderedTileCount = 0;
  // Initialized lazily in buildFilterBar() because getTranslation() requires attachment.
  private CategoryChooserComponent filterCategory;
  private List<ImageAsset> allImages = List.of();

  public OverviewView() {
    // Natural page scrolling — do not setSizeFull() so content can grow beyond the viewport.
    setWidthFull();
    setPadding(true);
    setSpacing(true);

    add(new H2(getTranslation("overview.title")));

    // ── Toolbar ───────────────────────────────────────────────────────────
    Button refreshBtn = new Button(getTranslation("overview.refresh"), e -> loadData());
    refreshBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

    viewToggle = new ViewModeToggle(false, this::setTileMode);

    HorizontalLayout toolbar = new HorizontalLayout(refreshBtn, viewToggle);
    toolbar.setAlignItems(Alignment.CENTER);
    add(toolbar);

    // ── Filter bar ────────────────────────────────────────────────────────
    add(buildFilterBar());

    // ── Batch action toolbar (hidden until items selected) ────────────────
    add(buildBatchToolbar());

    // ── Table view ────────────────────────────────────────────────────────
    configureGrid();
    add(grid);

    // ── Tile container (hidden until toggled) ──────────────────────────────
    // No fixed height or overflow-y: the page scrolls naturally as the grid grows.
    tileContainer.getStyle()
        .set("display", "grid")
        .set("grid-template-columns", "repeat(auto-fill, minmax(220px, 1fr))")
        .set("gap", "1rem")
        .set("padding", "0.5rem")
        .set("width", "100%");
    tileContainer.setVisible(false);
    add(tileContainer);

    // ── Load more button (tile view only) ──────────────────────────────────
    loadMoreTilesBtn.setText(getTranslation("overview.loadmore"));
    loadMoreTilesBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    loadMoreTilesBtn.setVisible(false);
    // Append the next page of tiles WITHOUT destroying already-rendered cards.
    loadMoreTilesBtn.addClickListener(e -> appendTiles(currentFilteredImages, PAGE_SIZE));
    add(loadMoreTilesBtn);
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

  // -------------------------------------------------------------------------
  // Batch toolbar
  // -------------------------------------------------------------------------

  private HorizontalLayout buildBatchToolbar() {
    selectedCountLabel.getStyle().set("font-size", "var(--lumo-font-size-s)")
        .set("align-self", "flex-end");

    Button approveAllBtn = new Button(getTranslation("overview.batch.approve"), e -> batchApprove());
    approveAllBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_SUCCESS,
                                   ButtonVariant.LUMO_TERTIARY);

    Button lockAllBtn = new Button(getTranslation("overview.batch.lock"), e -> batchLock());
    lockAllBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_CONTRAST,
                                ButtonVariant.LUMO_TERTIARY);

    Button archiveAllBtn = new Button(getTranslation("overview.batch.delete"), e -> batchArchive());
    archiveAllBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR,
                                   ButtonVariant.LUMO_TERTIARY);

    Button reprocessAllBtn = new Button(getTranslation("overview.batch.reprocess"), e -> batchReprocess());
    reprocessAllBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

    // Compare button — only enabled when exactly 2 images are selected
    compareBtn.setText(getTranslation("overview.compare"));
    compareBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
    compareBtn.setVisible(false); // shown only with exactly-2 selection
    compareBtn.addClickListener(e -> openComparisonDialog());

    batchToolbar.add(selectedCountLabel, approveAllBtn, lockAllBtn, archiveAllBtn,
                     reprocessAllBtn, compareBtn);
    batchToolbar.setAlignItems(Alignment.CENTER);
    batchToolbar.setSpacing(true);
    batchToolbar.setVisible(false);
    return batchToolbar;
  }

  private void batchApprove() {
    PersistenceService ps = ServiceRegistry.getInstance().getPersistenceService();
    Set<ImageAsset> selected = grid.getSelectedItems();
    selected.forEach(a -> ps.approveImage(a.getId()));
    showNotification(getTranslation("overview.approved", selected.size() + " images"), true);
    loadData();
  }

  private void batchLock() {
    PersistenceService ps = ServiceRegistry.getInstance().getPersistenceService();
    Set<ImageAsset> selected = grid.getSelectedItems();
    selected.forEach(a -> ps.unapproveImage(a.getId()));
    showNotification(getTranslation("overview.locked", selected.size() + " images"), false);
    loadData();
  }

  private void batchArchive() {
    PersistenceService ps = ServiceRegistry.getInstance().getPersistenceService();
    Set<ImageAsset> selected = grid.getSelectedItems();
    selected.forEach(a -> {
      ps.softDeleteImage(a.getId(), getTranslation("overview.archive.reason.batch"));
      ServiceRegistry.getInstance().getAuditService()
          .log("SOFT_DELETE", a.getId(), a.getOriginalFilename(), "Batch archive from overview");
    });
    showNotification(getTranslation("overview.batch.archived", selected.size()), false);
    loadData();
  }

  private void batchReprocess() {
    Set<ImageAsset> selected = grid.getSelectedItems();
    int count = 0;
    for (ImageAsset asset : selected) {
      try {
        ServiceRegistry.getInstance().getReprocessingService().reprocess(asset.getId());
        count++;
      } catch (Exception ex) {
        logger().warn("Failed to reprocess {}: {}", asset.getId(), ex.getMessage());
      }
    }
    showNotification(getTranslation("overview.reprocess.started", count + " images"), true);
  }

  private void resetFilters() {
    filter.reset();
    filterCategory.reset();
    filterRisk.setValue(null);
    filterVisibility.setValue(VIS_ALL);
    applyFilter();
  }

  private void applyFilter() {
    PersistenceService ps = ServiceRegistry.getInstance().getPersistenceService();
    currentFilteredImages = filter.apply(allImages, ps);
    grid.setItems(currentFilteredImages);
    if (tileMode) {
      renderTiles(currentFilteredImages);  // full reset + first page
    }
  }

  // -------------------------------------------------------------------------
  // View mode toggle callback
  // -------------------------------------------------------------------------

  private void setTileMode(boolean tiles) {
    tileMode = tiles;
    grid.setVisible(!tileMode);
    tileContainer.setVisible(tileMode);
    loadMoreTilesBtn.setVisible(false);
    if (tileMode) {
      PersistenceService ps = ServiceRegistry.getInstance().getPersistenceService();
      currentFilteredImages = filter.apply(allImages, ps);
      renderTiles(currentFilteredImages);
    }
  }

  // -------------------------------------------------------------------------
  // Grid configuration
  // -------------------------------------------------------------------------

  private void configureGrid() {
    grid.setWidthFull();
    // setAllRowsVisible: grid sizes to fit all rows; the page scrolls naturally.
    grid.setAllRowsVisible(true);
    grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
    grid.setSelectionMode(Grid.SelectionMode.MULTI);
    grid.addSelectionListener(e -> {
      int count = e.getAllSelectedItems().size();
      boolean hasSelection = count > 0;
      batchToolbar.setVisible(hasSelection);
      selectedCountLabel.setText(getTranslation("overview.selected.count", count));
      // Compare button is only useful when exactly 2 images are selected
      compareBtn.setVisible(count == 2);
    });

    // Thumbnail
    grid.addComponentColumn(asset -> buildThumb(asset, "60px"))
        .setHeader(getTranslation("overview.col.preview")).setWidth("80px").setFlexGrow(0);

    // Filename
    grid.addColumn(ImageAsset::getOriginalFilename)
        .setHeader(getTranslation("overview.col.filename")).setFlexGrow(2).setSortable(true);

    // Category (editable — tree-structured chooser dialog)
    grid.addComponentColumn(asset -> {
      PersistenceService ps = ServiceRegistry.getInstance().getPersistenceService();
      SourceCategory cur = ps.findAnalysis(asset.getId()).map(SemanticAnalysis::getSourceCategory).orElse(null);
      String label = cur != null ? CategoryRegistry.getUserLabel(cur) : "\u2014";
      Button chooseBtn = new Button(label);
      chooseBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
      chooseBtn.getStyle().set("font-size", "var(--lumo-font-size-s)");
      chooseBtn.addClickListener(e ->
                                     new CategoryTreeChooserDialog(selected -> {
                                       ps.updateSourceCategory(asset.getId(), selected);
                                       showNotification(
                                           getTranslation("overview.category.updated",
                                                          CategoryRegistry.getUserLabel(selected)), true);
                                       loadData();
                                     }).open());
      return chooseBtn;
    }).setHeader(getTranslation("overview.col.category")).setFlexGrow(1);

    // Description
    grid.addComponentColumn(asset -> {
      PersistenceService ps = ServiceRegistry.getInstance().getPersistenceService();
      return ps.findAnalysis(asset.getId()).map(a -> {
        String s = a.getShortSummary();
        Span span = new Span(s != null ? s : "—");
        span.getStyle().set("white-space", "normal");
        return span;
      }).orElse(new Span("—"));
    }).setHeader(getTranslation("overview.col.description")).setFlexGrow(4);

    // Location
    grid.addComponentColumn(asset -> {
      PersistenceService ps = ServiceRegistry.getInstance().getPersistenceService();
      return ps.findLocation(asset.getId())
          .map(l -> new Span(l.toHumanReadable()))
          .orElse(new Span("—"));
    }).setHeader(getTranslation("overview.col.location")).setFlexGrow(2);

    // Risk (editable)
    grid.addComponentColumn(asset -> buildRiskSelect(asset))
        .setHeader(getTranslation("overview.col.risk")).setWidth("140px").setFlexGrow(0);

    // Visibility toggle
    grid.addComponentColumn(asset -> approveButton(asset))
        .setHeader(getTranslation("overview.col.visibility")).setWidth("140px").setFlexGrow(0);

    // Details
    grid.addComponentColumn(asset -> {
      Button btn = new Button(getTranslation("overview.details.button"), INFO_CIRCLE.create());
      btn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
      btn.addClickListener(e -> openDetailDialog(asset));
      return btn;
    }).setHeader("").setWidth("110px").setFlexGrow(0);

    // Delete
    grid.addComponentColumn(asset -> {
      Button del = new Button(TRASH.create());
      del.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
      del.getElement().setAttribute("title", getTranslation("overview.delete.title"));
      del.addClickListener(e -> confirmDelete(asset));
      return del;
    }).setHeader("").setWidth("60px").setFlexGrow(0);
  }

  // -------------------------------------------------------------------------
  // Tile rendering
  // -------------------------------------------------------------------------

  /**
   * Full reset: clears all tile cards and renders the first {@link #PAGE_SIZE} items.
   * Call this whenever the displayed list changes (filter change, mode toggle, data reload).
   * For "Load more" use {@link #appendTiles} instead.
   */
  private void renderTiles(List<ImageAsset> images) {
    tileContainer.removeAll();
    renderedTileCount = 0;
    appendTiles(images, PAGE_SIZE);
  }

  /**
   * Appends up to {@code count} new tile cards starting at {@link #renderedTileCount}.
   * Does NOT clear previously rendered cards — call {@link #renderTiles} for that.
   */
  private void appendTiles(List<ImageAsset> images, int count) {
    int from = renderedTileCount;
    int to = Math.min(from + count, images.size());
    for (int i = from; i < to; i++) {
      tileContainer.add(buildTileCard(images.get(i)));
    }
    renderedTileCount = to;
    loadMoreTilesBtn.setVisible(renderedTileCount < images.size());
  }

  private Div buildTileCard(ImageAsset asset) {
    PersistenceService ps = ServiceRegistry.getInstance().getPersistenceService();

    Div card = new Div();
    // Fixed height: all cards keep the same size regardless of content length.
    // The info section scrolls internally if it overflows.
    card.getStyle()
        .set("border", "1px solid var(--lumo-contrast-10pct)")
        .set("border-radius", "var(--lumo-border-radius-l)")
        .set("overflow", "hidden")
        .set("background", "var(--lumo-base-color)")
        .set("display", "flex")
        .set("flex-direction", "column")
        .set("height", "360px")
        .set("cursor", "default");

    // Double-click → detail dialog
    card.getElement().addEventListener("dblclick", e -> openDetailDialog(asset));

    // Thumbnail — fixed height, does not shrink
    Div thumbWrapper = new Div(buildThumb(asset, "150px"));
    thumbWrapper.getStyle()
        .set("background", "var(--lumo-contrast-5pct)")
        .set("display", "flex").set("align-items", "center").set("justify-content", "center")
        .set("height", "150px").set("flex-shrink", "0").set("overflow", "hidden");
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

    // Filename
    Span name = new Span(asset.getOriginalFilename());
    name.getStyle().set("font-weight", "600").set("font-size", "var(--lumo-font-size-s)")
        .set("overflow", "hidden").set("text-overflow", "ellipsis")
        .set("white-space", "nowrap").set("display", "block");
    name.getElement().setAttribute("title", asset.getOriginalFilename());
    info.add(name);

    // Category — shows user-friendly label (e.g. "Landscape") and coarse group
    SemanticAnalysis tileAnalysis = ps.findAnalysis(asset.getId()).orElse(null);
    SourceCategory cat = tileAnalysis != null ? tileAnalysis.getSourceCategory() : null;
    if (cat != null) {
      String catDisplay = CategoryRegistry.getUserLabel(cat)
          + " \u00b7 " + CategoryRegistry.getGroupLabel(cat);
      Span catSpan = new Span(catDisplay);
      catSpan.getStyle().set("font-size", "var(--lumo-font-size-xs)")
          .set("color", "var(--lumo-secondary-text-color)");
      info.add(catSpan);
    }

    // Secondary categories — compact comma-separated list
    if (tileAnalysis != null) {
      java.util.List<SourceCategory> secondary = tileAnalysis.getSecondaryCategories();
      if (secondary != null && !secondary.isEmpty()) {
        String secDisplay = secondary.stream()
            .map(CategoryRegistry::getUserLabel)
            .collect(java.util.stream.Collectors.joining(", "));
        Span secSpan = new Span(secDisplay);
        secSpan.getStyle()
            .set("font-size", "var(--lumo-font-size-xs)")
            .set("color", "var(--lumo-tertiary-text-color)")
            .set("font-style", "italic");
        info.add(secSpan);
      }
    }

    // Location
    ps.findLocation(asset.getId()).ifPresent(loc -> {
      Span locSpan = new Span(getTranslation("overview.col.location") + ": " + loc.toHumanReadable());
      locSpan.getStyle().set("font-size", "var(--lumo-font-size-xs)")
          .set("color", "var(--lumo-secondary-text-color)");
      info.add(locSpan);
    });

    // Risk + Visibility icons row
    HorizontalLayout iconRow = new HorizontalLayout();
    iconRow.setSpacing(true);
    iconRow.setAlignItems(Alignment.CENTER);

    RiskLevel risk = ps.findAssessment(asset.getId()).map(SensitivityAssessment::getRiskLevel).orElse(null);
    iconRow.add(riskIcon(risk));
    iconRow.add(visibilityIcon(asset, ps));
    info.add(iconRow);

    // Action area — two rows for cleaner grouping and reduced crowding.
    // Row 1 (informational): Details + Reprocess — open or enrich the image
    // Row 2 (state-changing): visibility toggle + delete — modify or remove

    Button detailBtn = new Button(getTranslation("overview.details.button"));
    detailBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
    detailBtn.addClickListener(e -> openDetailDialog(asset));

    Button reprocessBtn = new Button(getTranslation("overview.reprocess"));
    reprocessBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
    reprocessBtn.addClickListener(e -> startReprocess(asset));

    HorizontalLayout actionRow1 = new HorizontalLayout(detailBtn, reprocessBtn);
    actionRow1.setSpacing(true);
    actionRow1.setPadding(false);

    Button delBtn = new Button(TRASH.create());
    delBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
    delBtn.getElement().setAttribute("title", getTranslation("overview.delete.title"));
    delBtn.addClickListener(e -> confirmDelete(asset));

    HorizontalLayout actionRow2 = new HorizontalLayout(approveButton(asset), delBtn);
    actionRow2.setSpacing(true);
    actionRow2.setPadding(false);
    actionRow2.setAlignItems(Alignment.CENTER);

    VerticalLayout actions = new VerticalLayout(actionRow1, actionRow2);
    actions.setPadding(false);
    actions.setSpacing(false);
    actions.getStyle().set("gap", "0.15rem");

    info.add(actions);
    card.add(info);
    return card;
  }

  // -------------------------------------------------------------------------
  // Risk and visibility iconography
  // -------------------------------------------------------------------------

  /**
   * Returns a coloured icon representing the image's risk level, with a tooltip.
   * SAFE = green check, REVIEW = orange question, SENSITIVE = red exclamation.
   */
  private Component riskIcon(RiskLevel risk) {
    if (risk == null) {
      Span dash = new Span("—");
      dash.getStyle().set("font-size", "var(--lumo-font-size-xs)");
      return dash;
    }
    Icon icon = switch (risk) {
      case SAFE -> VaadinIcon.CHECK_CIRCLE.create();
      case REVIEW -> VaadinIcon.QUESTION_CIRCLE.create();
      case SENSITIVE -> VaadinIcon.EXCLAMATION_CIRCLE_O.create();
    };
    String color = switch (risk) {
      case SAFE -> "var(--lumo-success-color)";
      case REVIEW -> "var(--lumo-warning-color, orange)";
      case SENSITIVE -> "var(--lumo-error-color)";
    };
    icon.setSize("18px");
    icon.setColor(color);
    icon.getElement().setAttribute("title",
                                   getTranslation("overview.risk.tooltip." + risk.name().toLowerCase()));
    return icon;
  }

  /**
   * Returns an eye/eye-slash icon indicating whether the image is approved for
   * search results, with a tooltip.
   */
  private Component visibilityIcon(ImageAsset asset, PersistenceService ps) {
    boolean approved = ps.isApproved(asset.getId());
    Icon icon = approved ? VaadinIcon.EYE.create() : VaadinIcon.EYE_SLASH.create();
    icon.setSize("18px");
    icon.setColor(approved ? "var(--lumo-primary-color)" : "var(--lumo-contrast-50pct)");
    icon.getElement().setAttribute("title",
                                   getTranslation(approved ? "overview.visibility.tooltip.approved"
                                                      : "overview.visibility.tooltip.locked"));
    return icon;
  }

  // -------------------------------------------------------------------------
  // Component helpers
  // -------------------------------------------------------------------------

  /**
   * Builds a thumbnail component using the cached {@link PreviewService} preview.
   * Falls back to streaming the original file if the preview cannot be generated.
   */
  private Component buildThumb(ImageAsset asset, String height) {
    try {
      PreviewService previews = ServiceRegistry.getInstance().getPreviewService();
      Path originalPath = ServiceRegistry.getInstance()
          .getImageStorageService().resolvePath(asset.getId());
      if (!Files.exists(originalPath)) return new Span("—");

      StreamResource res = previews.getTilePreview(asset.getId(), originalPath, asset.getStoredFilename());
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
      logger().debug("Could not build thumbnail for {}", asset.getId(), e);
    }
    return new Span("—");
  }

  private Select<RiskLevel> buildRiskSelect(ImageAsset asset) {
    PersistenceService ps = ServiceRegistry.getInstance().getPersistenceService();
    RiskLevel current = ps.findAssessment(asset.getId())
        .map(SensitivityAssessment::getRiskLevel).orElse(null);
    Select<RiskLevel> sel = new Select<>();
    sel.setItems(RiskLevel.values());
    sel.setPlaceholder("—");
    if (current != null) sel.setValue(current);
    sel.getStyle().set("font-size", "var(--lumo-font-size-s)");
    sel.addValueChangeListener(e -> {
      if (e.getValue() != null && e.getValue() != e.getOldValue()) {
        ps.updateRiskLevel(asset.getId(), e.getValue());
        if (e.getValue() == RiskLevel.SAFE) ps.approveImage(asset.getId());
        else ps.unapproveImage(asset.getId());
        showNotification(getTranslation("overview.risk.updated", e.getValue().name()), true);
        loadData();
      }
    });
    return sel;
  }

  private HorizontalLayout approveButton(ImageAsset asset) {
    PersistenceService ps = ServiceRegistry.getInstance().getPersistenceService();
    boolean hasAssessment = ps.findAssessment(asset.getId()).isPresent();
    if (!hasAssessment) {
      Span pending = new Span(getTranslation("overview.processing"));
      pending.getStyle().set("font-size", "var(--lumo-font-size-s)")
          .set("color", "var(--lumo-secondary-text-color)");
      return new HorizontalLayout(pending);
    }
    boolean approved = ps.isApproved(asset.getId());
    Button btn;
    if (approved) {
      btn = new Button(getTranslation("overview.lock"), EYE_SLASH.create());
      btn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_CONTRAST, ButtonVariant.LUMO_TERTIARY);
    } else {
      btn = new Button(getTranslation("overview.approve"), EYE.create());
      btn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_SUCCESS, ButtonVariant.LUMO_TERTIARY);
    }
    btn.addClickListener(e -> {
      if (approved) {
        ps.unapproveImage(asset.getId());
        showNotification(getTranslation("overview.locked", asset.getOriginalFilename()), false);
      } else {
        ps.approveImage(asset.getId());
        showNotification(getTranslation("overview.approved", asset.getOriginalFilename()), true);
      }
      loadData();
    });
    return new HorizontalLayout(btn);
  }

  // -------------------------------------------------------------------------
  // Actions
  // -------------------------------------------------------------------------

  private void openDetailDialog(ImageAsset asset) {
    PersistenceService ps = ServiceRegistry.getInstance().getPersistenceService();
    SemanticAnalysis analysis = ps.findAnalysis(asset.getId()).orElse(null);
    SensitivityAssessment assess = ps.findAssessment(asset.getId()).orElse(null);
    LocationSummary location = ps.findLocation(asset.getId()).orElse(null);
    new DetailDialog(asset, analysis, assess, location).open();
  }

  /**
   * Opens a side-by-side {@link ComparisonDialog} for the exactly two currently selected images.
   * Called when the "Compare" button in the batch toolbar is clicked.
   */
  private void openComparisonDialog() {
    Set<ImageAsset> selected = grid.getSelectedItems();
    if (selected.size() != 2) return; // guard — button should only appear with exactly 2
    List<ImageAsset> pair = new java.util.ArrayList<>(selected);
    new ComparisonDialog(pair.get(0), pair.get(1)).open();
  }

  private void confirmDelete(ImageAsset asset) {
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle(getTranslation("overview.delete.title"));

    VerticalLayout body = new VerticalLayout();
    body.add(new Paragraph(getTranslation("overview.delete.text", asset.getOriginalFilename())));

    Button archiveBtn = new Button(getTranslation("overview.archive"), e -> {
      softDeleteImage(asset);
      dialog.close();
    });
    archiveBtn.addThemeVariants(ButtonVariant.LUMO_CONTRAST, ButtonVariant.LUMO_PRIMARY);

    Button hardDeleteBtn = new Button(getTranslation("overview.delete.hard"), e -> {
      hardDeleteImage(asset);
      dialog.close();
    });
    hardDeleteBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_PRIMARY);

    Button cancelBtn = new Button(getTranslation("overview.delete.cancel"), e -> dialog.close());
    cancelBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

    dialog.getFooter().add(cancelBtn, archiveBtn, hardDeleteBtn);
    dialog.add(body);
    dialog.open();
  }

  private void softDeleteImage(ImageAsset asset) {
    PersistenceService ps = ServiceRegistry.getInstance().getPersistenceService();
    ps.softDeleteImage(asset.getId(), getTranslation("overview.archive.reason.single"));
    ServiceRegistry.getInstance().getAuditService()
        .log("SOFT_DELETE", asset.getId(), asset.getOriginalFilename(), "User archived from overview");
    showNotification(getTranslation("overview.archived", asset.getOriginalFilename()), false);
    loadData();
  }

  private void hardDeleteImage(ImageAsset asset) {
    try {
      ServiceRegistry.getInstance().deleteImage(asset.getId());
      ServiceRegistry.getInstance().getAuditService()
          .log("HARD_DELETE", asset.getId(), asset.getOriginalFilename(), "User permanently deleted");
      showNotification(getTranslation("overview.deleted", asset.getOriginalFilename()), false);
      loadData();
    } catch (Exception ex) {
      logger().error("Failed to delete image {}: {}", asset.getId(), ex.getMessage(), ex);
      Notification n = Notification.show(
          getTranslation("overview.delete.error", ex.getMessage()), 5000, Notification.Position.MIDDLE);
      n.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
  }

  private void showNotification(String message, boolean success) {
    Notification n = Notification.show(message, 3000, Notification.Position.BOTTOM_END);
    n.addThemeVariants(success ? NotificationVariant.LUMO_SUCCESS : NotificationVariant.LUMO_CONTRAST);
  }

  private void startReprocess(ImageAsset asset) {
    try {
      ServiceRegistry.getInstance().getReprocessingService().reprocess(asset.getId());
      showNotification(getTranslation("overview.reprocess.started", asset.getOriginalFilename()),
                       true);
    } catch (Exception ex) {
      logger().error("Failed to queue reprocessing for {}: {}", asset.getId(), ex.getMessage(), ex);
      Notification n = Notification.show(
          getTranslation("overview.reprocess.error", ex.getMessage()),
          5000, Notification.Position.MIDDLE);
      n.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
  }

  // -------------------------------------------------------------------------
  // Data loading
  // -------------------------------------------------------------------------

  private void loadData() {
    allImages = ServiceRegistry.getInstance().getPersistenceService().findAllImages();
    applyFilter();  // renderTiles() inside handles the full reset
  }

  @Override
  public void beforeEnter(BeforeEnterEvent event) {
    loadData();
  }
}
