package com.svenruppert.flow.views.tuning;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * Shared styling helpers for the Search Tuning Lab panels and result cards.
 */
final class TuningStyles {

  private TuningStyles() {
  }

  /**
   * Styles {@code panel} as a card (border + background) with a fixed width.
   */
  static void stylePanel(VerticalLayout panel, String width) {
    panel.setWidth(width);
    panel.setMinWidth(width);
    panel.getStyle()
        .set("flex-shrink", "0")
        .set("border", "1px solid var(--lumo-contrast-10pct)")
        .set("border-radius", "var(--lumo-border-radius-l)")
        .set("padding", "0.6rem 0.75rem")
        .set("background", "var(--lumo-base-color)");
    panel.setSpacing(false);
    panel.setPadding(false);
  }

  /**
   * Thin horizontal separator line.
   */
  static Div separator() {
    Div d = new Div();
    d.getStyle()
        .set("border-top", "1px solid var(--lumo-contrast-5pct)")
        .set("margin", "0.35rem 0")
        .set("width", "100%");
    return d;
  }

  /**
   * Control group with a small uppercase heading.
   */
  static VerticalLayout controlSection(String heading, Component... components) {
    H4 h = new H4(heading);
    h.getStyle()
        .set("margin", "0.5rem 0 0.2rem 0")
        .set("font-size", "var(--lumo-font-size-xs)")
        .set("color", "var(--lumo-secondary-text-color)")
        .set("text-transform", "uppercase")
        .set("letter-spacing", "0.04em");
    VerticalLayout sec = new VerticalLayout(h);
    sec.add(components);
    sec.setSpacing(false);
    sec.setPadding(false);
    sec.getStyle()
        .set("border-bottom", "1px solid var(--lumo-contrast-5pct)")
        .set("padding-bottom", "0.4rem");
    return sec;
  }

  /**
   * Small uppercase section label (no H4 overhead).
   */
  static Span sectionLabel(String text) {
    Span s = new Span(text);
    s.getStyle()
        .set("font-size", "var(--lumo-font-size-xs)")
        .set("color", "var(--lumo-secondary-text-color)")
        .set("text-transform", "uppercase")
        .set("letter-spacing", "0.04em")
        .set("font-weight", "600")
        .set("margin-top", "0.1rem");
    return s;
  }

  /**
   * Small colored tag chip.
   */
  static Span tag(String text, String bg, String color) {
    Span tag = new Span(text);
    tag.getStyle()
        .set("background", bg).set("color", color)
        .set("font-size", "var(--lumo-font-size-xxs)")
        .set("padding", "1px 5px")
        .set("border-radius", "var(--lumo-border-radius-s)")
        .set("white-space", "nowrap");
    return tag;
  }
}
