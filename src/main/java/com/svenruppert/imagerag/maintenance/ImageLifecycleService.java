package com.svenruppert.imagerag.maintenance;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.imagerag.persistence.PersistenceService;
import com.svenruppert.imagerag.service.ImageStorageService;
import com.svenruppert.imagerag.service.KeywordIndexService;
import com.svenruppert.imagerag.service.PreviewService;
import com.svenruppert.imagerag.service.VectorIndexService;

import java.io.IOException;
import java.util.UUID;

/**
 * Orchestrates permanent deletion and restore of images across all stores
 * (vector index, keyword index, image file, preview cache, EclipseStore).
 */
public class ImageLifecycleService
    implements HasLogger {

  private final PersistenceService persistenceService;
  private final VectorIndexService vectorIndexService;
  private final KeywordIndexService keywordIndexService;
  private final KeywordIndexMaintenance keywordIndexMaintenance;
  private final PreviewService previewService;
  private final ImageStorageService imageStorageService;

  public ImageLifecycleService(PersistenceService persistenceService,
                               VectorIndexService vectorIndexService,
                               KeywordIndexService keywordIndexService,
                               KeywordIndexMaintenance keywordIndexMaintenance,
                               PreviewService previewService,
                               ImageStorageService imageStorageService) {
    this.persistenceService = persistenceService;
    this.vectorIndexService = vectorIndexService;
    this.keywordIndexService = keywordIndexService;
    this.keywordIndexMaintenance = keywordIndexMaintenance;
    this.previewService = previewService;
    this.imageStorageService = imageStorageService;
  }

  /**
   * Completely removes an image from every data store. Permanent and irreversible;
   * the caller (UI) confirms with the user first.
   */
  public void delete(UUID imageId)
      throws IOException {
    vectorIndexService.remove(imageId);
    keywordIndexService.remove(imageId);
    previewService.deletePreviewCache(imageId);
    imageStorageService.delete(imageId);
    persistenceService.deleteImage(imageId);
    logger().info("Permanently deleted image imageId={}", imageId);
  }

  /**
   * Restores a previously archived (soft-deleted) image to the active dataset.
   * <ol>
   *   <li>Clears the archive flags and re-approves the image if its stored sensitivity
   *       assessment is SAFE; otherwise it stays locked.</li>
   *   <li>Re-adds the image to the keyword index, which excludes archived images after a
   *       restart. Idempotent.</li>
   * </ol>
   * <p>The vector index entry survives archive/restore cycles because soft delete does
   * not remove it.
   */
  public void restore(UUID imageId) {
    persistenceService.restoreImage(imageId);
    persistenceService.findImage(imageId).ifPresent(asset -> {
      if (keywordIndexMaintenance.reindex(asset)) {
        logger().info("Re-indexed keyword entry for restored image imageId={}", imageId);
      }
    });
  }
}
