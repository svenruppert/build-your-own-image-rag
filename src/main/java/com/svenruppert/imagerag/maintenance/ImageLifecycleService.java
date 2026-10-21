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
package com.svenruppert.imagerag.maintenance;

import com.svenruppert.imagerag.service.AuditService;
import com.svenruppert.imagerag.domain.enums.AuditAction;
import com.svenruppert.imagerag.domain.ImageAsset;
import com.svenruppert.functional.result.functions.CheckedExecutor;
import com.svenruppert.functional.result.Unit;
import com.svenruppert.functional.result.Result;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.imagerag.persistence.PersistenceService;
import com.svenruppert.imagerag.service.ImageStorageService;
import com.svenruppert.imagerag.service.KeywordIndexService;
import com.svenruppert.imagerag.service.PreviewService;
import com.svenruppert.imagerag.service.VectorIndexService;

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
  private final AuditService auditService;

  public ImageLifecycleService(PersistenceService persistenceService,
                               VectorIndexService vectorIndexService,
                               KeywordIndexService keywordIndexService,
                               KeywordIndexMaintenance keywordIndexMaintenance,
                               PreviewService previewService,
                               ImageStorageService imageStorageService,
                               AuditService auditService) {
    this.persistenceService = persistenceService;
    this.vectorIndexService = vectorIndexService;
    this.keywordIndexService = keywordIndexService;
    this.keywordIndexMaintenance = keywordIndexMaintenance;
    this.previewService = previewService;
    this.imageStorageService = imageStorageService;
    this.auditService = auditService;
  }

  /**
   * Completely removes an image from every data store and records it in the audit log.
   * Permanent and irreversible; the caller (UI) confirms with the user first.
   *
   * @param reason language-independent reason stored in the audit log
   * @return success, or a failure carrying the reason why the image could not be removed
   */
  public Result<Unit, String> delete(UUID imageId, String reason) {
    String filename = persistenceService.findImage(imageId)
        .map(ImageAsset::getOriginalFilename).orElse(null);
    CheckedExecutor indexRemoval = () -> {
      vectorIndexService.remove(imageId);
      keywordIndexService.remove(imageId);
      previewService.deletePreviewCache(imageId);
    };
    CheckedExecutor recordRemoval = () -> persistenceService.deleteImage(imageId);
    return indexRemoval.execute()
        .mapError(ImageLifecycleService::describe)
        // The stored record is only removed once the image file is gone.
        .flatMap(done -> imageStorageService.delete(imageId))
        .flatMap(done -> recordRemoval.execute().mapError(ImageLifecycleService::describe))
        .peek(done -> {
          auditService.log(AuditAction.PERMANENT_DELETE, imageId, filename, reason);
          logger().info("Permanently deleted image imageId={}", imageId);
        })
        .peekFailure(error -> logger().error("Permanent delete failed for {}: {}", imageId, error));
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
   *
   * @param reason language-independent reason stored in the audit log
   * @return success, or a failure carrying the reason why the image could not be restored
   */
  public Result<Unit, String> restore(UUID imageId, String reason) {
    CheckedExecutor restoration = () -> persistenceService.restoreImage(imageId);
    return restoration.execute()
        .mapError(ImageLifecycleService::describe)
        .peek(done -> {
          String filename = persistenceService.findImage(imageId)
              .map(ImageAsset::getOriginalFilename).orElse(null);
          auditService.log(AuditAction.RESTORE, imageId, filename, reason);
          persistenceService.findImage(imageId).ifPresent(asset -> {
            if (keywordIndexMaintenance.reindex(asset)) {
              logger().info("Re-indexed keyword entry for restored image imageId={}", imageId);
            }
          });
        })
        .peekFailure(error -> logger().error("Restore failed for {}: {}", imageId, error));
  }

  private static String describe(Throwable error) {
    return error.getClass().getSimpleName() + ": " + error.getMessage();
  }
}
