package com.svenruppert.flow.views.search;

import com.svenruppert.imagerag.domain.RecentSearchEntry;
import com.svenruppert.imagerag.persistence.PersistenceService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Full search-history browser dialog.
 *
 * <p>Shows all stored {@link RecentSearchEntry} records in a grid with columns for
 * timestamp, original query, LLM-transformed query, and mode.  Supports multi-select
 * deletion and a clear-all action.
 *
 * <p>Clicking a row invokes the {@code onSelect} callback with the chosen entry and
 * closes the dialog — the caller is expected to copy the original query back into the
 * search field.
 *
 * <p>Any structural change to the history (delete or clear) invokes the {@code onChanged}
 * callback so the caller can refresh the compact chip bar.
 */
public class SearchHistoryDialog extends Dialog {

  private static final DateTimeFormatter DATE_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

  private final PersistenceService               ps;
  private final Consumer<RecentSearchEntry>       onSelect;
  private final Runnable                          onChanged;
  private final Grid<RecentSearchEntry>           grid =
      new Grid<>(RecentSearchEntry.class, false);

  /**
   * Creates and opens the history dialog.
   *
   * @param ps        the persistence service for reading and mutating the history
   * @param onSelect  callback invoked when the user selects a history entry to load;
   *                  receives the selected entry; the dialog is closed automatically
   * @param onChanged callback invoked after any delete/clear operation so the caller
   *                  can refresh the compact chip bar
   */
  public SearchHistoryDialog(PersistenceService ps,
                             Consumer<RecentSearchEntry> onSelect,
                             Runnable onChanged) {
    this.ps        = ps;
    this.onSelect  = onSelect;
    this.onChanged = onChanged;

    setHeaderTitle("Search History");
    setWidth("860px");
    setHeight("60vh");
    setCloseOnOutsideClick(true);

    configureGrid();

    VerticalLayout content = new VerticalLayout(grid);
    content.setSizeFull();
    content.setPadding(false);
    add(content);

    // ── Footer ────────────────────────────────────────────────────────────
    Button deleteBtn = new Button("Delete Selected");
    deleteBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
    deleteBtn.addClickListener(e -> deleteSelected());

    Button clearBtn = new Button("Clear All");
    clearBtn.addThemeVariants(ButtonVariant.LUMO_ERROR);
    clearBtn.addClickListener(e -> clearAll());

    Button closeBtn = new Button("Close", e -> close());
    closeBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    getFooter().add(deleteBtn, clearBtn, closeBtn);

    refreshGrid();
  }

  private void configureGrid() {
    grid.setWidthFull();
    grid.setHeightFull();
    grid.setSelectionMode(Grid.SelectionMode.MULTI);
    grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);

    grid.addColumn(e -> e.getTimestamp() != null ? DATE_FMT.format(e.getTimestamp()) : "\u2014")
        .setHeader("Time")
        .setWidth("145px")
        .setFlexGrow(0);

    grid.addColumn(RecentSearchEntry::getQuery)
        .setHeader("Original Query")
        .setFlexGrow(2);

    grid.addComponentColumn(e -> {
      String fq = e.getFinalQuery();
      if (fq == null || fq.isBlank()) {
        Span dash = new Span("\u2014");
        dash.getStyle().set("color", "var(--lumo-secondary-text-color)");
        return dash;
      }
      Span s = new Span(fq);
      s.getStyle()
          .set("font-size", "var(--lumo-font-size-xs)")
          .set("color", "var(--lumo-secondary-text-color)")
          .set("white-space", "normal");
      return s;
    }).setHeader("Transformed Query (LLM)").setFlexGrow(3);

    grid.addColumn(e -> e.getMode() != null ? e.getMode().name().replace("_", " ") : "\u2014")
        .setHeader("Mode")
        .setWidth("180px")
        .setFlexGrow(0);

    // Single click on a row → load into search field and close
    grid.addItemClickListener(event -> {
      onSelect.accept(event.getItem());
      close();
    });
  }

  private void refreshGrid() {
    grid.setItems(new ArrayList<>(ps.getRecentSearches()));
  }

  private void deleteSelected() {
    List<RecentSearchEntry> selected = new ArrayList<>(grid.getSelectedItems());
    if (selected.isEmpty()) {
      Notification.show("No entries selected.", 2000, Notification.Position.MIDDLE);
      return;
    }
    ps.deleteRecentSearches(selected);
    refreshGrid();
    onChanged.run();
  }

  private void clearAll() {
    ps.clearRecentSearches();
    refreshGrid();
    onChanged.run();
  }
}
