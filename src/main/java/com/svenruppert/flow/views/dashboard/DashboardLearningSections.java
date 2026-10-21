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
package com.svenruppert.flow.views.dashboard;

import com.svenruppert.flow.views.archive.ArchiveView;
import com.svenruppert.flow.views.migration.MigrationCenterView;
import com.svenruppert.flow.views.multimodal.MultimodalSearchView;
import com.svenruppert.flow.views.overview.OverviewView;
import com.svenruppert.flow.views.pipeline.PipelineView;
import com.svenruppert.flow.views.search.SearchView;
import com.svenruppert.flow.views.taxonomy.TaxonomyMaintenanceView;
import com.svenruppert.flow.views.tuning.SearchTuningView;
import com.svenruppert.flow.views.upload.UploadView;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.util.function.Function;

/**
 * The static learning content of the {@link DashboardView}: pipeline architecture map,
 * learning modules (A–F), recommended learning sequence and guided experiments.
 * <p>Texts are resolved through the translation function of the owning view, so the
 * sections follow the view's locale.
 */
final class DashboardLearningSections {

  private final Function<String, String> tr;

  DashboardLearningSections(Function<String, String> tr) {
    this.tr = tr;
  }

  // ── 4. Architecture map ───────────────────────────────────────────────────

  Component buildArchitectureMap() {
    H2 heading = new H2(tr.apply("dashboard.arch.title"));
    heading.getStyle().set("margin", "0 0 var(--lumo-space-xs) 0");

    Paragraph archDesc = new Paragraph(tr.apply("dashboard.arch.desc"));
    archDesc.getStyle()
        .set("color", "var(--lumo-secondary-text-color)")
        .set("font-size", "var(--lumo-font-size-s)")
        .set("margin", "0 0 var(--lumo-space-m) 0");

    HorizontalLayout pipe = new HorizontalLayout();
    pipe.setAlignItems(FlexComponent.Alignment.CENTER);
    pipe.setWidthFull();
    pipe.getStyle().set("overflow-x", "auto").set("padding", "var(--lumo-space-s) 0");

    pipe.add(
        archBox(VaadinIcon.UPLOAD, "dashboard.arch.step.upload", "dashboard.arch.desc.upload", "/" + UploadView.PATH),
        arrow(),
        archBox(VaadinIcon.EYE, "dashboard.arch.step.duplicate", "dashboard.arch.desc.duplicate", "/" + PipelineView.PATH),
        arrow(),
        archBox(VaadinIcon.SEARCH, "dashboard.arch.step.ocr", "dashboard.arch.desc.ocr", "/" + PipelineView.PATH),
        arrow(),
        archBox(VaadinIcon.EYE, "dashboard.arch.step.vision", "dashboard.arch.desc.vision", "/" + OverviewView.PATH),
        arrow(),
        archBox(VaadinIcon.SPLIT, "dashboard.arch.step.embedding", "dashboard.arch.desc.embedding", "/" + MigrationCenterView.PATH),
        arrow(),
        archBox(VaadinIcon.CHART_LINE, "dashboard.arch.step.index", "dashboard.arch.desc.index", "/" + SearchTuningView.PATH),
        arrow(),
        archBox(VaadinIcon.SEARCH, "dashboard.arch.step.search", "dashboard.arch.desc.search", "/" + SearchView.PATH),
        arrow(),
        archBox(VaadinIcon.TABLE, "dashboard.arch.step.result", "dashboard.arch.desc.result", "/" + SearchView.PATH)
    );

    VerticalLayout section = new VerticalLayout(heading, archDesc, pipe);
    section.setPadding(true);
    section.setSpacing(false);
    applyMutedCard(section);
    return section;
  }

  private VerticalLayout archBox(VaadinIcon icon, String titleKey, String descKey, String navPath) {
    Icon ic = icon.create();
    ic.getStyle()
        .set("color", "var(--lumo-primary-color)")
        .set("width", "var(--lumo-icon-size-l)")
        .set("height", "var(--lumo-icon-size-l)");

    H4 title = new H4(tr.apply(titleKey));
    title.getStyle()
        .set("margin", "4px 0 2px 0")
        .set("font-size", "var(--lumo-font-size-s)")
        .set("text-align", "center");

    Paragraph desc = new Paragraph(tr.apply(descKey));
    desc.getStyle()
        .set("margin", "0")
        .set("font-size", "var(--lumo-font-size-xs)")
        .set("color", "var(--lumo-secondary-text-color)")
        .set("text-align", "center");

    VerticalLayout box = new VerticalLayout(ic, title, desc);
    box.setAlignItems(FlexComponent.Alignment.CENTER);
    box.setSpacing(false);
    box.setPadding(true);
    box.setWidth("120px");
    box.getStyle()
        .set("min-width", "100px")
        .set("background", "var(--lumo-base-color)")
        .set("border", "1px solid var(--lumo-contrast-20pct)")
        .set("border-radius", "var(--lumo-border-radius-l)")
        .set("cursor", "pointer")
        .set("transition", "border-color 0.15s");
    final String path = navPath;
    box.getElement().addEventListener("click", e -> UI.getCurrent().navigate(path));
    return box;
  }

