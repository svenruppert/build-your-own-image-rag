package com.svenruppert.imagerag.service.impl;

import com.svenruppert.imagerag.domain.CategoryMetadata;
import com.svenruppert.imagerag.domain.ImageAsset;
import com.svenruppert.imagerag.domain.LocationSummary;
import com.svenruppert.imagerag.domain.OcrResult;
import com.svenruppert.imagerag.domain.SemanticAnalysis;
import com.svenruppert.imagerag.domain.SensitivityAssessment;
import com.svenruppert.imagerag.persistence.PersistenceService;
import com.svenruppert.imagerag.service.ImageCatalogService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link ImageCatalogService} backed by the EclipseStore {@link PersistenceService}.
 */
public class ImageCatalogServiceImpl
    implements ImageCatalogService {

  private final PersistenceService persistenceService;

  public ImageCatalogServiceImpl(PersistenceService persistenceService) {
    this.persistenceService = persistenceService;
  }

  @Override
  public Optional<ImageAsset> findImage(UUID imageId) {
    return persistenceService.findImage(imageId);
  }

  @Override
  public List<ImageAsset> findAllImages() {
    return persistenceService.findAllImages();
  }

  @Override
  public List<ImageAsset> findArchivedImages() {
    return persistenceService.findArchivedImages();
  }

  @Override
  public Optional<SemanticAnalysis> findAnalysis(UUID imageId) {
    return persistenceService.findAnalysis(imageId);
  }

  @Override
  public Optional<SensitivityAssessment> findAssessment(UUID imageId) {
    return persistenceService.findAssessment(imageId);
  }

  @Override
  public Optional<LocationSummary> findLocation(UUID imageId) {
    return persistenceService.findLocation(imageId);
  }

  @Override
  public Optional<OcrResult> findOcrResult(UUID imageId) {
    return persistenceService.findOcrResult(imageId);
  }

  @Override
  public boolean isApproved(UUID imageId) {
    return persistenceService.isApproved(imageId);
  }

  @Override
  public List<UUID> findAllIndexedImageIds() {
    return persistenceService.findAllIndexedImageIds();
  }

  @Override
  public int getRawVectorCount() {
    return persistenceService.getRawVectorCount();
  }

  @Override
  public Optional<float[]> findRawVector(UUID imageId) {
    return persistenceService.findRawVector(imageId);
  }

  @Override
  public List<CategoryMetadata> findAllCategoryMetadata() {
    return persistenceService.findAllCategoryMetadata();
  }
}
