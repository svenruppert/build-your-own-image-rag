package com.svenruppert.flow.views.shared;

import com.svenruppert.imagerag.domain.CategoryRegistry;
import com.svenruppert.imagerag.domain.enums.CategoryGroup;
import com.svenruppert.imagerag.domain.enums.SourceCategory;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.select.Select;

import java.util.List;

/**
 * A two-level cascading category selector for the image-archive filter bar.
 * <p>The first dropdown lists coarse {@link CategoryGroup} buckets ("Nature",
 * "Animals", …).  When a group is chosen the second dropdown is populated with
 * the fine-grained {@link SourceCategory} values that belong to that group
 * (e.g. "Landscape", "Mountain", "Forest", …) and made visible.  Clearing the
 * group dropdown hides and clears the second one as well.
 * <p>Register a unified change callback via {@link #addChangeListener(Runnable)};
 * it fires whenever either select changes.  Read the current selection through
 * {@link #getSelectedGroup()} and {@link #getSelectedCategory()}.
 */
public class CategoryChooserComponent
    extends HorizontalLayout {

  private final Select<CategoryGroup> groupSelect = new Select<>();
  private final Select<SourceCategory> categorySelect = new Select<>();

  /**
   * Creates the component.
   *
   * @param groupLabel    i18n label for the coarse-group dropdown
   * @param categoryLabel i18n label for the specific-category dropdown
   * @param anyLabel      placeholder shown when nothing is selected ("Any", "Alle", …)
   */
  public CategoryChooserComponent(String groupLabel, String categoryLabel, String anyLabel) {
    setSpacing(true);
    setAlignItems(Alignment.END);
    setPadding(false);

    // ── Group select ──────────────────────────────────────────────────────────
    groupSelect.setLabel(groupLabel);
    groupSelect.setItems(CategoryGroup.values());
    groupSelect.setPlaceholder(anyLabel);
    groupSelect.setItemLabelGenerator(g -> g == null ? anyLabel : g.getLabel());
    groupSelect.setWidth("180px");

    // ── Specific category select — hidden until a group is chosen ─────────────
    categorySelect.setLabel(categoryLabel);
    categorySelect.setPlaceholder(anyLabel);
    categorySelect.setWidth("200px");
    categorySelect.setVisible(false);

    groupSelect.addValueChangeListener(e -> {
      CategoryGroup group = e.getValue();
      categorySelect.clear();
      if (group == null) {
        categorySelect.setItems(List.of());
        categorySelect.setVisible(false);
      } else {
        List<SourceCategory> cats = CategoryRegistry.getCategoriesInGroup(group);
        categorySelect.setItems(cats);
        categorySelect.setItemLabelGenerator(CategoryRegistry::getUserLabel);
        categorySelect.setVisible(true);
      }
    });

    add(groupSelect, categorySelect);
  }

  // ── Public API ────────────────────────────────────────────────────────────

  /**
   * Returns the selected coarse group, or {@code null} if none selected.
   */
  public CategoryGroup getSelectedGroup() {
    return groupSelect.getValue();
  }

  /**
   * Returns the selected fine-grained category, or {@code null} if none selected.
   */
  public SourceCategory getSelectedCategory() {
    return categorySelect.getValue();
  }

  /**
   * Clears both selects and hides the specific-category dropdown.
   */
  public void reset() {
    groupSelect.clear();
    categorySelect.clear();
    categorySelect.setVisible(false);
  }

  /**
   * Registers a callback that fires whenever the group or specific-category
   * selection changes.  May be called multiple times to add multiple listeners.
   */
  public void addChangeListener(Runnable listener) {
    groupSelect.addValueChangeListener(e -> listener.run());
    categorySelect.addValueChangeListener(e -> listener.run());
  }
}
