package com.svenruppert.flow.views.pipeline;

import com.svenruppert.flow.MainLayout;
import com.svenruppert.flow.views.detail.DetailDialog;
import com.svenruppert.imagerag.bootstrap.ServiceRegistry;
import com.svenruppert.imagerag.domain.LocationSummary;
import com.svenruppert.imagerag.domain.ProcessingSettings;
import com.svenruppert.imagerag.domain.SemanticAnalysis;
import com.svenruppert.imagerag.domain.SensitivityAssessment;
import com.svenruppert.imagerag.persistence.PersistenceService;
import com.svenruppert.imagerag.pipeline.IngestionJob;
import com.svenruppert.imagerag.pipeline.JobStep;
import com.svenruppert.imagerag.pipeline.JobType;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
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

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Live pipeline monitor.
 *
 * <p>Uses Vaadin client-side polling ({@link com.vaadin.flow.component.UI#setPollInterval})
 * to refresh the grid every 2 seconds — no {@code @Push} required.
 * Each queued or running job shows an "Abbrechen" button that calls
 * {@link com.svenruppert.imagerag.pipeline.IngestionPipeline#cancel} on the server.
 *
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

  public PipelineView() {
    setSizeFull();
    setPadding(true);
    setSpacing(true);

    add(new H2(getTranslation("pipeline.title")));
    add(new Paragraph(getTranslation("pipeline.description")));

    // ── Parallelism selectors ──────────────────────────────────────────────
    add(buildParallelismSelector());
    add(buildSearchParallelismSelector());

    Button refreshBtn = new Button(getTranslation("pipeline.refresh"), e -> refreshGrid());
    refreshBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

    add(new HorizontalLayout(statsLabel, refreshBtn));

    configureGrid();
    add(grid);
    setFlexGrow(1, grid);

    refreshGrid();
  }

  // -------------------------------------------------------------------------
  // Parallelism selector
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
  // Search parallelism selector
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

  // -------------------------------------------------------------------------
  // Polling lifecycle
  // -------------------------------------------------------------------------

  @Override
  protected void onAttach(AttachEvent attachEvent) {
    super.onAttach(attachEvent);
    attachEvent.getUI().setPollInterval(POLL_INTERVAL_MS);
    attachEvent.getUI().addPollListener(e -> refreshGrid());
  }

  @Override
  protected void onDetach(DetachEvent detachEvent) {
    super.onDetach(detachEvent);
    detachEvent.getUI().setPollInterval(-1);
  }

  // -------------------------------------------------------------------------
  // Grid configuration
  // -------------------------------------------------------------------------

  private void configureGrid() {
    grid.setWidthFull();
    grid.setHeightFull();
    grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);

    // Status badge
    grid.addComponentColumn(job -> stepBadge(job.getCurrentStep()))
        .setHeader(getTranslation("pipeline.col.status"))
        .setWidth("140px")
        .setFlexGrow(0);

    // Job type badge — distinguishes upload jobs from reprocess jobs
    grid.addComponentColumn(job -> jobTypeBadge(job.getJobType()))
        .setHeader(getTranslation("pipeline.col.type"))
        .setWidth("140px")
        .setFlexGrow(0);

    // Current step label
    grid.addColumn(job -> job.getCurrentStep().getLabel())
        .setHeader(getTranslation("pipeline.col.step"))
        .setFlexGrow(2)
        .setSortable(true);

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

    // Context message: error text for FAILED jobs; duplicate info for DUPLICATE jobs
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
      return new Span();
    }).setHeader(getTranslation("pipeline.col.error")).setFlexGrow(3);

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
    }).setHeader("").setWidth("120px").setFlexGrow(0);
  }

  // -------------------------------------------------------------------------
  // Data refresh
  // -------------------------------------------------------------------------

  private void refreshGrid() {
    List<IngestionJob> jobs = ServiceRegistry.getInstance()
        .getIngestionPipeline()
        .getAllJobs()
        .stream()
        .sorted(Comparator.comparing(IngestionJob::getSubmittedAt).reversed())
        .toList();

    grid.setItems(jobs);
    updateStats(jobs);
  }

  private void updateStats(List<IngestionJob> jobs) {
    long queued     = jobs.stream().filter(j -> j.getCurrentStep() == JobStep.QUEUED).count();
    long running    = jobs.stream().filter(IngestionJob::isRunning).count();
    long completed  = jobs.stream().filter(j -> j.getCurrentStep() == JobStep.COMPLETED).count();
    long duplicates = jobs.stream().filter(j -> j.getCurrentStep() == JobStep.DUPLICATE).count();
    long failed     = jobs.stream().filter(j -> j.getCurrentStep() == JobStep.FAILED).count();
    long cancelled  = jobs.stream().filter(j -> j.getCurrentStep() == JobStep.CANCELLED).count();

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
      SemanticAnalysis    analysis   = ps.findAnalysis(existingId).orElse(null);
      SensitivityAssessment assessment = ps.findAssessment(existingId).orElse(null);
      LocationSummary     location   = ps.findLocation(existingId).orElse(null);
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
      case QUEUED    -> badge.getElement().getThemeList().add("contrast");
      case COMPLETED -> badge.getElement().getThemeList().add("success");
      case FAILED    -> badge.getElement().getThemeList().add("error");
      case CANCELLED -> badge.getElement().getThemeList().add("contrast");
      // DUPLICATE is a distinct, operationally clear terminal state — not an error
      case DUPLICATE -> badge.getElement().getThemeList().add("contrast");
      default        -> badge.getElement().getThemeList().add("primary");
    }
    return badge;
  }
}