  private Span arrow() {
    Span a = new Span("→");
    a.getStyle()
        .set("font-size", "var(--lumo-font-size-l)")
        .set("color", "var(--lumo-secondary-text-color)")
        .set("flex-shrink", "0")
        .set("padding", "0 2px");
    return a;
  }

  // ── 5. Learning modules ───────────────────────────────────────────────────

  Component buildModulesSection() {
    H2 heading = new H2(tr.apply("dashboard.modules.title"));
    heading.getStyle().set("margin", "0 0 var(--lumo-space-xs) 0");

    Paragraph desc = new Paragraph(tr.apply("dashboard.modules.desc"));
    desc.getStyle()
        .set("color", "var(--lumo-secondary-text-color)")
        .set("font-size", "var(--lumo-font-size-s)")
        .set("margin", "0 0 var(--lumo-space-m) 0");

    Div grid = new Div();
    grid.getStyle()
        .set("display", "grid")
        .set("grid-template-columns", "repeat(auto-fill, minmax(300px, 1fr))")
        .set("gap", "var(--lumo-space-m)")
        .set("width", "100%");

    // Module A — Ingestion
    grid.add(moduleCard(VaadinIcon.UPLOAD, "dashboard.module.a",
                        new NavEntry(tr.apply("dashboard.module.a.goto.upload"), "/" + UploadView.PATH),
                        new NavEntry(tr.apply("dashboard.module.a.goto.pipeline"), "/" + PipelineView.PATH)));

    // Module B — Analysis & Enrichment
    grid.add(moduleCard(VaadinIcon.EYE, "dashboard.module.b",
                        new NavEntry(tr.apply("dashboard.module.b.goto.overview"), "/" + OverviewView.PATH)));

    // Module C — Retrieval & Search
    grid.add(moduleCard(VaadinIcon.SEARCH, "dashboard.module.c",
                        new NavEntry(tr.apply("dashboard.module.c.goto.search"), "/" + SearchView.PATH),
                        new NavEntry(tr.apply("dashboard.module.c.goto.multimodal"), "/" + MultimodalSearchView.PATH)));

    // Module D — Search Tuning Lab
    grid.add(moduleCard(VaadinIcon.CHART_LINE, "dashboard.module.d",
                        new NavEntry(tr.apply("dashboard.module.d.goto.tuning"), "/" + SearchTuningView.PATH)));

    // Module E — Taxonomy & Category Governance
    grid.add(moduleCard(VaadinIcon.TAG, "dashboard.module.e",
                        new NavEntry(tr.apply("dashboard.module.e.goto.taxonomy"), "/" + TaxonomyMaintenanceView.PATH)));

    // Module F — Migration & Governance
    grid.add(moduleCard(VaadinIcon.COG, "dashboard.module.f",
                        new NavEntry(tr.apply("dashboard.module.f.goto.migration"), "/" + MigrationCenterView.PATH),
                        new NavEntry(tr.apply("dashboard.module.f.goto.archive"), "/" + ArchiveView.PATH)));

    VerticalLayout section = new VerticalLayout(heading, desc, grid);
    section.setPadding(false);
    section.setSpacing(false);
    return section;
  }

