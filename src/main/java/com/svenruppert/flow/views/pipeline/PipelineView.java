package com.svenruppert.flow.views.pipeline;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.MainLayout;
import com.svenruppert.flow.views.shared.ViewServices;
import com.svenruppert.imagerag.pipeline.IngestionJob;
import com.svenruppert.imagerag.pipeline.IngestionPipeline;
import com.svenruppert.imagerag.pipeline.JobStep;
import com.svenruppert.imagerag.pipeline.JobType;
import com.svenruppert.imagerag.settings.Settings;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Live pipeline monitor.
 * <p>A server-side {@link ScheduledExecutorService} ticks every 2 seconds and pushes
 * grid updates to the browser via {@link com.vaadin.flow.component.UI#access(com.vaadin.flow.server.Command)}.
 * This relies on the {@code @Push(AUTOMATIC)} configured in {@code AppShell}.
 * Each queued or running job shows a cancel button that calls
 * {@link com.svenruppert.imagerag.pipeline.IngestionPipeline#cancel} on the server
 * (see {@link PipelineJobGrid}).
 * <p>Also exposes bounded selectors for the maximum number of parallel ingestion jobs and
 * parallel searches ({@link ParallelismSelector}).  Changes take effect immediately for
 * newly submitted jobs; in-flight jobs on the old executor finish normally.
 */
@Route(value = PipelineView.PATH, layout = MainLayout.class)
public class PipelineView
    extends VerticalLayout
    implements HasDynamicTitle, HasLogger {

  public static final String PATH = "pipeline";

  private static final int REFRESH_INTERVAL_MS = 2_000;

  private final ViewServices services;
  private final PipelineJobGrid grid;
  private final Span statsLabel = new Span();
  private final Button pauseBtn = new Button();
  private final PipelineBatchProgress batchProgress = new PipelineBatchProgress();
  /**
   * Shared scheduler — one daemon thread per view instance; cancelled on detach.
   */
  private ScheduledExecutorService scheduler;
  private ScheduledFuture<?> refreshTask;
  // ── Filter state ──────────────────────────────────────────────────────────
  private JobStep filterStep = null;
  private JobType filterType = null;

  public PipelineView() {
    this.services = ViewServices.current();

    setSizeFull();
    setPadding(true);
    setSpacing(true);

    add(new H2(getTranslation("pipeline.title")));
    add(new Paragraph(getTranslation("pipeline.description")));

    // ── Parallelism selectors ──────────────────────────────────────────────
    add(new ParallelismSelector(services.settings(), Settings.PIPELINE_INGESTION_PARALLELISM,
                                "pipeline.parallelism.label", "pipeline.parallelism.updated",
                                "pipeline.parallelism.note"));
    add(new ParallelismSelector(services.settings(), Settings.SEARCH_PARALLELISM,
                                "pipeline.search.parallelism.label",
                                "pipeline.search.parallelism.updated",
                                "pipeline.search.parallelism.note"));

    // ── Pause/Resume + Refresh + Rebuild Keyword Index ───────────────────
    pauseBtn.setText(getTranslation("pipeline.pause"));
    pauseBtn.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
    pauseBtn.addClickListener(e -> togglePause());

    Button refreshBtn = new Button(getTranslation("pipeline.refresh"), e -> refreshGrid());
    refreshBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

    Button rebuildKeywordBtn = new Button(getTranslation("pipeline.rebuild.keyword"),
                                          e -> confirmRebuildKeywordIndex());
    rebuildKeywordBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

    Button rebuildVectorBtn = new Button(getTranslation("pipeline.rebuild.vector"),
                                         e -> confirmRebuildVectorIndex());
    rebuildVectorBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

    add(new HorizontalLayout(pauseBtn, statsLabel, refreshBtn, rebuildKeywordBtn, rebuildVectorBtn));

    // ── Filter bar ─────────────────────────────────────────────────────────
    add(buildFilterBar());

    // ── Batch progress section + completion summary card ───────────────────
    add(batchProgress);
    add(batchProgress.summaryCard());

    grid = new PipelineJobGrid(services);
    add(grid);
    setFlexGrow(1, grid);

    refreshGrid();
  }

  // -------------------------------------------------------------------------
  // Pause/Resume
  // -------------------------------------------------------------------------

  private void togglePause() {
    IngestionPipeline pipeline = services.ingestionPipeline();
    if (pipeline.isPaused()) {
      pipeline.resume();
      pauseBtn.setText(getTranslation("pipeline.pause"));
    } else {
      pipeline.pause();
      pauseBtn.setText(getTranslation("pipeline.resume"));
    }
  }

  // -------------------------------------------------------------------------
  // Filter bar
  // -------------------------------------------------------------------------

  private HorizontalLayout buildFilterBar() {
    Select<String> stepSelect = new Select<>();
    stepSelect.setLabel(getTranslation("pipeline.filter.status"));
    stepSelect.setWidth("200px");

    // Build items: "All" + each step name
    List<String> stepItems = new ArrayList<>();
    stepItems.add(getTranslation("pipeline.filter.all"));
    for (JobStep s : JobStep.values()) {
      stepItems.add(s.getLabel());
    }
    stepSelect.setItems(stepItems);
    stepSelect.setValue(getTranslation("pipeline.filter.all"));
    stepSelect.addValueChangeListener(e -> {
      String val = e.getValue();
      if (val == null || val.equals(getTranslation("pipeline.filter.all"))) {
        filterStep = null;
      } else {
        for (JobStep s : JobStep.values()) {
          if (s.getLabel().equals(val)) {
            filterStep = s;
            break;
          }
        }
      }
      refreshGrid();
    });

    Select<String> typeSelect = new Select<>();
    typeSelect.setLabel(getTranslation("pipeline.filter.type"));
    typeSelect.setWidth("160px");
    typeSelect.setItems(getTranslation("pipeline.filter.all"),
                        getTranslation("pipeline.type.upload"),
                        getTranslation("pipeline.type.reprocess"));
    typeSelect.setValue(getTranslation("pipeline.filter.all"));
    typeSelect.addValueChangeListener(e -> {
      String val = e.getValue();
      if (val == null || val.equals(getTranslation("pipeline.filter.all"))) {
        filterType = null;
      } else if (val.equals(getTranslation("pipeline.type.upload"))) {
        filterType = JobType.INGEST_UPLOAD;
      } else {
        filterType = JobType.REPROCESS_EXISTING;
      }
      refreshGrid();
    });

    HorizontalLayout bar = new HorizontalLayout(stepSelect, typeSelect);
    bar.setAlignItems(Alignment.END);
    bar.setSpacing(true);
    return bar;
  }

  // -------------------------------------------------------------------------
  // Index rebuilds
  // -------------------------------------------------------------------------

  private void confirmRebuildVectorIndex() {
    ConfirmDialog dlg = new ConfirmDialog();
    dlg.setHeader(getTranslation("pipeline.rebuild.vector"));
    dlg.setText(getTranslation("pipeline.rebuild.vector.confirm",
                               services.vectorBackendType().name()));
    dlg.setCancelable(true);
    dlg.setConfirmText(getTranslation("pipeline.rebuild.confirm.button"));
    dlg.addConfirmListener(e -> rebuildVectorIndex());
    dlg.open();
  }

  private void rebuildVectorIndex() {
    Thread.ofVirtual().start(() -> {
      try {
        services.rebuildVectorIndex();
        getUI().ifPresent(ui -> ui.access(() ->
                                              Notification.show(getTranslation("pipeline.rebuild.vector.started"),
                                                                4000, Notification.Position.BOTTOM_END)
                                                  .addThemeVariants(NotificationVariant.LUMO_SUCCESS)));
      } catch (RuntimeException ex) {
        // Worker-thread boundary: no Vaadin error handler runs here, so every failure is
        // logged here and shown to the user.
        logger().error("Vector index rebuild failed: {}", ex.getMessage(), ex);
        getUI().ifPresent(ui -> ui.access(() ->
                                              Notification.show(getTranslation("pipeline.rebuild.vector.failed", ex.getMessage()),
                                                                6000, Notification.Position.MIDDLE)
                                                  .addThemeVariants(NotificationVariant.LUMO_ERROR)));
      }
    });
  }

  private void confirmRebuildKeywordIndex() {
    ConfirmDialog dlg = new ConfirmDialog();
    dlg.setHeader(getTranslation("pipeline.rebuild.keyword"));
    dlg.setText(getTranslation("pipeline.rebuild.keyword.confirm"));
    dlg.setCancelable(true);
    dlg.setConfirmText(getTranslation("pipeline.rebuild.confirm.button"));
    dlg.addConfirmListener(e -> rebuildKeywordIndex());
    dlg.open();
  }

  private void rebuildKeywordIndex() {
    Thread.ofVirtual().start(() -> {
      try {
        services.rebuildKeywordIndex();
        getUI().ifPresent(ui -> ui.access(() ->
                                              Notification.show(getTranslation("pipeline.rebuild.started"),
                                                                3000, Notification.Position.BOTTOM_END)
                                                  .addThemeVariants(NotificationVariant.LUMO_SUCCESS)));
      } catch (RuntimeException ex) {
        // Worker-thread boundary: no Vaadin error handler runs here, so every failure is
        // logged here and shown to the user.
        logger().error("Keyword index rebuild failed: {}", ex.getMessage(), ex);
        getUI().ifPresent(ui -> ui.access(() ->
                                              Notification.show(getTranslation("pipeline.rebuild.failed", ex.getMessage()),
                                                                5000, Notification.Position.MIDDLE)
                                                  .addThemeVariants(NotificationVariant.LUMO_ERROR)));
      }
    });
  }

  // -------------------------------------------------------------------------
  // Polling lifecycle
  // -------------------------------------------------------------------------

  @Override
  protected void onAttach(AttachEvent attachEvent) {
    super.onAttach(attachEvent);
    final var ui = attachEvent.getUI();
    scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "pipeline-view-refresh");
      t.setDaemon(true);
      return t;
    });
    refreshTask = scheduler.scheduleAtFixedRate(
        () -> ui.access(this::refreshGrid),
        REFRESH_INTERVAL_MS, REFRESH_INTERVAL_MS, TimeUnit.MILLISECONDS);
  }

  @Override
  protected void onDetach(DetachEvent detachEvent) {
    super.onDetach(detachEvent);
    if (refreshTask != null) {
      refreshTask.cancel(false);
      refreshTask = null;
    }
    if (scheduler != null) {
      scheduler.shutdownNow();
      scheduler = null;
    }
  }

  // -------------------------------------------------------------------------
  // Data refresh
  // -------------------------------------------------------------------------

  private void refreshGrid() {
    List<IngestionJob> allJobs = services
        .ingestionPipeline()
        .getAllJobs();

    // Apply filters
    List<IngestionJob> jobs = allJobs.stream()
        .filter(j -> filterStep == null || j.getCurrentStep() == filterStep)
        .filter(j -> filterType == null || j.getJobType() == filterType)
        .sorted(Comparator.comparing(IngestionJob::getSubmittedAt).reversed())
        .toList();

    grid.setItems(jobs);
    updateStats(allJobs);
    batchProgress.update(allJobs);
  }

  private void updateStats(List<IngestionJob> jobs) {
    long queued = jobs.stream().filter(j -> j.getCurrentStep() == JobStep.QUEUED).count();
    long running = jobs.stream().filter(IngestionJob::isRunning).count();
    long completed = jobs.stream().filter(j -> j.getCurrentStep() == JobStep.COMPLETED).count();
    long duplicates = jobs.stream().filter(j -> j.getCurrentStep() == JobStep.DUPLICATE).count();
    long failed = jobs.stream().filter(j -> j.getCurrentStep() == JobStep.FAILED).count();
    long cancelled = jobs.stream().filter(j -> j.getCurrentStep() == JobStep.CANCELLED).count();

    statsLabel.setText(getTranslation("pipeline.stats",
                                      jobs.size(), queued, running, completed, duplicates, failed, cancelled));
  }

  @Override
  public String getPageTitle() {
    return getTranslation("pipeline.page.title");
  }
}
