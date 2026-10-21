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
package com.svenruppert.imagerag.pipeline;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.functional.result.Result;
import com.svenruppert.imagerag.domain.*;
import com.svenruppert.imagerag.domain.enums.RecommendedStorageMode;
import com.svenruppert.imagerag.domain.enums.RiskLevel;
import com.svenruppert.imagerag.dto.KeywordIndexDocument;
import com.svenruppert.imagerag.dto.VisionAnalysisResponse;
import com.svenruppert.imagerag.ollama.OllamaConfig;
import com.svenruppert.imagerag.persistence.PersistenceService;
import com.svenruppert.imagerag.service.*;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

/**
 * The AI-heavy pipeline steps shared by uploads and reprocessing jobs: OCR, vision
 * analysis, semantic derivation, sensitivity assessment, visibility decision,
 * embedding and keyword indexing.
 * <p>Every step starts at a {@link Checkpoint} supplied by the {@link IngestionPipeline},
 * which fails fast on cancellation and blocks while the pipeline is paused.
 */
final class IngestionSteps
    implements HasLogger {

  /**
   * Note added to the sensitivity assessment when the vision analysis failed.
   */
  static final String VISION_FAILED_NOTE =
      "Vision analysis failed — content was not analyzed, manual review required";

  private final VisionAnalysisService visionAnalysisService;
  private final SemanticDerivationService semanticDerivationService;
  private final SensitivityAssessmentService sensitivityAssessmentService;
  private final EmbeddingService embeddingService;
  private final VectorIndexService vectorIndexService;
  private final PersistenceService persistenceService;
  private final KeywordIndexService keywordIndexService;
  private final OllamaConfig ollamaConfig;
  private final Checkpoint checkpoint;

  IngestionSteps(VisionAnalysisService visionAnalysisService,
                 SemanticDerivationService semanticDerivationService,
                 SensitivityAssessmentService sensitivityAssessmentService,
                 EmbeddingService embeddingService,
                 VectorIndexService vectorIndexService,
                 PersistenceService persistenceService,
                 KeywordIndexService keywordIndexService,
                 OllamaConfig ollamaConfig,
                 Checkpoint checkpoint) {
    this.visionAnalysisService = visionAnalysisService;
    this.semanticDerivationService = semanticDerivationService;
    this.sensitivityAssessmentService = sensitivityAssessmentService;
    this.embeddingService = embeddingService;
    this.vectorIndexService = vectorIndexService;
    this.persistenceService = persistenceService;
    this.keywordIndexService = keywordIndexService;
    this.ollamaConfig = ollamaConfig;
    this.checkpoint = checkpoint;
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

  static ReprocessingDiff buildReprocessingDiff(SemanticAnalysis prevAnalysis,
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

  /**
   * OCR step with the dedicated OCR prompt. On failure no new result is stored and
   * {@code fallback} is returned (null for new images, the previous result on reprocess).
   */
  OcrResult runOcr(IngestionJob job, String label, UUID imageId, Path imagePath,
                   OcrResult fallback)
      throws InterruptedException {
    checkpoint.check();
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
  AnalysisOutcome runAnalysis(IngestionJob job, String label, ImageAsset asset,
                              Path imagePath, ImageMetadataInfo metadataInfo,
                              LocationSummary locationSummary)
      throws InterruptedException {
    UUID imageId = asset.getId();

    checkpoint.check();
    job.transition(JobStep.ANALYZING_VISION);
    VisionAnalysisResponse visionResponse = visionAnalysisService.analyzeImage(imagePath);
    logger().info("[{}] Vision done (successful={})", label, visionResponse.successful());

    checkpoint.check();
    job.transition(JobStep.DERIVING_SEMANTICS);
    SemanticAnalysis semanticAnalysis = semanticDerivationService.derive(imageId, visionResponse);
    persistenceService.saveAnalysis(imageId, semanticAnalysis);

    checkpoint.check();
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
  void runEmbedding(IngestionJob job, String label, UUID imageId, SemanticAnalysis analysis)
      throws InterruptedException {
    checkpoint.check();
    job.transition(JobStep.EMBEDDING);
    Result<float[], String> embedding = embeddingService.embed(EmbeddingTextBuilder.build(analysis));
    if (!(embedding instanceof Result.Success<float[], String>(float[] vector))) {
      embedding.peekFailure(reason -> logger().warn(
          "[{}] No embedding ({}) — vector index not updated", label, reason));
      return;
    }
    checkpoint.check();
    job.transition(JobStep.INDEXING);
    vectorIndexService.index(imageId, vector);
    persistenceService.saveVectorEntry(imageId, new VectorEntry(
        imageId, ollamaConfig.getEmbeddingModel(), vector.length, Instant.now()));
    logger().info("[{}] Indexed {} dims", label, vector.length);
  }

  void runKeywordIndexing(IngestionJob job, ImageAsset asset, SemanticAnalysis analysis,
                          LocationSummary locationSummary, OcrResult ocrResult)
      throws InterruptedException {
    checkpoint.check();
    job.transition(JobStep.KEYWORD_INDEXING);
    keywordIndexService.index(KeywordIndexDocument.from(asset, analysis, locationSummary, ocrResult));
  }

  /**
   * Step boundary supplied by the pipeline: throws on cancellation, blocks while paused.
   */
  @FunctionalInterface
  interface Checkpoint {
    void check()
        throws InterruptedException;
  }

  record AnalysisOutcome(SemanticAnalysis analysis, SensitivityAssessment assessment) {
  }
}