  private Component moduleCard(VaadinIcon icon, String keyPrefix, NavEntry... navEntries) {
    Icon ic = icon.create();
    ic.getStyle()
        .set("color", "var(--lumo-primary-color)")
        .set("width", "var(--lumo-icon-size-xl)")
        .set("height", "var(--lumo-icon-size-xl)");

    H3 title = new H3(tr.apply(keyPrefix + ".title"));
    title.getStyle().set("margin", "var(--lumo-space-xs) 0 var(--lumo-space-xs) 0");

    // "What you will understand here"
    Paragraph whatYouLearn = new Paragraph(tr.apply(keyPrefix + ".learn"));
    whatYouLearn.getStyle()
        .set("font-size", "var(--lumo-font-size-xs)")
        .set("color", "var(--lumo-primary-text-color)")
        .set("font-weight", "600")
        .set("margin", "0 0 var(--lumo-space-xs) 0");

    // Short description
    Paragraph cardDesc = new Paragraph(tr.apply(keyPrefix + ".desc"));
    cardDesc.getStyle()
        .set("color", "var(--lumo-secondary-text-color)")
        .set("font-size", "var(--lumo-font-size-s)")
        .set("margin", "0 0 var(--lumo-space-m) 0");

    // Navigation buttons
    HorizontalLayout btnRow = new HorizontalLayout();
    btnRow.setSpacing(true);
    btnRow.setPadding(false);
    for (NavEntry entry : navEntries) {
      Button btn = new Button(entry.label());
      btn.addThemeVariants(entry == navEntries[0]
                               ? ButtonVariant.LUMO_PRIMARY : ButtonVariant.LUMO_TERTIARY,
                           ButtonVariant.LUMO_SMALL);
      final String path = entry.path();
      btn.addClickListener(e -> UI.getCurrent().navigate(path));
      btnRow.add(btn);
    }

    VerticalLayout card = new VerticalLayout(ic, title, whatYouLearn, cardDesc, btnRow);
    card.setPadding(true);
    card.setSpacing(false);
    card.getStyle()
        .set("background", "var(--lumo-base-color)")
        .set("border", "1px solid var(--lumo-contrast-20pct)")
        .set("border-radius", "var(--lumo-border-radius-l)")
        .set("box-shadow", "var(--lumo-box-shadow-xs)");
    return card;
  }

  Component buildSequenceSection() {
    H2 heading = new H2(tr.apply("dashboard.sequence.title"));
    heading.getStyle().set("margin", "0 0 var(--lumo-space-xs) 0");

    Paragraph desc = new Paragraph(tr.apply("dashboard.sequence.desc"));
    desc.getStyle()
        .set("color", "var(--lumo-secondary-text-color)")
        .set("font-size", "var(--lumo-font-size-s)")
        .set("margin", "0 0 var(--lumo-space-m) 0");

    // Two-column grid for steps
    Div grid = new Div();
    grid.getStyle()
        .set("display", "grid")
        .set("grid-template-columns", "repeat(auto-fill, minmax(340px, 1fr))")
        .set("gap", "var(--lumo-space-xs) var(--lumo-space-m)")
        .set("width", "100%");

    String[][] steps = {
        {"dashboard.sequence.step1", "/" + UploadView.PATH},
        {"dashboard.sequence.step2", "/" + PipelineView.PATH},
        {"dashboard.sequence.step3", "/" + OverviewView.PATH},
        {"dashboard.sequence.step4", "/" + SearchView.PATH},
        {"dashboard.sequence.step5", "/" + SearchView.PATH},
        {"dashboard.sequence.step6", "/" + MultimodalSearchView.PATH},
        {"dashboard.sequence.step7", "/" + SearchTuningView.PATH},
        {"dashboard.sequence.step8", "/" + TaxonomyMaintenanceView.PATH},
        {"dashboard.sequence.step9", "/" + MigrationCenterView.PATH},
        {"dashboard.sequence.step10", "/" + ArchiveView.PATH},
    };

    for (int i = 0; i < steps.length; i++) {
      grid.add(stepRow(i + 1, tr.apply(steps[i][0]), steps[i][1]));
    }

    VerticalLayout section = new VerticalLayout(heading, desc, grid);
    section.setPadding(true);
    section.setSpacing(false);
    applyMutedCard(section);
    return section;
  }

  // ── 6. Recommended learning sequence ─────────────────────────────────────

  private Div stepRow(int num, String text, String navPath) {
    Span badge = new Span(String.valueOf(num));
    badge.getStyle()
        .set("background", "var(--lumo-primary-color)")
        .set("color", "var(--lumo-primary-contrast-color)")
        .set("border-radius", "50%")
        .set("width", "26px")
        .set("height", "26px")
        .set("display", "inline-flex")
        .set("align-items", "center")
        .set("justify-content", "center")
        .set("font-size", "var(--lumo-font-size-xs)")
        .set("font-weight", "bold")
        .set("flex-shrink", "0");

    Span label = new Span(text);
    label.getStyle()
        .set("font-size", "var(--lumo-font-size-s)")
        .set("flex-grow", "1");

    Button goBtn = new Button("→");
    goBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
    goBtn.getStyle().set("flex-shrink", "0");
    final String path = navPath;
    goBtn.addClickListener(e -> UI.getCurrent().navigate(path));

    Div row = new Div(badge, label, goBtn);
    row.getStyle()
        .set("display", "flex")
        .set("align-items", "center")
        .set("gap", "var(--lumo-space-s)")
        .set("padding", "var(--lumo-space-xs) var(--lumo-space-s)")
        .set("background", "var(--lumo-base-color)")
        .set("border-radius", "var(--lumo-border-radius-m)")
        .set("border", "1px solid var(--lumo-contrast-10pct)");
    return row;
  }

