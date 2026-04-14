package com.svenruppert.imagerag.pipeline;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.imagerag.domain.*;
import com.svenruppert.imagerag.domain.enums.RiskLevel;
import com.svenruppert.imagerag.dto.ExtractedMetadata;
import com.svenruppert.imagerag.dto.StoredImage;
import com.svenruppert.imagerag.dto.VisionAnalysisResponse;
import com.svenruppert.imagerag.persistence.PersistenceService;
import com.svenruppert.imagerag.service.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Sequential async ingestion pipeline.
 *
 * <p>Images are submitted via {@link #submit} and processed <em>one at a time</em>
 * on a single virtual-thread-backed executor, ensuring the LLM is never overwhelmed
 * by concurrent requests. Each job can be cancelled via {@link #cancel}.
 *
 * <p>All submitted jobs are kept in a {@link CopyOnWriteArrayList} so the
 * {@link com.svenruppert.flow.views.pipeline.PipelineView} can read them
 * safely without locks at any time.
 */
public class IngestionPipeline
    implements HasLogger {

  private final List<IngestionJob> jobs = new CopyOnWriteArrayList<>();

  /**
   * Worker executor — size reflects the current ingestion parallelism setting.
   * Initially sequential (1 thread). Call {@link #updateParallelism} to resize.
   * Declared {@code volatile} so {@link #submit} always sees the latest executor.
   */
  private volatile ExecutorService executor = buildExecutor(1);

  private final ImageStorageService imageStorageService;
  private final MetadataExtractionService metadataExtractionService;
  private final ReverseGeocodingService reverseGeocodingService;
  private final VisionAnalysisService visionAnalysisService;
  private final SemanticDerivationService semanticDerivationService;
  private final SensitivityAssessmentService sensitivityAssessmentService;
  private final EmbeddingService embeddingService;
  private final VectorIndexService vectorIndexService;
  private final PersistenceService persistenceService;

  public IngestionPipeline(ImageStorageService imageStorageService,
                           MetadataExtractionService metadataExtractionService,
                           ReverseGeocodingService reverseGeocodingService,
                           VisionAnalysisService visionAnalysisService,
                           SemanticDerivationService semanticDerivationService,
                           SensitivityAssessmentService sensitivityAssessmentService,
                           EmbeddingService embeddingService,
                           VectorIndexService vectorIndexService,
                           PersistenceService persistenceService) {
    this.imageStorageService = imageStorageService;
    this.metadataExtractionService = metadataExtractionService;
    this.reverseGeocodingService = reverseGeocodingService;
    this.visionAnalysisService = visionAnalysisService;
    this.semanticDerivationService = semanticDerivationService;
    this.sensitivityAssessmentService = sensitivityAssessmentService;
    this.embeddingService = embeddingService;
    this.vectorIndexService = vectorIndexService;
    this.persistenceService = persistenceService;
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
    Future<?> future = executor.submit(() -> process(job, data));
    job.setFuture(future);
    logger().info("Job queued: {} ({})", filename, job.getJobId());
    return job;
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

  public long countByStep(JobStep step) {
    return jobs.stream().filter(j -> j.getCurrentStep() == step).count();
  }

  /**
   * Dynamically changes the number of concurrent ingestion workers.
   *
   * <p>The current executor is shut down gracefully (already-running and queued
   * jobs will still complete on it).  A new executor with the requested thread
   * count is installed for future submissions.  The value is clamped to [1, 6].
   *
   * @param parallelism desired number of concurrent workers
   */
  public synchronized void updateParallelism(int parallelism) {
    int clamped = Math.max(1, Math.min(6, parallelism));
    ExecutorService old = executor;
    executor = buildExecutor(clamped);
    old.shutdown(); // lets already-submitted jobs finish; no new work accepted
    logger().info("Ingestion parallelism updated to {}", clamped);
  }

  // -------------------------------------------------------------------------
  // Pipeline orchestration — runs on the single worker thread, never on UI thread
  // -------------------------------------------------------------------------

  public void shutdown() {
    executor.shutdownNow();
  }

  private static ExecutorService buildExecutor(int parallelism) {
    if (parallelism <= 1) {
      return Executors.newSingleThreadExecutor(
          Thread.ofVirtual().name("ingestion-pipeline").factory());
    }
    return Executors.newFixedThreadPool(
        parallelism, Thread.ofVirtual().name("ingestion-", 0).factory());
  }

  // -------------------------------------------------------------------------
  // SHA-256 helper — used for duplicate detection from raw byte array
  // -------------------------------------------------------------------------

  private static String computeSha256(byte[] data) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(data);
      return HexFormat.of().formatHex(hash);
    } catch (Exception e) {
      return null; // hash unavailable; duplicate check skipped
    }
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
      checkCancelled();
      job.transition(JobStep.STORING);
      StoredImage stored = imageStorageService.store(
          new ByteArrayInputStream(data), job.getFilename(), job.getMimeType());
      var imageId = stored.imageId();
      logger().info("[{}] Stored → {}", job.getFilename(), imageId);

      // Step 2: Extract EXIF / technical metadata
      checkCancelled();
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
        checkCancelled();
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

      // Step 4: Vision analysis (LLM — slow, sequential)
      checkCancelled();
      job.transition(JobStep.ANALYZING_VISION);
      VisionAnalysisResponse visionResponse = visionAnalysisService.analyzeImage(
          imageStorageService.resolvePath(imageId));
      logger().info("[{}] Vision done (successful={})", job.getFilename(), visionResponse.successful());

      // Step 5: Semantic derivation (LLM — structured JSON)
      checkCancelled();
      job.transition(JobStep.DERIVING_SEMANTICS);
      SemanticAnalysis semanticAnalysis = semanticDerivationService.derive(imageId, visionResponse);
      persistenceService.saveAnalysis(imageId, semanticAnalysis);

      // Step 6: Privacy / sensitivity assessment
      checkCancelled();
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
      checkCancelled();
      job.transition(JobStep.EMBEDDING);
      String embeddingText = buildEmbeddingText(semanticAnalysis);
      float[] vector = embeddingService.embed(embeddingText);

      // Step 8: Vector index
      if (vector != null && vector.length > 0) {
        checkCancelled();
        job.transition(JobStep.INDEXING);
        vectorIndexService.index(imageId, vector);
        VectorEntry entry = new VectorEntry(imageId, "nomic-embed-text",
                                            vector.length, Instant.now());
        persistenceService.saveVectorEntry(imageId, entry);
        logger().info("[{}] Indexed {} dims", job.getFilename(), vector.length);
      } else {
        logger().warn("[{}] Empty embedding — skipping vector index", job.getFilename());
      }

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

  private String buildEmbeddingText(SemanticAnalysis analysis) {
    StringBuilder sb = new StringBuilder();
    if (analysis.getSummary() != null) sb.append(analysis.getSummary());
    if (analysis.getTags() != null && !analysis.getTags().isEmpty())
      sb.append(" Tags: ").append(String.join(", ", analysis.getTags()));
    if (analysis.getSeasonHint() != null)
      sb.append(" Season: ").append(analysis.getSeasonHint().name());
    if (analysis.getSourceCategory() != null)
      sb.append(" Category: ").append(analysis.getSourceCategory().name());
    return sb.toString();
  }
}
