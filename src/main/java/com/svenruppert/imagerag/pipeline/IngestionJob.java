package com.svenruppert.imagerag.pipeline;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Future;

/**
 * Represents one image through the ingestion pipeline.
 * All state mutations are synchronized; reads are volatile-safe for UI polling.
 */
public class IngestionJob {

  private final UUID jobId;
  private final String filename;
  private final String mimeType;
  private final Instant submittedAt;
  private final JobType jobType;

  private volatile JobStep currentStep = JobStep.QUEUED;
  private volatile Instant startedAt;
  private volatile Instant finishedAt;
  private volatile UUID resultImageId;
  private volatile String errorMessage;
  private volatile Future<?> future;

  /**
   * Set when the job terminates as {@link JobStep#DUPLICATE}.
   * Points to the UUID of the asset that already holds the same content.
   */
  private volatile UUID duplicateOfId;

  /**
   * Human-readable filename of the already-stored duplicate asset.
   * Used by the pipeline UI to give the operator a quick reference.
   */
  private volatile String duplicateOfFilename;

  public IngestionJob(String filename, String mimeType) {
    this(filename, mimeType, JobType.INGEST_UPLOAD);
  }

  public IngestionJob(String filename, String mimeType, JobType jobType) {
    this.jobId = UUID.randomUUID();
    this.filename = filename;
    this.mimeType = mimeType;
    this.jobType = jobType;
    this.submittedAt = Instant.now();
  }

  /**
   * Store the executor Future so the job can cancel itself later.
   */
  public synchronized void setFuture(Future<?> f) {
    this.future = f;
  }

  /**
   * Request cancellation. Removes the task from the executor queue if not yet
   * started, or interrupts the worker thread if already running.
   * Idempotent — calling on an already-terminal job is a no-op.
   */
  public synchronized void cancel() {
    if (currentStep.isTerminal()) return;
    if (future != null) future.cancel(true);
    this.currentStep = JobStep.CANCELLED;
    this.finishedAt = Instant.now();
  }

  /**
   * Advance to the next step. Thread-safe.
   */
  public synchronized void transition(JobStep step) {
    if (currentStep.isTerminal()) return; // no transitions out of terminal state
    this.currentStep = step;
    if (step == JobStep.STORING || step == JobStep.LOADING_IMAGE) {
      this.startedAt = Instant.now();
    }
    if (step.isTerminal()) {
      this.finishedAt = Instant.now();
    }
  }

  /**
   * Mark as FAILED with a reason. Thread-safe.
   */
  public synchronized void fail(String reason) {
    this.errorMessage = reason;
    transition(JobStep.FAILED);
  }

  /**
   * Mark as COMPLETED and store the resulting image id.
   */
  public synchronized void complete(UUID imageId) {
    this.resultImageId = imageId;
    transition(JobStep.COMPLETED);
  }

  /**
   * Terminates the job as a {@link JobStep#DUPLICATE}.
   * Records the existing asset for display in the pipeline UI.
   *
   * @param existingId       UUID of the asset that already stores the same content
   * @param existingFilename original filename of that existing asset
   */
  public synchronized void markDuplicate(UUID existingId, String existingFilename) {
    this.duplicateOfId = existingId;
    this.duplicateOfFilename = existingFilename;
    transition(JobStep.DUPLICATE);
  }

  // --- Derived helpers ---

  public boolean isTerminal() {
    return currentStep.isTerminal();
  }

  public boolean isRunning() {
    return !currentStep.isTerminal() && currentStep != JobStep.QUEUED;
  }

  /**
   * Wall-clock duration from start to now (or to finish if already terminal).
   */
  public Duration elapsed() {
    if (startedAt == null) return Duration.ZERO;
    Instant end = (finishedAt != null) ? finishedAt : Instant.now();
    return Duration.between(startedAt, end);
  }

  // --- Getters ---

  public JobType getJobType() {
    return jobType;
  }

  public UUID getJobId() {
    return jobId;
  }

  public String getFilename() {
    return filename;
  }

  public String getMimeType() {
    return mimeType;
  }

  public Instant getSubmittedAt() {
    return submittedAt;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getFinishedAt() {
    return finishedAt;
  }

  public JobStep getCurrentStep() {
    return currentStep;
  }

  public UUID getResultImageId() {
    return resultImageId;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public UUID getDuplicateOfId() {
    return duplicateOfId;
  }

  public String getDuplicateOfFilename() {
    return duplicateOfFilename;
  }
}
