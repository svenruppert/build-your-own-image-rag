package com.svenruppert.flow.views.pipeline;

import com.svenruppert.flow.MainLayout;
import com.svenruppert.flow.views.detail.DetailDialog;
import com.svenruppert.flow.views.detail.ReprocessingDiffDialog;
import com.svenruppert.imagerag.bootstrap.ServiceRegistry;
import com.svenruppert.imagerag.domain.LocationSummary;
import com.svenruppert.imagerag.domain.ProcessingSettings;
import com.svenruppert.imagerag.domain.SemanticAnalysis;
import com.svenruppert.imagerag.domain.SensitivityAssessment;
import com.svenruppert.imagerag.persistence.PersistenceService;
import com.svenruppert.imagerag.pipeline.IngestionJob;
import com.svenruppert.imagerag.pipeline.IngestionPipeline;
import com.svenruppert.imagerag.pipeline.JobStep;
import com.svenruppert.imagerag.pipeline.JobType;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Live pipeline monitor.
 * <p>Uses Vaadin client-side polling ({@link com.vaadin.flow.component.UI#setPollInterval})
 * to refresh the grid every 2 seconds — no {@code @Push} required.
 * Each queued or running job shows an "Abbrechen" button that calls
 * {@link com.svenruppert.imagerag.pipeline.IngestionPipeline#cancel} on the server.
 * <p>Also exposes a bounded {@link Select} for the maximum number of parallel ingestion
 * jobs.  Changes take effect immediately for newly submitted jobs; in-flight jobs
 * on the old executor finish normally.
 */
@PageTitle("Processing Pipeline")
@Route(value = PipelineView.PATH, layout = MainLayout.class)
public class PipelineView
    extends VerticalLayout {

  public static final String PATH = "pipeline";

  private static final int POLL_INTERVAL_MS = 2_000;
  private static final DateTimeFormatter TIME_FMT =
      DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

  private final Grid<IngestionJob> grid = new Grid<>(IngestionJob.class, false);
  private final Span statsLabel = new Span();
  // ── Pause button ─────────────────────────────────────────────────────────
  private final Button pauseBtn = new Button();
  // ── Batch progress widgets ─────────────────────────────────────────────────
  private final ProgressBar batchProgressBar = new ProgressBar(0, 1);
  private final Span batchCountLabel = new Span();
  private final Span batchElapsedLabel = new Span();
  private final Span batchEtaLabel = new Span();
  private final HorizontalLayout batchSection = new HorizontalLayout();
  // ── Completion summary card (visible when all jobs are terminal) ────────────
  private final Div completionSummaryCard = new Div();
  // ── Filter state ──────────────────────────────────────────────────────────
  private JobStep filterStep = null;
  private JobType filterType = null;

  public PipelineView() {
    setSizeFull();
    setPadding(true);
    setSpacing(true);

    add(new H2(getTranslation("pipeline.title")));
    add(new Paragraph(getTranslation("pipeline.description")));

    // ── Parallelism selectors ──────────────────────────────────────────────
    add(buildParallelismSelector());
    add(buildSearchParallelismSelector());

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

    // ── Batch progress section ─────────────────────────────────────────────
    add(buildBatchSection());

    // ── Completion summary card ────────────────────────────────────────────
    completionSummaryCard.getStyle()
        .set("background", "var(--lumo-contrast-5pct)")
        .set("border-radius", "var(--lumo-border-radius-l)")
        .set("padding", "var(--lumo-space-m)")
        .set("margin", "var(--lumo-space-s) 0");
    completionSummaryCard.setVisible(false);
    add(completionSummaryCard);

    configureGrid();
    add(grid);
    setFlexGrow(1, grid);

    refreshGrid();
  }

  // -------------------------------------------------------------------------
  // Pause/Resume
  // -------------------------------------------------------------------------

  /**
   * Formats a {@link Duration} as {@code "Xm Ys"} or {@code "Ys"} for durations under a minute.
   */
  private static String formatDuration(Duration d) {
    long s = d.toSeconds();
    if (s < 60) return s + "s";
    return (s / 60) + "m " + (s % 60) + "s";
  }

  // -------------------------------------------------------------------------
  // Filter bar
  // -------------------------------------------------------------------------

  private void togglePause() {
    IngestionPipeline pipeline = ServiceRegistry.getInstance().getIngestionPipeline();
    if (pipeline.isPaused()) {
      pipeline.resume();
      pauseBtn.setText(getTranslation("pipeline.pause"));
    } else {
      pipeline.pause();
      pauseBtn.setText(getTranslation("pipeline.resume"));
    }
  }

  // -------------------------------------------------------------------------
  // Keyword index rebuild
  // -------------------------------------------------------------------------

  private HorizontalLayout buildFilterBar() {
    Select<String> stepSelect = new Select<>();
    stepSelect.setLabel(getTranslation("pipeline.filter.status"));
    stepSelect.setWidth("200px");

    // Build items: "All" + each step name
    java.util.List<String> stepItems = new java.util.ArrayList<>();
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

  private void confirmRebuildVectorIndex() {
    ConfirmDialog dlg = new ConfirmDialog();
    dlg.setHeader(getTranslation("pipeline.rebuild.vector"));
    dlg.setText(getTranslation("pipeline.rebuild.vector.confirm",
                               ServiceRegistry.getInstance().getVectorBackendType().name()));
    dlg.setCancelable(true);
    dlg.setConfirmText("Rebuild");
    dlg.addConfirmListener(e -> rebuildVectorIndex());
    dlg.open();
  }

  private void rebuildVectorIndex() {
    Thread.ofVirtual().start(() -> {
      try {
        ServiceRegistry.getInstance().rebuildVectorIndex();
        getUI().ifPresent(ui -> ui.access(() ->
                                              Notification.show(getTranslation("pipeline.rebuild.vector.started"),
                                                                4000, Notification.Position.BOTTOM_END)
                                                  .addThemeVariants(NotificationVariant.LUMO_SUCCESS)));
      } catch (Exception ex) {
        getUI().ifPresent(ui -> ui.access(() ->
                                              Notification.show("Vector index rebuild failed: " + ex.getMessage(),
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
    dlg.setConfirmText("Rebuild");
    dlg.addConfirmListener(e -> rebuildKeywordIndex());
    dlg.open();
  }

  // -------------------------------------------------------------------------
  // Parallelism selector
  // -------------------------------------------------------------------------

  private void rebuildKeywordIndex() {
    Thread.ofVirtual().start(() -> {
      try {
        var kr = ServiceRegistry.getInstance().getKeywordIndexService();
        var ps = ServiceRegistry.getInstance().getPersistenceService();
        kr.rebuildAll(
            ps::findAllImages,
            ps::findAnalysis,
            ps::findLocation,
            ps::findOcrResult);
        getUI().ifPresent(ui -> ui.access(() ->
                                              Notification.show(getTranslation("pipeline.rebuild.started"),
                                                                3000, Notification.Position.BOTTOM_END)
                                                  .addThemeVariants(NotificationVariant.LUMO_SUCCESS)));
      } catch (Exception ex) {
        getUI().ifPresent(ui -> ui.access(() ->
                                              Notification.show("Rebuild failed: " + ex.getMessage(),
                                                                5000, Notification.Position.MIDDLE)
                                                  .addThemeVariants(NotificationVariant.LUMO_ERROR)));
      }
    });
  }

  // -------------------------------------------------------------------------
  // Search parallelism selector
  // -------------------------------------------------------------------------

  private HorizontalLayout buildParallelismSelector() {
    ProcessingSettings settings = ServiceRegistry.getInstance().getProcessingSettings();

    Select<Integer> parallelismSelect = new Select<>();
    parallelismSelect.setLabel(getTranslation("pipeline.parallelism.label"));
    parallelismSelect.setItems(1, 2, 4);
    parallelismSelect.setValue(settings.getIngestionParallelism());
    parallelismSelect.setWidth("160px");

    parallelismSelect.addValueChangeListener(e -> {
      if (e.getValue() == null) return;
      settings.setIngestionParallelism(e.getValue());
      ServiceRegistry.getInstance().getIngestionPipeline().updateParallelism(e.getValue());
      Notification n = Notification.show(
          getTranslation("pipeline.parallelism.updated", e.getValue()),
          3000, Notification.Position.BOTTOM_END);
      n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    });

    Span note = new Span(getTranslation("pipeline.parallelism.note"));
    note.getStyle()
        .set("font-size", "var(--lumo-font-size-s)")
        .set("color", "var(--lumo-secondary-text-color)")
        .set("align-self", "flex-end")
        .set("padding-bottom", "var(--lumo-space-xs)");

    HorizontalLayout row = new HorizontalLayout(parallelismSelect, note);
    row.setAlignItems(Alignment.END);
    return row;
  }

  // -------------------------------------------------------------------------
  // Polling lifecycle
  // -------------------------------------------------------------------------

  private HorizontalLayout buildSearchParallelismSelector() {
    ProcessingSettings settings = ServiceRegistry.getInstance().getProcessingSettings();

    Select<Integer> searchSelect = new Select<>();
    searchSelect.setLabel(getTranslation("pipeline.search.parallelism.label"));
    searchSelect.setItems(1, 2, 4);
    searchSelect.setValue(settings.getSearchParallelism());
    searchSelect.setWidth("160px");

    searchSelect.addValueChangeListener(e -> {
      if (e.getValue() == null) {
        return;
      }
      // Resize the shared search executor — makes the setting immediately effective
      ServiceRegistry.getInstance().updateSearchParallelism(e.getValue());
      Notification n = Notification.show(
          getTranslation("pipeline.search.parallelism.updated", e.getValue()),
          3000, Notification.Position.BOTTOM_END);
      n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    });

    Span note = new Span(getTranslation("pipeline.search.parallelism.note"));
    note.getStyle()
        .set("font-size", "var(--lumo-font-size-s)")
        .set("color", "var(--lumo-secondary-text-color)")
        .set("align-self", "flex-end")
        .set("padding-bottom", "var(--lumo-space-xs)");

    HorizontalLayout row = new HorizontalLayout(searchSelect, note);
    row.setAlignItems(Alignment.END);
    return row;
  }

  @Override
  protected void onAttach(AttachEvent attachEvent) {
    super.onAttach(attachEvent);
    attachEvent.getUI().setPollInterval(POLL_INTERVAL_MS);
    attachEvent.getUI().addPollListener(e -> refreshGrid());
  }

  // -------------------------------------------------------------------------
  // Batch progress
  // -------------------------------------------------------------------------

  @Override
  protected void onDetach(DetachEvent detachEvent) {
    super.onDetach(detachEvent);
    detachEvent.getUI().setPollInterval(-1);
  }

  private HorizontalLayout buildBatchSection() {
    batchProgressBar.setWidth("280px");

    batchCountLabel.getStyle()
        .set("font-size", "var(--lumo-font-size-s)")
        .set("align-self", "flex-end");
    batchElapsedLabel.getStyle()
        .set("font-size", "var(--lumo-font-size-s)")
        .set("color", "var(--lumo-secondary-text-color)")
        .set("align-self", "flex-end");
    batchEtaLabel.getStyle()
        .set("font-size", "var(--lumo-font-size-s)")
        .set("color", "var(--lumo-secondary-text-color)")
        .set("align-self", "flex-end");

    batchSection.add(batchProgressBar, batchCountLabel, batchElapsedLabel, batchEtaLabel);
    batchSection.setAlignItems(Alignment.END);
    batchSection.setSpacing(true);
    batchSection.setVisible(false); // hidden when no jobs exist
    return batchSection;
  }

  /**
   * Updates the batch progress bar, count, elapsed time, and ETA based on all known jobs.
   * When all jobs are terminal, shows a completion summary card instead of the ETA.
   */
  private void updateBatchProgress(List<IngestionJob> jobs) {
    if (jobs.isEmpty()) {
      batchSection.setVisible(false);
      completionSummaryCard.setVisible(false);
      return;
    }

    batchSection.setVisible(true);

    long total = jobs.size();
    long terminal = jobs.stream().filter(IngestionJob::isTerminal).count();
    long remaining = total - terminal;

    // Progress bar
    batchProgressBar.setValue(total > 0 ? (double) terminal / total : 0.0);

    // Count label
    batchCountLabel.setText(getTranslation("pipeline.progress.processed", terminal, total));

    // Elapsed since earliest submitted-at
    Instant earliest = jobs.stream()
        .map(IngestionJob::getSubmittedAt)
        .min(Instant::compareTo)
        .orElse(Instant.now());
    Instant latest = jobs.stream()
        .map(j -> j.getFinishedAt() != null ? j.getFinishedAt() : Instant.now())
        .max(Instant::compareTo)
        .orElse(Instant.now());
    Duration elapsed = Duration.between(earliest, remaining == 0 ? latest : Instant.now());
    batchElapsedLabel.setText(getTranslation("pipeline.progress.elapsed",
                                             formatDuration(elapsed)));

    // ETA from average duration of COMPLETED jobs (not DUPLICATE / FAILED / CANCELLED)
    List<Duration> completedDurations = jobs.stream()
        .filter(j -> j.getCurrentStep() == JobStep.COMPLETED)
        .map(IngestionJob::elapsed)
        .filter(d -> !d.isZero())
        .toList();

    if (remaining == 0) {
      batchEtaLabel.setText(getTranslation("pipeline.progress.done"));
      showCompletionSummary(jobs, elapsed, completedDurations);
    } else {
      completionSummaryCard.setVisible(false);
      if (completedDurations.size() < 2) {
        batchEtaLabel.setText(getTranslation("pipeline.progress.calculating"));
      } else {
        long avgSeconds = completedDurations.stream()
            .mapToLong(Duration::toSeconds)
            .sum() / completedDurations.size();
        Duration eta = Duration.ofSeconds(avgSeconds * remaining);
        batchEtaLabel.setText(getTranslation("pipeline.progress.eta", formatDuration(eta)));
      }
    }
  }

  /**
   * Populates and shows the completion summary card when all jobs in the batch are terminal.
   */
  private void showCompletionSummary(List<IngestionJob> jobs, Duration elapsed,
                                     List<Duration> completedDurations) {
    completionSummaryCard.removeAll();

    long total = jobs.size();
    long completed = jobs.stream().filter(j -> j.getCurrentStep() == JobStep.COMPLETED).count();
    long duplicate = jobs.stream().filter(j -> j.getCurrentStep() == JobStep.DUPLICATE).count();
    long failed = jobs.stream().filter(j -> j.getCurrentStep() == JobStep.FAILED).count();
    long cancelled = jobs.stream().filter(j -> j.getCurrentStep() == JobStep.CANCELLED).count();

    long avgSeconds = completedDurations.isEmpty() ? 0
        : completedDurations.stream().mapToLong(Duration::toSeconds).sum()
          / completedDurations.size();

    // Title row
    Span title = new Span(getTranslation("pipeline.summary.title"));
    title.getStyle().set("font-weight", "700").set("font-size", "var(--lumo-font-size-m)");

    // Stats row — compact badges
    HorizontalLayout stats = new HorizontalLayout();
    stats.setSpacing(true);
    stats.getStyle().set("flex-wrap", "wrap");
    stats.add(summaryBadge(getTranslation("pipeline.summary.total", total), "#455A64", "white"));
    stats.add(summaryBadge(getTranslation("pipeline.summary.completed", completed), "#2E7D32", "white"));
    stats.add(summaryBadge(getTranslation("pipeline.summary.duplicate", duplicate), "#E65100", "white"));
    stats.add(summaryBadge(getTranslation("pipeline.summary.failed", failed), "#C62828", "white"));
    stats.add(summaryBadge(getTranslation("pipeline.summary.cancelled", cancelled), "#78909C", "white"));

    // Time row
    Span timeInfo = new Span(
        getTranslation("pipeline.summary.elapsed", formatDuration(elapsed))
            + (avgSeconds > 0
            ? "  ·  " + getTranslation("pipeline.summary.avg", formatDuration(Duration.ofSeconds(avgSeconds)))
            : ""));
    timeInfo.getStyle()
        .set("font-size", "var(--lumo-font-size-s)")
        .set("color", "var(--lumo-secondary-text-color)");

    VerticalLayout card = new VerticalLayout(title, stats, timeInfo);
    card.setPadding(false);
    card.setSpacing(false);
    card.getStyle().set("gap", "var(--lumo-space-xs)");
    completionSummaryCard.add(card);
    completionSummaryCard.setVisible(true);
  }

  private Span summaryBadge(String text, String bg, String fg) {
    Span s = new Span(text);
    s.getElement().getThemeList().add("badge");
    s.getStyle().set("background-color", bg).set("color", fg);
    return s;
  }

  // -------------------------------------------------------------------------
  // Grid configuration
  // -------------------------------------------------------------------------

  private void configureGrid() {
    grid.setWidthFull();
    grid.setHeightFull();
    grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);

    // Status badge — stage-coloured; covers all pipeline stages at a glance.
    // The "Current Step" plain-text column has been removed as it was redundant.
    grid.addComponentColumn(job -> stepBadge(job.getCurrentStep()))
        .setHeader(getTranslation("pipeline.col.status"))
        .setWidth("240px")
        .setFlexGrow(0);

    // Job type badge — distinguishes upload jobs from reprocess jobs
    grid.addComponentColumn(job -> jobTypeBadge(job.getJobType()))
        .setHeader(getTranslation("pipeline.col.type"))
        .setWidth("130px")
        .setFlexGrow(0);

    // Filename
    grid.addColumn(IngestionJob::getFilename)
        .setHeader(getTranslation("pipeline.col.filename"))
        .setFlexGrow(3)
        .setSortable(true);

    // Inline progress bar for running jobs
    grid.addComponentColumn(job -> {
      if (job.isRunning()) {
        ProgressBar bar = new ProgressBar();
        bar.setIndeterminate(true);
        bar.setWidth("120px");
        return bar;
      }
      return new Span();
    }).setHeader("").setWidth("140px").setFlexGrow(0);

    // Elapsed time
    grid.addColumn(job -> {
      var elapsed = job.elapsed();
      if (elapsed.isZero()) return "—";
      long s = elapsed.toSeconds();
      if (s < 60) return s + "s";
      return (s / 60) + "m " + (s % 60) + "s";
    }).setHeader(getTranslation("pipeline.col.duration")).setWidth("90px").setFlexGrow(0);

    // Submitted at
    grid.addColumn(job -> job.getSubmittedAt() != null
            ? TIME_FMT.format(job.getSubmittedAt()) : "—")
        .setHeader(getTranslation("pipeline.col.queued.at"))
        .setWidth("90px")
        .setFlexGrow(0);

    // Finished at
    grid.addColumn(job -> job.getFinishedAt() != null
            ? TIME_FMT.format(job.getFinishedAt()) : "—")
        .setHeader(getTranslation("pipeline.col.finished.at"))
        .setWidth("110px")
        .setFlexGrow(0);

    // Context column: error / duplicate info / reprocess diff button
    grid.addComponentColumn(job -> {
      if (job.getCurrentStep() == JobStep.FAILED && job.getErrorMessage() != null) {
        Span err = new Span(job.getErrorMessage());
        err.getStyle()
            .set("color", "var(--lumo-error-color)")
            .set("font-size", "var(--lumo-font-size-s)");
        return err;
      }
      if (job.getCurrentStep() == JobStep.DUPLICATE && job.getDuplicateOfFilename() != null) {
        Span dup = new Span(getTranslation("pipeline.col.duplicate.context",
                                           job.getDuplicateOfFilename()));
        dup.getStyle()
            .set("color", "var(--lumo-warning-text-color, var(--lumo-secondary-text-color))")
            .set("font-size", "var(--lumo-font-size-s)");
        Button openBtn = new Button(getTranslation("pipeline.col.duplicate.open"));
        openBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        openBtn.addClickListener(e -> openDuplicateDialog(job.getDuplicateOfId()));
        VerticalLayout cell = new VerticalLayout(dup, openBtn);
        cell.setPadding(false);
        cell.setSpacing(false);
        cell.getStyle().set("gap", "2px");
        return cell;
      }
      // Show "View Diff" for completed reprocessing jobs that have diff data
      if (job.getJobType() == JobType.REPROCESS_EXISTING
          && job.getCurrentStep() == JobStep.COMPLETED
          && job.getReprocessingDiff() != null) {
        Button diffBtn = new Button(getTranslation("pipeline.diff.view"));
        diffBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        diffBtn.addClickListener(e -> new ReprocessingDiffDialog(job.getReprocessingDiff()).open());
        return diffBtn;
      }
      return new Span();
    }).setHeader(getTranslation("pipeline.col.error")).setFlexGrow(3);

    // Promote button — visible only for QUEUED jobs (moves to front of queue)
    grid.addComponentColumn(job -> {
      if (job.getCurrentStep() == JobStep.QUEUED) {
        Button promoteBtn = new Button(getTranslation("pipeline.promote"));
        promoteBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        promoteBtn.getElement().setAttribute("title",
                                             "Move this job to the front of the queue so it executes next");
        promoteBtn.addClickListener(e -> {
          ServiceRegistry.getInstance().getIngestionPipeline().promote(job);
          Notification.show(getTranslation("pipeline.promote.done", job.getFilename()),
                            2500, Notification.Position.BOTTOM_END)
              .addThemeVariants(NotificationVariant.LUMO_PRIMARY);
        });
        return promoteBtn;
      }
      return new Span();
    }).setHeader("").setWidth("110px").setFlexGrow(0);

    // Cancel button — visible only for queued and running jobs
    grid.addComponentColumn(job -> {
      if (!job.isTerminal()) {
        Button cancelBtn = new Button(getTranslation("pipeline.cancel"));
        cancelBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR,
                                   ButtonVariant.LUMO_TERTIARY);
        cancelBtn.addClickListener(e ->
                                       ServiceRegistry.getInstance().getIngestionPipeline().cancel(job));
        return cancelBtn;
      }
      return new Span();
    }).setHeader("").setWidth("110px").setFlexGrow(0);

    // Retry button — visible only for FAILED jobs
    grid.addComponentColumn(job -> {
      if (job.getCurrentStep() == JobStep.FAILED) {
        Button retryBtn = new Button(getTranslation("pipeline.retry"));
        retryBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);
        retryBtn.addClickListener(e -> retryJob(job));
        return retryBtn;
      }
      return new Span();
    }).setHeader("").setWidth("100px").setFlexGrow(0);
  }

  // -------------------------------------------------------------------------
  // Retry helper
  // -------------------------------------------------------------------------

  private void retryJob(IngestionJob job) {
    try {
      ServiceRegistry.getInstance().getIngestionPipeline().retry(job);
      Notification.show(getTranslation("pipeline.retry.started", job.getFilename()),
                        3000, Notification.Position.BOTTOM_END)
          .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    } catch (Exception e) {
      Notification.show(getTranslation("pipeline.retry.failed", e.getMessage()),
                        5000, Notification.Position.MIDDLE)
          .addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
  }

  // -------------------------------------------------------------------------
  // Data refresh
  // -------------------------------------------------------------------------

  private void refreshGrid() {
    List<IngestionJob> allJobs = ServiceRegistry.getInstance()
        .getIngestionPipeline()
        .getAllJobs();

    // Apply filters
    List<IngestionJob> jobs = allJobs.stream()
        .filter(j -> filterStep == null || j.getCurrentStep() == filterStep)
        .filter(j -> filterType == null || j.getJobType() == filterType)
        .sorted(Comparator.comparing(IngestionJob::getSubmittedAt).reversed())
        .toList();

    grid.setItems(jobs);
    updateStats(allJobs);
    updateBatchProgress(allJobs);
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

  // -------------------------------------------------------------------------
  // Duplicate helper — navigate to the original image
  // -------------------------------------------------------------------------

  /**
   * Opens a read-only {@link DetailDialog} for the image that a DUPLICATE job points to.
   * Called from the pipeline grid's context column; runs on the Vaadin UI thread.
   */
  private void openDuplicateDialog(UUID existingId) {
    if (existingId == null) return;
    PersistenceService ps = ServiceRegistry.getInstance().getPersistenceService();
    ps.findImage(existingId).ifPresent(asset -> {
      SemanticAnalysis analysis = ps.findAnalysis(existingId).orElse(null);
      SensitivityAssessment assessment = ps.findAssessment(existingId).orElse(null);
      LocationSummary location = ps.findLocation(existingId).orElse(null);
      new DetailDialog(asset, analysis, assessment, location).open();
    });
  }

  // -------------------------------------------------------------------------
  // Badge helper
  // -------------------------------------------------------------------------

  private Span jobTypeBadge(JobType type) {
    if (type == null) {
      return new Span();
    }
    Span badge = new Span(getTranslation(
        type == JobType.REPROCESS_EXISTING ? "pipeline.type.reprocess" : "pipeline.type.upload"));
    badge.getElement().getThemeList().add("badge");
    if (type == JobType.REPROCESS_EXISTING) {
      badge.getElement().getThemeList().add("contrast");
    } else {
      badge.getElement().getThemeList().add("primary");
    }
    return badge;
  }

  private Span stepBadge(JobStep step) {
    Span badge = new Span(step.getLabel());
    badge.getElement().getThemeList().add("badge");
    switch (step) {
      // ── Terminal states use built-in Lumo theme variants ─────────────────
      case QUEUED -> badge.getElement().getThemeList().add("contrast");
      case COMPLETED -> badge.getElement().getThemeList().add("success");
      case FAILED -> badge.getElement().getThemeList().add("error");
      case CANCELLED -> badge.getElement().getThemeList().add("contrast");
      // DUPLICATE: orange — not an error, but distinct from success
      case DUPLICATE -> badge.getStyle()
          .set("background-color", "#E65100").set("color", "white");
      // ── Active pipeline stages — each gets a distinct colour ─────────────
      case STORING, LOADING_IMAGE -> badge.getStyle()
          .set("background-color", "#0277BD").set("color", "white");          // steel-blue
      case EXTRACTING_METADATA, GEOCODING -> badge.getStyle()
          .set("background-color", "#6A1B9A").set("color", "white");          // purple
      case OCR_TEXT -> badge.getStyle()
          .set("background-color", "#F57F17").set("color", "white");          // amber-dark
      case ANALYZING_VISION -> badge.getStyle()
          .set("background-color", "#4527A0").set("color", "white");          // deep-purple
      case DERIVING_SEMANTICS -> badge.getStyle()
          .set("background-color", "#00695C").set("color", "white");          // teal
      case ASSESSING_SENSITIVITY -> badge.getStyle()
          .set("background-color", "#BF360C").set("color", "white");          // deep-orange
      case EMBEDDING -> badge.getStyle()
          .set("background-color", "#F9A825").set("color", "#1a1a1a");        // yellow (dark text)
      case INDEXING, KEYWORD_INDEXING -> badge.getStyle()
          .set("background-color", "#1B5E20").set("color", "white");          // dark-green
      default -> throw new RuntimeException("Unknown step: " + step);
    }
    return badge;
  }
}
