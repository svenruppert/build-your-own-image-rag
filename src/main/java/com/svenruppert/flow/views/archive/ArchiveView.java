package com.svenruppert.flow.views.archive;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.MainLayout;
import com.svenruppert.flow.views.detail.DetailDialog;
import com.svenruppert.imagerag.bootstrap.ServiceRegistry;
import com.svenruppert.imagerag.domain.CategoryRegistry;
import com.svenruppert.imagerag.domain.ImageAsset;
import com.svenruppert.imagerag.domain.SemanticAnalysis;
import com.svenruppert.imagerag.domain.SensitivityAssessment;
import com.svenruppert.imagerag.persistence.PersistenceService;
import com.svenruppert.imagerag.service.PreviewService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

import static com.vaadin.flow.component.icon.VaadinIcon.REFRESH;
import static com.vaadin.flow.component.icon.VaadinIcon.TRASH;
import static com.vaadin.flow.component.icon.VaadinIcon.REPLY;

/**
 * Dedicated view for browsing and managing archived (soft-deleted) images.
 *
 * <p>Archived images are those where {@link ImageAsset#isDeleted()} is {@code true}.
 * They are hidden from the normal Overview and Search flows but remain in persistent
 * storage until explicitly deleted permanently.
 *
 * <p>Actions available per row and in batch:
 * <ul>
 *   <li><b>Restore</b> — clears the deleted flag and re-approves/re-indexes the image
 *       so it reappears in Overview and Search.</li>
 *   <li><b>Delete Permanently</b> — irreversibly removes the image from all storage
 *       layers (file system, EclipseStore, vector index, keyword index, preview cache).</li>
 * </ul>
 */
