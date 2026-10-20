package com.svenruppert.flow.views.taxonomy;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.MainLayout;
import com.svenruppert.flow.views.shared.ViewServices;
import com.svenruppert.imagerag.domain.TaxonomyAnalysisScope;
import com.svenruppert.imagerag.domain.TaxonomySuggestion;
import com.svenruppert.imagerag.service.TaxonomyAnalysisProgress;
import com.svenruppert.imagerag.service.TaxonomyAnalysisService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Taxonomy Maintenance view — two-column workbench with live progress inspector.
 * <p>Layout:
 * <pre>
 * ┌──────────────────────────────────────────────────────────────────────────┐
 * │  Header (title + description)                                            │
 * ├─────────────────────┬────────────────────────────────────────────────────┤
 * │  Analysis Steps     │  Scope Definition                                  │
 * │  (inspector)        │  Progress bar + ETA                                │
 * │                     │  [Dry-run preview card | Completion summary card]  │
 * │                     │  TabSheet: Image Suggestions / Taxonomy / Deprecated│
 * │                     │            / Cluster discovery                     │
 * └─────────────────────┴────────────────────────────────────────────────────┘
 * </pre>
 * <p>The inspector on the left shows the 7-step analysis pipeline with live
 * color-coded status indicators, mirroring {@code SearchInspectorComponent}.
 * <p>Analysis runs on a virtual thread; the UI is updated via {@code ui.access()}
 * driven by a {@link TaxonomyAnalysisProgress} progress callback. The scope panel,
 * result cards, suggestion tabs and cluster discovery live in their own components.
 */
