package com.svenruppert.flow.views.pipeline;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.views.detail.DetailDialog;
import com.svenruppert.flow.views.detail.ReprocessingDiffDialog;
import com.svenruppert.flow.views.shared.ViewServices;
import com.svenruppert.imagerag.domain.LocationSummary;
import com.svenruppert.imagerag.domain.SemanticAnalysis;
import com.svenruppert.imagerag.domain.SensitivityAssessment;
import com.svenruppert.imagerag.pipeline.IngestionJob;
import com.svenruppert.imagerag.pipeline.JobStep;
import com.svenruppert.imagerag.pipeline.JobType;
import com.svenruppert.imagerag.service.ImageCatalogService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

/**
 * Job grid of the pipeline monitor: stage and type badges, timing columns, a context
 * column (error, duplicate, reprocessing diff) and per-row promote / cancel / retry
 * actions.
 */
class PipelineJobGrid
    extends Grid<IngestionJob>
    implements HasLogger {

  private static final DateTimeFormatter TIME_FMT =
      DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

  private final ViewServices services;

  PipelineJobGrid(ViewServices services) {
    super(IngestionJob.class, false);
    this.services = services;

    setWidthFull();
    setHeightFull();
    addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);

    // Status badge — stage-colored; covers all pipeline stages at a glance.
    addComponentColumn(job -> stepBadge(job.getCurrentStep()))
        .setHeader(getTranslation("pipeline.col.status"))
        .setWidth("240px")
        .setFlexGrow(0);

    // Job type badge — distinguishes upload jobs from reprocess jobs
    addComponentColumn(job -> jobTypeBadge(job.getJobType()))
        .setHeader(getTranslation("pipeline.col.type"))
        .setWidth("130px")
        .setFlexGrow(0);

    // Filename
    addColumn(IngestionJob::getFilename)
        .setHeader(getTranslation("pipeline.col.filename"))
        .setFlexGrow(3)
        .setSortable(true);

    // Inline progress bar for running jobs
    addComponentColumn(job -> {
      if (job.isRunning()) {
        ProgressBar bar = new ProgressBar();
        bar.setIndeterminate(true);
        bar.setWidth("120px");
        return bar;
      }
      return new Span();
    }).setHeader("").setWidth("140px").setFlexGrow(0);

    // Elapsed time
    addColumn(job -> {
      var elapsed = job.elapsed();
      if (elapsed.isZero()) return "—";
      long s = elapsed.toSeconds();
      if (s < 60) return s + "s";
      return (s / 60) + "m " + (s % 60) + "s";
    }).setHeader(getTranslation("pipeline.col.duration")).setWidth("90px").setFlexGrow(0);

    // Submitted at
    addColumn(job -> job.getSubmittedAt() != null
        ? TIME_FMT.format(job.getSubmittedAt()) : "—")
        .setHeader(getTranslation("pipeline.col.queued.at"))
        .setWidth("90px")
        .setFlexGrow(0);

    // Finished at
    addColumn(job -> job.getFinishedAt() != null
        ? TIME_FMT.format(job.getFinishedAt()) : "—")
        .setHeader(getTranslation("pipeline.col.finished.at"))
        .setWidth("110px")
        .setFlexGrow(0);

    // Context column: error / duplicate info / reprocess diff button
    addComponentColumn(this::contextCell)
        .setHeader(getTranslation("pipeline.col.error")).setFlexGrow(3);

    // Promote button — visible only for QUEUED jobs (moves to front of queue)
    addComponentColumn(job -> {
      if (job.getCurrentStep() == JobStep.QUEUED) {
        Button promoteBtn = new Button(getTranslation("pipeline.promote"));
        promoteBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        promoteBtn.getElement().setAttribute("title",
                                             getTranslation("pipeline.promote.tooltip"));
        promoteBtn.addClickListener(e -> {
          services.ingestionPipeline().promote(job);
          Notification.show(getTranslation("pipeline.promote.done", job.getFilename()),
                            2500, Notification.Position.BOTTOM_END)
              .addThemeVariants(NotificationVariant.LUMO_PRIMARY);
        });
        return promoteBtn;
      }
      return new Span();
    }).setHeader("").setWidth("110px").setFlexGrow(0);

    // Cancel button — visible only for queued and running jobs
    addComponentColumn(job -> {
      if (!job.isTerminal()) {
        Button cancelBtn = new Button(getTranslation("pipeline.cancel"));
        cancelBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR,
                                   ButtonVariant.LUMO_TERTIARY);
        cancelBtn.addClickListener(e ->
                                       services.ingestionPipeline().cancel(job));
        return cancelBtn;
      }
      return new Span();
    }).setHeader("").setWidth("110px").setFlexGrow(0);

    // Retry button — visible only for FAILED jobs
    addComponentColumn(job -> {
      if (job.getCurrentStep() == JobStep.FAILED) {
        Button retryBtn = new Button(getTranslation("pipeline.retry"));
        retryBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);
        retryBtn.addClickListener(e -> retryJob(job));
        return retryBtn;
      }
      return new Span();
    }).setHeader("").setWidth("100px").setFlexGrow(0);
  }

  private Component contextCell(IngestionJob job) {
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
  }

  private void retryJob(IngestionJob job) {
    try {
      services.ingestionPipeline().retry(job);
      Notification.show(getTranslation("pipeline.retry.started", job.getFilename()),
                        3000, Notification.Position.BOTTOM_END)
          .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    } catch (IllegalStateException | RejectedExecutionException e) {
      // Expected: job not FAILED, original file gone, or pipeline shut down.
      logger().warn("Retry of job {} rejected: {}", job.getJobId(), e.getMessage());
      Notification.show(getTranslation("pipeline.retry.failed", e.getMessage()),
                        5000, Notification.Position.MIDDLE)
          .addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
  }

  /**
   * Opens a read-only {@link DetailDialog} for the image that a DUPLICATE job points to.
   * Called from the context column; runs on the Vaadin UI thread.
   */
  private void openDuplicateDialog(UUID existingId) {
    if (existingId == null) return;
    ImageCatalogService ps = services.catalog();
    ps.findImage(existingId).ifPresent(asset -> {
      SemanticAnalysis analysis = ps.findAnalysis(existingId).orElse(null);
      SensitivityAssessment assessment = ps.findAssessment(existingId).orElse(null);
      LocationSummary location = ps.findLocation(existingId).orElse(null);
      new DetailDialog(asset, analysis, assessment, location).open();
    });
  }

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
      // ── Active pipeline stages — each gets a distinct color ──────────────
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
