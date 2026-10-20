package com.svenruppert.imagerag.pipeline;

import com.svenruppert.imagerag.settings.SettingsService;
import com.svenruppert.imagerag.settings.Settings;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.functional.result.Result;
import com.svenruppert.imagerag.domain.*;
import com.svenruppert.imagerag.domain.enums.RecommendedStorageMode;
import com.svenruppert.imagerag.domain.enums.RiskLevel;
import com.svenruppert.imagerag.dto.ExtractedMetadata;
import com.svenruppert.imagerag.dto.KeywordIndexDocument;
import com.svenruppert.imagerag.dto.StoredImage;
import com.svenruppert.imagerag.dto.VisionAnalysisResponse;
import com.svenruppert.imagerag.ollama.OllamaConfig;
import com.svenruppert.imagerag.persistence.PersistenceService;
import com.svenruppert.imagerag.service.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * Asynchronous ingestion pipeline for uploads and reprocessing jobs.
 * <p>Jobs run on a bounded executor of virtual threads; the number of concurrent workers
 * is set via {@link #updateParallelism} (bounded by {@link Settings#PIPELINE_INGESTION_PARALLELISM}). Each job can be
 * cancelled via {@link #cancel}, and the whole pipeline can be paused between steps.
 * <p>Jobs are kept in a {@link CopyOnWriteArrayList} so the pipeline view can read them
 * without locks. Only the most recent finished jobs are kept
 * ({@link com.svenruppert.imagerag.settings.Settings#PIPELINE_MAX_FINISHED_JOBS}).
 */
public class IngestionPipeline
    implements HasLogger {

  /**
   * Note added to the sensitivity assessment when the vision analysis failed.
   */
  static final String VISION_FAILED_NOTE =
      "Vision analysis failed — content was not analyzed, manual review required";

  private final List<IngestionJob> jobs = new CopyOnWriteArrayList<>();
  /**
   * Guards {@link #paused}; workers wait on it while the pipeline is paused.
   */
  private final Object pauseMonitor = new Object();
  private boolean paused;
  private final ImageStorageService imageStorageService;
  private final MetadataExtractionService metadataExtractionService;
  private final ReverseGeocodingService reverseGeocodingService;
  private final VisionAnalysisService visionAnalysisService;
  private final SemanticDerivationService semanticDerivationService;
  private final SensitivityAssessmentService sensitivityAssessmentService;
  private final EmbeddingService embeddingService;
  private final VectorIndexService vectorIndexService;
  private final PersistenceService persistenceService;
  private final KeywordIndexService keywordIndexService;
  private final OllamaConfig ollamaConfig;
  /**
   * Maps each queued or running job's UUID to its {@link FutureTask}.
   * Used by {@link #promote} to locate and reorder the task in the work queue;
   * entries are removed when the task finishes.
   */
  private final ConcurrentHashMap<UUID, FutureTask<?>> taskMap = new ConcurrentHashMap<>();
  /**
   * Work queue for the current executor. A {@link LinkedBlockingDeque} allows
   * {@link #promote} to move a queued task to the front.
   */
  private volatile LinkedBlockingDeque<Runnable> workQueue = new LinkedBlockingDeque<>();
  /**
   * Worker executor — size reflects the current ingestion parallelism setting.
   * Initially sequential (1 thread). Call {@link #updateParallelism} to resize.
   */
  private volatile ThreadPoolExecutor executor = buildThreadPoolExecutor(1, workQueue);

  public IngestionPipeline(ImageStorageService imageStorageService,
                           MetadataExtractionService metadataExtractionService,
                           ReverseGeocodingService reverseGeocodingService,
                           VisionAnalysisService visionAnalysisService,
                           SemanticDerivationService semanticDerivationService,
                           SensitivityAssessmentService sensitivityAssessmentService,
                           EmbeddingService embeddingService,
                           VectorIndexService vectorIndexService,
                           PersistenceService persistenceService,
                           KeywordIndexService keywordIndexService,
                           OllamaConfig ollamaConfig) {
    this.imageStorageService = imageStorageService;
    this.metadataExtractionService = metadataExtractionService;
    this.reverseGeocodingService = reverseGeocodingService;
    this.visionAnalysisService = visionAnalysisService;
    this.semanticDerivationService = semanticDerivationService;
    this.sensitivityAssessmentService = sensitivityAssessmentService;
    this.embeddingService = embeddingService;
    this.vectorIndexService = vectorIndexService;
    this.persistenceService = persistenceService;
    this.keywordIndexService = keywordIndexService;
    this.ollamaConfig = ollamaConfig;
  }

  /**
   * Throws {@link InterruptedException} if the worker thread has been interrupted,
   * indicating a user-requested cancellation between pipeline steps.
   */
  private static void checkCancelled()
      throws InterruptedException {
    if (Thread.currentThread().isInterrupted()) {
      throw new InterruptedException("Pipeline job cancelled");
    }
  }

  private static ThreadPoolExecutor buildThreadPoolExecutor(int parallelism,
                                                            LinkedBlockingDeque<Runnable> queue) {
    int threads = Math.max(1, parallelism);
    return new ThreadPoolExecutor(
        threads, threads, 0L, TimeUnit.MILLISECONDS, queue,
        Thread.ofVirtual().name("ingestion-", 0).factory());
  }

  private static String computeSha256(byte[] data) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(data));
    } catch (NoSuchAlgorithmException e) {
      // Every Java platform is required to provide SHA-256.
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  /**
   * Checkpoint between pipeline steps: fails fast on cancellation and blocks while the
   * pipeline is paused. A cancellation during the pause ends the wait with an
   * {@link InterruptedException}.
   */
  private void checkPauseOrCancelled()
      throws InterruptedException {
    checkCancelled();
    synchronized (pauseMonitor) {
      while (paused) {
        pauseMonitor.wait();
      }
    }
  }

  // -------------------------------------------------------------------------
  // Public API
  // -------------------------------------------------------------------------

  /**
   * Pauses the pipeline: running jobs stop at their next checkpoint, queued jobs wait.
   * Idempotent and safe to call from several threads.
   */
  public void pause() {
    synchronized (pauseMonitor) {
      paused = true;
    }
    logger().info("Pipeline paused");
  }

  public void resume() {
    synchronized (pauseMonitor) {
      paused = false;
      pauseMonitor.notifyAll();
    }
    logger().info("Pipeline resumed");
  }

  public boolean isPaused() {
    synchronized (pauseMonitor) {
      return paused;
    }
  }

  /**
   * Submits an image for processing from a temp file written by the upload component.
   * <p>No bytes are read here — the pipeline reads the file only when the job starts,
   * keeping peak memory at {@code parallelism × max_file_size}. The temp file is deleted
   * right after it was read, or when the job is cancelled before it started.
   *
   * @param tempFile path to the temp file written by the upload component
   * @param filename original filename shown to the user
   * @param mimeType MIME type of the uploaded image
   * @return the created {@link IngestionJob} for status tracking
   */
  public IngestionJob submitFromPath(Path tempFile, String filename, String mimeType) {
    IngestionJob job = new IngestionJob(filename, mimeType);
    enqueue(job, () -> processFromPath(job, tempFile), () -> deleteTempFile(job, tempFile));
    logger().info("Job queued (from path): {} ({})", filename, job.getJobId());
    return job;
  }

  /**
   * Submits a reprocessing job for an already-stored image asset.
   * <p>The job skips file storage and duplicate detection and re-runs the AI-heavy
   * stages: OCR, vision analysis, semantic derivation, sensitivity assessment,
   * embedding, vector and keyword indexing. It carries {@link JobType#REPROCESS_EXISTING}.
   *
   * @param asset the image asset to reprocess; must already exist in the image store
   * @return the created {@link IngestionJob} for status tracking
   */
  public IngestionJob submitReprocess(ImageAsset asset) {
    IngestionJob job = new IngestionJob(asset.getOriginalFilename(), asset.getMimeType(),
                                        JobType.REPROCESS_EXISTING);
    enqueue(job, () -> reprocess(job, asset), null);
    logger().info("Reprocess job queued: {} ({})", asset.getOriginalFilename(), job.getJobId());
    return job;
  }

  /**
   * Retries a FAILED job. If the image was stored, reprocesses it.
   */
  public IngestionJob retry(IngestionJob failedJob) {
    if (failedJob.getCurrentStep() != JobStep.FAILED) {
      throw new IllegalStateException("Can only retry FAILED jobs");
    }
    UUID imageId = failedJob.getResultImageId();
    if (imageId != null) {
      return persistenceService.findImage(imageId)
          .map(this::submitReprocess)
          .orElseThrow(() -> new IllegalStateException("Image not found for retry"));
    }
    throw new IllegalStateException(
        "Cannot retry: original file is no longer available. Please re-upload.");
  }

  /**
   * Cancels a specific job. Safe to call from any thread, including the Vaadin UI thread.
   * If the job is QUEUED it will be removed from the executor queue before it starts.
   * If it is already RUNNING the worker thread is interrupted at the next checkpoint.
   */
  public void cancel(IngestionJob job) {
    logger().info("Cancellation requested for job {} ({})", job.getJobId(), job.getFilename());
    job.cancel();
  }

  /**
   * Returns an unmodifiable live view of all retained jobs (newest-last).
   */
  public List<IngestionJob> getAllJobs() {
    return Collections.unmodifiableList(jobs);
  }

  /**
   * Dynamically changes the number of concurrent ingestion workers.
   * <p>The current executor is shut down gracefully (already-running and queued
   * jobs still complete on it). A new executor with the requested thread count is
   * installed for future submissions. The value is clamped to the bounds of
   * {@link Settings#PIPELINE_INGESTION_PARALLELISM}.
   *
   * @param parallelism desired number of concurrent workers
   */
  public synchronized void updateParallelism(int parallelism) {
    int clamped = Math.clamp(parallelism, Settings.PIPELINE_INGESTION_PARALLELISM.min(),
                             Settings.PIPELINE_INGESTION_PARALLELISM.max());
    ThreadPoolExecutor old = executor;
    // A fresh queue lets the old executor drain its queue without interference.
    // promote() reads workQueue via a volatile read, so new submissions use the new queue.
    workQueue = new LinkedBlockingDeque<>();
    executor = buildThreadPoolExecutor(clamped, workQueue);
    old.shutdown();
    logger().info("Ingestion parallelism updated to {}", clamped);
  }

  /**
   * Promotes a QUEUED job to the front of the work queue so it executes next.
   * If the task has already been picked up by a worker thread, this is a safe no-op.
   *
   * @param job the job to promote; must currently be in {@link JobStep#QUEUED} state
   */
  public void promote(IngestionJob job) {
    if (job.getCurrentStep() != JobStep.QUEUED) {
      logger().warn("Cannot promote non-queued job {} (step={})",
                    job.getFilename(), job.getCurrentStep());
      return;
    }
    FutureTask<?> task = taskMap.get(job.getJobId());
    if (task == null) {
      logger().warn("No task found for job {} — cannot promote", job.getFilename());
      return;
    }
    // workQueue may have been replaced by updateParallelism(); then remove() returns false.
    LinkedBlockingDeque<Runnable> q = workQueue;
    if (q.remove(task)) {
      q.addFirst(task);
      job.setPriority(1);
      logger().info("Job {} promoted to front of queue", job.getFilename());
    } else {
      logger().warn("Job {} not found in current work queue (may have started or queue was replaced)",
                    job.getFilename());
    }
  }

  public void shutdown() {
    executor.shutdownNow();
  }

  // -------------------------------------------------------------------------
  // Job bookkeeping
  // -------------------------------------------------------------------------

  /**
   * Registers the job and hands its work to the executor.
   *
   * @param onCancelled runs when the task is cancelled (also before it started); may be null
   */
  private void enqueue(IngestionJob job, Runnable work, Runnable onCancelled) {
    jobs.add(job);
    FutureTask<Void> task = new FutureTask<>(work, null) {
      @Override
      protected void done() {
        taskMap.remove(job.getJobId());
        if (isCancelled() && onCancelled != null) {
          onCancelled.run();
        }
        pruneFinishedJobs();
      }
    };
    taskMap.put(job.getJobId(), task);
    job.setFuture(task);
    executor.execute(task);
  }

  /**
   * Drops the oldest finished jobs so that at most
   * {@link Settings#PIPELINE_MAX_FINISHED_JOBS} remain.
   */
  private void pruneFinishedJobs() {
    List<IngestionJob> finished = jobs.stream().filter(IngestionJob::isTerminal).toList();
    int excess = finished.size() - SettingsService.getInstance().get(Settings.PIPELINE_MAX_FINISHED_JOBS);
    if (excess > 0) {
      jobs.removeAll(finished.subList(0, excess));
    }
  }

  private void deleteTempFile(IngestionJob job, Path tempFile) {
    try {
      Files.deleteIfExists(tempFile);
    } catch (IOException e) {
      logger().warn("[{}] Could not delete temp file {}: {}", job.getFilename(), tempFile, e.getMessage());
    }
  }

  // -------------------------------------------------------------------------
  // Pipeline orchestration — runs on worker threads, never on the UI thread
  // -------------------------------------------------------------------------

  /**
   * Reads the temp file into memory, deletes it immediately, then delegates to
   * {@link #process(IngestionJob, byte[])}.
   */
  private void processFromPath(IngestionJob job, Path tempFile) {
    byte[] data;
    try {
      data = Files.readAllBytes(tempFile);
    } catch (IOException e) {
      logger().error("[{}] Failed to read temp file {}: {}",
                     job.getFilename(), tempFile, e.getMessage(), e);
      job.fail("Failed to read uploaded file: " + e.getMessage());
      return;
    } finally {
      deleteTempFile(job, tempFile);
    }
    process(job, data);
  }

  private void process(IngestionJob job, byte[] data) {
    String label = job.getFilename();
    try {
      // ── Duplicate detection (hash check before any expensive work) ────────
      String sha256 = computeSha256(data);
      Optional<UUID> existingId = persistenceService.findImageIdByHash(sha256);
      if (existingId.isPresent()) {
        UUID eid = existingId.get();
        String existingFilename = persistenceService.findImage(eid)
            .map(ImageAsset::getOriginalFilename)
            .orElse(eid.toString());
        logger().info("[{}] Duplicate detected — same content already stored as '{}' ({})",
                      label, existingFilename, eid);
        job.markDuplicate(eid, existingFilename);
        return;
      }

      // Step 1: Store image file
      checkPauseOrCancelled();
      job.transition(JobStep.STORING);
      Result<StoredImage, String> storeResult = imageStorageService.store(
          new ByteArrayInputStream(data), job.getFilename(), job.getMimeType());
      if (!(storeResult instanceof Result.Success<StoredImage, String>(StoredImage stored))) {
        storeResult.peekFailure(reason -> {
          logger().error("[{}] Pipeline FAILED: {}", label, reason);
          job.fail(reason);
        });
        return;
      }
      UUID imageId = stored.imageId();
      Path imagePath = imageStorageService.resolvePath(imageId);
      logger().info("[{}] Stored → {}", label, imageId);

      // Step 2: Extract EXIF / technical metadata (best effort, never fails)
      checkPauseOrCancelled();
      job.transition(JobStep.EXTRACTING_METADATA);
      ExtractedMetadata extracted = metadataExtractionService.extract(imagePath);

      ImageAsset asset = new ImageAsset(
          imageId,
          stored.originalFilename(),
          stored.storedFilename(),
          job.getMimeType(),
          stored.fileSize(),
          extracted.width(),
          extracted.height(),
          stored.sha256(),
          Instant.now(),
          true,
          extracted.exifPresent(),
          extracted.gpsPresent());
      persistenceService.saveImage(asset);
      // Register hash so future uploads of the same file are caught as duplicates
      persistenceService.registerHash(stored.sha256(), imageId);

      ImageMetadataInfo metadataInfo = new ImageMetadataInfo(
          extracted.captureTimestamp(),
          extracted.cameraModel(),
          extracted.latitude(),
          extracted.longitude());
      persistenceService.saveMetadata(imageId, metadataInfo);

      // Step 3: Reverse geocoding (only if GPS data present)
      LocationSummary locationSummary = null;
      if (extracted.gpsPresent()
          && extracted.latitude() != null
          && extracted.longitude() != null) {
        checkPauseOrCancelled();
        job.transition(JobStep.GEOCODING);
        try {
          locationSummary = reverseGeocodingService.reverseGeocode(
              extracted.latitude(), extracted.longitude());
          persistenceService.saveLocation(imageId, locationSummary);
          logger().debug("[{}] Location: {}", label, locationSummary.toHumanReadable());
        } catch (RuntimeException e) {
          // Geocoding is optional enrichment: a failure must not fail the whole job.
          logger().warn("[{}] Geocoding failed: {}", label, e.getMessage(), e);
        }
      }

      // Steps 4-6: OCR, vision, semantics, sensitivity, visibility
      OcrResult ocrResult = runOcr(job, label, imageId, imagePath, null);
      AnalysisOutcome outcome = runAnalysis(job, label, asset, imagePath, metadataInfo, locationSummary);

      // Steps 7-9: embedding, vector index, keyword index
      runEmbedding(job, label, imageId, outcome.analysis());
      runKeywordIndexing(job, asset, outcome.analysis(), locationSummary, ocrResult);

      job.complete(imageId);
      logger().info("[{}] Pipeline COMPLETED → imageId={}", label, imageId);

    } catch (InterruptedException e) {
      // User-requested cancellation. Do not re-interrupt: the worker thread must stay
      // clean for the next job.
      job.transition(JobStep.CANCELLED); // no-op if cancel() was already called
      logger().info("[{}] Job cancelled by interrupt", label);
    } catch (RuntimeException e) {
      // Job boundary: any unexpected failure marks this job FAILED instead of killing
      // the worker thread.
      logger().error("[{}] Pipeline FAILED: {}", label, e.getMessage(), e);
      job.fail(e.getMessage());
    }
  }

  private void reprocess(IngestionJob job, ImageAsset asset) {
    UUID imageId = asset.getId();
    String label = "reprocess][" + asset.getOriginalFilename();

    // Snapshot of the previous derived state for the diff
    SemanticAnalysis previousAnalysis = persistenceService.findAnalysis(imageId).orElse(null);
    SensitivityAssessment previousAssessment = persistenceService.findAssessment(imageId).orElse(null);
    OcrResult previousOcr = persistenceService.findOcrResult(imageId).orElse(null);

    try {
      // Step 1: Load image from disk
      checkPauseOrCancelled();
      job.transition(JobStep.LOADING_IMAGE);
      Path imagePath = imageStorageService.resolvePath(imageId);
      logger().info("[{}] Loading image from {}", label, imagePath);

      ImageMetadataInfo metadataInfo = persistenceService.findMetadata(imageId).orElse(null);
      LocationSummary locationSummary = persistenceService.findLocation(imageId).orElse(null);

      // Keep the previous OCR result if the OCR call fails this time.
      OcrResult ocrResult = runOcr(job, label, imageId, imagePath, previousOcr);
      AnalysisOutcome outcome = runAnalysis(job, label, asset, imagePath, metadataInfo, locationSummary);
      runEmbedding(job, label, imageId, outcome.analysis());

      job.setReprocessingDiff(buildReprocessingDiff(
          previousAnalysis, previousAssessment, previousOcr,
          outcome.analysis(), outcome.assessment(), ocrResult));

      runKeywordIndexing(job, asset, outcome.analysis(), locationSummary, ocrResult);

      job.complete(imageId);
      logger().info("[{}] COMPLETED", label);

    } catch (InterruptedException e) {
      job.transition(JobStep.CANCELLED);
      logger().info("[{}] Cancelled by interrupt", label);
    } catch (RuntimeException e) {
      // Job boundary: any unexpected failure marks this job FAILED instead of killing
      // the worker thread.
      logger().error("[{}] FAILED: {}", label, e.getMessage(), e);
      job.fail(e.getMessage());
    }
  }

  // -------------------------------------------------------------------------
  // Shared pipeline steps
  // -------------------------------------------------------------------------

  private record AnalysisOutcome(SemanticAnalysis analysis, SensitivityAssessment assessment) {
  }

  /**
   * OCR step with the dedicated OCR prompt. On failure no new result is stored and
   * {@code fallback} is returned (null for new images, the previous result on reprocess).
   */
  private OcrResult runOcr(IngestionJob job, String label, UUID imageId, Path imagePath,
                           OcrResult fallback)
      throws InterruptedException {
    checkPauseOrCancelled();
    job.transition(JobStep.OCR_TEXT);
    return switch (visionAnalysisService.extractText(imagePath)) {
      case Result.Success<String, String>(String text) -> {
        boolean textFound = !text.isBlank();
        OcrResult result = new OcrResult(imageId, textFound ? text : null, textFound,
                                         ollamaConfig.getVisionModel(), Instant.now());
        persistenceService.saveOcrResult(imageId, result);
        logger().debug("[{}] OCR done (textFound={})", label, textFound);
        yield result;
      }
      case Result.Failure<String, String>(String reason) -> {
        logger().warn("[{}] OCR step failed (non-fatal): {}", label, reason);
        yield fallback;
      }
    };
  }

  /**
   * Vision analysis, semantic derivation, sensitivity assessment and visibility decision.
   */
  private AnalysisOutcome runAnalysis(IngestionJob job, String label, ImageAsset asset,
                                      Path imagePath, ImageMetadataInfo metadataInfo,
                                      LocationSummary locationSummary)
      throws InterruptedException {
    UUID imageId = asset.getId();

    checkPauseOrCancelled();
    job.transition(JobStep.ANALYZING_VISION);
    VisionAnalysisResponse visionResponse = visionAnalysisService.analyzeImage(imagePath);
    logger().info("[{}] Vision done (successful={})", label, visionResponse.successful());

    checkPauseOrCancelled();
    job.transition(JobStep.DERIVING_SEMANTICS);
    SemanticAnalysis semanticAnalysis = semanticDerivationService.derive(imageId, visionResponse);
    persistenceService.saveAnalysis(imageId, semanticAnalysis);

    checkPauseOrCancelled();
    job.transition(JobStep.ASSESSING_SENSITIVITY);
    SensitivityAssessment assessment = sensitivityAssessmentService.assess(
        asset, metadataInfo, locationSummary, semanticAnalysis);
    if (!visionResponse.successful()) {
      requireReviewForUnanalyzedContent(assessment);
    }
    persistenceService.saveAssessment(imageId, assessment);
    logger().info("[{}] Risk: {} | Flags: {}", label, assessment.getRiskLevel(), assessment.getFlags());

    applyVisibility(label, imageId, assessment);
    return new AnalysisOutcome(semanticAnalysis, assessment);
  }

  /**
   * Without a successful vision analysis the assessment is based on a stub text and
   * says nothing about the content, so the image must never be auto-approved.
   */
  private static void requireReviewForUnanalyzedContent(SensitivityAssessment assessment) {
    if (assessment.getRiskLevel() == RiskLevel.SAFE) {
      assessment.setRiskLevel(RiskLevel.REVIEW);
      assessment.setRecommendedStorageMode(RecommendedStorageMode.STORE_REDUCED);
    }
    assessment.setReviewRequired(true);
    String notes = assessment.getNotes();
    assessment.setNotes(notes == null || notes.isBlank()
                            ? VISION_FAILED_NOTE
                            : VISION_FAILED_NOTE + "; " + notes);
  }

  /**
   * Approves SAFE, non-archived images for search; locks everything else. Resets any
   * earlier manual approval or lock on reprocessing.
   */
  private void applyVisibility(String label, UUID imageId, SensitivityAssessment assessment) {
    boolean archived = persistenceService.isDeleted(imageId);
    if (assessment.getRiskLevel() == RiskLevel.SAFE && !archived) {
      persistenceService.approveImage(imageId);
      logger().info("[{}] Auto-approved for search (SAFE risk)", label);
    } else {
      persistenceService.unapproveImage(imageId);
      logger().info("[{}] Locked — {}", label,
                    archived ? "image is archived" : assessment.getRiskLevel() + " risk requires manual approval");
    }
  }

  /**
   * Embeds the analysis text and stores the vector. {@code index} replaces an existing
   * entry, so no prior remove is needed on reprocessing.
   */
  private void runEmbedding(IngestionJob job, String label, UUID imageId, SemanticAnalysis analysis)
      throws InterruptedException {
    checkPauseOrCancelled();
    job.transition(JobStep.EMBEDDING);
    Result<float[], String> embedding = embeddingService.embed(EmbeddingTextBuilder.build(analysis));
    if (!(embedding instanceof Result.Success<float[], String>(float[] vector))) {
      embedding.peekFailure(reason -> logger().warn(
          "[{}] No embedding ({}) — vector index not updated", label, reason));
      return;
    }
    checkPauseOrCancelled();
    job.transition(JobStep.INDEXING);
    vectorIndexService.index(imageId, vector);
    persistenceService.saveVectorEntry(imageId, new VectorEntry(
        imageId, ollamaConfig.getEmbeddingModel(), vector.length, Instant.now()));
    logger().info("[{}] Indexed {} dims", label, vector.length);
  }

  private void runKeywordIndexing(IngestionJob job, ImageAsset asset, SemanticAnalysis analysis,
                                  LocationSummary locationSummary, OcrResult ocrResult)
      throws InterruptedException {
    checkPauseOrCancelled();
    job.transition(JobStep.KEYWORD_INDEXING);
    keywordIndexService.index(KeywordIndexDocument.from(asset, analysis, locationSummary, ocrResult));
  }

  private ReprocessingDiff buildReprocessingDiff(SemanticAnalysis prevAnalysis,
                                                 SensitivityAssessment prevAssessment,
                                                 OcrResult prevOcr,
                                                 SemanticAnalysis newAnalysis,
                                                 SensitivityAssessment newAssessment,
                                                 OcrResult newOcr) {
    return new ReprocessingDiff(
        prevAnalysis != null ? prevAnalysis.getSummary() : null,
        newAnalysis != null ? newAnalysis.getSummary() : null,
        prevAnalysis != null ? prevAnalysis.getSourceCategory() : null,
        newAnalysis != null ? newAnalysis.getSourceCategory() : null,
        prevAnalysis != null ? prevAnalysis.getSecondaryCategories() : null,
        newAnalysis != null ? newAnalysis.getSecondaryCategories() : null,
        prevAssessment != null ? prevAssessment.getRiskLevel() : null,
        newAssessment != null ? newAssessment.getRiskLevel() : null,
        prevAnalysis != null ? prevAnalysis.getTags() : null,
        newAnalysis != null ? newAnalysis.getTags() : null,
        prevAnalysis != null ? prevAnalysis.getVisionModel() : null,
        newAnalysis != null ? newAnalysis.getVisionModel() : null,
        prevAnalysis != null ? prevAnalysis.getSemanticPromptVersion() : null,
        newAnalysis != null ? newAnalysis.getSemanticPromptVersion() : null,
        prevOcr != null ? prevOcr.getExtractedText() : null,
        newOcr != null ? newOcr.getExtractedText() : null
    );
  }
}
