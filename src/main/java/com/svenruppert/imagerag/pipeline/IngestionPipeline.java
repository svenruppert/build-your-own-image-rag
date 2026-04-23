package com.svenruppert.imagerag.pipeline;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.imagerag.domain.*;
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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sequential async ingestion pipeline.
 * <p>Images are submitted via {@link #submit} and processed <em>one at a time</em>
 * on a single virtual-thread-backed executor, ensuring the LLM is never overwhelmed
 * by concurrent requests. Each job can be cancelled via {@link #cancel}.
 * <p>All submitted jobs are kept in a {@link CopyOnWriteArrayList} so the
 * {@link com.svenruppert.flow.views.pipeline.PipelineView} can read them
 * safely without locks at any time.
 */
public class IngestionPipeline
    implements HasLogger {

  private final List<IngestionJob> jobs = new CopyOnWriteArrayList<>();
  // Pause/resume support
  private final AtomicBoolean paused = new AtomicBoolean(false);
  private final Semaphore pauseSemaphore = new Semaphore(1);
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
   * Maps each job's UUID to the {@link FutureTask} that was submitted to the executor.
   * Used by {@link #promote} to locate and reorder the task in the work queue.
   */
  private final ConcurrentHashMap<UUID, FutureTask<?>> taskMap = new ConcurrentHashMap<>();
  /**
   * Work queue for the current executor.  Using {@link LinkedBlockingDeque} instead of a
   * plain {@link LinkedBlockingQueue} allows {@link #promote} to move a queued task to the
   * front of the deque so it executes before other pending tasks.
   */
  private volatile LinkedBlockingDeque<Runnable> workQueue = new LinkedBlockingDeque<>();
  /**
   * Worker executor — size reflects the current ingestion parallelism setting.
   * Initially sequential (1 thread).  Call {@link #updateParallelism} to resize.
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

  // -------------------------------------------------------------------------
  // Public API
  // -------------------------------------------------------------------------

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
      byte[] hash = digest.digest(data);
      return HexFormat.of().formatHex(hash);
    } catch (Exception e) {
      return null; // hash unavailable; duplicate check skipped
    }
  }

  private void checkPauseOrCancelled()
      throws InterruptedException {
    checkCancelled();
    if (paused.get()) {
      try {
        pauseSemaphore.acquire();
        pauseSemaphore.release();
      } catch (InterruptedException e) {
        throw new InterruptedException("Pipeline job cancelled during pause");
      }
    }
  }

  public void pause() {
    paused.set(true);
    try {
      pauseSemaphore.acquire();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    logger().info("Pipeline paused");
  }

  public void resume() {
    paused.set(false);
    pauseSemaphore.release();
    logger().info("Pipeline resumed");
  }

  public boolean isPaused() {
    return paused.get();
  }

  /**
   * Submits an image for sequential async processing.
   * The InputStream is buffered immediately so the caller (Vaadin Upload handler)
   * can return without risk of the stream being closed.
   *
   * @return the created {@link IngestionJob} for status tracking
   */
  public IngestionJob submit(InputStream inputStream, String filename, String mimeType)
      throws IOException {
    byte[] data = inputStream.readAllBytes();
    IngestionJob job = new IngestionJob(filename, mimeType);
    jobs.add(job);
    FutureTask<Void> task = new FutureTask<>(() -> {
      process(job, data);
      return null;
    });
    taskMap.put(job.getJobId(), task);
    executor.execute(task);
    job.setFuture(task);
    logger().info("Job queued: {} ({})", filename, job.getJobId());
    return job;
  }

  /**
   * Submits an image for processing from a temp file written by the upload component.
   * <p>Unlike {@link #submit}, no bytes are read here — the temp file path is simply
   * captured in the job closure.  The pipeline reads the file from disk only when the
   * job actually starts (i.e. a worker slot is free), keeping peak memory consumption
   * at {@code parallelism × max_file_size} instead of {@code queued_files × max_file_size}.
   * The temp file is deleted immediately after the bytes are read.
   *
   * @param tempFile path to the temp file written by Vaadin's {@code MultiFileBuffer}
   * @param filename original filename shown to the user
   * @param mimeType MIME type of the uploaded image
   * @return the created {@link IngestionJob} for status tracking
   */
  public IngestionJob submitFromPath(Path tempFile, String filename, String mimeType)
      throws IOException {
    IngestionJob job = new IngestionJob(filename, mimeType);
    jobs.add(job);
    FutureTask<Void> task = new FutureTask<>(() -> {
      processFromPath(job, tempFile);
      return null;
    });
    taskMap.put(job.getJobId(), task);
    executor.execute(task);
    job.setFuture(task);
    logger().info("Job queued (from path): {} ({})", filename, job.getJobId());
    return job;
  }

  /**
   * Submits a reprocessing job for an already-stored image asset.
   * <p>Unlike {@link #submit}, no bytes are read from a stream — the image file already
   * exists on disk.  The job skips file storage and duplicate detection and re-runs only
   * the AI-heavy stages: vision analysis, semantic derivation, sensitivity assessment,
   * embedding, and vector index update.
   * <p>The job is visible in the pipeline UI just like a regular upload job but carries
   * {@link JobType#REPROCESS_EXISTING} to distinguish it from uploads.
   *
   * @param asset the image asset to reprocess; must already exist in the image store
   * @return the created {@link IngestionJob} for status tracking
   */
  public IngestionJob submitReprocess(ImageAsset asset) {
    IngestionJob job = new IngestionJob(asset.getOriginalFilename(), asset.getMimeType(),
                                        JobType.REPROCESS_EXISTING);
    jobs.add(job);
    FutureTask<Void> task = new FutureTask<>(() -> {
      reprocess(job, asset);
      return null;
    });
    taskMap.put(job.getJobId(), task);
    executor.execute(task);
    job.setFuture(task);
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
      // Image was stored — reprocess it
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
   * Returns an unmodifiable live view of all jobs (newest-last).
   */
  public List<IngestionJob> getAllJobs() {
    return Collections.unmodifiableList(jobs);
  }

  // -------------------------------------------------------------------------
  // Pipeline orchestration — runs on the single worker thread, never on UI thread
  // -------------------------------------------------------------------------

  public long countByStep(JobStep step) {
    return jobs.stream().filter(j -> j.getCurrentStep() == step).count();
  }

  /**
   * Dynamically changes the number of concurrent ingestion workers.
   * <p>The current executor is shut down gracefully (already-running and queued
   * jobs will still complete on it).  A new executor with the requested thread
   * count is installed for future submissions.  The value is clamped to [1, 6].
   *
   * @param parallelism desired number of concurrent workers
   */
  public synchronized void updateParallelism(int parallelism) {
    int clamped = Math.clamp(parallelism, 1, 6);
    ThreadPoolExecutor old = executor;
    // Create a fresh queue so the old executor can drain its queue without interference
    // from the new executor.  Tasks already in the old queue complete on old executor threads.
    // promote() references workQueue via a volatile read, so newly submitted tasks get the
    // new queue automatically.
    workQueue = new LinkedBlockingDeque<>();
    executor = buildThreadPoolExecutor(clamped, workQueue);
    old.shutdown(); // lets already-submitted jobs finish; no new work accepted
    logger().info("Ingestion parallelism updated to {}", clamped);
  }

  // -------------------------------------------------------------------------
  // SHA-256 helper — used for duplicate detection from raw byte array
  // -------------------------------------------------------------------------

  /**
   * Promotes a QUEUED job to the front of the work queue so it executes next.
   * <p>Implementation: the job's {@link FutureTask} is looked up in the task map, removed
   * from wherever it sits in the {@link LinkedBlockingDeque}, and re-inserted at the head.
   * If the task has already been picked up by a worker thread (i.e. is already running),
   * the deque no longer contains it and this method is a safe no-op.
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
    // Remove from current position and push to the front (next to execute).
    // workQueue may have been replaced by updateParallelism(); in that case remove() returns false
    // and we log accordingly — this is a safe, benign race condition.
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

  private void reprocess(IngestionJob job, ImageAsset asset) {
    UUID imageId = asset.getId();

    // ── Snapshot pre-reprocessing state for diff computation ──────────────────
    SemanticAnalysis previousAnalysis = persistenceService.findAnalysis(imageId).orElse(null);
    SensitivityAssessment previousAssessment = persistenceService.findAssessment(imageId).orElse(null);
    OcrResult previousOcr = persistenceService.findOcrResult(imageId).orElse(null);

    // Working variables assigned during the pipeline steps
    SemanticAnalysis semanticAnalysis = null;
    SensitivityAssessment assessment = null;
    OcrResult ocrResult = null;

    try {
      // Step 1: Load image from disk
      checkPauseOrCancelled();
      job.transition(JobStep.LOADING_IMAGE);
      java.nio.file.Path imagePath = imageStorageService.resolvePath(imageId);
      logger().info("[reprocess][{}] Loading image from {}", asset.getOriginalFilename(), imagePath);

      // Step 2: Vision analysis (LLM — slow)
      checkPauseOrCancelled();
      job.transition(JobStep.ANALYZING_VISION);
      VisionAnalysisResponse visionResponse =
          visionAnalysisService.analyzeImage(imagePath);
      logger().info("[reprocess][{}] Vision done (successful={})",
                    asset.getOriginalFilename(), visionResponse.successful());

      // Step 3: Semantic derivation — overwrites existing analysis
      checkPauseOrCancelled();
      job.transition(JobStep.DERIVING_SEMANTICS);
      semanticAnalysis = semanticDerivationService.derive(imageId, visionResponse);
      persistenceService.saveAnalysis(imageId, semanticAnalysis);

      // Step 4: Sensitivity assessment — re-evaluate using stored metadata/location
      checkPauseOrCancelled();
      job.transition(JobStep.ASSESSING_SENSITIVITY);
      ImageMetadataInfo metadataInfo = persistenceService.findMetadata(imageId).orElse(null);
      LocationSummary locationSummary = persistenceService.findLocation(imageId).orElse(null);
      assessment = sensitivityAssessmentService.assess(
          asset, metadataInfo, locationSummary, semanticAnalysis);
      persistenceService.saveAssessment(imageId, assessment);
      logger().info("[reprocess][{}] Risk: {} | Flags: {}",
                    asset.getOriginalFilename(), assessment.getRiskLevel(), assessment.getFlags());

      // Re-evaluate visibility — intentionally resets any prior manual approval/lock.
      // SAFE → auto-approved; other → locked (requires manual approval in Overview).
      if (assessment.getRiskLevel() == RiskLevel.SAFE) {
        persistenceService.approveImage(imageId);
        logger().info("[reprocess][{}] Auto-approved (SAFE)", asset.getOriginalFilename());
      } else {
        persistenceService.unapproveImage(imageId);
        logger().info("[reprocess][{}] Locked ({} risk)",
                      asset.getOriginalFilename(), assessment.getRiskLevel());
      }

      // Step 5: Embedding
      checkPauseOrCancelled();
      job.transition(JobStep.EMBEDDING);
      String embeddingText = buildEmbeddingText(semanticAnalysis);
      float[] vector = embeddingService.embed(embeddingText);

      // Step 6: Vector index — remove stale entry, insert fresh one
      if (vector != null && vector.length > 0) {
        checkPauseOrCancelled();
        job.transition(JobStep.INDEXING);
        vectorIndexService.remove(imageId);
        vectorIndexService.index(imageId, vector);
        VectorEntry entry = new VectorEntry(imageId, ollamaConfig.getEmbeddingModel(),
                                            vector.length, Instant.now());
        persistenceService.saveVectorEntry(imageId, entry);
        logger().info("[reprocess][{}] Indexed {} dims",
                      asset.getOriginalFilename(), vector.length);
      } else {
        logger().warn("[reprocess][{}] Empty embedding — vector index not updated",
                      asset.getOriginalFilename());
      }

      // Step 7: OCR (reprocess)
      checkPauseOrCancelled();
      job.transition(JobStep.OCR_TEXT);
      try {
        VisionAnalysisResponse ocrResponse =
            visionAnalysisService.analyzeImage(imagePath);
        String raw = ocrResponse.rawDescription();
        boolean hasText = raw != null && !raw.isBlank() && !raw.equals("NO_TEXT");
        ocrResult = new OcrResult(imageId, hasText ? raw : null, hasText,
                                  ollamaConfig.getVisionModel(), Instant.now());
        persistenceService.saveOcrResult(imageId, ocrResult);
      } catch (Exception e) {
        logger().warn("[reprocess][{}] OCR step failed (non-fatal): {}", asset.getOriginalFilename(), e.getMessage());
      }

      // ── Compute diff between previous and new derived state ───────────────
      if (previousAnalysis != null || semanticAnalysis != null) {
        job.setReprocessingDiff(buildReprocessingDiff(
            previousAnalysis, previousAssessment, previousOcr,
            semanticAnalysis, assessment, ocrResult));
      }

      // Step 8: Keyword indexing (BM25)
      checkPauseOrCancelled();
      job.transition(JobStep.KEYWORD_INDEXING);
      keywordIndexService.index(KeywordIndexDocument.from(
          asset, semanticAnalysis, locationSummary, ocrResult));

      job.complete(imageId);
      logger().info("[reprocess][{}] COMPLETED", asset.getOriginalFilename());

    } catch (InterruptedException e) {
      job.transition(JobStep.CANCELLED);
      logger().info("[reprocess][{}] Cancelled by interrupt", asset.getOriginalFilename());
    } catch (Exception e) {
      logger().error("[reprocess][{}] FAILED: {}", asset.getOriginalFilename(), e.getMessage(), e);
      job.fail(e.getMessage());
    }
  }

  /**
   * Reads the temp file into memory, deletes it immediately, then delegates to
   * {@link #process(IngestionJob, byte[])}.  The {@code finally} block guarantees
   * the temp file is removed even if the read fails.
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
      try {
        Files.deleteIfExists(tempFile);
      } catch (IOException ignored) {
        logger().warn("[{}] Could not delete temp file {}", job.getFilename(), tempFile);
      }
    }
    process(job, data);
  }

  private void process(IngestionJob job, byte[] data) {
    try {
      // ── Duplicate detection (hash check before any expensive work) ────────
      String sha256 = computeSha256(data);
      if (sha256 != null) {
        Optional<UUID> existingId = persistenceService.findImageIdByHash(sha256);
        if (existingId.isPresent()) {
          UUID eid = existingId.get();
          String existingFilename = persistenceService.findImage(eid)
              .map(ImageAsset::getOriginalFilename)
              .orElse(eid.toString());
          logger().info("[{}] Duplicate detected — same content already stored as '{}' ({})",
                        job.getFilename(), existingFilename, eid);
          job.markDuplicate(eid, existingFilename);
          return;
        }
      }

      // Step 1: Store image file
      checkPauseOrCancelled();
      job.transition(JobStep.STORING);
      StoredImage stored = imageStorageService.store(
          new ByteArrayInputStream(data), job.getFilename(), job.getMimeType());
      var imageId = stored.imageId();
      logger().info("[{}] Stored → {}", job.getFilename(), imageId);

      // Step 2: Extract EXIF / technical metadata
      checkPauseOrCancelled();
      job.transition(JobStep.EXTRACTING_METADATA);
      ExtractedMetadata extracted;
      try {
        extracted = metadataExtractionService.extract(
            imageStorageService.resolvePath(imageId));
      } catch (Exception e) {
        logger().warn("[{}] Metadata extraction failed: {}", job.getFilename(), e.getMessage());
        extracted = ExtractedMetadata.minimal(0, 0);
      }

      // Persist ImageAsset
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
          logger().info("[{}] Location: {}", job.getFilename(), locationSummary.toHumanReadable());
        } catch (Exception e) {
          logger().warn("[{}] Geocoding failed: {}", job.getFilename(), e.getMessage());
        }
      }

      // Step 3b: OCR text extraction
      checkPauseOrCancelled();
      job.transition(JobStep.OCR_TEXT);
      OcrResult ocrResult = null;
      try {
        VisionAnalysisResponse ocrResponse =
            visionAnalysisService.analyzeImage(imageStorageService.resolvePath(imageId));
        String raw = ocrResponse.rawDescription();
        boolean hasText = raw != null && !raw.isBlank() && !raw.equals("NO_TEXT");
        ocrResult = new OcrResult(imageId, hasText ? raw : null, hasText,
                                  ollamaConfig.getVisionModel(), Instant.now());
        persistenceService.saveOcrResult(imageId, ocrResult);
      } catch (Exception e) {
        logger().warn("[{}] OCR step failed (non-fatal): {}", job.getFilename(), e.getMessage());
      }

      // Step 4: Vision analysis (LLM — slow, sequential)
      checkPauseOrCancelled();
      job.transition(JobStep.ANALYZING_VISION);
      VisionAnalysisResponse visionResponse = visionAnalysisService.analyzeImage(
          imageStorageService.resolvePath(imageId));
      logger().info("[{}] Vision done (successful={})", job.getFilename(), visionResponse.successful());

      // Step 5: Semantic derivation (LLM — structured JSON)
      checkPauseOrCancelled();
      job.transition(JobStep.DERIVING_SEMANTICS);
      SemanticAnalysis semanticAnalysis = semanticDerivationService.derive(imageId, visionResponse);
      persistenceService.saveAnalysis(imageId, semanticAnalysis);

      // Step 6: Privacy / sensitivity assessment
      checkPauseOrCancelled();
      job.transition(JobStep.ASSESSING_SENSITIVITY);
      SensitivityAssessment assessment = sensitivityAssessmentService.assess(
          asset, metadataInfo, locationSummary, semanticAnalysis);
      persistenceService.saveAssessment(imageId, assessment);
      logger().info("[{}] Risk: {} | Flags: {}", job.getFilename(),
                    assessment.getRiskLevel(), assessment.getFlags());

      // SAFE images are auto-approved and immediately searchable.
      // REVIEW / SENSITIVE images require explicit user approval in the Overview.
      if (assessment.getRiskLevel() == RiskLevel.SAFE) {
        persistenceService.approveImage(imageId);
        logger().info("[{}] Auto-approved for search (SAFE risk)", job.getFilename());
      } else {
        logger().info("[{}] Locked — requires manual approval ({} risk)",
                      job.getFilename(), assessment.getRiskLevel());
      }

      // Step 7: Embedding
      checkPauseOrCancelled();
      job.transition(JobStep.EMBEDDING);
      String embeddingText = buildEmbeddingText(semanticAnalysis);
      float[] vector = embeddingService.embed(embeddingText);

      // Step 8: Vector index
      if (vector != null && vector.length > 0) {
        checkPauseOrCancelled();
        job.transition(JobStep.INDEXING);
        vectorIndexService.index(imageId, vector);
        VectorEntry entry = new VectorEntry(imageId, ollamaConfig.getEmbeddingModel(),
                                            vector.length, Instant.now());
        persistenceService.saveVectorEntry(imageId, entry);
        logger().info("[{}] Indexed {} dims", job.getFilename(), vector.length);
      } else {
        logger().warn("[{}] Empty embedding — skipping vector index", job.getFilename());
      }

      // Step 9: BM25 keyword index
      checkPauseOrCancelled();
      job.transition(JobStep.KEYWORD_INDEXING);
      keywordIndexService.index(KeywordIndexDocument.from(
          asset, semanticAnalysis, locationSummary, ocrResult));

      // Done
      job.complete(imageId);
      logger().info("[{}] Pipeline COMPLETED → imageId={}", job.getFilename(), imageId);

    } catch (InterruptedException e) {
      // Worker thread interrupted — user-requested cancellation.
      // Do NOT re-interrupt: the single worker thread must stay clean for the next job.
      job.transition(JobStep.CANCELLED); // no-op if cancel() was already called
      logger().info("[{}] Job cancelled by interrupt", job.getFilename());
    } catch (Exception e) {
      logger().error("[{}] Pipeline FAILED: {}", job.getFilename(), e.getMessage(), e);
      job.fail(e.getMessage());
    }
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

  private String buildEmbeddingText(SemanticAnalysis analysis) {
    StringBuilder sb = new StringBuilder();
    if (analysis.getSummary() != null) sb.append(analysis.getSummary());
    if (analysis.getTags() != null && !analysis.getTags().isEmpty())
      sb.append(" Tags: ").append(String.join(", ", analysis.getTags()));
    if (analysis.getSeasonHint() != null)
      sb.append(" Season: ").append(analysis.getSeasonHint().name());
    if (analysis.getSourceCategory() != null)
      sb.append(" Category: ").append(analysis.getSourceCategory().name());
    if (analysis.getSecondaryCategories() != null && !analysis.getSecondaryCategories().isEmpty()) {
      String secCats = analysis.getSecondaryCategories().stream()
          .map(Enum::name)
          .collect(java.util.stream.Collectors.joining(" "));
      sb.append(" ").append(secCats);
    }
    return sb.toString();
  }
}
