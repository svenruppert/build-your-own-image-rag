package com.svenruppert.flow.views.detail;

import com.svenruppert.imagerag.domain.CategoryRegistry;
import com.svenruppert.imagerag.domain.enums.CategoryGroup;
import com.svenruppert.imagerag.domain.enums.SourceCategory;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Tree-structured category chooser dialog.
 * <p>Categories are grouped by their {@link CategoryGroup}.  Each group is shown as an
 * expandable {@link Details} section; clicking any category button calls the provided
 * {@code onSelect} callback and closes the dialog.
 * <p>Typical usage:
 * <pre>{@code
 * Button openBtn = new Button("Choose category…");
 * openBtn.addClickListener(e ->
 *     new CategoryTreeChooserDialog(cat -> {
 *         // update asset category …
 *     }).open());
 * }</pre>
 */
public class CategoryTreeChooserDialog
    extends Dialog {

  /**
   * @param onSelect called with the chosen {@link SourceCategory} when the user clicks a
   *                 category button; the dialog closes automatically afterwards.
   */
  public CategoryTreeChooserDialog(Consumer<SourceCategory> onSelect) {
    setHeaderTitle(getTranslation("overview.category.chooser.title"));
    setWidth("560px");
    setMaxHeight("80vh");
    setCloseOnOutsideClick(true);

    VerticalLayout content = new VerticalLayout();
    content.setPadding(false);
    content.setSpacing(true);

    // Group all SourceCategory values by their CategoryGroup
    Map<CategoryGroup, List<SourceCategory>> grouped = new LinkedHashMap<>();
    for (CategoryGroup group : CategoryGroup.values()) {
      grouped.put(group, new ArrayList<>());
    }
    for (SourceCategory cat : SourceCategory.values()) {
      CategoryGroup group = CategoryRegistry.getGroup(cat);
      grouped.computeIfAbsent(group, g -> new ArrayList<>()).add(cat);
    }

    for (Map.Entry<CategoryGroup, List<SourceCategory>> entry : grouped.entrySet()) {
      List<SourceCategory> cats = entry.getValue();
      if (cats.isEmpty()) continue;

      // ── Category buttons grid ──────────────────────────────────────────
      Div buttonGrid = new Div();
      buttonGrid.getStyle()
          .set("display", "flex")
          .set("flex-wrap", "wrap")
          .set("gap", "var(--lumo-space-xs)")
          .set("padding", "var(--lumo-space-xs) 0");

      for (SourceCategory cat : cats) {
        Button catBtn = new Button(CategoryRegistry.getUserLabel(cat));
        catBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        catBtn.addClickListener(e -> {
          onSelect.accept(cat);
          close();
        });
        buttonGrid.add(catBtn);
      }

      // ── Expandable group section ───────────────────────────────────────
      Span groupLabel = new Span(entry.getKey().name().replace("_", " "));
      groupLabel.getStyle().set("font-weight", "600");

      Details section = new Details(groupLabel, buttonGrid);
      section.setWidthFull();
      // Expand the first group by default for discoverability
      if (content.getComponentCount() == 0) {
        section.setOpened(true);
      }
      content.add(section);
    }

    add(content);

    Button cancelBtn = new Button(getTranslation("common.cancel"), e -> close());
    cancelBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    getFooter().add(cancelBtn);
  }
}
