package com.svenruppert.imagerag.service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Live progress state for a running taxonomy-analysis pass.
 *
 * <p>Instances are created by the UI before starting analysis, then mutated by
 * {@link com.svenruppert.imagerag.service.impl.TaxonomyAnalysisServiceImpl} through a
 * {@link java.util.function.Consumer} callback.  The UI reads the state inside
 * {@code ui.access()} blocks that are triggered by that callback.
 *
 * <p>This object is not thread-safe in itself; the caller is responsible for
 * ensuring that writes happen on the analysis thread and reads happen inside
 * {@code ui.access()} (which serialises them correctly).
 */
public class TaxonomyAnalysisProgress {

  // ── Step labels ───────────────────────────────────────────────────────────
  public static final String STEP_SCOPE = "Scope Definition";
  public static final String STEP_IMAGES = "Image Loading";
  public static final String STEP_TAXONOMY = "Taxonomy Loading";
  public static final String STEP_ANALYSIS = "Image Analysis";
  public static final String STEP_SUGGESTIONS = "Generating Suggestions";
  public static final String STEP_PREVIEW = "Dry-Run Preview";
  public static final String STEP_DONE = "Completed";

  public static final String[] ALL_STEPS = {
      STEP_SCOPE, STEP_IMAGES, STEP_TAXONOMY,
      STEP_ANALYSIS, STEP_SUGGESTIONS, STEP_PREVIEW, STEP_DONE
  };
  // ── Internal state ────────────────────────────────────────────────────────
  private final Map<String, StepStatus> stepStatuses = new LinkedHashMap<>();
  private final Map<String, String> stepSummaries = new LinkedHashMap<>();
  private final long startTimeMs = System.currentTimeMillis();
  private final boolean isDryRun;
  private int totalImages = 0;
  private int analyzedImages = 0;
  private int suggestionsGenerated = 0;
  private boolean completed = false;
  private boolean failed = false;
  private String errorMessage = null;
  public TaxonomyAnalysisProgress(boolean isDryRun) {
    this.isDryRun = isDryRun;
    for (String step : ALL_STEPS) {
      stepStatuses.put(step, StepStatus.PENDING);
      stepSummaries.put(step, "Waiting\u2026");
    }
  }

  public void markActive(String step, String summary) {
    stepStatuses.put(step, StepStatus.ACTIVE);
    stepSummaries.put(step, summary != null ? summary : "Running\u2026");
  }

  // ── Mutation API (called from analysis thread) ────────────────────────────

  public void markCompleted(String step, String summary) {
    stepStatuses.put(step, StepStatus.COMPLETED);
    stepSummaries.put(step, summary != null ? summary : "Done");
  }

  public void markFailed(String step, String summary) {
    stepStatuses.put(step, StepStatus.FAILED);
    stepSummaries.put(step, summary != null ? summary : "Failed");
  }

  public void markSkipped(String step, String summary) {
    stepStatuses.put(step, StepStatus.SKIPPED);
    stepSummaries.put(step, summary != null ? summary : "Skipped");
  }

  public void incrementAnalyzed() {
    this.analyzedImages++;
  }

  public void setCompleted() {
    this.completed = true;
  }

  public Map<String, StepStatus> getStepStatuses() {
    return stepStatuses;
  }

  public Map<String, String> getStepSummaries() {
    return stepSummaries;
  }

  public int getTotalImages() {
    return totalImages;
  }

  public void setTotalImages(int total) {
    this.totalImages = total;
  }

  // ── Read API (called from UI thread inside ui.access()) ───────────────────

  public int getAnalyzedImages() {
    return analyzedImages;
  }

  public int getSuggestionsGenerated() {
    return suggestionsGenerated;
  }

  public void setSuggestionsGenerated(int count) {
    this.suggestionsGenerated = count;
  }

  public boolean isDryRun() {
    return isDryRun;
  }

  public boolean isCompleted() {
    return completed;
  }

  public boolean isFailed() {
    return failed;
  }

  public void setFailed(String error) {
    this.failed = true;
    this.errorMessage = error;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public long elapsedMs() {
    return System.currentTimeMillis() - startTimeMs;
  }

  /**
   * Progress fraction in [0, 1] based on images analyzed vs total.
   */
  public double getProgressFraction() {
    return totalImages > 0
        ? Math.min(1.0, (double) analyzedImages / totalImages)
        : 0.0;
  }

  /**
   * Human-readable elapsed time.
   */
  public String getElapsedFormatted() {
    long s = elapsedMs() / 1000;
    return s < 60 ? s + "s" : (s / 60) + "m " + (s % 60) + "s";
  }

  /**
   * Best-effort ETA based on images processed so far.
   * Returns "—" when no meaningful estimate is possible.
   */
  public String getEtaFormatted() {
    if (analyzedImages <= 0 || totalImages <= 0) return "\u2014";
    long elapsed = elapsedMs();
    if (elapsed <= 0) return "\u2014";
    double msPerImage = (double) elapsed / analyzedImages;
    long remaining = (long) ((totalImages - analyzedImages) * msPerImage);
    long s = remaining / 1000;
    return s < 60 ? "~" + s + "s" : "~" + (s / 60) + "m " + (s % 60) + "s";
  }

  // ── Step status ───────────────────────────────────────────────────────────
  public enum StepStatus { PENDING, ACTIVE, COMPLETED, FAILED, SKIPPED }
}
