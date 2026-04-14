package com.svenruppert.imagerag.service.impl;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.imagerag.domain.*;
import com.svenruppert.imagerag.dto.ExtractedMetadata;
import com.svenruppert.imagerag.dto.StoredImage;
import com.svenruppert.imagerag.dto.VisionAnalysisResponse;
import com.svenruppert.imagerag.persistence.PersistenceService;
import com.svenruppert.imagerag.service.*;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.UUID;

public class ImageIngestionServiceImpl
    implements ImageIngestionService, HasLogger {

  private final ImageStorageService imageStorageService;
  private final MetadataExtractionService metadataExtractionService;
  private final ReverseGeocodingService reverseGeocodingService;
  private final VisionAnalysisService visionAnalysisService;
  private final SemanticDerivationService semanticDerivationService;
  private final SensitivityAssessmentService sensitivityAssessmentService;
  private final EmbeddingService embeddingService;
  private final VectorIndexService vectorIndexService;
  private final PersistenceService persistenceService;

  public ImageIngestionServiceImpl(ImageStorageService imageStorageService,
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

  @Override
  public UUID ingest(InputStream inputStream, String filename, String mimeType)
      throws IOException {
    logger().info("Starting ingestion for: {}", filename);

    // 1. Store image file
    StoredImage stored = imageStorageService.store(inputStream, filename, mimeType);
    UUID imageId = stored.imageId();
    logger().info("Image stored with id: {}", imageId);

    // 2. Extract technical metadata
    ExtractedMetadata extracted;
    try {
      extracted = metadataExtractionService.extract(imageStorageService.resolvePath(imageId));
    } catch (IOException e) {
      logger().warn("Metadata extraction failed for {}: {}", filename, e.getMessage());
      extracted = ExtractedMetadata.minimal(0, 0);
    }

    // 3. Build and persist ImageAsset
    ImageAsset asset = new ImageAsset(
        imageId,
        stored.originalFilename(),
        stored.storedFilename(),
        mimeType,
        stored.fileSize(),
        extracted.width(),
        extracted.height(),
        stored.sha256(),
        Instant.now(),
        true,
        extracted.exifPresent(),
        extracted.gpsPresent()
    );
    persistenceService.saveImage(asset);

    // 4. Build and persist ImageMetadataInfo
    ImageMetadataInfo metadataInfo = new ImageMetadataInfo(
        extracted.captureTimestamp(),
        extracted.cameraModel(),
        extracted.latitude(),
        extracted.longitude()
    );
    persistenceService.saveMetadata(imageId, metadataInfo);

    // 5. Reverse geocoding (if GPS available)
    LocationSummary locationSummary = null;
    if (extracted.gpsPresent() && extracted.latitude() != null && extracted.longitude() != null) {
      try {
        locationSummary = reverseGeocodingService.reverseGeocode(
            extracted.latitude(), extracted.longitude());
        persistenceService.saveLocation(imageId, locationSummary);
        logger().info("Location resolved: {}", locationSummary.toHumanReadable());
      } catch (Exception e) {
        logger().warn("Reverse geocoding failed: {}", e.getMessage());
      }
    }

    // 6. Vision analysis
    VisionAnalysisResponse visionResponse = visionAnalysisService.analyzeImage(
        imageStorageService.resolvePath(imageId));

    // 7. Semantic derivation
    SemanticAnalysis semanticAnalysis = semanticDerivationService.derive(imageId, visionResponse);
    persistenceService.saveAnalysis(imageId, semanticAnalysis);

    // 8. Sensitivity assessment
    SensitivityAssessment assessment = sensitivityAssessmentService.assess(
        asset, metadataInfo, locationSummary, semanticAnalysis);
    persistenceService.saveAssessment(imageId, assessment);
    logger().info("Sensitivity: {} | Flags: {}", assessment.getRiskLevel(), assessment.getFlags());

    // 9. Embedding + vector indexing
    String embeddingText = buildEmbeddingText(semanticAnalysis);
    float[] vector = embeddingService.embed(embeddingText);

    if (vector != null && vector.length > 0) {
      vectorIndexService.index(imageId, vector);

      VectorEntry vectorEntry = new VectorEntry(imageId, "nomic-embed-text", vector.length, Instant.now());
      persistenceService.saveVectorEntry(imageId, vectorEntry);
      logger().info("Indexed embedding ({} dims) for image {}", vector.length, imageId);
    } else {
      logger().warn("Embedding failed — image {} will not be searchable via vector search", imageId);
    }

    logger().info("Ingestion complete for {} (id={})", filename, imageId);
    return imageId;
  }

  private String buildEmbeddingText(SemanticAnalysis analysis) {
    StringBuilder sb = new StringBuilder();
    if (analysis.getSummary() != null) sb.append(analysis.getSummary());
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
