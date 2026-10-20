package com.svenruppert.imagerag.service;

import com.svenruppert.imagerag.domain.CategoryMetadata;
import com.svenruppert.imagerag.domain.ImageAsset;
import com.svenruppert.imagerag.domain.LocationSummary;
import com.svenruppert.imagerag.domain.OcrResult;
import com.svenruppert.imagerag.domain.SemanticAnalysis;
import com.svenruppert.imagerag.domain.SensitivityAssessment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only access to the image inventory and its derived data for the UI layer.
 * <p>The {@link Optional} lookups model plain presence or absence: an image may simply
 * not have been analyzed, assessed or geo-located yet.
 */
public interface ImageCatalogService {

  Optional<ImageAsset> findImage(UUID imageId);

  /**
   * Returns all active (non-archived) images.
   */
  List<ImageAsset> findAllImages();

  List<ImageAsset> findArchivedImages();

  Optional<SemanticAnalysis> findAnalysis(UUID imageId);

  Optional<SensitivityAssessment> findAssessment(UUID imageId);

  Optional<LocationSummary> findLocation(UUID imageId);

  Optional<OcrResult> findOcrResult(UUID imageId);

  boolean isApproved(UUID imageId);

  List<UUID> findAllIndexedImageIds();

  int getRawVectorCount();

  Optional<float[]> findRawVector(UUID imageId);

  List<CategoryMetadata> findAllCategoryMetadata();
}
