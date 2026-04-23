package com.svenruppert.flow.views.dashboard;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.MainLayout;
import com.svenruppert.flow.views.archive.ArchiveView;
import com.svenruppert.flow.views.migration.MigrationCenterView;
import com.svenruppert.flow.views.multimodal.MultimodalSearchView;
import com.svenruppert.flow.views.overview.OverviewView;
import com.svenruppert.flow.views.pipeline.PipelineView;
import com.svenruppert.flow.views.search.SearchView;
import com.svenruppert.flow.views.shared.ViewServices;
import com.svenruppert.flow.views.taxonomy.TaxonomyMaintenanceView;
import com.svenruppert.flow.views.tuning.SearchTuningView;
import com.svenruppert.flow.views.upload.UploadView;
import com.svenruppert.imagerag.domain.SemanticAnalysis;
import com.svenruppert.imagerag.domain.enums.SuggestionStatus;
import com.svenruppert.imagerag.ollama.OllamaConfig;
import com.svenruppert.imagerag.persistence.PersistenceService;
import com.svenruppert.imagerag.pipeline.IngestionPipeline;
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
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import com.vaadin.flow.router.PageTitle;
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
@PageTitle("Dashboard")
public class DashboardView
    extends VerticalLayout
    implements LocaleChangeObserver, HasLogger {

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
    add(buildStartHerePanel());
    add(buildStatusSection());
    add(buildLabStateSection());
    add(buildArchitectureMap());
    add(buildModulesSection());
    add(buildSequenceSection());
    add(buildExperimentsSection());
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
    PersistenceService ps = services.persistence();
    OllamaConfig cfg = services.ollamaConfig();
    IngestionPipeline pipeline = services.ingestionPipeline();

    int activeImages = ps.findAllImages().size();
    int archivedImages = ps.findArchivedImages().size();
    int vectorCount = ps.getRawVectorCount();
    int openSuggestions = ps.findSuggestionsByStatus(SuggestionStatus.OPEN).size();
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
    applyMutedCard(section);
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
    PersistenceService ps = services.persistence();
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
    int promptVersions = ps.findAllPromptVersions().size();
    grid.add(labItem(getTranslation("dashboard.lab.prompt.versions"),
                     String.valueOf(promptVersions), null));

    // Pending taxonomy suggestions
    int pending = ps.findSuggestionsByStatus(SuggestionStatus.OPEN).size();
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
    int clusterSuggestions = ps.findAllClusterSuggestions().size();
    grid.add(labItem(getTranslation("dashboard.lab.cluster.suggestions"),
                     String.valueOf(clusterSuggestions),
                     clusterSuggestions > 0 ? "/" + TaxonomyMaintenanceView.PATH : null));

    VerticalLayout section = new VerticalLayout(heading, intro, grid);
    section.setPadding(true);
    section.setSpacing(false);
    applyMutedCard(section);
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

  // ── 4. Architecture map ───────────────────────────────────────────────────

  private Component buildArchitectureMap() {
    H2 heading = new H2(getTranslation("dashboard.arch.title"));
    heading.getStyle().set("margin", "0 0 var(--lumo-space-xs) 0");

    Paragraph archDesc = new Paragraph(getTranslation("dashboard.arch.desc"));
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

    H4 title = new H4(getTranslation(titleKey));
    title.getStyle()
        .set("margin", "4px 0 2px 0")
        .set("font-size", "var(--lumo-font-size-s)")
        .set("text-align", "center");

    Paragraph desc = new Paragraph(getTranslation(descKey));
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

  private Component buildModulesSection() {
    H2 heading = new H2(getTranslation("dashboard.modules.title"));
    heading.getStyle().set("margin", "0 0 var(--lumo-space-xs) 0");

    Paragraph desc = new Paragraph(getTranslation("dashboard.modules.desc"));
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
                        new NavEntry(getTranslation("dashboard.module.a.goto.upload"), "/" + UploadView.PATH),
                        new NavEntry(getTranslation("dashboard.module.a.goto.pipeline"), "/" + PipelineView.PATH)));

    // Module B — Analysis & Enrichment
    grid.add(moduleCard(VaadinIcon.EYE, "dashboard.module.b",
                        new NavEntry(getTranslation("dashboard.module.b.goto.overview"), "/" + OverviewView.PATH)));

    // Module C — Retrieval & Search
    grid.add(moduleCard(VaadinIcon.SEARCH, "dashboard.module.c",
                        new NavEntry(getTranslation("dashboard.module.c.goto.search"), "/" + SearchView.PATH),
                        new NavEntry(getTranslation("dashboard.module.c.goto.multimodal"), "/" + MultimodalSearchView.PATH)));

    // Module D — Search Tuning Lab
    grid.add(moduleCard(VaadinIcon.CHART_LINE, "dashboard.module.d",
                        new NavEntry(getTranslation("dashboard.module.d.goto.tuning"), "/" + SearchTuningView.PATH)));

    // Module E — Taxonomy & Category Governance
    grid.add(moduleCard(VaadinIcon.TAG, "dashboard.module.e",
                        new NavEntry(getTranslation("dashboard.module.e.goto.taxonomy"), "/" + TaxonomyMaintenanceView.PATH)));

    // Module F — Migration & Governance
    grid.add(moduleCard(VaadinIcon.COG, "dashboard.module.f",
                        new NavEntry(getTranslation("dashboard.module.f.goto.migration"), "/" + MigrationCenterView.PATH),
                        new NavEntry(getTranslation("dashboard.module.f.goto.archive"), "/" + ArchiveView.PATH)));

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

    H3 title = new H3(getTranslation(keyPrefix + ".title"));
    title.getStyle().set("margin", "var(--lumo-space-xs) 0 var(--lumo-space-xs) 0");

    // "What you will understand here"
    Paragraph whatYouLearn = new Paragraph(getTranslation(keyPrefix + ".learn"));
    whatYouLearn.getStyle()
        .set("font-size", "var(--lumo-font-size-xs)")
        .set("color", "var(--lumo-primary-text-color)")
        .set("font-weight", "600")
        .set("margin", "0 0 var(--lumo-space-xs) 0");

    // Short description
    Paragraph cardDesc = new Paragraph(getTranslation(keyPrefix + ".desc"));
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

  private Component buildSequenceSection() {
    H2 heading = new H2(getTranslation("dashboard.sequence.title"));
    heading.getStyle().set("margin", "0 0 var(--lumo-space-xs) 0");

    Paragraph desc = new Paragraph(getTranslation("dashboard.sequence.desc"));
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
      grid.add(stepRow(i + 1, getTranslation(steps[i][0]), steps[i][1]));
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

  private Component buildExperimentsSection() {
    H2 heading = new H2(getTranslation("dashboard.experiments.title"));
    heading.getStyle().set("margin", "0 0 var(--lumo-space-xs) 0");

    Paragraph desc = new Paragraph(getTranslation("dashboard.experiments.desc"));
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

    H4 title = new H4(getTranslation(keyPrefix + ".title"));
    title.getStyle().set("margin", "var(--lumo-space-xs) 0 var(--lumo-space-xs) 0");

    // Learning goal
    Span goal = new Span(getTranslation(keyPrefix + ".goal"));
    goal.getStyle()
        .set("font-size", "var(--lumo-font-size-xs)")
        .set("font-weight", "600")
        .set("color", "var(--lumo-primary-text-color)")
        .set("display", "block")
        .set("margin-bottom", "var(--lumo-space-xs)");

    Paragraph cardDesc = new Paragraph(getTranslation(keyPrefix + ".desc"));
    cardDesc.getStyle()
        .set("color", "var(--lumo-secondary-text-color)")
        .set("font-size", "var(--lumo-font-size-s)")
        .set("margin", "0 0 var(--lumo-space-m) 0");

    Button btn = new Button(getTranslation(keyPrefix + ".goto"));
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
  private void applyMutedCard(VerticalLayout layout) {
    layout.getStyle()
        .set("background", "var(--lumo-contrast-5pct)")
        .set("border-radius", "var(--lumo-border-radius-l)");
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  @Override
  public void localeChange(LocaleChangeEvent event) {
    removeAll();
    buildAll();
  }

  // ── i18n ──────────────────────────────────────────────────────────────────

  /**
   * Simple value holder for a navigation button label + route path.
   */
  private record NavEntry(String label, String path) { }
}
