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
package com.svenruppert.flow.views.multimodal;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * Shared layout helpers of the multimodal search panels.
 */
final class MultimodalUi {

  private MultimodalUi() {
  }

  static VerticalLayout panel(String width) {
    VerticalLayout p = new VerticalLayout();
    stylePanel(p, width);
    return p;
  }

  /**
   * Applies the bordered, fixed-width side-panel style.
   */
  static void stylePanel(VerticalLayout p, String width) {
    p.setPadding(true);
    p.setSpacing(false);
    p.getStyle()
        .set("border", "1px solid var(--lumo-contrast-10pct)")
        .set("border-radius", "var(--lumo-border-radius-l)")
        .set("background", "var(--lumo-base-color)")
        .set("flex-shrink", "0")
        .set("min-width", width).set("max-width", width)
        .set("overflow-y", "auto");
  }

  static Span sectionLabel(String text) {
    Span s = new Span(text);
    s.getStyle()
        .set("font-size", "var(--lumo-font-size-xxs)").set("font-weight", "700")
        .set("text-transform", "uppercase").set("letter-spacing", "0.05em")
        .set("color", "var(--lumo-secondary-text-color)").set("display", "block")
        .set("margin-top", "0.5rem");
    return s;
  }

  static Div sep() {
    Div d = new Div();
    d.getStyle()
        .set("height", "1px").set("background", "var(--lumo-contrast-10pct)")
        .set("margin", "0.4rem 0");
    return d;
  }
}
