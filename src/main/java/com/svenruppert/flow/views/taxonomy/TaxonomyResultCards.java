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
package com.svenruppert.flow.views.taxonomy;

import com.svenruppert.imagerag.domain.TaxonomySuggestion;
import com.svenruppert.imagerag.service.TaxonomyAnalysisProgress;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;

import java.util.List;

/**
 * Dry-run preview and completion summary cards shown after a taxonomy analysis run.
 * At most one of the two cards is visible at a time.
 */
class TaxonomyResultCards
    extends Div {

  private final Div dryRunCard = new Div();
  private final Div completionCard = new Div();

  TaxonomyResultCards() {
    styleInfoCard(dryRunCard, "var(--lumo-primary-color-10pct)", "var(--lumo-primary-color)");
    dryRunCard.setVisible(false);

    styleInfoCard(completionCard, "var(--lumo-success-color-10pct)", "var(--lumo-success-color)");
    completionCard.setVisible(false);

    add(dryRunCard, completionCard);
    setWidthFull();
  }

  private static void styleInfoCard(Div card, String bg, String borderColor) {
    card.getStyle()
        .set("background", bg)
        .set("border-left", "4px solid " + borderColor)
        .set("border-radius", "var(--lumo-border-radius-m)")
        .set("padding", "0.75rem 1rem")
        .set("width", "100%")
        .set("box-sizing", "border-box");
  }

  void hideAll() {
    dryRunCard.setVisible(false);
    completionCard.setVisible(false);
  }

  void showDryRun(List<TaxonomySuggestion> suggestions, TaxonomyAnalysisProgress progress) {
    completionCard.setVisible(false);
    fill(dryRunCard, "taxonomy.dryrun.card.title", "taxonomy.dryrun.card.body",
         "taxonomy.dryrun.card.hint", "var(--lumo-primary-color)", suggestions, progress);
  }

  void showCompletion(List<TaxonomySuggestion> suggestions, TaxonomyAnalysisProgress progress) {
    dryRunCard.setVisible(false);
    fill(completionCard, "taxonomy.completion.card.title", "taxonomy.completion.card.body",
         "taxonomy.completion.card.hint", "var(--lumo-success-color)", suggestions, progress);
  }

  private void fill(Div card, String titleKey, String bodyKey, String hintKey,
                    String headingColor,
                    List<TaxonomySuggestion> suggestions, TaxonomyAnalysisProgress progress) {
    card.removeAll();

    H4 heading = new H4(getTranslation(titleKey));
    heading.getStyle().set("margin", "0 0 0.4rem 0").set("color", headingColor);

    long imageLevel = suggestions.stream().filter(TaxonomySuggestion::isImageLevel).count();
    long taxLevel = suggestions.stream().filter(s -> !s.isImageLevel()).count();

    Paragraph body = new Paragraph(getTranslation(bodyKey,
                                                  suggestions.size(), imageLevel, taxLevel,
                                                  progress.getElapsedFormatted()));
    body.getStyle().set("margin", "0").set("font-size", "var(--lumo-font-size-s)");

    Paragraph hint = new Paragraph(getTranslation(hintKey));
    hint.getStyle()
        .set("margin", "0.4rem 0 0 0")
        .set("font-size", "var(--lumo-font-size-xs)")
        .set("color", "var(--lumo-secondary-text-color)");

    card.add(heading, body, hint);
    card.setVisible(true);
  }
}
