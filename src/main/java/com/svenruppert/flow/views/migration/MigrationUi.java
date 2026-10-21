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
package com.svenruppert.flow.views.migration;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * Layout helpers shared by the tabs of the migration center.
 */
final class MigrationUi {

  private MigrationUi() {
  }

  /**
   * Applies the common tab layout: no spacing, padded, full width, small top padding.
   */
  static void styleTab(VerticalLayout tab) {
    tab.setSpacing(false);
    tab.setPadding(true);
    tab.setWidthFull();
    tab.getStyle().set("padding-top", "0.75rem");
  }

  static VerticalLayout styledPanel(String width) {
    VerticalLayout panel = new VerticalLayout();
    panel.setWidth(width);
    panel.setMinWidth(width);
    panel.setSpacing(false);
    panel.setPadding(true);
    panel.getStyle()
        .set("flex-shrink", "0")
        .set("border", "1px solid var(--lumo-contrast-10pct)")
        .set("border-radius", "var(--lumo-border-radius-l)")
        .set("background", "var(--lumo-base-color)");
    return panel;
  }

  static H3 sectionHeading(String text) {
    H3 h = new H3(text);
    h.getStyle()
        .set("font-size", "var(--lumo-font-size-xs)")
        .set("text-transform", "uppercase")
        .set("letter-spacing", "0.05em")
        .set("color", "var(--lumo-secondary-text-color)")
        .set("margin", "0.75rem 0 0.3rem 0");
    return h;
  }

  static Div configRow(String label, String value) {
    Div row = new Div();
    row.getStyle()
        .set("display", "flex").set("gap", "0.5rem")
        .set("font-size", "var(--lumo-font-size-s)")
        .set("padding", "0.2rem 0")
        .set("border-bottom", "1px solid var(--lumo-contrast-5pct)");

    Span l = new Span(label + ":");
    l.getStyle().set("color", "var(--lumo-secondary-text-color)")
        .set("min-width", "180px").set("flex-shrink", "0");

    Span v = new Span(value != null ? value : "—");
    v.getStyle().set("color", "var(--lumo-body-text-color)")
        .set("font-family", "monospace")
        .set("overflow-x", "auto");

    row.add(l, v);
    return row;
  }

  static int countLines(String s) {
    if (s == null || s.isEmpty()) return 0;
    return (int) s.chars().filter(c -> c == '\n').count() + 1;
  }
}
