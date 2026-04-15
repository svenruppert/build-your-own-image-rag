package com.svenruppert.flow.views.overview;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.MainLayout;
import com.svenruppert.flow.views.detail.DetailDialog;
import com.svenruppert.flow.views.shared.ViewModeToggle;
import com.svenruppert.imagerag.bootstrap.ServiceRegistry;
import com.svenruppert.imagerag.domain.*;
import com.svenruppert.imagerag.domain.enums.RiskLevel;
import com.svenruppert.imagerag.domain.enums.SourceCategory;
import com.svenruppert.imagerag.persistence.PersistenceService;
import com.svenruppert.imagerag.service.PreviewService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
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

  private final Grid<ImageAsset> grid = new Grid<>(ImageAsset.class, false);
  private final Div              tileContainer = new Div();
  private boolean tileMode = false;
  private ViewModeToggle viewToggle;

  // ── Filter state ──────────────────────────────────────────────────────────
  private final ImageOverviewFilter filter = new ImageOverviewFilter();
  private final Select<SourceCategory> filterCategory   = new Select<>();
  private final Select<RiskLevel>      filterRisk       = new Select<>();
  private final Select<String>         filterVisibility = new Select<>();
  private static final String VIS_ALL     = "all";
  private static final String VIS_APPROVED = "approved";
  private static final String VIS_LOCKED  = "locked";

  private List<ImageAsset> allImages = List.of();

  public OverviewView() {
    setSizeFull();
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

    // ── Table view ────────────────────────────────────────────────────────
    configureGrid();
    add(grid);
    setFlexGrow(1, grid);

    // ── Tile container (hidden until toggled) ──────────────────────────────
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
  // Filter bar
  // -------------------------------------------------------------------------

  private HorizontalLayout buildFilterBar() {
    // Category filter
    filterCategory.setLabel(getTranslation("overview.filter.category"));
    filterCategory.setItems(SourceCategory.values());
    filterCategory.setPlaceholder(getTranslation("overview.filter.all"));
    filterCategory.setItemLabelGenerator(c -> c == null ? getTranslation("overview.filter.all") : c.name());
    filterCategory.addValueChangeListener(e -> {
      filter.setCategory(e.getValue());
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
      case VIS_LOCKED   -> getTranslation("overview.filter.locked");
      default           -> getTranslation("overview.filter.all");
    });
    filterVisibility.setValue(VIS_ALL);
    filterVisibility.addValueChangeListener(e -> {
      filter.setApproved(switch (e.getValue()) {
        case VIS_APPROVED -> Boolean.TRUE;
        case VIS_LOCKED   -> Boolean.FALSE;
        default           -> null;
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
    filterCategory.setValue(null);
    filterRisk.setValue(null);
    filterVisibility.setValue(VIS_ALL);
    applyFilter();
  }

  private void applyFilter() {
    PersistenceService ps = ServiceRegistry.getInstance().getPersistenceService();
    List<ImageAsset> filtered = filter.apply(allImages, ps);
    grid.setItems(filtered);
    if (tileMode) renderTiles(filtered);
  }

  // -------------------------------------------------------------------------
  // View mode toggle callback
  // -------------------------------------------------------------------------

  private void setTileMode(boolean tiles) {
    tileMode = tiles;
    grid.setVisible(!tileMode);
    tileContainer.setVisible(tileMode);
    if (tileMode) {
      PersistenceService ps = ServiceRegistry.getInstance().getPersistenceService();
      renderTiles(filter.apply(allImages, ps));
    }
  }

  // -------------------------------------------------------------------------
  // Grid configuration
  // -------------------------------------------------------------------------

  private void configureGrid() {
    grid.setWidthFull();
    grid.setHeightFull();
    grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);

    // Thumbnail
    grid.addComponentColumn(asset -> buildThumb(asset, "60px"))
        .setHeader(getTranslation("overview.col.preview")).setWidth("80px").setFlexGrow(0);

    // Filename
    grid.addColumn(ImageAsset::getOriginalFilename)
        .setHeader(getTranslation("overview.col.filename")).setFlexGrow(2).setSortable(true);

    // Category (editable)
    grid.addComponentColumn(asset -> {
      PersistenceService ps = ServiceRegistry.getInstance().getPersistenceService();
      SourceCategory cur = ps.findAnalysis(asset.getId()).map(SemanticAnalysis::getSourceCategory).orElse(null);
      if (cur == null) return new Span("—");
      Select<SourceCategory> sel = new Select<>();
      sel.setItems(SourceCategory.values());
      sel.setValue(cur);
      sel.getStyle().set("font-size", "var(--lumo-font-size-s)");
      sel.addValueChangeListener(e -> {
        if (e.getValue() != null && e.getValue() != e.getOldValue()) {
          ps.updateSourceCategory(asset.getId(), e.getValue());
          showNotification(getTranslation("overview.category.updated", e.getValue().name()), true);
        }
      });
      return sel;
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

  private void renderTiles(List<ImageAsset> images) {
    tileContainer.removeAll();
    for (ImageAsset asset : images) tileContainer.add(buildTileCard(asset));
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

    // Category
    SourceCategory cat = ps.findAnalysis(asset.getId()).map(SemanticAnalysis::getSourceCategory).orElse(null);
    if (cat != null) {
      Span catSpan = new Span(getTranslation("overview.col.category") + ": " + cat.name());
      catSpan.getStyle().set("font-size", "var(--lumo-font-size-xs)")
          .set("color", "var(--lumo-secondary-text-color)");
      info.add(catSpan);
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

    // Action buttons
    HorizontalLayout actions = new HorizontalLayout();
    actions.setSpacing(true);

    // Approve / Lock toggle
    actions.add(approveButton(asset));

    // Details button (explicit — double-click is the shortcut)
    Button detailBtn = new Button(getTranslation("overview.details.button"));
    detailBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
    detailBtn.addClickListener(e -> openDetailDialog(asset));
    actions.add(detailBtn);

    // Delete button
    Button delBtn = new Button(TRASH.create());
    delBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
    delBtn.getElement().setAttribute("title", getTranslation("overview.delete.title"));
    delBtn.addClickListener(e -> confirmDelete(asset));
    actions.add(delBtn);

    // Reprocess button — re-runs AI stages (vision, semantic, sensitivity, embedding)
    // on the file that is already stored on disk; does not create a new asset
    Button reprocessBtn = new Button(getTranslation("overview.reprocess"));
    reprocessBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
    reprocessBtn.addClickListener(e -> startReprocess(asset));
    actions.add(reprocessBtn);

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
  private com.vaadin.flow.component.Component visibilityIcon(ImageAsset asset, PersistenceService ps) {
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
  private com.vaadin.flow.component.Component buildThumb(ImageAsset asset, String height) {
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

  private void confirmDelete(ImageAsset asset) {
    ConfirmDialog dialog = new ConfirmDialog();
    dialog.setHeader(getTranslation("overview.delete.title"));
    dialog.setText(getTranslation("overview.delete.text", asset.getOriginalFilename()));
    dialog.setCancelable(true);
    dialog.setCancelText(getTranslation("overview.delete.cancel"));
    dialog.setConfirmText(getTranslation("overview.delete.confirm"));
    dialog.setConfirmButtonTheme("error primary");
    dialog.addConfirmListener(e -> deleteImage(asset));
    dialog.open();
  }

  private void deleteImage(ImageAsset asset) {
    try {
      ServiceRegistry.getInstance().deleteImage(asset.getId());
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
    applyFilter();
    if (tileMode) {
      PersistenceService ps = ServiceRegistry.getInstance().getPersistenceService();
      renderTiles(filter.apply(allImages, ps));
    }
  }

  @Override
  public void beforeEnter(BeforeEnterEvent event) {
    loadData();
  }
}
