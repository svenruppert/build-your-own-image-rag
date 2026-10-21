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
package com.svenruppert.flow.views.shared;

import com.svenruppert.imagerag.domain.enums.RiskLevel;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;

/**
 * Factory for the visual representation of a {@link RiskLevel}, shared by all views
 * that show an image's risk: a Lumo badge with the level name, or a colored icon
 * with a translated tooltip.
 */
public final class RiskBadge {

  private RiskBadge() {
  }

  /**
   * Creates a Lumo badge showing the risk level name, colored by severity.
   * A {@code null} risk yields an em-dash placeholder.
   */
  public static Span badge(RiskLevel risk) {
    if (risk == null) {
      return new Span(Formats.EMPTY);
    }
    Span badge = new Span(risk.name());
    badge.getElement().getThemeList().add("badge");
    badge.getElement().getThemeList().add(switch (risk) {
      case SAFE -> "success";
      case REVIEW -> "contrast";
      case SENSITIVE -> "error";
    });
    return badge;
  }

  /**
   * Creates a colored icon (green check, orange question mark, red exclamation)
   * with a translated tooltip. A {@code null} risk yields a small em-dash placeholder.
   *
   * @param size CSS size of the icon, e.g. {@code "16px"}
   */
  public static Component icon(RiskLevel risk, String size) {
    if (risk == null) {
      Span dash = new Span(Formats.EMPTY);
      dash.getStyle().set("font-size", "var(--lumo-font-size-xs)");
      return dash;
    }
    Icon icon = switch (risk) {
      case SAFE -> VaadinIcon.CHECK_CIRCLE.create();
      case REVIEW -> VaadinIcon.QUESTION_CIRCLE.create();
      case SENSITIVE -> VaadinIcon.EXCLAMATION_CIRCLE_O.create();
    };
    icon.setColor(switch (risk) {
      case SAFE -> "var(--lumo-success-color)";
      case REVIEW -> "var(--lumo-warning-color, orange)";
      case SENSITIVE -> "var(--lumo-error-color)";
    });
    icon.setSize(size);
    icon.getElement().setAttribute("title",
                                   icon.getTranslation("overview.risk.tooltip." + risk.name().toLowerCase()));
    return icon;
  }
}
