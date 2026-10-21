/*
 * Copyright © 2026 Sven Ruppert (sven.ruppert@gmail.com)
 *
 * Licensed under the EUPL, Version 1.2 (the "Licence");
 * you may not use this file except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *     https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */
package com.svenruppert.imagerag.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Live progress state for a running taxonomy-analysis pass.
 * <p>Instances are created by the UI before starting analysis, then mutated by
 * {@link com.svenruppert.imagerag.service.impl.TaxonomyAnalysisServiceImpl} on the
 * analysis thread. Because {@code ui.access()} runs asynchronously, the UI may read
 * the state while the analysis thread still writes it. All methods are therefore
 * synchronized, the step maps are returned as copies, and readers should render from
 * a consistent {@link #snapshot()}.
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
  private final long startTimeMs;
  private final boolean isDryRun;
  private int totalImages = 0;
  private int analyzedImages = 0;
  private int suggestionsGenerated = 0;
  private boolean completed = false;
  private boolean failed = false;
  private String errorMessage = null;

  private TaxonomyAnalysisProgress(TaxonomyAnalysisProgress source) {
    this.isDryRun = source.isDryRun;
    this.startTimeMs = source.startTimeMs;
    this.stepStatuses.putAll(source.stepStatuses);
    this.stepSummaries.putAll(source.stepSummaries);
    this.totalImages = source.totalImages;
    this.analyzedImages = source.analyzedImages;
    this.suggestionsGenerated = source.suggestionsGenerated;
    this.completed = source.completed;
    this.failed = source.failed;
    this.errorMessage = source.errorMessage;
  }

  public TaxonomyAnalysisProgress(boolean isDryRun) {
    this.startTimeMs = System.currentTimeMillis();
    this.isDryRun = isDryRun;
    for (String step : ALL_STEPS) {
      stepStatuses.put(step, StepStatus.PENDING);
      stepSummaries.put(step, "Waiting\u2026");
    }
  }

  public synchronized void markActive(String step, String summary) {
    stepStatuses.put(step, StepStatus.ACTIVE);
    stepSummaries.put(step, summary != null ? summary : "Running\u2026");
  }

  // ── Mutation API (called from analysis thread) ────────────────────────────

  public synchronized void markCompleted(String step, String summary) {
    stepStatuses.put(step, StepStatus.COMPLETED);
    stepSummaries.put(step, summary != null ? summary : "Done");
  }

  public synchronized void markFailed(String step, String summary) {
    stepStatuses.put(step, StepStatus.FAILED);
    stepSummaries.put(step, summary != null ? summary : "Failed");
  }

  public synchronized void markSkipped(String step, String summary) {
    stepStatuses.put(step, StepStatus.SKIPPED);
    stepSummaries.put(step, summary != null ? summary : "Skipped");
  }

  public synchronized void incrementAnalyzed() {
    this.analyzedImages++;
  }

  public synchronized void setCompleted() {
    this.completed = true;
  }

  public synchronized Map<String, StepStatus> getStepStatuses() {
    return Collections.unmodifiableMap(new LinkedHashMap<>(stepStatuses));
  }

  public synchronized Map<String, String> getStepSummaries() {
    return Collections.unmodifiableMap(new LinkedHashMap<>(stepSummaries));
  }

  /**
   * Returns a detached, consistent copy of the current state for rendering.
   */
  public synchronized TaxonomyAnalysisProgress snapshot() {
    return new TaxonomyAnalysisProgress(this);
  }

  public synchronized int getTotalImages() {
    return totalImages;
  }

  public synchronized void setTotalImages(int total) {
    this.totalImages = total;
  }

  // ── Read API (called from UI thread inside ui.access()) ───────────────────

  public synchronized int getAnalyzedImages() {
    return analyzedImages;
  }

  public synchronized int getSuggestionsGenerated() {
    return suggestionsGenerated;
  }

  public synchronized void setSuggestionsGenerated(int count) {
    this.suggestionsGenerated = count;
  }

  public synchronized boolean isDryRun() {
    return isDryRun;
  }

  public synchronized boolean isCompleted() {
    return completed;
  }

  public synchronized boolean isFailed() {
    return failed;
  }

  public synchronized void setFailed(String error) {
    this.failed = true;
    this.errorMessage = error;
  }

  public synchronized String getErrorMessage() {
    return errorMessage;
  }

  public synchronized long elapsedMs() {
    return System.currentTimeMillis() - startTimeMs;
  }

  /**
   * Progress fraction in [0, 1] based on images analyzed vs total.
   */
  public synchronized double getProgressFraction() {
    return totalImages > 0
        ? Math.min(1.0, (double) analyzedImages / totalImages)
        : 0.0;
  }

  /**
   * Human-readable elapsed time.
   */
  public synchronized String getElapsedFormatted() {
    long s = elapsedMs() / 1000;
    return s < 60 ? s + "s" : (s / 60) + "m " + (s % 60) + "s";
  }

  /**
   * Best-effort ETA based on images processed so far.
   * Returns "—" when no meaningful estimate is possible.
   */
  public synchronized String getEtaFormatted() {
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
