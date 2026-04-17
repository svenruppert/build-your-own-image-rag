package com.svenruppert.flow.views.taxonomy;

import com.svenruppert.imagerag.domain.CategoryRegistry;
import com.svenruppert.imagerag.domain.ImageAsset;
import com.svenruppert.imagerag.domain.SemanticAnalysis;
import com.svenruppert.imagerag.persistence.PersistenceService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Image-picker dialog for the MANUAL_SELECTION analysis scope.
 *
 * <p>Displays a filterable grid of all non-archived images.  The user selects
 * any subset and confirms; the confirmed list of image UUIDs is returned via the
 * supplied callback.  Cancelling the dialog leaves the previous selection unchanged.
 *
 * <p>Previously selected image IDs (passed as {@code preselected}) are highlighted
 * automatically when the dialog opens.
 */
public class ManualImageSelectionDialog
    extends Dialog {

  private final Grid<ImageAsset> grid = new Grid<>();
  private final Span selLabel = new Span();
  private final List<ImageAsset> allImages;

  /**
   * @param ps          persistence service to load images from
   * @param preselected previously selected image IDs (may be empty)
   * @param onConfirm   callback invoked with the confirmed list of UUIDs when the user clicks OK
   */
  public ManualImageSelectionDialog(PersistenceService ps,
                                    List<UUID> preselected,
                                    Consumer<List<UUID>> onConfirm) {
    setHeaderTitle("Select Images for Analysis");
    setWidth("740px");
    setMaxHeight("80vh");
    setCloseOnOutsideClick(false);

    allImages = new ArrayList<>(ps.findAllImages());

    // ── Filter field ──────────────────────────────────────────────────────
    TextField filter = new TextField();
    filter.setPlaceholder("Filter by filename or category\u2026");
    filter.setWidthFull();
    filter.setClearButtonVisible(true);
    filter.setValueChangeMode(ValueChangeMode.LAZY);
    filter.addValueChangeListener(e -> applyFilter(e.getValue(), ps));

    // ── Grid ──────────────────────────────────────────────────────────────
    grid.setSelectionMode(Grid.SelectionMode.MULTI);
    grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);
    grid.setWidthFull();
    grid.setHeight("380px");

    grid.addColumn(ImageAsset::getOriginalFilename)
        .setHeader("Filename").setFlexGrow(2).setSortable(true).setAutoWidth(true);

    grid.addColumn(asset -> {
      SemanticAnalysis analysis = ps.findAnalysis(asset.getId()).orElse(null);
      return analysis != null && analysis.getSourceCategory() != null
          ? CategoryRegistry.getUserLabel(analysis.getSourceCategory()) : "\u2014";
    }).setHeader("Category").setAutoWidth(true).setSortable(false);

    grid.setItems(allImages);

    // Pre-select previously selected images
    if (preselected != null && !preselected.isEmpty()) {
      Set<UUID> idSet = Set.copyOf(preselected);
      allImages.stream()
          .filter(a -> idSet.contains(a.getId()))
          .forEach(grid::select);
    }

    updateSelLabel();
    grid.addSelectionListener(e -> updateSelLabel());

    // ── Footer buttons ────────────────────────────────────────────────────
    selLabel.getStyle()
        .set("font-size", "var(--lumo-font-size-s)")
        .set("color", "var(--lumo-secondary-text-color)");

    Button selectAll = new Button("Select All", e -> {
      allImages.forEach(grid::select);
      updateSelLabel();
    });
    selectAll.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

    Button clearAll = new Button("Clear", e -> {
      grid.deselectAll();
      updateSelLabel();
    });
    clearAll.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

    Button cancelBtn = new Button("Cancel", e -> close());
    cancelBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

    Button okBtn = new Button("Confirm Selection", e -> {
      List<UUID> selected = grid.getSelectedItems().stream()
          .map(ImageAsset::getId)
          .collect(Collectors.toList());
      close();
      onConfirm.accept(selected);
    });
    okBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    HorizontalLayout selRow = new HorizontalLayout(selLabel, selectAll, clearAll);
    selRow.setAlignItems(com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.CENTER);
    selRow.setSpacing(true);

    getFooter().add(selRow, cancelBtn, okBtn);

    add(filter, grid);
  }

  private void applyFilter(String text, PersistenceService ps) {
    if (text == null || text.isBlank()) {
      grid.setItems(allImages);
    } else {
      String lower = text.toLowerCase();
      List<ImageAsset> filtered = allImages.stream()
          .filter(a -> {
            if (a.getOriginalFilename() != null
                && a.getOriginalFilename().toLowerCase().contains(lower)) return true;
            SemanticAnalysis an = ps.findAnalysis(a.getId()).orElse(null);
            if (an != null && an.getSourceCategory() != null) {
              return CategoryRegistry.getUserLabel(an.getSourceCategory())
                  .toLowerCase().contains(lower);
            }
            return false;
          })
          .collect(Collectors.toList());
      grid.setItems(filtered);
    }
    updateSelLabel();
  }

  private void updateSelLabel() {
    int count = grid.getSelectedItems().size();
    selLabel.setText(count + " image(s) selected");
  }
}
