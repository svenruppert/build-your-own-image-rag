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

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.MainLayout;
import com.svenruppert.flow.views.migration.MigrationCenterView;
import com.svenruppert.flow.views.pipeline.PipelineView;
import com.svenruppert.flow.views.shared.ViewServices;
import com.svenruppert.flow.views.taxonomy.TaxonomyMaintenanceView;
import com.svenruppert.flow.views.upload.UploadView;
import com.svenruppert.imagerag.domain.SemanticAnalysis;
import com.svenruppert.imagerag.domain.enums.SuggestionStatus;
import com.svenruppert.imagerag.ollama.OllamaConfig;
import com.svenruppert.imagerag.service.ImageCatalogService;
import com.svenruppert.imagerag.pipeline.IngestionPipeline;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;

import java.util.Collection;
import java.util.Objects;

/**
 * Central training dashboard — the didactic control center for Image-RAG
 * training sessions.
 * <p>Sections:
 * <ol>
 *   <li>Start-Here hero panel for orientation
 *   <li>Session status — live system-state chips
 *   <li>Lab-state insights — configuration context panel
 *   <li>Pipeline architecture map — clickable process flow
 *   <li>Learning modules (A–F) — grouped training paths with didactic context
 *   <li>Recommended learning sequence — numbered step guide
 *   <li>Guided experiments — quick-launch cards for hands-on exercises
 * </ol>
 * <p>Not a pure KPI page or a flat navigation menu.
 * The Dashboard combines orientation, learning-path guidance, system-state
 * awareness, and direct experiment launch.
 */
