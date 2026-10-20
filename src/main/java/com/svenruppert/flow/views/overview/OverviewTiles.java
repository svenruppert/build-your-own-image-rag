package com.svenruppert.flow.views.overview;

import com.svenruppert.flow.views.shared.ImagePreviewFactory;
import com.svenruppert.flow.views.shared.RiskBadge;
import com.svenruppert.flow.views.shared.ViewServices;
import com.svenruppert.imagerag.domain.CategoryRegistry;
import com.svenruppert.imagerag.domain.ImageAsset;
import com.svenruppert.imagerag.domain.SemanticAnalysis;
import com.svenruppert.imagerag.domain.SensitivityAssessment;
import com.svenruppert.imagerag.domain.enums.RiskLevel;
import com.svenruppert.imagerag.domain.enums.SourceCategory;
import com.svenruppert.imagerag.service.ImageCatalogService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Tile view of the overview: a CSS grid of fixed-height cards rendered page by page.
 * The "load more" button is a separate component ({@link #loadMoreButton()}) so the view
 * can place it below the tile container.
 */
class OverviewTiles
    extends Div {

  private static final int PAGE_SIZE = 30;

  private final ViewServices services;
  private final ImagePreviewFactory previewFactory;
  private final OverviewImageActions actions;
  private final Button loadMoreTilesBtn = new Button();

  private List<ImageAsset> images = List.of();
  /**
   * Number of tile cards currently rendered.
   * Incremented by {@link #appendTiles} so "Load more" only renders NEW tiles
   * instead of destroying and recreating all existing ones.
   */
  private int renderedTileCount = 0;

  OverviewTiles(ViewServices services, ImagePreviewFactory previewFactory,
                OverviewImageActions actions) {
    this.services = services;
    this.previewFactory = previewFactory;
    this.actions = actions;

    // No fixed height or overflow-y: the page scrolls naturally as the grid grows.
    getStyle()
        .set("display", "grid")
        .set("grid-template-columns", "repeat(auto-fill, minmax(220px, 1fr))")
        .set("gap", "1rem")
        .set("padding", "0.5rem")
        .set("width", "100%");
    setVisible(false);

    loadMoreTilesBtn.setText(getTranslation("overview.loadmore"));
    loadMoreTilesBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    loadMoreTilesBtn.setVisible(false);
    // Append the next page of tiles WITHOUT destroying already-rendered cards.
    loadMoreTilesBtn.addClickListener(e -> appendTiles(PAGE_SIZE));
  }

  Button loadMoreButton() {
    return loadMoreTilesBtn;
  }

  /**
   * Full reset: clears all tile cards and renders the first {@link #PAGE_SIZE} items.
   * Call this whenever the displayed list changes (filter change, mode toggle, data reload).
   */
  void render(List<ImageAsset> images) {
    this.images = images;
    removeAll();
    renderedTileCount = 0;
    appendTiles(PAGE_SIZE);
  }

  /**
   * Appends up to {@code count} new tile cards starting at {@link #renderedTileCount}.
   * Does NOT clear previously rendered cards — call {@link #render} for that.
   */
  private void appendTiles(int count) {
    int from = renderedTileCount;
    int to = Math.min(from + count, images.size());
    for (int i = from; i < to; i++) {
      add(buildTileCard(images.get(i)));
    }
    renderedTileCount = to;
    loadMoreTilesBtn.setVisible(renderedTileCount < images.size());
  }

  private Div buildTileCard(ImageAsset asset) {
    ImageCatalogService ps = services.catalog();

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
    card.getElement().addEventListener("dblclick", e -> actions.openDetailDialog(asset));

    // Thumbnail — fixed height, does not shrink
    Div thumbWrapper = new Div(previewFactory.tile(asset, "150px"));
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
          + " · " + CategoryRegistry.getGroupLabel(cat);
      Span catSpan = new Span(catDisplay);
      catSpan.getStyle().set("font-size", "var(--lumo-font-size-xs)")
          .set("color", "var(--lumo-secondary-text-color)");
      info.add(catSpan);
    }

    // Secondary categories — compact comma-separated list
    if (tileAnalysis != null) {
      List<SourceCategory> secondary = tileAnalysis.getSecondaryCategories();
      if (secondary != null && !secondary.isEmpty()) {
        String secDisplay = secondary.stream()
            .map(CategoryRegistry::getUserLabel)
            .collect(Collectors.joining(", "));
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
    iconRow.setAlignItems(FlexComponent.Alignment.CENTER);

    RiskLevel risk = ps.findAssessment(asset.getId()).map(SensitivityAssessment::getRiskLevel).orElse(null);
    iconRow.add(RiskBadge.icon(risk, "18px"));
    iconRow.add(actions.visibilityIcon(asset));
    info.add(iconRow);

    // Action area — two rows for cleaner grouping and reduced crowding.
    // Row 1 (informational): Details + Reprocess — open or enrich the image
    // Row 2 (state-changing): visibility toggle + delete — modify or remove

    Button detailBtn = new Button(getTranslation("overview.details.button"));
    detailBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
    detailBtn.addClickListener(e -> actions.openDetailDialog(asset));

    Button reprocessBtn = new Button(getTranslation("overview.reprocess"));
    reprocessBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
    reprocessBtn.addClickListener(e -> actions.startReprocess(asset));

    HorizontalLayout actionRow1 = new HorizontalLayout(detailBtn, reprocessBtn);
    actionRow1.setSpacing(true);
    actionRow1.setPadding(false);

    Button delBtn = new Button(VaadinIcon.TRASH.create());
    delBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
    delBtn.getElement().setAttribute("title", getTranslation("overview.delete.title"));
    delBtn.addClickListener(e -> actions.confirmDelete(asset));

    HorizontalLayout actionRow2 = new HorizontalLayout(actions.approveButton(asset), delBtn);
    actionRow2.setSpacing(true);
    actionRow2.setPadding(false);
    actionRow2.setAlignItems(FlexComponent.Alignment.CENTER);

    VerticalLayout actionsLayout = new VerticalLayout(actionRow1, actionRow2);
    actionsLayout.setPadding(false);
    actionsLayout.setSpacing(false);
    actionsLayout.getStyle().set("gap", "0.15rem");

    info.add(actionsLayout);
    card.add(info);
    return card;
  }
}