@Route(value = TaxonomyMaintenanceView.PATH, layout = MainLayout.class)
public class TaxonomyMaintenanceView
    extends VerticalLayout
    implements BeforeEnterObserver, HasDynamicTitle, HasLogger {

  public static final String PATH = "taxonomy";

  private final TaxonomyAnalysisService analysisService;

  private final TaxonomyAnalysisInspectorComponent inspector =
      new TaxonomyAnalysisInspectorComponent();
  private final TaxonomyScopePanel scopePanel;
  private final TaxonomyResultCards resultCards = new TaxonomyResultCards();
  private final TaxonomySuggestionTabs suggestionTabs;

  // ── Progress ──────────────────────────────────────────────────────────────
  private final ProgressBar progressBar = new ProgressBar();
  private final Span progressLabel = new Span();
  private final Span etaLabel = new Span();
  private final Div progressSection;

  private volatile boolean analysisRunning = false;

  public TaxonomyMaintenanceView() {
    ViewServices services = ViewServices.current();
    this.analysisService = services.taxonomyAnalysis();

    setSpacing(false);
    setPadding(true);
    setWidthFull();

    scopePanel = new TaxonomyScopePanel(services.catalog(), this::runAnalysis);
    progressSection = buildProgressSection();
    ClusterDiscoveryPanel clusterPanel = new ClusterDiscoveryPanel(
        services.clusterDiscovery(), services.catalog(), services.imagePreviews());
    suggestionTabs = new TaxonomySuggestionTabs(
        services.taxonomySuggestions(), services.catalog(),
        getTranslation("cluster.tab.title"), clusterPanel);

    add(buildHeader(), buildWorkbench());
  }

  @Override
  public void beforeEnter(BeforeEnterEvent event) {
    suggestionTabs.refreshAllGrids();
    scopePanel.updateScopeSummary();
  }

  // ── Layout ────────────────────────────────────────────────────────────────

  private Component buildHeader() {
    H2 title = new H2(getTranslation("taxonomy.title"));
    Paragraph desc = new Paragraph(getTranslation("taxonomy.description"));
    desc.getStyle().set("color", "var(--lumo-secondary-text-color)");
    VerticalLayout header = new VerticalLayout(title, desc);
    header.setSpacing(false);
    header.setPadding(false);
    return header;
  }

  private Component buildWorkbench() {
    // Left column — inspector
    VerticalLayout leftCol = new VerticalLayout(inspector);
    leftCol.setSpacing(false);
    leftCol.setPadding(false);
    leftCol.setWidth("280px");
    leftCol.setMinWidth("240px");
    leftCol.getStyle().set("flex-shrink", "0");

    // Right column — scope + progress + results
    VerticalLayout rightCol = new VerticalLayout(
        scopePanel,
        progressSection,
        resultCards,
        suggestionTabs
    );
    rightCol.setSpacing(true);
    rightCol.setPadding(false);
    rightCol.setWidthFull();

    HorizontalLayout workbench = new HorizontalLayout(leftCol, rightCol);
    workbench.setWidthFull();
    workbench.setAlignItems(FlexComponent.Alignment.START);
    workbench.setSpacing(true);
    workbench.setPadding(false);
    return workbench;
  }

  private Div buildProgressSection() {
    progressBar.setMin(0);
    progressBar.setMax(1);
    progressBar.setValue(0);
    progressBar.setWidthFull();

    progressLabel.getStyle()
        .set("font-size", "var(--lumo-font-size-s)")
        .set("color", "var(--lumo-secondary-text-color)");
    etaLabel.getStyle()
        .set("font-size", "var(--lumo-font-size-s)")
        .set("color", "var(--lumo-secondary-text-color)");

    HorizontalLayout metaRow = new HorizontalLayout(progressLabel, etaLabel);
    metaRow.setWidthFull();
    metaRow.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

    Div section = new Div(metaRow, progressBar);
    section.setWidthFull();
    section.setVisible(false);
    return section;
  }

  // ── Analysis execution ────────────────────────────────────────────────────

  private void runAnalysis(boolean dryRun) {
    if (analysisRunning) {
      TaxonomyNotifications.show(getTranslation("taxonomy.already.running"), true);
      return;
    }

    TaxonomyAnalysisScope scope = scopePanel.buildScope();
    if (scope == null) return; // validation failed (user was warned)

    analysisRunning = true;
    scopePanel.setControlsEnabled(false);
    inspector.reset();
    progressBar.setValue(0);
    progressLabel.setText(getTranslation("taxonomy.progress.starting"));
    etaLabel.setText("");
    progressSection.setVisible(true);
    resultCards.hideAll();

    UI ui = UI.getCurrent();
    AtomicReference<TaxonomyAnalysisProgress> lastProgress = new AtomicReference<>();

    Thread.ofVirtual().name("taxonomy-analysis").start(() -> {
      List<TaxonomySuggestion> result = new ArrayList<>();
      try {
        if (dryRun) {
          result.addAll(analysisService.analyzeDryRun(scope, progress -> {
            lastProgress.set(progress);
            ui.access(() -> applyProgress(progress.snapshot()));
          }));
        } else {
          result.addAll(analysisService.analyze(scope, progress -> {
            lastProgress.set(progress);
            ui.access(() -> applyProgress(progress.snapshot()));
          }));
        }

        final List<TaxonomySuggestion> finalResult = result;
        final TaxonomyAnalysisProgress finalProgress = lastProgress.get() != null
            ? lastProgress.get().snapshot()
            : new TaxonomyAnalysisProgress(dryRun);

        ui.access(() -> {
          suggestionTabs.refreshAllGrids();
          progressSection.setVisible(false);
          if (dryRun) {
            resultCards.showDryRun(finalResult, finalProgress);
          } else {
            resultCards.showCompletion(finalResult, finalProgress);
          }
          scopePanel.setControlsEnabled(true);
          analysisRunning = false;
        });
      } catch (RuntimeException e) {
        // Worker-thread boundary: no Vaadin error handler runs here, so every failure is
        // logged here and shown to the user.
        logger().error("Taxonomy analysis failed: {}", e.getMessage(), e);
        ui.access(() -> {
          progressSection.setVisible(false);
          TaxonomyNotifications.show(getTranslation("taxonomy.analyze.error", e.getMessage()), true);
          scopePanel.setControlsEnabled(true);
          analysisRunning = false;
        });
      }
    });
  }

  /**
   * Applies a progress snapshot to the inspector and progress bar.
   * Must be called inside {@code ui.access()}.
   */
  private void applyProgress(TaxonomyAnalysisProgress progress) {
    inspector.update(progress);

    double fraction = progress.getProgressFraction();
    if (fraction > 0) {
      progressBar.setValue(fraction);
    }

    int total = progress.getTotalImages();
    int analyzed = progress.getAnalyzedImages();
    if (total > 0) {
      progressLabel.setText(getTranslation("taxonomy.progress.images",
                                           analyzed, total, progress.getElapsedFormatted()));
      String eta = progress.getEtaFormatted();
      etaLabel.setText("—".equals(eta) ? "" :
                           getTranslation("taxonomy.progress.eta", eta));
    } else {
      progressLabel.setText(getTranslation("taxonomy.progress.starting"));
    }
  }

  @Override
  public String getPageTitle() {
    return getTranslation("taxonomy.page.title");
  }
}
