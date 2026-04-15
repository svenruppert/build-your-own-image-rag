package com.svenruppert.flow.views.shared;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

import java.util.function.Consumer;

/**
 * A prominent segmented-control toggle for switching between table view and tile/card view.
 *
 * <p>Each button shows both an icon <em>and</em> a text label so the user immediately
 * understands the two presentation modes:
 * <ul>
 *   <li>{@link VaadinIcon#LIST} + "Table" — row-based grid view</li>
 *   <li>{@link VaadinIcon#GRID_BIG} + "Tiles" — card/tile view</li>
 * </ul>
 * The active button carries the {@code LUMO_PRIMARY} theme variant; the inactive button
 * uses {@code LUMO_CONTRAST} so the choice is always visually obvious.
 *
 * <p>The component emits a change event only when the mode actually flips — clicking
 * the already-active button is a no-op.
 */
public class ViewModeToggle
    extends HorizontalLayout {

  private static final String LABEL_TABLE = "Table";
  private static final String LABEL_TILES = "Tiles";

  private final Button tableBtn;
  private final Button tilesBtn;
  private boolean tileMode;

  /**
   * Creates a new toggle.
   *
   * @param initialTileMode {@code true} if tile view should be active initially
   * @param onToggle        callback invoked with the new mode whenever it changes;
   *                        {@code true} = tile view, {@code false} = table view
   */
  public ViewModeToggle(boolean initialTileMode, Consumer<Boolean> onToggle) {
    this.tileMode = initialTileMode;
    setPadding(false);
    setSpacing(false);
    setAlignItems(FlexComponent.Alignment.CENTER);
    // Outer border visually groups both buttons as one segmented control.
    // overflow:hidden clips button corners so they conform to the wrapper radius.
    getStyle()
        .set("gap", "0")
        .set("border", "1px solid var(--lumo-contrast-20pct)")
        .set("border-radius", "var(--lumo-border-radius-m)")
        .set("overflow", "hidden")
        // Prevent the segmented control from being squashed by surrounding flex containers
        .set("flex-shrink", "0")
        .set("align-self", "flex-start");

    tableBtn = buildSegmentButton(VaadinIcon.LIST, LABEL_TABLE, true);
    tilesBtn = buildSegmentButton(VaadinIcon.GRID_BIG, LABEL_TILES, false);

    tableBtn.addClickListener(e -> {
      if (this.tileMode) {
        this.tileMode = false;
        updateStyles();
        onToggle.accept(false);
      }
    });

    tilesBtn.addClickListener(e -> {
      if (!this.tileMode) {
        this.tileMode = true;
        updateStyles();
        onToggle.accept(true);
      }
    });

    updateStyles();
    add(tableBtn, tilesBtn);
  }

  /**
   * Builds a single segment button with an icon and a text label side-by-side.
   *
   * @param icon     icon to display left of the label
   * @param label    text label
   * @param leftEdge {@code true} if this is the left button (rounded left corners only)
   */
  private Button buildSegmentButton(VaadinIcon icon, String label, boolean leftEdge) {
    Icon ico = icon.create();
    ico.setSize("16px");
    ico.getStyle().set("flex-shrink", "0");

    Span text = new Span(label);
    text.getStyle().set("font-size", "var(--lumo-font-size-s)");

    HorizontalLayout content = new HorizontalLayout(ico, text);
    content.setSpacing(false);
    content.setAlignItems(FlexComponent.Alignment.CENTER);
    content.getStyle().set("gap", "0.35rem");

    Button btn = new Button(content);
    // Explicit sizing prevents the button from collapsing under layout pressure
    btn.getStyle()
        .set("min-width", "90px")
        .set("min-height", "36px")
        .set("height", "36px");

    if (leftEdge) {
      btn.getStyle()
          .set("border-radius", "var(--lumo-border-radius-m) 0 0 var(--lumo-border-radius-m)")
          .set("border-right", "1px solid var(--lumo-contrast-20pct)");
    } else {
      btn.getStyle()
          .set("border-radius", "0 var(--lumo-border-radius-m) var(--lumo-border-radius-m) 0");
    }
    return btn;
  }

  private void updateStyles() {
    if (tileMode) {
      tableBtn.removeThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_CONTRAST);
      tableBtn.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
      tilesBtn.removeThemeVariants(ButtonVariant.LUMO_CONTRAST);
      tilesBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    } else {
      tilesBtn.removeThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_CONTRAST);
      tilesBtn.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
      tableBtn.removeThemeVariants(ButtonVariant.LUMO_CONTRAST);
      tableBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    }
  }
}
