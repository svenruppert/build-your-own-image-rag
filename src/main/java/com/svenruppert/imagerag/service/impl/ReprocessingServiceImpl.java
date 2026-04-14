package com.svenruppert.imagerag.service.impl;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.imagerag.domain.*;
import com.svenruppert.imagerag.domain.enums.RiskLevel;
import com.svenruppert.imagerag.dto.VisionAnalysisResponse;
import com.svenruppert.imagerag.persistence.PersistenceService;
import com.svenruppert.imagerag.service.*;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Asynchronous reprocessing of existing image assets.
 *
 * <p>Re-runs the AI-heavy pipeline stages on a file that is already stored on disk:
 * <ol>
 *   <li>Vision analysis (LLM)</li>
 *   <li>Semantic derivation (LLM)</li>
 *   <li>Sensitivity assessment (rule-based)</li>
 *   <li>Embedding computation</li>
 *   <li>Vector index update (remove stale entry, insert fresh one)</li>
 * </ol>
 *
 * <p>The existing {@link ImageAsset} record (filename, size, hash, EXIF flags, etc.)
 * is preserved.  Only derived data is overwritten.  Auto-approval is re-evaluated:
 * images that are SAFE after reprocessing are automatically approved; others are
 * locked and require manual approval in the Overview view.
 *
 * <p>Each call to {@link #reprocess} submits work to a small dedicated virtual-thread
 * executor so the calling Vaadin UI thread is never blocked.
 */
public class ReprocessingServiceImpl
    implements ReprocessingService, HasLogger {

  private final ExecutorService executor = Executors.newFixedThreadPool(
      2, Thread.ofVirtual().name("reprocess-", 0).factory());

  private final ImageStorageService        imageStorageService;
  private final VisionAnalysisService      visionAnalysisService;
  private final SemanticDerivationService  semanticDerivationService;
  private final SensitivityAssessmentService sensitivityAssessmentService;
  private final EmbeddingService           embeddingService;
  private final VectorIndexService         vectorIndexService;
  private final PersistenceService         persistenceService;

  public ReprocessingServiceImpl(ImageStorageService imageStorageService,
                                 VisionAnalysisService visionAnalysisService,
                                 SemanticDerivationService semanticDerivationService,
                                 SensitivityAssessmentService sensitivityAssessmentService,
                                 EmbeddingService embeddingService,
                                 VectorIndexService vectorIndexService,
                                 PersistenceService persistenceService) {
    this.imageStorageService       = imageStorageService;
    this.visionAnalysisService     = visionAnalysisService;
    this.semanticDerivationService = semanticDerivationService;
    this.sensitivityAssessmentService = sensitivityAssessmentService;
    this.embeddingService          = embeddingService;
    this.vectorIndexService        = vectorIndexService;
    this.persistenceService        = persistenceService;
  }

  @Override
  public void reprocess(UUID imageId) {
    ImageAsset asset = persistenceService.findImage(imageId)
        .orElseThrow(() -> new IllegalArgumentException(
            "Cannot reprocess: image not found for id=" + imageId));
    logger().info("Reprocessing queued for imageId={} ({})", imageId, asset.getOriginalFilename());
    executor.submit(() -> doReprocess(asset));
  }

  private void doReprocess(ImageAsset asset) {
    UUID imageId = asset.getId();
    try {
      logger().info("[reprocess] Starting for {} ({})", asset.getOriginalFilename(), imageId);

      // Locate the image file on disk
      Path imagePath = imageStorageService.resolvePath(imageId);

      // Step 1: Vision analysis
      logger().info("[reprocess] Vision analysis...");
      VisionAnalysisResponse visionResponse = visionAnalysisService.analyzeImage(imagePath);

      // Step 2: Semantic derivation — overwrites existing analysis
      logger().info("[reprocess] Semantic derivation...");
      SemanticAnalysis semanticAnalysis = semanticDerivationService.derive(imageId, visionResponse);
      persistenceService.saveAnalysis(imageId, semanticAnalysis);

      // Step 3: Sensitivity assessment — re-evaluate using stored metadata/location
      logger().info("[reprocess] Sensitivity assessment...");
      ImageMetadataInfo metadataInfo = persistenceService.findMetadata(imageId).orElse(null);
      LocationSummary   locationSummary = persistenceService.findLocation(imageId).orElse(null);
      SensitivityAssessment assessment = sensitivityAssessmentService.assess(
          asset, metadataInfo, locationSummary, semanticAnalysis);
      persistenceService.saveAssessment(imageId, assessment);
      logger().info("[reprocess] Risk: {} | Flags: {}", assessment.getRiskLevel(), assessment.getFlags());

      // Re-evaluate visibility based on the fresh assessment.
      //
      // Intentional design: reprocessing always resets the visibility to the state
      // that the new risk level dictates — it never preserves a previous manual
      // approval or lock decision.  This avoids stale approvals where an image was
      // SAFE when first ingested but the AI model now classifies it as SENSITIVE
      // after a re-run.
      //
      //   SAFE  → auto-approved  (immediately visible in search results)
      //   other → locked         (requires explicit manual approval in the Overview)
      if (assessment.getRiskLevel() == RiskLevel.SAFE) {
        persistenceService.approveImage(imageId);
        logger().info("[reprocess] Auto-approved (SAFE risk)");
      } else {
        persistenceService.unapproveImage(imageId);
        logger().info("[reprocess] Locked — requires manual approval ({} risk)",
                      assessment.getRiskLevel());
      }

      // Step 4: Embedding
      logger().info("[reprocess] Computing embedding...");
      String embeddingText = buildEmbeddingText(semanticAnalysis);
      float[] vector = embeddingService.embed(embeddingText);

      // Step 5: Vector index — remove stale entry, insert fresh one
      if (vector != null && vector.length > 0) {
        vectorIndexService.remove(imageId);   // drop stale vector
        vectorIndexService.index(imageId, vector);
        VectorEntry entry = new VectorEntry(imageId, "nomic-embed-text",
                                            vector.length, Instant.now());
        persistenceService.saveVectorEntry(imageId, entry);
        logger().info("[reprocess] Indexed {} dims", vector.length);
      } else {
        logger().warn("[reprocess] Empty embedding — vector index not updated");
      }

      logger().info("[reprocess] COMPLETED for {} ({})", asset.getOriginalFilename(), imageId);

    } catch (Exception e) {
      logger().error("[reprocess] FAILED for {} ({}): {}",
                     asset.getOriginalFilename(), imageId, e.getMessage(), e);
    }
  }

  private String buildEmbeddingText(SemanticAnalysis analysis) {
    StringBuilder sb = new StringBuilder();
    if (analysis.getSummary() != null) {
      sb.append(analysis.getSummary());
    }
    if (analysis.getTags() != null && !analysis.getTags().isEmpty()) {
      sb.append(" Tags: ").append(String.join(", ", analysis.getTags()));
    }
    if (analysis.getSeasonHint() != null) {
      sb.append(" Season: ").append(analysis.getSeasonHint().name());
    }
    if (analysis.getSourceCategory() != null) {
      sb.append(" Category: ").append(analysis.getSourceCategory().name());
    }
    return sb.toString();
  }
}
