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
