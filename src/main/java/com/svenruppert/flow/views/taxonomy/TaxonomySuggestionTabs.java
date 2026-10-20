package com.svenruppert.flow.views.taxonomy;

import com.svenruppert.functional.result.Result;
import com.svenruppert.functional.result.Unit;
import com.svenruppert.imagerag.domain.CategoryMetadata;
import com.svenruppert.imagerag.domain.CategoryRegistry;
import com.svenruppert.imagerag.domain.TaxonomySuggestion;
import com.svenruppert.imagerag.domain.enums.CategoryLifecycleState;
import com.svenruppert.imagerag.domain.enums.SuggestionStatus;
import com.svenruppert.imagerag.service.ImageCatalogService;
import com.svenruppert.imagerag.service.TaxonomySuggestionService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridMultiSelectionModel;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.TabSheet;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Result tabs of the taxonomy maintenance view: image-level and taxonomy-level suggestion
 * grids with batch actions, the deprecated-categories grid and an additional trailing tab
 * supplied by the caller (cluster discovery).
 */
class TaxonomySuggestionTabs
    extends TabSheet {

  private static final DateTimeFormatter DATE_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

  private final TaxonomySuggestionService suggestionService;
  private final ImageCatalogService catalog;

  private final Grid<TaxonomySuggestion> imageGrid = new Grid<>();
  private final Grid<TaxonomySuggestion> taxonomyGrid = new Grid<>();
  private final Grid<CategoryMetadata> deprecatedGrid = new Grid<>();
  private final HorizontalLayout imageBatchBar = new HorizontalLayout();
  private final HorizontalLayout taxonomyBatchBar = new HorizontalLayout();

  TaxonomySuggestionTabs(TaxonomySuggestionService suggestionService,
                         ImageCatalogService catalog,
                         String trailingTabLabel,
                         Component trailingTab) {
    this.suggestionService = suggestionService;
    this.catalog = catalog;

    VerticalLayout imageTabContent = new VerticalLayout(buildImageBatchBar(), buildImageGrid());
    imageTabContent.setSpacing(false);
    imageTabContent.setPadding(false);

    VerticalLayout taxonomyTabContent =
        new VerticalLayout(buildTaxonomyBatchBar(), buildTaxonomyGrid());
    taxonomyTabContent.setSpacing(false);
    taxonomyTabContent.setPadding(false);

    VerticalLayout deprecatedTabContent = new VerticalLayout(buildDeprecatedGrid());
    deprecatedTabContent.setSpacing(false);
    deprecatedTabContent.setPadding(false);

    add(getTranslation("taxonomy.tab.images"), imageTabContent);
    add(getTranslation("taxonomy.tab.taxonomy"), taxonomyTabContent);
    add(getTranslation("taxonomy.tab.deprecated"), deprecatedTabContent);
    add(trailingTabLabel, trailingTab);
    setWidthFull();
  }

  // ── Image suggestions grid ────────────────────────────────────────────────

  private Component buildImageBatchBar() {
    Button acceptAll = new Button(getTranslation("taxonomy.batch.accept"),
                                  e -> bulkAction("accept_image"));
    Button rejectAll = new Button(getTranslation("taxonomy.batch.reject"),
                                  e -> bulkAction("reject_image"));
    Button applyAll = new Button(getTranslation("taxonomy.batch.apply"),
                                 e -> bulkAction("apply_image"));
    applyAll.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);

    imageBatchBar.add(acceptAll, rejectAll, applyAll);
    imageBatchBar.setVisible(false);
    return imageBatchBar;
  }

  private Component buildImageGrid() {
    imageGrid.setMultiSort(false);
    imageGrid.setSelectionMode(Grid.SelectionMode.MULTI);
    imageGrid.setWidthFull();
    imageGrid.setHeight("380px");

    imageGrid.addColumn(s -> s.getTargetImageFilename() != null
            ? s.getTargetImageFilename() : "—")
        .setHeader(getTranslation("taxonomy.col.filename"))
        .setSortable(true).setAutoWidth(true);

    imageGrid.addColumn(s -> s.getType() != null
            ? getTranslation("taxonomy.type." + s.getType().name()) : "—")
        .setHeader(getTranslation("taxonomy.col.type")).setAutoWidth(true);

    imageGrid.addColumn(s -> s.getCurrentCategory() != null
            ? CategoryRegistry.getUserLabel(s.getCurrentCategory()) : "—")
        .setHeader(getTranslation("taxonomy.col.current")).setAutoWidth(true);

    imageGrid.addColumn(s -> s.getSuggestedCategory() != null
            ? CategoryRegistry.getUserLabel(s.getSuggestedCategory()) : "—")
        .setHeader(getTranslation("taxonomy.col.suggested")).setAutoWidth(true);

    imageGrid.addColumn(s -> String.format("%.0f%%", s.getConfidence() * 100))
        .setHeader(getTranslation("taxonomy.col.confidence")).setAutoWidth(true);

    imageGrid.addColumn(TaxonomySuggestion::getRationale)
        .setHeader(getTranslation("taxonomy.col.rationale"))
        .setAutoWidth(true).setFlexGrow(1);

    imageGrid.addComponentColumn(s -> buildStatusBadge(s.getStatus()))
        .setHeader(getTranslation("taxonomy.col.status")).setAutoWidth(true);

    imageGrid.addComponentColumn(this::buildActionButtons)
        .setHeader(getTranslation("taxonomy.col.actions")).setAutoWidth(true);

    ((GridMultiSelectionModel<TaxonomySuggestion>) imageGrid.getSelectionModel())
        .addMultiSelectionListener(e -> imageBatchBar.setVisible(!e.getValue().isEmpty()));

    return imageGrid;
  }

  // ── Taxonomy suggestions grid ─────────────────────────────────────────────

  private Component buildTaxonomyBatchBar() {
    Button acceptAll = new Button(getTranslation("taxonomy.batch.accept"),
                                  e -> bulkAction("accept_taxonomy"));
    Button rejectAll = new Button(getTranslation("taxonomy.batch.reject"),
                                  e -> bulkAction("reject_taxonomy"));
    Button applyAll = new Button(getTranslation("taxonomy.batch.apply"),
                                 e -> bulkAction("apply_taxonomy"));
    applyAll.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);

    taxonomyBatchBar.add(acceptAll, rejectAll, applyAll);
    taxonomyBatchBar.setVisible(false);
    return taxonomyBatchBar;
  }

  private Component buildTaxonomyGrid() {
    taxonomyGrid.setMultiSort(false);
    taxonomyGrid.setSelectionMode(Grid.SelectionMode.MULTI);
    taxonomyGrid.setWidthFull();
    taxonomyGrid.setHeight("380px");

    taxonomyGrid.addColumn(s -> s.getType() != null
            ? getTranslation("taxonomy.type." + s.getType().name()) : "—")
        .setHeader(getTranslation("taxonomy.col.type")).setAutoWidth(true);

    taxonomyGrid.addColumn(s -> s.getCurrentCategory() != null
            ? CategoryRegistry.getUserLabel(s.getCurrentCategory()) : "—")
        .setHeader(getTranslation("taxonomy.col.current")).setAutoWidth(true);

    taxonomyGrid.addColumn(s -> s.getSuggestedCategory() != null
            ? CategoryRegistry.getUserLabel(s.getSuggestedCategory()) : "—")
        .setHeader(getTranslation("taxonomy.col.suggested")).setAutoWidth(true);

    taxonomyGrid.addColumn(s -> s.getSuggestedAlias() != null ? s.getSuggestedAlias() : "—")
        .setHeader(getTranslation("taxonomy.col.alias")).setAutoWidth(true);

    taxonomyGrid.addColumn(TaxonomySuggestion::getRationale)
        .setHeader(getTranslation("taxonomy.col.rationale"))
        .setAutoWidth(true).setFlexGrow(1);

    taxonomyGrid.addComponentColumn(s -> buildStatusBadge(s.getStatus()))
        .setHeader(getTranslation("taxonomy.col.status")).setAutoWidth(true);

    taxonomyGrid.addComponentColumn(this::buildActionButtons)
        .setHeader(getTranslation("taxonomy.col.actions")).setAutoWidth(true);

    ((GridMultiSelectionModel<TaxonomySuggestion>) taxonomyGrid.getSelectionModel())
        .addMultiSelectionListener(e -> taxonomyBatchBar.setVisible(!e.getValue().isEmpty()));

    return taxonomyGrid;
  }

  // ── Deprecated categories grid ────────────────────────────────────────────

  private Component buildDeprecatedGrid() {
    H3 heading = new H3(getTranslation("taxonomy.deprecated.heading"));

    deprecatedGrid.setWidthFull();
    deprecatedGrid.setHeight("280px");

    deprecatedGrid.addColumn(m -> m.getCategory() != null
            ? CategoryRegistry.getUserLabel(m.getCategory()) : "—")
        .setHeader(getTranslation("taxonomy.col.category")).setAutoWidth(true);

    deprecatedGrid.addColumn(m -> m.getLifecycleState() != null
            ? getTranslation("taxonomy.lifecycle." + m.getLifecycleState().name()) : "—")
        .setHeader(getTranslation("taxonomy.col.lifecycle")).setAutoWidth(true);

    deprecatedGrid.addColumn(m -> m.getReplacementCategory() != null
            ? CategoryRegistry.getUserLabel(m.getReplacementCategory()) : "—")
        .setHeader(getTranslation("taxonomy.col.replacement")).setAutoWidth(true);

    deprecatedGrid.addColumn(m -> m.getNotes() != null ? m.getNotes() : "—")
        .setHeader(getTranslation("taxonomy.col.notes"))
        .setAutoWidth(true).setFlexGrow(1);

    deprecatedGrid.addColumn(m -> m.getUpdatedAt() != null
            ? DATE_FMT.format(m.getUpdatedAt()) : "—")
        .setHeader(getTranslation("taxonomy.col.updated.at")).setAutoWidth(true);

    VerticalLayout layout = new VerticalLayout(heading, deprecatedGrid);
    layout.setSpacing(false);
    layout.setPadding(false);
    return layout;
  }

  // ── Action buttons per row ────────────────────────────────────────────────

  private Component buildActionButtons(TaxonomySuggestion s) {
    HorizontalLayout buttons = new HorizontalLayout();
    buttons.setSpacing(true);
    SuggestionStatus status = s.getStatus();

    if (status == SuggestionStatus.OPEN || status == SuggestionStatus.ACCEPTED) {
      Button acceptBtn = new Button(getTranslation("taxonomy.action.accept"), e -> {
        suggestionService.accept(s.getId());
        refreshAllGrids();
        TaxonomyNotifications.show(getTranslation("taxonomy.accepted"), false);
      });
      acceptBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_SUCCESS);
      acceptBtn.setEnabled(status == SuggestionStatus.OPEN);

      Button rejectBtn = new Button(getTranslation("taxonomy.action.reject"), e -> {
        suggestionService.reject(s.getId());
        refreshAllGrids();
        TaxonomyNotifications.show(getTranslation("taxonomy.rejected"), false);
      });
      rejectBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);

      Button applyBtn = new Button(getTranslation("taxonomy.action.apply"),
                                   e -> applyWithConfirm(s));
      applyBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);

      buttons.add(acceptBtn, rejectBtn, applyBtn);
    } else {
      buttons.add(buildStatusBadge(status));
    }
    return buttons;
  }

  private Span buildStatusBadge(SuggestionStatus status) {
    String theme = switch (status) {
      case OPEN -> "badge";
      case ACCEPTED -> "badge success";
      case REJECTED -> "badge error";
      case APPLIED -> "badge contrast";
    };
    Span badge = new Span(getTranslation("taxonomy.status." + status.name()));
    badge.getElement().getThemeList().add(theme);
    return badge;
  }

  // ── Bulk actions ──────────────────────────────────────────────────────────

  private void bulkAction(String action) {
    boolean isImage = action.contains("image");
    Grid<TaxonomySuggestion> grid = isImage ? imageGrid : taxonomyGrid;
    Set<TaxonomySuggestion> selected = grid.getSelectedItems();
    if (selected.isEmpty()) return;

    List<UUID> ids = selected.stream()
        .map(TaxonomySuggestion::getId)
        .collect(Collectors.toList());

    if (action.startsWith("accept")) {
      suggestionService.bulkAccept(ids);
      TaxonomyNotifications.show(getTranslation("taxonomy.bulk.accepted", ids.size()), false);
      refreshAllGrids();
    } else if (action.startsWith("reject")) {
      suggestionService.bulkReject(ids);
      TaxonomyNotifications.show(getTranslation("taxonomy.bulk.rejected", ids.size()), false);
      refreshAllGrids();
    } else if (action.startsWith("apply")) {
      ConfirmDialog confirm = new ConfirmDialog();
      confirm.setHeader(getTranslation("taxonomy.bulk.apply.confirm.title"));
      confirm.setText(getTranslation("taxonomy.bulk.apply.confirm.text", ids.size()));
      confirm.setCancelable(true);
      confirm.setCancelText(getTranslation("common.cancel"));
      confirm.setConfirmText(getTranslation("taxonomy.action.apply"));
      confirm.addConfirmListener(ev -> {
        int applied = suggestionService.bulkApply(ids);
        refreshAllGrids();
        TaxonomyNotifications.show(getTranslation("taxonomy.bulk.applied", applied), false);
      });
      confirm.open();
    }
  }

  private void applyWithConfirm(TaxonomySuggestion s) {
    ConfirmDialog confirm = new ConfirmDialog();
    confirm.setHeader(getTranslation("taxonomy.apply.confirm.title"));
    confirm.setText(buildSuggestionDetail(s));
    confirm.setCancelable(true);
    confirm.setCancelText(getTranslation("common.cancel"));
    confirm.setConfirmText(getTranslation("taxonomy.action.apply"));
    confirm.addConfirmListener(e -> {
      switch (suggestionService.apply(s.getId())) {
        case Result.Success<Unit, String> done ->
            TaxonomyNotifications.show(getTranslation("taxonomy.applied"), false);
        case Result.Failure<Unit, String>(String error) ->
            TaxonomyNotifications.show(getTranslation("taxonomy.apply.failed", error), true);
      }
      refreshAllGrids();
    });
    confirm.open();
  }

  private String buildSuggestionDetail(TaxonomySuggestion s) {
    StringBuilder sb = new StringBuilder();
    if (s.getTargetImageFilename() != null) {
      sb.append(getTranslation("taxonomy.col.filename")).append(": ")
          .append(s.getTargetImageFilename()).append("\n");
    }
    if (s.getCurrentCategory() != null) {
      sb.append(getTranslation("taxonomy.col.current")).append(": ")
          .append(CategoryRegistry.getUserLabel(s.getCurrentCategory())).append(" → ");
    }
    if (s.getSuggestedCategory() != null) {
      sb.append(CategoryRegistry.getUserLabel(s.getSuggestedCategory()));
    }
    return sb.toString();
  }

  // ── Grid refresh ──────────────────────────────────────────────────────────

  /**
   * Reloads all suggestion grids and the deprecated-categories grid.
   */
  void refreshAllGrids() {
    List<TaxonomySuggestion> all = suggestionService.findAll();

    List<TaxonomySuggestion> allImageLevel = all.stream()
        .filter(TaxonomySuggestion::isImageLevel)
        .collect(Collectors.toList());
    List<TaxonomySuggestion> taxonomyLevel = all.stream()
        .filter(s -> !s.isImageLevel())
        .collect(Collectors.toList());

    imageGrid.setItems(allImageLevel);
    taxonomyGrid.setItems(taxonomyLevel);

    List<CategoryMetadata> deprecated =
        catalog.findAllCategoryMetadata().stream()
            .filter(m -> m.getLifecycleState() == CategoryLifecycleState.DEPRECATED)
            .collect(Collectors.toList());
    deprecatedGrid.setItems(deprecated);
  }
}
