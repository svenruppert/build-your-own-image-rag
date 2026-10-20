package com.svenruppert.flow.views.overview;

import com.svenruppert.flow.views.detail.CategoryTreeChooserDialog;
import com.svenruppert.flow.views.shared.ImagePreviewFactory;
import com.svenruppert.flow.views.shared.ViewServices;
import com.svenruppert.imagerag.domain.CategoryRegistry;
import com.svenruppert.imagerag.domain.ImageAsset;
import com.svenruppert.imagerag.domain.SemanticAnalysis;
import com.svenruppert.imagerag.domain.enums.SourceCategory;
import com.svenruppert.imagerag.service.ImageCatalogService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;

/**
 * Table view of the overview: multi-select grid with thumbnail, editable category and
 * risk, visibility toggle, details and delete per row. The grid sizes to fit all rows so
 * the page scrolls naturally.
 */
class OverviewImageGrid
    extends Grid<ImageAsset> {

  OverviewImageGrid(ViewServices services, ImagePreviewFactory previewFactory,
                    OverviewImageActions actions, Runnable reload) {
    super(ImageAsset.class, false);

    setWidthFull();
    // setAllRowsVisible: grid sizes to fit all rows; the page scrolls naturally.
    setAllRowsVisible(true);
    addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
    setSelectionMode(Grid.SelectionMode.MULTI);

    // Thumbnail
    addComponentColumn(asset -> previewFactory.tile(asset, "60px"))
        .setHeader(getTranslation("overview.col.preview")).setWidth("80px").setFlexGrow(0);

    // Filename
    addColumn(ImageAsset::getOriginalFilename)
        .setHeader(getTranslation("overview.col.filename")).setFlexGrow(2).setSortable(true);

    // Category (editable — tree-structured chooser dialog)
    addComponentColumn(asset -> {
      ImageCatalogService ps = services.catalog();
      SourceCategory cur = ps.findAnalysis(asset.getId()).map(SemanticAnalysis::getSourceCategory).orElse(null);
      String label = cur != null ? CategoryRegistry.getUserLabel(cur) : "—";
      Button chooseBtn = new Button(label);
      chooseBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
      chooseBtn.getStyle().set("font-size", "var(--lumo-font-size-s)");
      chooseBtn.addClickListener(e ->
                                     new CategoryTreeChooserDialog(selected -> {
                                       services.moderation().changeSourceCategory(asset.getId(), selected);
                                       OverviewImageActions.showNotification(
                                           getTranslation("overview.category.updated",
                                                          CategoryRegistry.getUserLabel(selected)), true);
                                       reload.run();
                                     }).open());
      return chooseBtn;
    }).setHeader(getTranslation("overview.col.category")).setFlexGrow(1);

    // Description
    addComponentColumn(asset -> {
      ImageCatalogService ps = services.catalog();
      return ps.findAnalysis(asset.getId()).map(a -> {
        String s = a.getShortSummary();
        Span span = new Span(s != null ? s : "—");
        span.getStyle().set("white-space", "normal");
        return span;
      }).orElse(new Span("—"));
    }).setHeader(getTranslation("overview.col.description")).setFlexGrow(4);

    // Location
    addComponentColumn(asset -> {
      ImageCatalogService ps = services.catalog();
      return ps.findLocation(asset.getId())
          .map(l -> new Span(l.toHumanReadable()))
          .orElse(new Span("—"));
    }).setHeader(getTranslation("overview.col.location")).setFlexGrow(2);

    // Risk (editable)
    addComponentColumn(actions::riskSelect)
        .setHeader(getTranslation("overview.col.risk")).setWidth("140px").setFlexGrow(0);

    // Visibility toggle
    addComponentColumn(actions::approveButton)
        .setHeader(getTranslation("overview.col.visibility")).setWidth("140px").setFlexGrow(0);

    // Details
    addComponentColumn(asset -> {
      Button btn = new Button(getTranslation("overview.details.button"), VaadinIcon.INFO_CIRCLE.create());
      btn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
      btn.addClickListener(e -> actions.openDetailDialog(asset));
      return btn;
    }).setHeader("").setWidth("110px").setFlexGrow(0);

    // Delete
    addComponentColumn(asset -> {
      Button del = new Button(VaadinIcon.TRASH.create());
      del.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
      del.getElement().setAttribute("title", getTranslation("overview.delete.title"));
      del.addClickListener(e -> actions.confirmDelete(asset));
      return del;
    }).setHeader("").setWidth("60px").setFlexGrow(0);
  }
}
