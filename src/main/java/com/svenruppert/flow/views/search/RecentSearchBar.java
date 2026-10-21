/*
 * Copyright © 2026 Sven Ruppert (sven.ruppert@gmail.com)
 *
 * Licensed under the EUPL, Version 1.2 (the "Licence");
 * you may not use this file except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *     https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */
package com.svenruppert.flow.views.search;

import com.svenruppert.imagerag.domain.RecentSearchEntry;
import com.svenruppert.imagerag.service.SearchHistoryService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;

import java.util.List;
import java.util.function.Consumer;

/**
 * Chip bar with the most recent searches of the {@link SearchView}.
 * <p>Up to {@value #MAX_VISIBLE_CHIPS} entries are shown as chips. Clicking a chip hands
 * the original query back to the view without executing it. A "More history" button
 * opens {@link SearchHistoryDialog} for full history management.
 */
class RecentSearchBar
    extends Div {

  static final int MAX_VISIBLE_CHIPS = 3;

  private final SearchHistoryService searchHistory;
  private final Consumer<String> onQueryPicked;

  /**
   * @param searchHistory service holding the recent searches
   * @param onQueryPicked receives the original query of a picked entry
   */
  RecentSearchBar(SearchHistoryService searchHistory, Consumer<String> onQueryPicked) {
    this.searchHistory = searchHistory;
    this.onQueryPicked = onQueryPicked;
    getStyle()
        .set("display", "flex")
        .set("flex-wrap", "wrap")
        .set("gap", "0.5rem")
        .set("align-items", "center")
        .set("padding", "0.25rem 0");
    refresh();
  }

  /**
   * Rebuilds the chips from the current search history.
   */
  void refresh() {
    removeAll();
    List<RecentSearchEntry> recent = searchHistory.getRecentSearches();
    if (recent.isEmpty()) {
      setVisible(false);
      return;
    }
    setVisible(true);

    Span label = new Span(getTranslation("search.history.label"));
    label.getStyle()
        .set("font-size", "var(--lumo-font-size-xs)")
        .set("color", "var(--lumo-secondary-text-color)")
        .set("flex-shrink", "0");
    add(label);

    // Clicking a chip copies the original query into the field — does NOT trigger search.
    int visible = Math.min(recent.size(), MAX_VISIBLE_CHIPS);
    for (int i = 0; i < visible; i++) {
      RecentSearchEntry entry = recent.get(i);
      Button chip = new Button(entry.getQuery());
      chip.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY,
                            ButtonVariant.LUMO_CONTRAST);
      chip.getStyle().set("font-size", "var(--lumo-font-size-xs)");
      chip.getElement().setAttribute("title",
                                     getTranslation("search.history.chip.tooltip"));
      chip.addClickListener(e -> onQueryPicked.accept(entry.getQuery()));
      add(chip);
    }

    // "More history" button when there are entries beyond the visible limit
    if (recent.size() > MAX_VISIBLE_CHIPS) {
      int hidden = recent.size() - MAX_VISIBLE_CHIPS;
      Button moreBtn = new Button(getTranslation("search.history.more", hidden));
      moreBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
      moreBtn.getStyle().set("font-size", "var(--lumo-font-size-xs)");
      moreBtn.addClickListener(e -> openHistoryDialog());
      add(moreBtn);
    }

    Button clearBtn = new Button(getTranslation("search.history.clear"));
    clearBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY,
                              ButtonVariant.LUMO_ERROR);
    clearBtn.getStyle().set("font-size", "var(--lumo-font-size-xs)");
    clearBtn.addClickListener(e -> {
      searchHistory.clearRecentSearches();
      refresh();
    });
    add(clearBtn);
  }

  private void openHistoryDialog() {
    new SearchHistoryDialog(
        searchHistory,
        // Load the original query back into the field for editing — do NOT execute
        entry -> onQueryPicked.accept(entry.getQuery()),
        this::refresh
    ).open();
  }
}
