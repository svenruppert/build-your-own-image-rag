package com.svenruppert.flow.views.taxonomy;

import com.svenruppert.flow.MainLayout;
import com.svenruppert.imagerag.bootstrap.ServiceRegistry;
import com.svenruppert.imagerag.domain.*;
import com.svenruppert.imagerag.domain.enums.CategoryGroup;
import com.svenruppert.imagerag.domain.enums.CategoryLifecycleState;
import com.svenruppert.imagerag.domain.enums.SourceCategory;
import com.svenruppert.imagerag.domain.enums.SuggestionStatus;
import com.svenruppert.imagerag.persistence.PersistenceService;
import com.svenruppert.imagerag.service.*;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridMultiSelectionModel;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

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
 * └─────────────────────┴────────────────────────────────────────────────────┘
 * </pre>
 * <p>The inspector on the left shows the 7-step analysis pipeline with live
 * colour-coded status indicators, mirroring {@code SearchInspectorComponent}.
 * <p>Analysis runs on a virtual thread; the UI is updated via {@code ui.access()}
 * driven by a {@link TaxonomyAnalysisProgress} progress callback.
 */
@Route(value = TaxonomyMaintenanceView.PATH, layout = MainLayout.class)
@PageTitle("Taxonomy Maintenance")
public class TaxonomyMaintenanceView
    extends VerticalLayout
    implements BeforeEnterObserver {

  public static final String PATH = "taxonomy";

  private static final DateTimeFormatter DATE_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
  /**
   * Default confidence threshold — read once from imagerag.properties at startup.
   */
  private static final double DEFAULT_CONFIDENCE_THRESHOLD =
      com.svenruppert.imagerag.bootstrap.AppConfig.getInstance().getTaxonomyConfidenceThreshold();
  // ── Services ──────────────────────────────────────────────────────────────
  private final TaxonomyAnalysisService analysisService;
  private final TaxonomySuggestionService suggestionService;
  private final PersistenceService persistenceService;
  private final ClusterDiscoveryService clusterDiscoveryService;
  // ── Inspector (left column) ───────────────────────────────────────────────
  private final TaxonomyAnalysisInspectorComponent inspector =
      new TaxonomyAnalysisInspectorComponent();

  // ── Scope controls ────────────────────────────────────────────────────────
  private final Select<TaxonomyAnalysisScope.Mode> modeSelect = new Select<>();
  private final ComboBox<CategoryGroup> groupCombo = new ComboBox<>();
  private final com.vaadin.flow.component.textfield.NumberField thresholdField =
      new com.vaadin.flow.component.textfield.NumberField();
  private final Checkbox subgroupCheck = new Checkbox();
  private final Button selectImagesBtn = new Button();
  private final Span scopeSummary = new Span();
  // ── Run buttons ───────────────────────────────────────────────────────────
  private final Button analyzeBtn = new Button();
  private final Button dryRunBtn = new Button();
  // ── Progress ──────────────────────────────────────────────────────────────
  private final ProgressBar progressBar = new ProgressBar();
  private final Span progressLabel = new Span();
  private final Span etaLabel = new Span();
  private final Div progressSection;
  // ── Info cards ────────────────────────────────────────────────────────────
  private final Div dryRunCard = new Div();
  private final Div completionCard = new Div();
  // ── Grids ─────────────────────────────────────────────────────────────────
  private final Grid<TaxonomySuggestion> imageGrid = new Grid<>();
  private final Grid<TaxonomySuggestion> taxonomyGrid = new Grid<>();
  private final Grid<CategoryMetadata> deprecatedGrid = new Grid<>();
  // ── Batch toolbars ────────────────────────────────────────────────────────
  private final HorizontalLayout imageBatchBar = new HorizontalLayout();
  private final HorizontalLayout taxonomyBatchBar = new HorizontalLayout();
  // ── Cluster discovery tab state ───────────────────────────────────────────
  private final Grid<CategoryClusterSuggestion> clusterGrid = new Grid<>();
  private final ProgressBar clusterProgress = new ProgressBar();
  private final Span clusterStatus = new Span();
  private final com.vaadin.flow.component.textfield.NumberField maxClustersField =
      new com.vaadin.flow.component.textfield.NumberField();
  private final com.vaadin.flow.component.textfield.NumberField minSizeField =
      new com.vaadin.flow.component.textfield.NumberField();
  private final com.vaadin.flow.component.textfield.NumberField similarityThresholdField =
      new com.vaadin.flow.component.textfield.NumberField();
  /**
   * Currently selected image IDs for MANUAL_SELECTION mode.
   */
  private List<UUID> manualImageIds = new ArrayList<>();
  // ── State ─────────────────────────────────────────────────────────────────
  private volatile boolean analysisRunning = false;
  private volatile boolean clusterRunning = false;

  public TaxonomyMaintenanceView() {
    ServiceRegistry sr = ServiceRegistry.getInstance();
    this.analysisService = sr.getTaxonomyAnalysisService();
    this.suggestionService = sr.getTaxonomySuggestionService();
    this.persistenceService = sr.getPersistenceService();
    this.clusterDiscoveryService = sr.getClusterDiscoveryService();

    setSpacing(false);
    setPadding(true);
    setWidthFull();

    progressSection = buildProgressSection();

    add(buildHeader(), buildWorkbench());
  }

  /**
   * Helper: adds a label → value row to the two-column stats grid.
   */
  private static void addClusterStatRow(Div grid, String label, String value) {
    Span l = new Span(label + ":");
    l.getStyle().set("color", "var(--lumo-secondary-text-color)").set("white-space", "nowrap");
    Span v = new Span(value);
    v.getStyle().set("color", "var(--lumo-body-text-color)");
    grid.add(l, v);
  }

  // ── Header ────────────────────────────────────────────────────────────────

  @Override
  public void beforeEnter(BeforeEnterEvent event) {
    refreshAllGrids();
    updateScopeSummary();
  }

  // ── Two-column workbench ──────────────────────────────────────────────────

  private Component buildHeader() {
    H2 title = new H2(getTranslation("taxonomy.title"));
    Paragraph desc = new Paragraph(getTranslation("taxonomy.description"));
    desc.getStyle().set("color", "var(--lumo-secondary-text-color)");
    VerticalLayout header = new VerticalLayout(title, desc);
    header.setSpacing(false);
    header.setPadding(false);
    return header;
  }

  // ── Scope panel ───────────────────────────────────────────────────────────

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
        buildScopePanel(),
        progressSection,
        buildInfoCards(),
        buildResultSection()
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

  private Component buildScopePanel() {
    // Mode selector
    modeSelect.setLabel(getTranslation("taxonomy.scope.mode"));
    modeSelect.setItems(TaxonomyAnalysisScope.Mode.values());
    modeSelect.setItemLabelGenerator(m -> getTranslation("taxonomy.scope.mode." + m.name()));
    modeSelect.setValue(TaxonomyAnalysisScope.Mode.ALL);
    modeSelect.addValueChangeListener(e -> onModeChanged(e.getValue()));

    // Category group (visible only in CATEGORY_GROUP mode)
    groupCombo.setLabel(getTranslation("taxonomy.scope.group"));
    groupCombo.setItems(CategoryGroup.values());
    groupCombo.setItemLabelGenerator(CategoryGroup::getLabel);
    groupCombo.setVisible(false);
    groupCombo.addValueChangeListener(e -> updateScopeSummary());

    // Include subgroups (visible only in CATEGORY_GROUP mode)
    subgroupCheck.setLabel(getTranslation("taxonomy.scope.include.subgroups"));
    subgroupCheck.setValue(true);
    subgroupCheck.setVisible(false);
    subgroupCheck.addValueChangeListener(e -> updateScopeSummary());

    // Confidence threshold (visible only in LOW_CONFIDENCE mode)
    thresholdField.setLabel(getTranslation("taxonomy.scope.threshold"));
    thresholdField.setValue(DEFAULT_CONFIDENCE_THRESHOLD);
    thresholdField.setMin(0.0);
    thresholdField.setMax(1.0);
    thresholdField.setStep(0.05);
    thresholdField.setVisible(false);
    thresholdField.addValueChangeListener(e -> updateScopeSummary());

    // Manual image selection button (visible only in MANUAL_SELECTION mode)
    selectImagesBtn.setText(getTranslation("taxonomy.scope.select.images"));
    selectImagesBtn.addThemeVariants(ButtonVariant.LUMO_CONTRAST, ButtonVariant.LUMO_SMALL);
    selectImagesBtn.setVisible(false);
    selectImagesBtn.addClickListener(e -> openManualSelectionDialog());

    // Scope summary badge
    scopeSummary.getStyle()
        .set("font-size", "var(--lumo-font-size-s)")
        .set("color", "var(--lumo-secondary-text-color)")
        .set("align-self", "flex-end")
        .set("padding-bottom", "0.25rem");

    // Run buttons
    analyzeBtn.setText(getTranslation("taxonomy.analyze"));
    analyzeBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    analyzeBtn.addClickListener(e -> runAnalysis(false));

    dryRunBtn.setText(getTranslation("taxonomy.analyze.dryrun"));
    dryRunBtn.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
    dryRunBtn.addClickListener(e -> runAnalysis(true));

    HorizontalLayout scopeRow1 = new HorizontalLayout(
        modeSelect, groupCombo, subgroupCheck, thresholdField, selectImagesBtn);
    scopeRow1.setAlignItems(FlexComponent.Alignment.END);
    scopeRow1.setSpacing(true);

    HorizontalLayout scopeRow2 = new HorizontalLayout(
        scopeSummary, analyzeBtn, dryRunBtn);
    scopeRow2.setAlignItems(FlexComponent.Alignment.END);
    scopeRow2.setSpacing(true);

    VerticalLayout panel = new VerticalLayout(scopeRow1, scopeRow2);
    panel.setSpacing(false);
    panel.setPadding(true);
    panel.getStyle()
        .set("border", "1px solid var(--lumo-contrast-10pct)")
        .set("border-radius", "var(--lumo-border-radius-l)")
        .set("background", "var(--lumo-base-color)");
    panel.setWidthFull();
    return panel;
  }

  private void onModeChanged(TaxonomyAnalysisScope.Mode mode) {
    boolean isGroup = mode == TaxonomyAnalysisScope.Mode.CATEGORY_GROUP;
    boolean isLowConf = mode == TaxonomyAnalysisScope.Mode.LOW_CONFIDENCE;
    boolean isManual = mode == TaxonomyAnalysisScope.Mode.MANUAL_SELECTION;

    groupCombo.setVisible(isGroup);
    subgroupCheck.setVisible(isGroup);
    thresholdField.setVisible(isLowConf);
    selectImagesBtn.setVisible(isManual);

    updateScopeSummary();
  }

  private void openManualSelectionDialog() {
    ManualImageSelectionDialog dialog = new ManualImageSelectionDialog(
        persistenceService, manualImageIds, selected -> {
      manualImageIds = new ArrayList<>(selected);
      updateScopeSummary();
    });
    dialog.open();
  }

  // ── Progress section ──────────────────────────────────────────────────────

  /**
   * Computes a human-readable scope summary (e.g. "~42 images in scope") and
   * updates the {@link #scopeSummary} label.  Runs on the UI thread; no I/O.
   */
  private void updateScopeSummary() {
    TaxonomyAnalysisScope.Mode mode = modeSelect.getValue();
    if (mode == null) {
      scopeSummary.setText("");
      return;
    }

    String text = switch (mode) {
      case ALL -> {
        long count = persistenceService.findAllImages().size();
        yield getTranslation("taxonomy.scope.summary.all", count);
      }
      case MANUAL_SELECTION -> {
        int sel = manualImageIds.size();
        yield sel == 0
            ? getTranslation("taxonomy.scope.summary.manual.none")
            : getTranslation("taxonomy.scope.summary.manual", sel);
      }
      case CATEGORY_GROUP -> {
        CategoryGroup group = groupCombo.getValue();
        if (group == null) yield getTranslation("taxonomy.scope.summary.group.none");
        boolean incl = subgroupCheck.getValue() != null && subgroupCheck.getValue();
        long count = persistenceService.findAllImages().stream()
            .filter(a -> {
              Optional<com.svenruppert.imagerag.domain.SemanticAnalysis> an =
                  persistenceService.findAnalysis(a.getId());
              if (an.isEmpty()) return false;
              boolean prim = CategoryRegistry.getGroup(an.get().getSourceCategory()) == group;
              boolean sec = incl && an.get().getSecondaryCategories().stream()
                  .anyMatch(sc -> CategoryRegistry.getGroup(sc) == group);
              return prim || sec;
            }).count();
        yield getTranslation("taxonomy.scope.summary.group", group.getLabel(), count);
      }
      case MISSING_CATEGORY -> {
        long count = persistenceService.findAllImages().stream()
            .filter(a -> {
              Optional<com.svenruppert.imagerag.domain.SemanticAnalysis> an =
                  persistenceService.findAnalysis(a.getId());
              if (an.isEmpty()) return true;
              SourceCategory cat = an.get().getSourceCategory();
              return cat == null || cat == SourceCategory.UNKNOWN || cat == SourceCategory.MIXED;
            }).count();
        yield getTranslation("taxonomy.scope.summary.missing", count);
      }
      case LOW_CONFIDENCE -> {
        Double thr = thresholdField.getValue();
        double threshold = thr != null ? thr : DEFAULT_CONFIDENCE_THRESHOLD;
        long count = persistenceService.findAllImages().stream()
            .filter(a -> {
              Optional<com.svenruppert.imagerag.domain.SemanticAnalysis> an =
                  persistenceService.findAnalysis(a.getId());
              if (an.isEmpty()) return false;
              com.svenruppert.imagerag.domain.CategoryConfidence cc =
                  an.get().getCategoryConfidence();
              return cc == null || cc.isAmbiguous(threshold);
            }).count();
        yield getTranslation("taxonomy.scope.summary.low.confidence", count, threshold);
      }
    };
    scopeSummary.setText(text);
  }

  // ── Info cards ────────────────────────────────────────────────────────────

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

  private Component buildInfoCards() {
    styleInfoCard(dryRunCard, "var(--lumo-primary-color-10pct)", "var(--lumo-primary-color)");
    dryRunCard.setVisible(false);

    styleInfoCard(completionCard, "var(--lumo-success-color-10pct)", "var(--lumo-success-color)");
    completionCard.setVisible(false);

    Div wrapper = new Div(dryRunCard, completionCard);
    wrapper.setWidthFull();
    return wrapper;
  }

  private void styleInfoCard(Div card, String bg, String borderColor) {
    card.getStyle()
        .set("background", bg)
        .set("border-left", "4px solid " + borderColor)
        .set("border-radius", "var(--lumo-border-radius-m)")
        .set("padding", "0.75rem 1rem")
        .set("width", "100%")
        .set("box-sizing", "border-box");
  }

  private void showDryRunCard(List<TaxonomySuggestion> suggestions, TaxonomyAnalysisProgress progress) {
    dryRunCard.removeAll();
    completionCard.setVisible(false);

    H4 heading = new H4(getTranslation("taxonomy.dryrun.card.title"));
    heading.getStyle().set("margin", "0 0 0.4rem 0").set("color", "var(--lumo-primary-color)");

    long imageLevel = suggestions.stream().filter(TaxonomySuggestion::isImageLevel).count();
    long taxLevel = suggestions.stream().filter(s -> !s.isImageLevel()).count();

    Paragraph body = new Paragraph(getTranslation("taxonomy.dryrun.card.body",
                                                  suggestions.size(), imageLevel, taxLevel,
                                                  progress.getElapsedFormatted()));
    body.getStyle().set("margin", "0").set("font-size", "var(--lumo-font-size-s)");

    Paragraph hint = new Paragraph(getTranslation("taxonomy.dryrun.card.hint"));
    hint.getStyle()
        .set("margin", "0.4rem 0 0 0")
        .set("font-size", "var(--lumo-font-size-xs)")
        .set("color", "var(--lumo-secondary-text-color)");

    dryRunCard.add(heading, body, hint);
    dryRunCard.setVisible(true);
  }

  // ── Result tabs ───────────────────────────────────────────────────────────

  private void showCompletionCard(List<TaxonomySuggestion> suggestions, TaxonomyAnalysisProgress progress) {
    completionCard.removeAll();
    dryRunCard.setVisible(false);

    H4 heading = new H4(getTranslation("taxonomy.completion.card.title"));
    heading.getStyle().set("margin", "0 0 0.4rem 0").set("color", "var(--lumo-success-color)");

    long imageLevel = suggestions.stream().filter(TaxonomySuggestion::isImageLevel).count();
    long taxLevel = suggestions.stream().filter(s -> !s.isImageLevel()).count();

    Paragraph body = new Paragraph(getTranslation("taxonomy.completion.card.body",
                                                  suggestions.size(), imageLevel, taxLevel,
                                                  progress.getElapsedFormatted()));
    body.getStyle().set("margin", "0").set("font-size", "var(--lumo-font-size-s)");

    Paragraph hint = new Paragraph(getTranslation("taxonomy.completion.card.hint"));
    hint.getStyle()
        .set("margin", "0.4rem 0 0 0")
        .set("font-size", "var(--lumo-font-size-xs)")
        .set("color", "var(--lumo-secondary-text-color)");

    completionCard.add(heading, body, hint);
    completionCard.setVisible(true);
  }

  // ── Cluster Discovery tab ─────────────────────────────────────────────────

  private Component buildResultSection() {
    TabSheet tabs = new TabSheet();

    VerticalLayout imageTabContent = new VerticalLayout(buildImageBatchBar(), buildImageGrid());
    imageTabContent.setSpacing(false);
    imageTabContent.setPadding(false);

    VerticalLayout taxonomyTabContent =
        new VerticalLayout(buildTaxonomyBatchBar(), buildTaxonomyGrid());
    taxonomyTabContent.setSpacing(false);
    taxonomyTabContent.setPadding(false);

    VerticalLayout deprecatedTabContent = new VerticalLayout(buildDeprecatedGrid());
    deprecatedTabContent.setSpacing(false);
    deprecatedTabContent.setPadding(false);

    tabs.add(getTranslation("taxonomy.tab.images"), imageTabContent);
    tabs.add(getTranslation("taxonomy.tab.taxonomy"), taxonomyTabContent);
    tabs.add(getTranslation("taxonomy.tab.deprecated"), deprecatedTabContent);
    tabs.add(getTranslation("cluster.tab.title"), buildClusterTab());
    tabs.setWidthFull();
    return tabs;
  }

  private Component buildClusterTab() {
    VerticalLayout tab = new VerticalLayout();
    tab.setSpacing(true);
    tab.setPadding(false);
    tab.setWidthFull();

    Paragraph desc = new Paragraph(getTranslation("cluster.description"));
    desc.getStyle().set("color", "var(--lumo-secondary-text-color)")
        .set("font-size", "var(--lumo-font-size-s)");
    tab.add(desc);

    // Configuration row
    maxClustersField.setLabel(getTranslation("cluster.max.clusters"));
    maxClustersField.setValue(10.0);
    maxClustersField.setMin(2);
    maxClustersField.setMax(50);
    maxClustersField.setStep(1);
    maxClustersField.setStepButtonsVisible(true);
    maxClustersField.setWidth("140px");

    minSizeField.setLabel(getTranslation("cluster.min.size"));
    minSizeField.setValue(3.0);
    minSizeField.setMin(1);
    minSizeField.setMax(50);
    minSizeField.setStep(1);
    minSizeField.setStepButtonsVisible(true);
    minSizeField.setWidth("140px");

    similarityThresholdField.setLabel(getTranslation("cluster.threshold"));
    similarityThresholdField.setValue(0.6);
    similarityThresholdField.setMin(0.1);
    similarityThresholdField.setMax(1.0);
    similarityThresholdField.setStep(0.05);
    similarityThresholdField.setStepButtonsVisible(true);
    similarityThresholdField.setWidth("160px");

    Button runBtn = new Button(getTranslation("cluster.run.button"), e -> runClusterDiscovery());
    runBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    HorizontalLayout configRow = new HorizontalLayout(
        maxClustersField, minSizeField, similarityThresholdField, runBtn);
    configRow.setAlignItems(FlexComponent.Alignment.END);
    configRow.setSpacing(true);
    tab.add(configRow);

    // Progress
    clusterProgress.setIndeterminate(true);
    clusterProgress.setVisible(false);
    clusterProgress.setWidthFull();
    clusterStatus.getStyle()
        .set("font-size", "var(--lumo-font-size-xs)")
        .set("color", "var(--lumo-secondary-text-color)");
    tab.add(clusterProgress, clusterStatus);

    // Grid
    buildClusterGrid();
    clusterGrid.setWidthFull();
    clusterGrid.setHeight("380px");
    tab.add(clusterGrid);

    // Populate from persisted suggestions on load
    refreshClusterGrid();
    return tab;
  }

  private void buildClusterGrid() {
    clusterGrid.addColumn(s -> s.getStatus() != null
            ? getTranslation("cluster.status." + s.getStatus().name().toLowerCase()) : "\u2014")
        .setHeader(getTranslation("cluster.suggestion.status")).setAutoWidth(true);

    clusterGrid.addColumn(s -> s.getSuggestionType() != null
            ? s.getSuggestionType().getLabel() : "\u2014")
        .setHeader(getTranslation("cluster.suggestion.type")).setAutoWidth(true);

    clusterGrid.addColumn(s -> s.getClusterLabel() != null
            ? s.getClusterLabel() : "\u2014")
        .setHeader(getTranslation("cluster.suggestion.label")).setAutoWidth(true).setFlexGrow(1);

    clusterGrid.addColumn(s -> s.getCategoryDistribution() != null
            ? s.getCategoryDistribution().keySet().stream()
              .sorted()
              .collect(Collectors.joining(", "))
            : "\u2014")
        .setHeader(getTranslation("cluster.suggestion.categories")).setAutoWidth(true);

    clusterGrid.addColumn(s -> String.valueOf(s.clusterSize()))
        .setHeader(getTranslation("cluster.suggestion.size")).setAutoWidth(true);

    clusterGrid.addColumn(s -> String.format("%.2f", s.getIntraClusterSimilarity()))
        .setHeader(getTranslation("cluster.suggestion.similarity")).setAutoWidth(true);

    clusterGrid.addComponentColumn(s -> {
      HorizontalLayout actions = new HorizontalLayout();
      actions.setSpacing(false);
      actions.getStyle().set("gap", "0.3rem");

      // "View images" — always visible so the user can inspect cluster members
      Button viewBtn = new Button(getTranslation("cluster.view.images"),
                                  e -> openClusterImagesDialog(s));
      viewBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
      actions.add(viewBtn);

      if (s.getStatus() == CategoryClusterSuggestion.ClusterSuggestionStatus.OPEN) {
        // "Accept" now shows an impact preview before committing
        Button acceptBtn = new Button(getTranslation("cluster.accept"),
                                      e -> openClusterAcceptPreview(s));
        acceptBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_SUCCESS);

        Button dismissBtn = new Button(getTranslation("cluster.dismiss"), e -> {
          s.dismiss();
          ServiceRegistry.getInstance().getPersistenceService().saveClusterSuggestion(s);
          refreshClusterGrid();
          Notification.show(getTranslation("cluster.dismissed", s.getClusterLabel()),
                            2000, Notification.Position.BOTTOM_START);
        });
        dismissBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

        actions.add(acceptBtn, dismissBtn);
      }
      return actions;
    }).setHeader(getTranslation("taxonomy.col.actions")).setAutoWidth(true).setFlexGrow(0);
  }

  private void runClusterDiscovery() {
    if (clusterRunning) {
      Notification.show(getTranslation("cluster.running"), 2000, Notification.Position.MIDDLE);
      return;
    }
    clusterRunning = true;
    int maxClusters = (int) (maxClustersField.getValue() != null
        ? maxClustersField.getValue() : 10.0);
    int minSize = (int) (minSizeField.getValue() != null
        ? minSizeField.getValue() : 3.0);
    double threshold = similarityThresholdField.getValue() != null
        ? similarityThresholdField.getValue() : 0.6;

    UI ui = UI.getCurrent();
    ui.access(() -> {
      clusterProgress.setVisible(true);
      clusterStatus.setText(getTranslation("cluster.running"));
    });

    Thread.ofVirtual().name("cluster-discovery").start(() -> {
      try {
        List<CategoryClusterSuggestion> suggestions = clusterDiscoveryService.discover(
            maxClusters, minSize, threshold,
            msg -> ui.access(() -> clusterStatus.setText(msg)));

        // Persist newly discovered suggestions
        PersistenceService ps = ServiceRegistry.getInstance().getPersistenceService();
        suggestions.forEach(ps::saveClusterSuggestion);

        ui.access(() -> {
          clusterProgress.setVisible(false);
          clusterStatus.setText(getTranslation("cluster.done", suggestions.size()));
          Notification n = Notification.show(
              getTranslation("cluster.done", suggestions.size()),
              3000, Notification.Position.BOTTOM_START);
          n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
          refreshClusterGrid();
          clusterRunning = false;
        });
      } catch (Exception ex) {
        ui.access(() -> {
          clusterProgress.setVisible(false);
          clusterStatus.setText(getTranslation("cluster.error", ex.getMessage()));
          showNotification(getTranslation("cluster.error", ex.getMessage()), true);
          clusterRunning = false;
        });
      }
    });
  }

  private void refreshClusterGrid() {
    List<CategoryClusterSuggestion> suggestions =
        ServiceRegistry.getInstance().getPersistenceService().findAllClusterSuggestions();
    clusterGrid.setItems(suggestions);
  }

  /**
   * Opens a dialog showing thumbnail previews of the images that belong to the cluster.
   * This lets reviewers judge whether the cluster is semantically coherent before
   * accepting or dismissing the suggestion.
   */
  private void openClusterImagesDialog(CategoryClusterSuggestion s) {
    Dialog dlg = new Dialog();
    String label = s.getClusterLabel() != null ? s.getClusterLabel() : "\u2014";
    dlg.setHeaderTitle(getTranslation("cluster.images.title", label));
    dlg.setWidth("760px");
    dlg.setMaxHeight("82vh");

    VerticalLayout content = new VerticalLayout();
    content.setPadding(false);
    content.setSpacing(true);
    content.setWidthFull();

    // Cluster stats summary
    Paragraph info = new Paragraph(getTranslation(
        "cluster.images.info", s.clusterSize(),
        String.format("%.2f", s.getIntraClusterSimilarity())));
    info.getStyle()
        .set("font-size", "var(--lumo-font-size-xs)")
        .set("color", "var(--lumo-secondary-text-color)")
        .set("margin", "0 0 0.5rem 0");
    content.add(info);

    // Thumbnail grid
    Div thumbGrid = new Div();
    thumbGrid.getStyle()
        .set("display", "grid")
        .set("grid-template-columns", "repeat(auto-fill, minmax(110px, 1fr))")
        .set("gap", "0.5rem");

    PersistenceService ps = ServiceRegistry.getInstance().getPersistenceService();
    ImageStorageService storage = ServiceRegistry.getInstance().getImageStorageService();
    PreviewService previews = ServiceRegistry.getInstance().getPreviewService();

    List<UUID> ids = s.getImageIds();
    // Cap at 40 thumbnails to keep the dialog manageable
    List<UUID> shown = ids.size() > 40 ? ids.subList(0, 40) : ids;
    for (UUID imgId : shown) {
      ps.findImage(imgId).ifPresent(asset -> {
        Div cell = new Div();
        cell.getStyle()
            .set("border", "1px solid var(--lumo-contrast-10pct)")
            .set("border-radius", "var(--lumo-border-radius-s)")
            .set("overflow", "hidden")
            .set("aspect-ratio", "1")
            .set("background", "var(--lumo-contrast-5pct)")
            .set("display", "flex")
            .set("align-items", "center")
            .set("justify-content", "center");

        try {
          Path imagePath = storage.resolvePath(asset.getId());
          if (Files.exists(imagePath)) {
            StreamResource res = previews.getTilePreview(
                asset.getId(), imagePath, asset.getStoredFilename());
            if (res == null) {
              res = new StreamResource(asset.getStoredFilename(),
                                       () -> {
                                         try {
                                           return Files.newInputStream(imagePath);
                                         } catch (Exception ex) {
                                           return InputStream.nullInputStream();
                                         }
                                       });
            }
            String altText = asset.getOriginalFilename() != null
                ? asset.getOriginalFilename() : imgId.toString();
            Image img = new Image(res, altText);
            img.setWidth("100%");
            img.setHeight("100%");
            img.getStyle().set("object-fit", "cover").set("display", "block");
            cell.add(img);
          } else {
            String name = asset.getOriginalFilename() != null
                ? asset.getOriginalFilename() : "?";
            Span placeholder = new Span(name.length() > 12 ? name.substring(0, 10) + "\u2026" : name);
            placeholder.getStyle()
                .set("font-size", "var(--lumo-font-size-xxs)")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("text-align", "center")
                .set("padding", "0.25rem");
            cell.add(placeholder);
          }
        } catch (Exception ex) {
          cell.add(new Span("?"));
        }
        thumbGrid.add(cell);
      });
    }

    if (ids.size() > 40) {
      Paragraph overflow = new Paragraph(getTranslation("cluster.images.overflow", ids.size() - 40));
      overflow.getStyle()
          .set("font-size", "var(--lumo-font-size-xs)")
          .set("color", "var(--lumo-secondary-text-color)")
          .set("margin-top", "0.5rem");
      content.add(thumbGrid, overflow);
    } else {
      content.add(thumbGrid);
    }

    dlg.add(content);
    Button closeBtn = new Button(getTranslation("common.close"), e -> dlg.close());
    dlg.getFooter().add(closeBtn);
    dlg.open();
  }

  /**
   * Shows a dry-run / impact preview dialog before the user confirms acceptance of a
   * cluster suggestion.  Displays the cluster size, suggestion type, category distribution,
   * and rationale so the user can make an informed decision.
   */
  private void openClusterAcceptPreview(CategoryClusterSuggestion s) {
    Dialog dlg = new Dialog();
    dlg.setHeaderTitle(getTranslation("cluster.accept.preview.title"));
    dlg.setWidth("460px");

    VerticalLayout content = new VerticalLayout();
    content.setPadding(false);
    content.setSpacing(false);
    content.setWidthFull();

    // Impact stats grid
    Div stats = new Div();
    stats.getStyle()
        .set("display", "grid")
        .set("grid-template-columns", "auto 1fr")
        .set("gap", "0.25rem 0.9rem")
        .set("font-size", "var(--lumo-font-size-s)")
        .set("background", "var(--lumo-contrast-5pct)")
        .set("border-radius", "var(--lumo-border-radius-s)")
        .set("padding", "0.5rem 0.75rem")
        .set("margin-bottom", "0.5rem")
        .set("width", "100%");

    addClusterStatRow(stats, getTranslation("cluster.accept.preview.images"),
                      String.valueOf(s.clusterSize()));
    addClusterStatRow(stats, getTranslation("cluster.accept.preview.type"),
                      s.getSuggestionType() != null ? s.getSuggestionType().getLabel() : "\u2014");
    addClusterStatRow(stats, getTranslation("cluster.accept.preview.dominant"),
                      s.getDominantCategory() != null ? s.getDominantCategory().name() : "\u2014");
    addClusterStatRow(stats, getTranslation("cluster.accept.preview.similarity"),
                      String.format("%.2f", s.getIntraClusterSimilarity()));

    if (!s.getCategoryDistribution().isEmpty()) {
      StringBuilder catStr = new StringBuilder();
      s.getCategoryDistribution().entrySet().stream()
          .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
          .forEach(entry -> {
            if (catStr.length() > 0) catStr.append(", ");
            catStr.append(entry.getKey()).append("\u00d7").append(entry.getValue());
          });
      addClusterStatRow(stats, getTranslation("cluster.accept.preview.categories"), catStr.toString());
    }
    content.add(stats);

    if (s.getRationale() != null && !s.getRationale().isBlank()) {
      Paragraph rationale = new Paragraph(s.getRationale());
      rationale.getStyle()
          .set("font-size", "var(--lumo-font-size-xs)")
          .set("color", "var(--lumo-secondary-text-color)")
          .set("margin", "0");
      content.add(rationale);
    }

    dlg.add(content);

    Button cancelBtn = new Button(getTranslation("common.cancel"), e -> dlg.close());
    Button confirmBtn = new Button(getTranslation("cluster.accept.confirm"));
    confirmBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
    confirmBtn.addClickListener(e -> {
      s.accept();
      ServiceRegistry.getInstance().getPersistenceService().saveClusterSuggestion(s);
      dlg.close();
      refreshClusterGrid();
      Notification n = Notification.show(
          getTranslation("cluster.accepted", s.getClusterLabel()),
          2000, Notification.Position.BOTTOM_START);
      n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    });
    dlg.getFooter().add(cancelBtn, confirmBtn);
    dlg.open();
  }

  // ── Image suggestions grid ────────────────────────────────────────────────

  private Component buildImageBatchBar() {
    Button acceptAll = new Button(getTranslation("taxonomy.batch.accept"),
                                  e -> bulkAction("accept_image"));
    Button rejectAll = new Button(getTranslation("taxonomy.batch.reject"),
                                  e -> bulkAction("reject_image"));
    Button applyAll = new Button(getTranslation("taxonomy.batch.apply"),
                                 e -> bulkAction("apply_image"));
    applyAll.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);

    imageBatchBar.add(acceptAll, rejectAll, applyAll);
    imageBatchBar.setVisible(false);
    return imageBatchBar;
  }

  private Component buildImageGrid() {
    imageGrid.setMultiSort(false);
    imageGrid.setSelectionMode(Grid.SelectionMode.MULTI);
    imageGrid.setWidthFull();
    imageGrid.setHeight("380px");

    imageGrid.addColumn(s -> s.getTargetImageFilename() != null
            ? s.getTargetImageFilename() : "\u2014")
        .setHeader(getTranslation("taxonomy.col.filename"))
        .setSortable(true).setAutoWidth(true);

    imageGrid.addColumn(s -> s.getType() != null
            ? getTranslation("taxonomy.type." + s.getType().name()) : "\u2014")
        .setHeader(getTranslation("taxonomy.col.type")).setAutoWidth(true);

    imageGrid.addColumn(s -> s.getCurrentCategory() != null
            ? CategoryRegistry.getUserLabel(s.getCurrentCategory()) : "\u2014")
        .setHeader(getTranslation("taxonomy.col.current")).setAutoWidth(true);

    imageGrid.addColumn(s -> s.getSuggestedCategory() != null
            ? CategoryRegistry.getUserLabel(s.getSuggestedCategory()) : "\u2014")
        .setHeader(getTranslation("taxonomy.col.suggested")).setAutoWidth(true);

    imageGrid.addColumn(s -> String.format("%.0f%%", s.getConfidence() * 100))
        .setHeader(getTranslation("taxonomy.col.confidence")).setAutoWidth(true);

    imageGrid.addColumn(TaxonomySuggestion::getRationale)
        .setHeader(getTranslation("taxonomy.col.rationale"))
        .setAutoWidth(true).setFlexGrow(1);

    imageGrid.addComponentColumn(s -> buildStatusBadge(s.getStatus()))
        .setHeader(getTranslation("taxonomy.col.status")).setAutoWidth(true);

    imageGrid.addComponentColumn(s -> buildActionButtons(s))
        .setHeader(getTranslation("taxonomy.col.actions")).setAutoWidth(true);

    ((GridMultiSelectionModel<TaxonomySuggestion>) imageGrid.getSelectionModel())
        .addMultiSelectionListener(e -> imageBatchBar.setVisible(!e.getValue().isEmpty()));

    return imageGrid;
  }

  // ── Taxonomy suggestions grid ─────────────────────────────────────────────

  private Component buildTaxonomyBatchBar() {
    Button acceptAll = new Button(getTranslation("taxonomy.batch.accept"),
                                  e -> bulkAction("accept_taxonomy"));
    Button rejectAll = new Button(getTranslation("taxonomy.batch.reject"),
                                  e -> bulkAction("reject_taxonomy"));
    Button applyAll = new Button(getTranslation("taxonomy.batch.apply"),
                                 e -> bulkAction("apply_taxonomy"));
    applyAll.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);

    taxonomyBatchBar.add(acceptAll, rejectAll, applyAll);
    taxonomyBatchBar.setVisible(false);
    return taxonomyBatchBar;
  }

  private Component buildTaxonomyGrid() {
    taxonomyGrid.setMultiSort(false);
    taxonomyGrid.setSelectionMode(Grid.SelectionMode.MULTI);
    taxonomyGrid.setWidthFull();
    taxonomyGrid.setHeight("380px");

    taxonomyGrid.addColumn(s -> s.getType() != null
            ? getTranslation("taxonomy.type." + s.getType().name()) : "\u2014")
        .setHeader(getTranslation("taxonomy.col.type")).setAutoWidth(true);

    taxonomyGrid.addColumn(s -> s.getCurrentCategory() != null
            ? CategoryRegistry.getUserLabel(s.getCurrentCategory()) : "\u2014")
        .setHeader(getTranslation("taxonomy.col.current")).setAutoWidth(true);

    taxonomyGrid.addColumn(s -> s.getSuggestedCategory() != null
            ? CategoryRegistry.getUserLabel(s.getSuggestedCategory()) : "\u2014")
        .setHeader(getTranslation("taxonomy.col.suggested")).setAutoWidth(true);

    taxonomyGrid.addColumn(s -> s.getSuggestedAlias() != null ? s.getSuggestedAlias() : "\u2014")
        .setHeader(getTranslation("taxonomy.col.alias")).setAutoWidth(true);

    taxonomyGrid.addColumn(TaxonomySuggestion::getRationale)
        .setHeader(getTranslation("taxonomy.col.rationale"))
        .setAutoWidth(true).setFlexGrow(1);

    taxonomyGrid.addComponentColumn(s -> buildStatusBadge(s.getStatus()))
        .setHeader(getTranslation("taxonomy.col.status")).setAutoWidth(true);

    taxonomyGrid.addComponentColumn(s -> buildActionButtons(s))
        .setHeader(getTranslation("taxonomy.col.actions")).setAutoWidth(true);

    ((GridMultiSelectionModel<TaxonomySuggestion>) taxonomyGrid.getSelectionModel())
        .addMultiSelectionListener(e -> taxonomyBatchBar.setVisible(!e.getValue().isEmpty()));

    return taxonomyGrid;
  }

  // ── Deprecated categories grid ────────────────────────────────────────────

  private Component buildDeprecatedGrid() {
    H3 heading = new H3(getTranslation("taxonomy.deprecated.heading"));

    deprecatedGrid.setWidthFull();
    deprecatedGrid.setHeight("280px");

    deprecatedGrid.addColumn(m -> m.getCategory() != null
            ? CategoryRegistry.getUserLabel(m.getCategory()) : "\u2014")
        .setHeader(getTranslation("taxonomy.col.category")).setAutoWidth(true);

    deprecatedGrid.addColumn(m -> m.getLifecycleState() != null
            ? getTranslation("taxonomy.lifecycle." + m.getLifecycleState().name()) : "\u2014")
        .setHeader(getTranslation("taxonomy.col.lifecycle")).setAutoWidth(true);

    deprecatedGrid.addColumn(m -> m.getReplacementCategory() != null
            ? CategoryRegistry.getUserLabel(m.getReplacementCategory()) : "\u2014")
        .setHeader(getTranslation("taxonomy.col.replacement")).setAutoWidth(true);

    deprecatedGrid.addColumn(m -> m.getNotes() != null ? m.getNotes() : "\u2014")
        .setHeader(getTranslation("taxonomy.col.notes"))
        .setAutoWidth(true).setFlexGrow(1);

    deprecatedGrid.addColumn(m -> m.getUpdatedAt() != null
            ? DATE_FMT.format(m.getUpdatedAt()) : "\u2014")
        .setHeader(getTranslation("taxonomy.col.updated.at")).setAutoWidth(true);

    VerticalLayout layout = new VerticalLayout(heading, deprecatedGrid);
    layout.setSpacing(false);
    layout.setPadding(false);
    return layout;
  }

  // ── Action buttons per row ────────────────────────────────────────────────

  private Component buildActionButtons(TaxonomySuggestion s) {
    HorizontalLayout buttons = new HorizontalLayout();
    buttons.setSpacing(true);
    SuggestionStatus status = s.getStatus();

    if (status == SuggestionStatus.OPEN || status == SuggestionStatus.ACCEPTED) {
      Button acceptBtn = new Button(getTranslation("taxonomy.action.accept"), e -> {
        suggestionService.accept(s.getId());
        refreshAllGrids();
        showNotification(getTranslation("taxonomy.accepted"), false);
      });
      acceptBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_SUCCESS);
      acceptBtn.setEnabled(status == SuggestionStatus.OPEN);

      Button rejectBtn = new Button(getTranslation("taxonomy.action.reject"), e -> {
        suggestionService.reject(s.getId());
        refreshAllGrids();
        showNotification(getTranslation("taxonomy.rejected"), false);
      });
      rejectBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);

      Button applyBtn = new Button(getTranslation("taxonomy.action.apply"),
                                   e -> applyWithConfirm(s));
      applyBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);

      buttons.add(acceptBtn, rejectBtn, applyBtn);
    } else {
      buttons.add(buildStatusBadge(status));
    }
    return buttons;
  }

  private Span buildStatusBadge(SuggestionStatus status) {
    String theme = switch (status) {
      case OPEN -> "badge";
      case ACCEPTED -> "badge success";
      case REJECTED -> "badge error";
      case APPLIED -> "badge contrast";
    };
    Span badge = new Span(getTranslation("taxonomy.status." + status.name()));
    badge.getElement().getThemeList().add(theme);
    return badge;
  }

  // ── Analysis execution ────────────────────────────────────────────────────

  private void runAnalysis(boolean dryRun) {
    if (analysisRunning) {
      showNotification(getTranslation("taxonomy.already.running"), true);
      return;
    }

    TaxonomyAnalysisScope scope = buildScope();
    if (scope == null) return; // validation failed (user was warned)

    analysisRunning = true;
    setControlsEnabled(false);
    inspector.reset();
    progressBar.setValue(0);
    progressLabel.setText(getTranslation("taxonomy.progress.starting"));
    etaLabel.setText("");
    progressSection.setVisible(true);
    dryRunCard.setVisible(false);
    completionCard.setVisible(false);

    UI ui = UI.getCurrent();
    AtomicReference<TaxonomyAnalysisProgress> lastProgress = new AtomicReference<>();

    Thread.ofVirtual().name("taxonomy-analysis").start(() -> {
      List<TaxonomySuggestion> result = new ArrayList<>();
      try {
        if (dryRun) {
          result.addAll(analysisService.analyzeDryRun(scope, progress -> {
            lastProgress.set(progress);
            ui.access(() -> applyProgress(progress));
          }));
        } else {
          result.addAll(analysisService.analyze(scope, progress -> {
            lastProgress.set(progress);
            ui.access(() -> applyProgress(progress));
          }));
        }

        final List<TaxonomySuggestion> finalResult = result;
        final TaxonomyAnalysisProgress finalProgress = lastProgress.get() != null
            ? lastProgress.get()
            : new TaxonomyAnalysisProgress(dryRun);

        ui.access(() -> {
          refreshAllGrids();
          progressSection.setVisible(false);
          if (dryRun) {
            showDryRunCard(finalResult, finalProgress);
          } else {
            showCompletionCard(finalResult, finalProgress);
          }
          setControlsEnabled(true);
          analysisRunning = false;
        });
      } catch (Exception e) {
        ui.access(() -> {
          progressSection.setVisible(false);
          showNotification(getTranslation("taxonomy.analyze.error", e.getMessage()), true);
          setControlsEnabled(true);
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
      etaLabel.setText("\u2014".equals(eta) ? "" :
                           getTranslation("taxonomy.progress.eta", eta));
    } else {
      progressLabel.setText(getTranslation("taxonomy.progress.starting"));
    }
  }

  private TaxonomyAnalysisScope buildScope() {
    TaxonomyAnalysisScope.Mode mode = modeSelect.getValue();
    if (mode == null) mode = TaxonomyAnalysisScope.Mode.ALL;

    return switch (mode) {
      case ALL -> TaxonomyAnalysisScope.all();

      case MANUAL_SELECTION -> {
        if (manualImageIds.isEmpty()) {
          showNotification(getTranslation("taxonomy.scope.manual.empty"), true);
          yield null;
        }
        yield TaxonomyAnalysisScope.manualSelection(manualImageIds);
      }

      case CATEGORY_GROUP -> {
        CategoryGroup group = groupCombo.getValue();
        if (group == null) {
          showNotification(getTranslation("taxonomy.scope.group.required"), true);
          yield null;
        }
        boolean incl = subgroupCheck.getValue() != null && subgroupCheck.getValue();
        yield TaxonomyAnalysisScope.forGroup(group, incl);
      }

      case MISSING_CATEGORY -> TaxonomyAnalysisScope.missingCategory();

      case LOW_CONFIDENCE -> {
        Double threshold = thresholdField.getValue();
        yield TaxonomyAnalysisScope.lowConfidence(threshold != null ? threshold : DEFAULT_CONFIDENCE_THRESHOLD);
      }
    };
  }

  private void setControlsEnabled(boolean enabled) {
    modeSelect.setEnabled(enabled);
    groupCombo.setEnabled(enabled);
    subgroupCheck.setEnabled(enabled);
    thresholdField.setEnabled(enabled);
    selectImagesBtn.setEnabled(enabled);
    analyzeBtn.setEnabled(enabled);
    dryRunBtn.setEnabled(enabled);
  }

  // ── Bulk actions ──────────────────────────────────────────────────────────

  private void bulkAction(String action) {
    boolean isImage = action.contains("image");
    Grid<TaxonomySuggestion> grid = isImage ? imageGrid : taxonomyGrid;
    Set<TaxonomySuggestion> selected = grid.getSelectedItems();
    if (selected.isEmpty()) return;

    List<UUID> ids = selected.stream()
        .map(TaxonomySuggestion::getId)
        .collect(Collectors.toList());

    if (action.startsWith("accept")) {
      suggestionService.bulkAccept(ids);
      showNotification(getTranslation("taxonomy.bulk.accepted", ids.size()), false);
      refreshAllGrids();
    } else if (action.startsWith("reject")) {
      suggestionService.bulkReject(ids);
      showNotification(getTranslation("taxonomy.bulk.rejected", ids.size()), false);
      refreshAllGrids();
    } else if (action.startsWith("apply")) {
      ConfirmDialog confirm = new ConfirmDialog();
      confirm.setHeader(getTranslation("taxonomy.bulk.apply.confirm.title"));
      confirm.setText(getTranslation("taxonomy.bulk.apply.confirm.text", ids.size()));
      confirm.setCancelable(true);
      confirm.setCancelText(getTranslation("common.cancel"));
      confirm.setConfirmText(getTranslation("taxonomy.action.apply"));
      confirm.addConfirmListener(ev -> {
        int applied = suggestionService.bulkApply(ids);
        refreshAllGrids();
        showNotification(getTranslation("taxonomy.bulk.applied", applied), false);
      });
      confirm.open();
    }
  }

  private void applyWithConfirm(TaxonomySuggestion s) {
    ConfirmDialog confirm = new ConfirmDialog();
    confirm.setHeader(getTranslation("taxonomy.apply.confirm.title"));
    confirm.setText(buildSuggestionDetail(s));
    confirm.setCancelable(true);
    confirm.setCancelText(getTranslation("common.cancel"));
    confirm.setConfirmText(getTranslation("taxonomy.action.apply"));
    confirm.addConfirmListener(e -> {
      suggestionService.apply(s.getId());
      refreshAllGrids();
      showNotification(getTranslation("taxonomy.applied"), false);
    });
    confirm.open();
  }

  private String buildSuggestionDetail(TaxonomySuggestion s) {
    StringBuilder sb = new StringBuilder();
    if (s.getTargetImageFilename() != null) {
      sb.append(getTranslation("taxonomy.col.filename")).append(": ")
          .append(s.getTargetImageFilename()).append("\n");
    }
    if (s.getCurrentCategory() != null) {
      sb.append(getTranslation("taxonomy.col.current")).append(": ")
          .append(CategoryRegistry.getUserLabel(s.getCurrentCategory())).append(" \u2192 ");
    }
    if (s.getSuggestedCategory() != null) {
      sb.append(CategoryRegistry.getUserLabel(s.getSuggestedCategory()));
    }
    return sb.toString();
  }

  // ── Grid refresh ──────────────────────────────────────────────────────────

  private void refreshAllGrids() {
    List<TaxonomySuggestion> all = suggestionService.findAll();

    List<TaxonomySuggestion> allImageLevel = all.stream()
        .filter(TaxonomySuggestion::isImageLevel)
        .collect(Collectors.toList());
    List<TaxonomySuggestion> taxonomyLevel = all.stream()
        .filter(s -> !s.isImageLevel())
        .collect(Collectors.toList());

    imageGrid.setItems(allImageLevel);
    taxonomyGrid.setItems(taxonomyLevel);

    List<CategoryMetadata> deprecated =
        persistenceService.findAllCategoryMetadata().stream()
            .filter(m -> m.getLifecycleState() == CategoryLifecycleState.DEPRECATED)
            .collect(Collectors.toList());
    deprecatedGrid.setItems(deprecated);
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private void showNotification(String message, boolean error) {
    Notification n = Notification.show(message, 4000, Notification.Position.BOTTOM_START);
    n.addThemeVariants(error ? NotificationVariant.LUMO_ERROR : NotificationVariant.LUMO_SUCCESS);
  }
}