@Route(value = DashboardView.PATH, layout = MainLayout.class)
public class DashboardView
    extends VerticalLayout
    implements LocaleChangeObserver, HasLogger, HasDynamicTitle {

  public static final String PATH = "";

  /**
   * Low-confidence threshold — primary score below this triggers a flag.
   */
  private static final double LOW_CONFIDENCE_THRESHOLD = 0.6;
  private final ViewServices services;

  public DashboardView() {
    this.services = ViewServices.current();

    setPadding(true);
    setSpacing(true);
    setWidthFull();
    getStyle().set("max-width", "1200px").set("margin", "0 auto");
    buildAll();
  }

  private void buildAll() {
    DashboardLearningSections learning = new DashboardLearningSections(this::getTranslation);
    add(buildStartHerePanel());
    add(buildStatusSection());
    add(buildLabStateSection());
    add(learning.buildArchitectureMap());
    add(learning.buildModulesSection());
    add(learning.buildSequenceSection());
    add(learning.buildExperimentsSection());
  }

  // ── 1. Start-Here hero ────────────────────────────────────────────────────

  private Component buildStartHerePanel() {
    H1 title = new H1(getTranslation("dashboard.title"));
    title.getStyle()
        .set("margin", "0")
        .set("font-size", "var(--lumo-font-size-xxxl)");

    Paragraph subtitle = new Paragraph(getTranslation("dashboard.subtitle"));
    subtitle.getStyle()
        .set("color", "var(--lumo-secondary-text-color)")
        .set("margin", "0 0 var(--lumo-space-s) 0");

    Paragraph welcome = new Paragraph(getTranslation("dashboard.welcome"));
    welcome.getStyle()
        .set("font-size", "var(--lumo-font-size-s)")
        .set("color", "var(--lumo-secondary-text-color)")
        .set("margin", "0 0 var(--lumo-space-m) 0")
        .set("max-width", "700px");

    Button startBtn = new Button(getTranslation("dashboard.start.btn"));
    startBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    startBtn.addClickListener(e -> UI.getCurrent().navigate("/" + UploadView.PATH));

    VerticalLayout hero = new VerticalLayout(title, subtitle, welcome, startBtn);
    hero.setPadding(true);
    hero.setSpacing(false);
    hero.getStyle()
        .set("border-left", "5px solid var(--lumo-primary-color)")
        .set("background", "var(--lumo-primary-color-10pct, #f0f4ff)")
        .set("border-radius", "0 var(--lumo-border-radius-l) var(--lumo-border-radius-l) 0");
    return hero;
  }

  // ── 2. Session status chips ───────────────────────────────────────────────

  private Component buildStatusSection() {
    ImageCatalogService ps = services.catalog();
    OllamaConfig cfg = services.ollamaConfig();
    IngestionPipeline pipeline = services.ingestionPipeline();

    int activeImages = ps.findAllImages().size();
    int archivedImages = ps.findArchivedImages().size();
    int vectorCount = ps.getRawVectorCount();
    int openSuggestions = services.taxonomySuggestions().findByStatus(SuggestionStatus.OPEN).size();
    boolean paused = pipeline.isPaused();

    // Compute OCR-text and low-confidence counts from analysis records
    Collection<SemanticAnalysis> analyses = ps.findAllImages().stream()
        .map(img -> ps.findAnalysis(img.getId()).orElse(null))
        .filter(Objects::nonNull)
        .toList();
    long ocrCount = analyses.stream()
        .filter(a -> Boolean.TRUE.equals(a.getContainsReadableText()))
        .count();
    long lowConfidenceCount = analyses.stream()
        .filter(a -> a.getCategoryConfidence() != null
            && a.getCategoryConfidence().getPrimaryScore() < LOW_CONFIDENCE_THRESHOLD)
        .count();

    H2 heading = new H2(getTranslation("dashboard.status.title"));
    heading.getStyle().set("margin", "0 0 var(--lumo-space-s) 0");

    // Row 1 — dataset state
    HorizontalLayout row1 = chipRow(
        chip(VaadinIcon.TABLE, getTranslation("dashboard.status.images"), String.valueOf(activeImages)),
        chip(VaadinIcon.ARCHIVE, getTranslation("dashboard.status.archived"), String.valueOf(archivedImages)),
        chip(VaadinIcon.CHART_LINE, getTranslation("dashboard.status.indexed"), String.valueOf(vectorCount)),
        chip(VaadinIcon.CHECK, getTranslation("dashboard.status.ocr"), String.valueOf(ocrCount)),
        chip(VaadinIcon.QUESTION_CIRCLE, getTranslation("dashboard.status.low.confidence"), String.valueOf(lowConfidenceCount)),
        chip(VaadinIcon.QUESTION_CIRCLE, getTranslation("dashboard.status.suggestions"), String.valueOf(openSuggestions))
    );

    // Row 2 — active configuration
    HorizontalLayout row2 = chipRow(
        chip(VaadinIcon.EYE, getTranslation("dashboard.status.vision.model"), cfg.getVisionModel()),
        chip(VaadinIcon.SPLIT, getTranslation("dashboard.status.embedding.model"), cfg.getEmbeddingModel()),
        chip(VaadinIcon.COG, getTranslation("dashboard.status.backend"), cfg.getHost()),
        chip(paused ? VaadinIcon.LIST : VaadinIcon.DASHBOARD,
             getTranslation("dashboard.status.pipeline"),
             paused ? getTranslation("dashboard.status.pipeline.paused")
                 : getTranslation("dashboard.status.pipeline.running"))
    );

    VerticalLayout section = new VerticalLayout(heading, row1, row2);
    section.setPadding(true);
    section.setSpacing(true);
    DashboardLearningSections.applyMutedCard(section);
    return section;
  }

  private HorizontalLayout chipRow(Component... chips) {
    HorizontalLayout row = new HorizontalLayout(chips);
    row.getStyle().set("flex-wrap", "wrap");
    row.setSpacing(true);
    row.setPadding(false);
    return row;
  }

  private Span chip(VaadinIcon icon, String label, String value) {
    Icon ic = icon.create();
    ic.getStyle()
        .set("width", "var(--lumo-icon-size-s)")
        .set("height", "var(--lumo-icon-size-s)")
        .set("flex-shrink", "0");

    Span labelSpan = new Span(label + ": ");
    labelSpan.getStyle().set("font-weight", "500");

    Span valueSpan = new Span(value);
    valueSpan.getStyle().set("font-weight", "bold");

    Span chip = new Span(ic, labelSpan, valueSpan);
    chip.getStyle()
        .set("display", "inline-flex")
        .set("align-items", "center")
        .set("gap", "4px")
        .set("background", "var(--lumo-base-color)")
        .set("border", "1px solid var(--lumo-contrast-20pct)")
        .set("border-radius", "var(--lumo-border-radius-m)")
        .set("padding", "4px 10px")
        .set("font-size", "var(--lumo-font-size-s)");
    return chip;
  }

  // ── 3. Lab state insights ─────────────────────────────────────────────────

  private Component buildLabStateSection() {
    ImageCatalogService ps = services.catalog();
    OllamaConfig cfg = services.ollamaConfig();
    IngestionPipeline pipeline = services.ingestionPipeline();

    H2 heading = new H2(getTranslation("dashboard.lab.title"));
    heading.getStyle().set("margin", "0 0 var(--lumo-space-s) 0");

    Paragraph intro = new Paragraph(getTranslation("dashboard.lab.desc"));
    intro.getStyle()
        .set("color", "var(--lumo-secondary-text-color)")
        .set("font-size", "var(--lumo-font-size-s)")
        .set("margin", "0 0 var(--lumo-space-m) 0");

    Div grid = new Div();
    grid.getStyle()
        .set("display", "grid")
        .set("grid-template-columns", "repeat(auto-fill, minmax(220px, 1fr))")
        .set("gap", "var(--lumo-space-s)");

    // Prompt versions
    int promptVersions = services.promptTemplates().findAll().size();
    grid.add(labItem(getTranslation("dashboard.lab.prompt.versions"),
                     String.valueOf(promptVersions), null));

    // Pending taxonomy suggestions
    int pending = services.taxonomySuggestions().findByStatus(SuggestionStatus.OPEN).size();
    grid.add(labItem(getTranslation("dashboard.lab.pending.suggestions"),
                     String.valueOf(pending),
                     pending > 0 ? "/" + TaxonomyMaintenanceView.PATH : null));

    // Pipeline state
    boolean paused = pipeline.isPaused();
    grid.add(labItem(getTranslation("dashboard.lab.pipeline.state"),
                     paused ? getTranslation("dashboard.status.pipeline.paused")
                         : getTranslation("dashboard.status.pipeline.running"),
                     "/" + PipelineView.PATH));

    // Vision model
    grid.add(labItem(getTranslation("dashboard.status.vision.model"),
                     cfg.getVisionModel(),
                     "/" + MigrationCenterView.PATH));

    // Embedding model
    grid.add(labItem(getTranslation("dashboard.status.embedding.model"),
                     cfg.getEmbeddingModel(),
                     "/" + MigrationCenterView.PATH));

    // Cluster suggestions
    int clusterSuggestions = services.clusterDiscovery().findAllSuggestions().size();
    grid.add(labItem(getTranslation("dashboard.lab.cluster.suggestions"),
                     String.valueOf(clusterSuggestions),
                     clusterSuggestions > 0 ? "/" + TaxonomyMaintenanceView.PATH : null));

    VerticalLayout section = new VerticalLayout(heading, intro, grid);
    section.setPadding(true);
    section.setSpacing(false);
    DashboardLearningSections.applyMutedCard(section);
    return section;
  }

  private Div labItem(String label, String value, String navPath) {
    Span labelSpan = new Span(label);
    labelSpan.getStyle()
        .set("font-size", "var(--lumo-font-size-xs)")
        .set("color", "var(--lumo-secondary-text-color)")
        .set("display", "block");

    Span valueSpan = new Span(value);
    valueSpan.getStyle()
        .set("font-size", "var(--lumo-font-size-s)")
        .set("font-weight", "bold")
        .set("display", "block")
        .set("margin-top", "2px");

    Div item = new Div(labelSpan, valueSpan);
    item.getStyle()
        .set("background", "var(--lumo-base-color)")
        .set("border", "1px solid var(--lumo-contrast-20pct)")
        .set("border-radius", "var(--lumo-border-radius-m)")
        .set("padding", "8px 12px");

    if (navPath != null) {
      item.getStyle().set("cursor", "pointer");
      final String path = navPath;
      item.getElement().addEventListener("click", e -> UI.getCurrent().navigate(path));
    }
    return item;
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  @Override
  public void localeChange(LocaleChangeEvent event) {
    removeAll();
    buildAll();
  }

  @Override
  public String getPageTitle() {
    return getTranslation("dashboard.page.title");
  }
}
