package com.svenruppert.flow.views.search;

import com.svenruppert.imagerag.domain.CategoryRegistry;
import com.svenruppert.imagerag.domain.SearchResultItem;
import com.svenruppert.imagerag.domain.enums.CategoryGroup;
import com.svenruppert.imagerag.domain.enums.RiskLevel;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Compact click-to-filter chips above the search results: one chip per category group
 * and per risk level, each with its result count. Clicking an active chip clears the
 * filter again.
 */
class SearchFacetBar
    extends Div {

  private static final String CATEGORY_PREFIX = "cat:";
  private static final String RISK_PREFIX = "risk:";

  /**
   * Receives the filtered result list, or {@code null} when all results are shown.
   */
  private final Consumer<List<SearchResultItem>> onFilterChanged;

  /**
   * Currently active facet key, e.g. "cat:NATURE" or "risk:HIGH", or null for "all".
   */
  private String activeFacetKey = null;

  SearchFacetBar(Consumer<List<SearchResultItem>> onFilterChanged) {
    this.onFilterChanged = onFilterChanged;
    setVisible(false);
    getStyle()
        .set("display", "flex")
        .set("flex-wrap", "wrap")
        .set("gap", "0.5rem")
        .set("align-items", "center")
        .set("padding", "0.5rem 0")
        .set("border-top", "1px solid var(--lumo-contrast-10pct)")
        .set("border-bottom", "1px solid var(--lumo-contrast-10pct)")
        .set("margin-bottom", "0.25rem");
  }

  /**
   * Shows the facets for a new result list and clears any active filter.
   */
  void show(List<SearchResultItem> allResults) {
    activeFacetKey = null;
    build(allResults);
  }

  /**
   * Rebuilds the chips from {@code allResults}, highlighting the active facet.
   *
   * @param allResults full unfiltered result list (base for counts)
   */
  private void build(List<SearchResultItem> allResults) {
    removeAll();

    Span filterLabel = new Span(getTranslation("search.facet.filter") + " ");
    filterLabel.getStyle()
        .set("font-size", "var(--lumo-font-size-xs)")
        .set("color", "var(--lumo-secondary-text-color)")
        .set("font-weight", "600")
        .set("flex-shrink", "0");
    add(filterLabel);

    // "All" chip — always shown, clears the active facet
    Button allChip = new Button(getTranslation("search.facet.all", allResults.size()));
    allChip.addThemeVariants(ButtonVariant.LUMO_SMALL,
                             activeFacetKey == null ? ButtonVariant.LUMO_PRIMARY : ButtonVariant.LUMO_TERTIARY);
    allChip.getStyle().set("font-size", "var(--lumo-font-size-xs)");
    allChip.addClickListener(e -> {
      activeFacetKey = null;
      build(allResults);
      onFilterChanged.accept(null);
    });
    add(allChip);

    // Category-group facets, sorted by count descending for visual relevance
    Map<CategoryGroup, Long> byCatGroup = allResults.stream()
        .filter(r -> r.getSourceCategory() != null)
        .collect(Collectors.groupingBy(
            r -> CategoryRegistry.getGroup(r.getSourceCategory()),
            Collectors.counting()));

    byCatGroup.entrySet().stream()
        .sorted(Map.Entry.<CategoryGroup, Long>comparingByValue().reversed())
        .forEach(entry -> {
          CategoryGroup group = entry.getKey();
          String key = CATEGORY_PREFIX + group.name();
          String label = group.name().charAt(0)
              + group.name().substring(1).toLowerCase().replace("_", " ");
          Button chip = new Button(label + " (" + entry.getValue() + ")");
          chip.addThemeVariants(ButtonVariant.LUMO_SMALL,
                                key.equals(activeFacetKey) ? ButtonVariant.LUMO_PRIMARY : ButtonVariant.LUMO_CONTRAST);
          chip.getStyle().set("font-size", "var(--lumo-font-size-xs)");
          chip.addClickListener(e -> applyFacet(allResults, key));
          add(chip);
        });

    if (!byCatGroup.isEmpty()) {
      Span sep = new Span("|");
      sep.getStyle()
          .set("color", "var(--lumo-contrast-30pct)")
          .set("font-size", "var(--lumo-font-size-xs)");
      add(sep);
    }

    // Risk-level facets
    Map<RiskLevel, Long> byRisk = allResults.stream()
        .filter(r -> r.getRiskLevel() != null)
        .collect(Collectors.groupingBy(SearchResultItem::getRiskLevel, Collectors.counting()));

    byRisk.entrySet().stream()
        .sorted(Map.Entry.<RiskLevel, Long>comparingByValue().reversed())
        .forEach(entry -> {
          RiskLevel risk = entry.getKey();
          String key = RISK_PREFIX + risk.name();
          Button chip = new Button(risk.name() + " (" + entry.getValue() + ")");
          chip.addThemeVariants(ButtonVariant.LUMO_SMALL,
                                key.equals(activeFacetKey) ? ButtonVariant.LUMO_PRIMARY : ButtonVariant.LUMO_TERTIARY);
          chip.getStyle().set("font-size", "var(--lumo-font-size-xs)");
          // Color-code by risk level
          if (risk == RiskLevel.SENSITIVE) {
            chip.getStyle().set("color", "var(--lumo-error-color)");
          } else if (risk == RiskLevel.REVIEW) {
            chip.getStyle().set("color", "var(--lumo-warning-color)");
          }
          chip.addClickListener(e -> applyFacet(allResults, key));
          add(chip);
        });
  }

  /**
   * Applies a facet filter by key. Clicking an already-active facet toggles it off.
   */
  private void applyFacet(List<SearchResultItem> allResults, String facetKey) {
    if (facetKey.equals(activeFacetKey)) {
      activeFacetKey = null;
      build(allResults);
      onFilterChanged.accept(null);
      return;
    }

    activeFacetKey = facetKey;
    List<SearchResultItem> filtered;
    if (facetKey.startsWith(CATEGORY_PREFIX)) {
      String groupName = facetKey.substring(CATEGORY_PREFIX.length());
      filtered = allResults.stream()
          .filter(r -> r.getSourceCategory() != null
              && CategoryRegistry.getGroup(r.getSourceCategory()).name().equals(groupName))
          .collect(Collectors.toList());
    } else if (facetKey.startsWith(RISK_PREFIX)) {
      String riskName = facetKey.substring(RISK_PREFIX.length());
      filtered = allResults.stream()
          .filter(r -> r.getRiskLevel() != null && r.getRiskLevel().name().equals(riskName))
          .collect(Collectors.toList());
    } else {
      filtered = new ArrayList<>(allResults);
    }

    build(allResults);
    onFilterChanged.accept(filtered);
  }
}