  Component buildExperimentsSection() {
    H2 heading = new H2(tr.apply("dashboard.experiments.title"));
    heading.getStyle().set("margin", "0 0 var(--lumo-space-xs) 0");

    Paragraph desc = new Paragraph(tr.apply("dashboard.experiments.desc"));
    desc.getStyle()
        .set("color", "var(--lumo-secondary-text-color)")
        .set("font-size", "var(--lumo-font-size-s)")
        .set("margin", "0 0 var(--lumo-space-m) 0");

    Div grid = new Div();
    grid.getStyle()
        .set("display", "grid")
        .set("grid-template-columns", "repeat(auto-fill, minmax(280px, 1fr))")
        .set("gap", "var(--lumo-space-m)")
        .set("width", "100%");

    grid.add(experimentCard(VaadinIcon.SPLIT,
                            "dashboard.experiment.compare", "/" + MultimodalSearchView.PATH));
    grid.add(experimentCard(VaadinIcon.QUESTION_CIRCLE,
                            "dashboard.experiment.wnf", "/" + SearchTuningView.PATH));
    grid.add(experimentCard(VaadinIcon.REFRESH,
                            "dashboard.experiment.reprocess", "/" + MigrationCenterView.PATH));
    grid.add(experimentCard(VaadinIcon.TAG,
                            "dashboard.experiment.taxonomy", "/" + TaxonomyMaintenanceView.PATH));
    grid.add(experimentCard(VaadinIcon.CHART_LINE,
                            "dashboard.experiment.feedback", "/" + SearchTuningView.PATH));

    VerticalLayout section = new VerticalLayout(heading, desc, grid);
    section.setPadding(false);
    section.setSpacing(false);
    return section;
  }

  // ── 7. Guided experiments ─────────────────────────────────────────────────

  private Component experimentCard(VaadinIcon icon, String keyPrefix, String navPath) {
    Icon ic = icon.create();
    ic.getStyle().set("color", "var(--lumo-primary-color)");

    H4 title = new H4(tr.apply(keyPrefix + ".title"));
    title.getStyle().set("margin", "var(--lumo-space-xs) 0 var(--lumo-space-xs) 0");

    // Learning goal
    Span goal = new Span(tr.apply(keyPrefix + ".goal"));
    goal.getStyle()
        .set("font-size", "var(--lumo-font-size-xs)")
        .set("font-weight", "600")
        .set("color", "var(--lumo-primary-text-color)")
        .set("display", "block")
        .set("margin-bottom", "var(--lumo-space-xs)");

    Paragraph cardDesc = new Paragraph(tr.apply(keyPrefix + ".desc"));
    cardDesc.getStyle()
        .set("color", "var(--lumo-secondary-text-color)")
        .set("font-size", "var(--lumo-font-size-s)")
        .set("margin", "0 0 var(--lumo-space-m) 0");

    Button btn = new Button(tr.apply(keyPrefix + ".goto"));
    btn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
    final String path = navPath;
    btn.addClickListener(e -> UI.getCurrent().navigate(path));

    VerticalLayout card = new VerticalLayout(ic, title, goal, cardDesc, btn);
    card.setPadding(true);
    card.setSpacing(false);
    card.getStyle()
        .set("background", "var(--lumo-base-color)")
        .set("border", "1px solid var(--lumo-primary-color-50pct)")
        .set("border-radius", "var(--lumo-border-radius-l)")
        .set("box-shadow", "var(--lumo-box-shadow-xs)");
    return card;
  }

  /**
   * Applies a muted card background (no shadow).
   */
  static void applyMutedCard(VerticalLayout layout) {
    layout.getStyle()
        .set("background", "var(--lumo-contrast-5pct)")
        .set("border-radius", "var(--lumo-border-radius-l)");
  }

  /**
   * Simple value holder for a navigation button label + route path.
   */
  private record NavEntry(String label, String path) { }
}