@PageTitle("Archive")
@Route(value = ArchiveView.PATH, layout = MainLayout.class)
public class ArchiveView
    extends VerticalLayout
    implements BeforeEnterObserver, HasLogger {

  public static final String PATH = "archive";

  private static final DateTimeFormatter DATE_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

  private final Grid<ImageAsset> grid = new Grid<>(ImageAsset.class, false);
  private final Span statsLabel = new Span();
  private final HorizontalLayout batchToolbar = new HorizontalLayout();
  private final Span selectedCountLabel = new Span();

  private List<ImageAsset> archivedImages = List.of();

  public ArchiveView() {
    setWidthFull();
    setPadding(true);
    setSpacing(true);

    // ── Header row ────────────────────────────────────────────────────────────
    H2 title = new H2(getTranslation("archive.title"));
    statsLabel.getStyle()
        .set("color", "var(--lumo-secondary-text-color)")
        .set("font-size", "var(--lumo-font-size-s)")
        .set("align-self", "flex-end");
    Button refreshBtn = new Button(getTranslation("archive.refresh"),
                                   REFRESH.create(), e -> loadData());
    refreshBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

    HorizontalLayout header = new HorizontalLayout(title, statsLabel, refreshBtn);
    header.setAlignItems(Alignment.BASELINE);
    header.setSpacing(true);
    add(header);

    // ── Batch toolbar (shown only when rows are selected) ─────────────────────
    add(buildBatchToolbar());

    // ── Archive grid ──────────────────────────────────────────────────────────
    configureGrid();
    add(grid);
  }

  // ── Lifecycle ─────────────────────────────────────────────────────────────

  @Override
  public void beforeEnter(BeforeEnterEvent event) {
    loadData();
  }

  // ── Grid setup ────────────────────────────────────────────────────────────

  private void configureGrid() {
    grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_NO_BORDER);
    grid.setSelectionMode(Grid.SelectionMode.MULTI);
    grid.setWidthFull();

    // Update batch toolbar visibility and count on selection change
    grid.addSelectionListener(e -> {
      int count = e.getAllSelectedItems().size();
      batchToolbar.setVisible(count > 0);
      selectedCountLabel.setText(count > 0
                                     ? getTranslation("overview.selected.count", count) : "");
    });

    // Preview thumbnail
    grid.addComponentColumn(asset -> buildPreviewCell(asset))
        .setHeader(getTranslation("overview.col.preview"))
        .setWidth("80px")
        .setFlexGrow(0);

    // Filename — double-click opens detail dialog
    grid.addComponentColumn(asset -> {
      Span name = new Span(asset.getOriginalFilename());
      name.getStyle()
          .set("font-weight", "500")
          .set("cursor", "pointer");
      name.getElement().setAttribute("title", asset.getOriginalFilename());
      name.addClickListener(e -> {
        PersistenceService ps = ServiceRegistry.getInstance().getPersistenceService();
        SemanticAnalysis analysis = ps.findAnalysis(asset.getId()).orElse(null);
        SensitivityAssessment assessment = ps.findAssessment(asset.getId()).orElse(null);
        com.svenruppert.imagerag.domain.LocationSummary location =
            ps.findLocation(asset.getId()).orElse(null);
        new DetailDialog(asset, analysis, assessment, location).open();
      });
      return name;
    }).setHeader(getTranslation("overview.col.filename")).setFlexGrow(2).setSortable(false);

    // Archived at timestamp
    grid.addColumn(asset -> asset.getDeletedAt() != null
            ? DATE_FMT.format(asset.getDeletedAt()) : "—")
        .setHeader(getTranslation("archive.col.archived.at"))
        .setWidth("150px")
        .setFlexGrow(0)
        .setSortable(true)
        .setComparator((a, b) -> {
          if (a.getDeletedAt() == null && b.getDeletedAt() == null) return 0;
          if (a.getDeletedAt() == null) return 1;
          if (b.getDeletedAt() == null) return -1;
          return a.getDeletedAt().compareTo(b.getDeletedAt());
        });

    // Archive reason
    grid.addColumn(asset -> asset.getDeletedReason() != null ? asset.getDeletedReason() : "—")
        .setHeader(getTranslation("archive.col.reason"))
        .setFlexGrow(2);

    // Category badge
    grid.addComponentColumn(this::buildCategoryCell)
        .setHeader(getTranslation("overview.col.category"))
        .setWidth("160px")
        .setFlexGrow(0);

    // Risk level badge
    grid.addComponentColumn(this::buildRiskCell)
        .setHeader(getTranslation("overview.col.risk"))
        .setWidth("100px")
        .setFlexGrow(0);

    // Restore action
    grid.addComponentColumn(asset -> {
      Button btn = new Button(getTranslation("archive.restore"), REPLY.create());
      btn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_SUCCESS,
                           ButtonVariant.LUMO_TERTIARY);
      btn.addClickListener(e -> confirmRestore(asset));
      return btn;
    }).setHeader("").setWidth("140px").setFlexGrow(0);

    // Permanently delete action
    grid.addComponentColumn(asset -> {
      Button btn = new Button(getTranslation("archive.delete.permanent"), TRASH.create());
      btn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR,
                           ButtonVariant.LUMO_TERTIARY);
      btn.addClickListener(e -> confirmPermanentDelete(asset));
      return btn;
    }).setHeader("").setWidth("175px").setFlexGrow(0);
  }

  // ── Batch toolbar ─────────────────────────────────────────────────────────

  private HorizontalLayout buildBatchToolbar() {
    selectedCountLabel.getStyle()
        .set("font-size", "var(--lumo-font-size-s)")
        .set("align-self", "flex-end");

    Button restoreAllBtn = new Button(getTranslation("archive.batch.restore"),
                                      REPLY.create(), e -> batchRestore());
    restoreAllBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_SUCCESS,
                                   ButtonVariant.LUMO_TERTIARY);

    Button deleteAllBtn = new Button(getTranslation("archive.batch.delete"),
                                     TRASH.create(), e -> confirmBatchDelete());
    deleteAllBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR,
                                  ButtonVariant.LUMO_TERTIARY);

    batchToolbar.add(selectedCountLabel, restoreAllBtn, deleteAllBtn);
    batchToolbar.setAlignItems(Alignment.CENTER);
    batchToolbar.setSpacing(true);
    batchToolbar.setVisible(false);
    return batchToolbar;
  }

  // ── Actions ───────────────────────────────────────────────────────────────

  private void confirmRestore(ImageAsset asset) {
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle(getTranslation("archive.restore.confirm.title"));

    Paragraph body = new Paragraph(
        getTranslation("archive.restore.confirm.text", asset.getOriginalFilename()));
    dialog.add(body);

    Button cancelBtn = new Button(getTranslation("common.cancel"), e -> dialog.close());
    cancelBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

    Button confirmBtn = new Button(getTranslation("archive.restore.confirm.confirm"), e -> {
      restoreImage(asset);
      dialog.close();
    });
    confirmBtn.addThemeVariants(ButtonVariant.LUMO_SUCCESS, ButtonVariant.LUMO_PRIMARY);

    dialog.getFooter().add(cancelBtn, confirmBtn);
    dialog.open();
  }

  private void restoreImage(ImageAsset asset) {
    try {
      ServiceRegistry.getInstance().restoreImage(asset.getId());
      ServiceRegistry.getInstance().getAuditService()
          .log("RESTORE", asset.getId(), asset.getOriginalFilename(),
               "Restored from archive view");
      Notification n = Notification.show(
          getTranslation("archive.restore.success", asset.getOriginalFilename()),
          3000, Notification.Position.BOTTOM_END);
      n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
      loadData();
    } catch (Exception ex) {
      logger().error("Restore failed for {}: {}", asset.getId(), ex.getMessage(), ex);
      Notification n = Notification.show(
          getTranslation("archive.restore.error", ex.getMessage()),
          5000, Notification.Position.MIDDLE);
      n.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
  }

  private void confirmPermanentDelete(ImageAsset asset) {
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle(getTranslation("archive.delete.confirm.title"));

    Paragraph body = new Paragraph(
        getTranslation("archive.delete.confirm.text", asset.getOriginalFilename()));
    dialog.add(body);

    // Visual warning — permanent action
    Span warning = new Span(getTranslation("archive.delete.confirm.warning"));
    warning.getStyle()
        .set("color", "var(--lumo-error-color)")
        .set("font-weight", "600")
        .set("font-size", "var(--lumo-font-size-s)");
    dialog.add(warning);

    Button cancelBtn = new Button(getTranslation("common.cancel"), e -> dialog.close());
    cancelBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

    Button confirmBtn = new Button(getTranslation("archive.delete.confirm.confirm"), e -> {
      permanentlyDeleteImage(asset);
      dialog.close();
    });
    confirmBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_PRIMARY);

    dialog.getFooter().add(cancelBtn, confirmBtn);
    dialog.open();
  }

  private void permanentlyDeleteImage(ImageAsset asset) {
    try {
      ServiceRegistry.getInstance().deleteImage(asset.getId());
      ServiceRegistry.getInstance().getAuditService()
          .log("PERMANENT_DELETE", asset.getId(), asset.getOriginalFilename(),
               "Permanently deleted from archive view");
      Notification n = Notification.show(
          getTranslation("archive.deleted.success", asset.getOriginalFilename()),
          3000, Notification.Position.BOTTOM_END);
      n.addThemeVariants(NotificationVariant.LUMO_CONTRAST);
      loadData();
    } catch (Exception ex) {
      logger().error("Permanent delete failed for {}: {}", asset.getId(), ex.getMessage(), ex);
      Notification n = Notification.show(
          getTranslation("archive.deleted.error", ex.getMessage()),
          5000, Notification.Position.MIDDLE);
      n.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
  }

  private void batchRestore() {
    Set<ImageAsset> selected = new java.util.HashSet<>(grid.getSelectedItems());
    int succeeded = 0;
    for (ImageAsset asset : selected) {
      try {
        ServiceRegistry.getInstance().restoreImage(asset.getId());
        ServiceRegistry.getInstance().getAuditService()
            .log("RESTORE", asset.getId(), asset.getOriginalFilename(),
                 "Batch restored from archive view");
        succeeded++;
      } catch (Exception ex) {
        logger().warn("Batch restore failed for {}: {}", asset.getId(), ex.getMessage());
      }
    }
    Notification n = Notification.show(
        getTranslation("archive.batch.restore.success", succeeded),
        3000, Notification.Position.BOTTOM_END);
    n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    loadData();
  }

  private void confirmBatchDelete() {
    Set<ImageAsset> selected = new java.util.HashSet<>(grid.getSelectedItems());
    if (selected.isEmpty()) return;

    Dialog dialog = new Dialog();
    dialog.setHeaderTitle(getTranslation("archive.delete.confirm.title"));

    Paragraph body = new Paragraph(
        getTranslation("archive.batch.delete.confirm.text", selected.size()));
    dialog.add(body);

    Span warning = new Span(getTranslation("archive.delete.confirm.warning"));
    warning.getStyle()
        .set("color", "var(--lumo-error-color)")
        .set("font-weight", "600")
        .set("font-size", "var(--lumo-font-size-s)");
    dialog.add(warning);

    Button cancelBtn = new Button(getTranslation("common.cancel"), e -> dialog.close());
    cancelBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

    Button confirmBtn = new Button(getTranslation("archive.delete.confirm.confirm"), e -> {
      batchPermanentDelete(selected);
      dialog.close();
    });
    confirmBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_PRIMARY);

    dialog.getFooter().add(cancelBtn, confirmBtn);
    dialog.open();
  }

  private void batchPermanentDelete(Set<ImageAsset> toDelete) {
    int succeeded = 0;
    for (ImageAsset asset : toDelete) {
      try {
        ServiceRegistry.getInstance().deleteImage(asset.getId());
        ServiceRegistry.getInstance().getAuditService()
            .log("PERMANENT_DELETE", asset.getId(), asset.getOriginalFilename(),
                 "Batch permanently deleted from archive view");
        succeeded++;
      } catch (Exception ex) {
        logger().warn("Batch permanent delete failed for {}: {}", asset.getId(), ex.getMessage());
      }
    }
    Notification n = Notification.show(
        getTranslation("archive.batch.deleted.success", succeeded),
        3000, Notification.Position.BOTTOM_END);
    n.addThemeVariants(NotificationVariant.LUMO_CONTRAST);
    loadData();
  }

  // ── Data ──────────────────────────────────────────────────────────────────

  private void loadData() {
    PersistenceService ps = ServiceRegistry.getInstance().getPersistenceService();
    archivedImages = ps.findArchivedImages();
    grid.setItems(archivedImages);
    grid.deselectAll();
    batchToolbar.setVisible(false);
    statsLabel.setText(getTranslation("archive.stats", archivedImages.size()));
  }

  // ── Cell builders ─────────────────────────────────────────────────────────

  private Component buildPreviewCell(ImageAsset asset) {
    ServiceRegistry sr = ServiceRegistry.getInstance();
    try {
      Path imgPath = sr.getImageStorageService().resolvePath(asset.getId());
      if (!Files.exists(imgPath)) return new Span("—");
      StreamResource res = sr.getPreviewService().getPreview(
          asset.getId(), imgPath, asset.getStoredFilename(), PreviewService.PreviewSize.TABLE);
      if (res == null) return new Span("—");
      Image img = new Image(res, asset.getOriginalFilename());
      img.setHeight("44px");
      img.getStyle().set("object-fit", "contain").set("border-radius", "4px");
      return img;
    } catch (Exception e) {
      return new Span("—");
    }
  }

  private Component buildCategoryCell(ImageAsset asset) {
    return ServiceRegistry.getInstance().getPersistenceService()
        .findAnalysis(asset.getId())
        .map(SemanticAnalysis::getSourceCategory)
        .map(cat -> {
          Span badge = new Span(CategoryRegistry.getUserLabel(cat));
          badge.getElement().getThemeList().add("badge");
          badge.getElement().getThemeList().add("contrast");
          return (Component) badge;
        })
        .orElseGet(() -> new Span("—"));
  }

  private Component buildRiskCell(ImageAsset asset) {
    return ServiceRegistry.getInstance().getPersistenceService()
        .findAssessment(asset.getId())
        .map(SensitivityAssessment::getRiskLevel)
        .map(risk -> {
          Span badge = new Span(risk.name());
          badge.getElement().getThemeList().add("badge");
          switch (risk) {
            case SAFE -> badge.getElement().getThemeList().add("success");
            case REVIEW -> badge.getElement().getThemeList().add("contrast");
            case SENSITIVE -> badge.getElement().getThemeList().add("error");
            default -> throw new IllegalStateException("Unexpected risk: " + risk);
          }
          return (Component) badge;
        })
        .orElseGet(() -> new Span("—"));
  }
}
