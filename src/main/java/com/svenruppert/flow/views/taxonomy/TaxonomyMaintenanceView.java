package com.svenruppert.flow.views.taxonomy;

import com.svenruppert.flow.MainLayout;
import com.svenruppert.imagerag.bootstrap.ServiceRegistry;
import com.svenruppert.imagerag.domain.CategoryMetadata;
import com.svenruppert.imagerag.domain.CategoryRegistry;
import com.svenruppert.imagerag.domain.TaxonomyAnalysisScope;
import com.svenruppert.imagerag.domain.TaxonomySuggestion;
import com.svenruppert.imagerag.domain.enums.CategoryGroup;
import com.svenruppert.imagerag.domain.enums.CategoryLifecycleState;
import com.svenruppert.imagerag.domain.enums.SourceCategory;
import com.svenruppert.imagerag.domain.enums.SuggestionStatus;
import com.svenruppert.imagerag.persistence.PersistenceService;
import com.svenruppert.imagerag.service.TaxonomyAnalysisProgress;
import com.svenruppert.imagerag.service.TaxonomyAnalysisService;
import com.svenruppert.imagerag.service.TaxonomySuggestionService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridMultiSelectionModel;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
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

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Taxonomy Maintenance view — two-column workbench with live progress inspector.
 *
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
 *
 * <p>The inspector on the left shows the 7-step analysis pipeline with live
 * colour-coded status indicators, mirroring {@code SearchInspectorComponent}.
 *
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

  // ── Services ──────────────────────────────────────────────────────────────
  private final TaxonomyAnalysisService  analysisService;
  private final TaxonomySuggestionService suggestionService;
  private final PersistenceService        persistenceService;

  /** Default confidence threshold — read once from imagerag.properties at startup. */
  private static final double DEFAULT_CONFIDENCE_THRESHOLD =
      com.svenruppert.imagerag.bootstrap.AppConfig.getInstance().getTaxonomyConfidenceThreshold();

  // ── Inspector (left column) ───────────────────────────────────────────────
  private final TaxonomyAnalysisInspectorComponent inspector =
      new TaxonomyAnalysisInspectorComponent();

  // ── Scope controls ────────────────────────────────────────────────────────
  private final Select<TaxonomyAnalysisScope.Mode> modeSelect       = new Select<>();
  private final ComboBox<CategoryGroup>             groupCombo       = new ComboBox<>();
  private final com.vaadin.flow.component.textfield.NumberField thresholdField =
      new com.vaadin.flow.component.textfield.NumberField();
  private final Checkbox                            subgroupCheck    = new Checkbox();
  private final Button                              selectImagesBtn  = new Button();
  private final Span                                scopeSummary     = new Span();

  /** Currently selected image IDs for MANUAL_SELECTION mode. */
  private List<UUID> manualImageIds = new ArrayList<>();

  // ── Run buttons ───────────────────────────────────────────────────────────
  private final Button analyzeBtn  = new Button();
  private final Button dryRunBtn   = new Button();

  // ── Progress ──────────────────────────────────────────────────────────────
  private final ProgressBar progressBar   = new ProgressBar();
  private final Span        progressLabel = new Span();
  private final Span        etaLabel      = new Span();
  private final Div         progressSection;

  // ── Info cards ────────────────────────────────────────────────────────────
  private final Div dryRunCard      = new Div();
  private final Div completionCard  = new Div();

  // ── Grids ─────────────────────────────────────────────────────────────────
  private final Grid<TaxonomySuggestion> imageGrid      = new Grid<>();
  private final Grid<TaxonomySuggestion> taxonomyGrid   = new Grid<>();
  private final Grid<CategoryMetadata>   deprecatedGrid = new Grid<>();

  // ── Batch toolbars ────────────────────────────────────────────────────────
  private final HorizontalLayout imageBatchBar    = new HorizontalLayout();
  private final HorizontalLayout taxonomyBatchBar = new HorizontalLayout();

  // ── State ─────────────────────────────────────────────────────────────────
  private volatile boolean analysisRunning = false;

  public TaxonomyMaintenanceView() {
    ServiceRegistry sr  = ServiceRegistry.getInstance();
    this.analysisService    = sr.getTaxonomyAnalysisService();
    this.suggestionService  = sr.getTaxonomySuggestionService();
    this.persistenceService = sr.getPersistenceService();

    setSpacing(false);
    setPadding(true);
    setWidthFull();

    progressSection = buildProgressSection();

    add(buildHeader(), buildWorkbench());
  }

  @Override
  public void beforeEnter(BeforeEnterEvent event) {
    refreshAllGrids();
    updateScopeSummary();
  }

  // ── Header ────────────────────────────────────────────────────────────────

  private Component buildHeader() {
    H2 title = new H2(getTranslation("taxonomy.title"));
    Paragraph desc = new Paragraph(getTranslation("taxonomy.description"));
    desc.getStyle().set("color", "var(--lumo-secondary-text-color)");
    VerticalLayout header = new VerticalLayout(title, desc);
    header.setSpacing(false);
    header.setPadding(false);
    return header;
  }

  // ── Two-column workbench ──────────────────────────────────────────────────

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

  // ── Scope panel ───────────────────────────────────────────────────────────

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
    boolean isGroup   = mode == TaxonomyAnalysisScope.Mode.CATEGORY_GROUP;
    boolean isLowConf = mode == TaxonomyAnalysisScope.Mode.LOW_CONFIDENCE;
    boolean isManual  = mode == TaxonomyAnalysisScope.Mode.MANUAL_SELECTION;

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
              boolean sec  = incl && an.get().getSecondaryCategories().stream()
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

  // ── Progress section ──────────────────────────────────────────────────────

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

  // ── Info cards ────────────────────────────────────────────────────────────

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
    long taxLevel   = suggestions.stream().filter(s -> !s.isImageLevel()).count();

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

  private void showCompletionCard(List<TaxonomySuggestion> suggestions, TaxonomyAnalysisProgress progress) {
    completionCard.removeAll();
    dryRunCard.setVisible(false);

    H4 heading = new H4(getTranslation("taxonomy.completion.card.title"));
    heading.getStyle().set("margin", "0 0 0.4rem 0").set("color", "var(--lumo-success-color)");

    long imageLevel = suggestions.stream().filter(TaxonomySuggestion::isImageLevel).count();
    long taxLevel   = suggestions.stream().filter(s -> !s.isImageLevel()).count();

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

  // ── Result tabs ───────────────────────────────────────────────────────────

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

    tabs.add(getTranslation("taxonomy.tab.images"),    imageTabContent);
    tabs.add(getTranslation("taxonomy.tab.taxonomy"),  taxonomyTabContent);
    tabs.add(getTranslation("taxonomy.tab.deprecated"), deprecatedTabContent);
    tabs.setWidthFull();
    return tabs;
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
      case OPEN     -> "badge";
      case ACCEPTED -> "badge success";
      case REJECTED -> "badge error";
      case APPLIED  -> "badge contrast";
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

    int total    = progress.getTotalImages();
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
