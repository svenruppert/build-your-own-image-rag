package com.svenruppert.flow.views.pipeline;

import com.svenruppert.imagerag.pipeline.IngestionJob;
import com.svenruppert.imagerag.pipeline.JobStep;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Batch progress row of the pipeline monitor (progress bar, processed count, elapsed time,
 * ETA) plus the completion summary card shown once every job is terminal.
 * <p>The summary card is a separate component ({@link #summaryCard()}) so the view can
 * place it independently of this row.
 */
class PipelineBatchProgress
    extends HorizontalLayout {

  private final ProgressBar batchProgressBar = new ProgressBar(0, 1);
  private final Span batchCountLabel = new Span();
  private final Span batchElapsedLabel = new Span();
  private final Span batchEtaLabel = new Span();
  private final Div completionSummaryCard = new Div();

  PipelineBatchProgress() {
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

    add(batchProgressBar, batchCountLabel, batchElapsedLabel, batchEtaLabel);
    setAlignItems(Alignment.END);
    setSpacing(true);
    setVisible(false); // hidden when no jobs exist

    completionSummaryCard.getStyle()
        .set("background", "var(--lumo-contrast-5pct)")
        .set("border-radius", "var(--lumo-border-radius-l)")
        .set("padding", "var(--lumo-space-m)")
        .set("margin", "var(--lumo-space-s) 0");
    completionSummaryCard.setVisible(false);
  }

  /**
   * Formats a {@link Duration} as {@code "Xm Ys"} or {@code "Ys"} for durations under a minute.
   */
  private static String formatDuration(Duration d) {
    long s = d.toSeconds();
    if (s < 60) return s + "s";
    return (s / 60) + "m " + (s % 60) + "s";
  }

  private static Span summaryBadge(String text, String bg, String fg) {
    Span s = new Span(text);
    s.getElement().getThemeList().add("badge");
    s.getStyle().set("background-color", bg).set("color", fg);
    return s;
  }

  /**
   * The completion summary card; visible only when all jobs are terminal.
   */
  Div summaryCard() {
    return completionSummaryCard;
  }

  /**
   * Updates the batch progress bar, count, elapsed time, and ETA based on all known jobs.
   * When all jobs are terminal, shows a completion summary card instead of the ETA.
   */
  void update(List<IngestionJob> jobs) {
    if (jobs.isEmpty()) {
      setVisible(false);
      completionSummaryCard.setVisible(false);
      return;
    }

    setVisible(true);

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
}
