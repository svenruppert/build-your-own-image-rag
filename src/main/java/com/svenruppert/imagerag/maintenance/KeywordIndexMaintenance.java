package com.svenruppert.imagerag.maintenance;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.imagerag.domain.ImageAsset;
import com.svenruppert.imagerag.dto.KeywordIndexDocument;
import com.svenruppert.imagerag.persistence.PersistenceService;
import com.svenruppert.imagerag.service.KeywordIndexService;

/**
 * Restores and rebuilds the Lucene keyword (BM25) index from persisted analyses.
 */
public class KeywordIndexMaintenance
    implements HasLogger {

  private final PersistenceService persistenceService;
  private final KeywordIndexService keywordIndexService;

  public KeywordIndexMaintenance(PersistenceService persistenceService,
                                 KeywordIndexService keywordIndexService) {
    this.persistenceService = persistenceService;
    this.keywordIndexService = keywordIndexService;
  }

  /**
   * Indexes every active (non-archived) image. Used at startup.
   */
  public void restoreAll() {
    logger().info("Restoring keyword index from persisted analyses...");
    int indexed = 0;
    for (ImageAsset asset : persistenceService.findAllImages()) {
      if (reindex(asset)) {
        indexed++;
      }
    }
    logger().info("Keyword index restored: {} documents", indexed);
  }

  /**
   * Rebuilds the whole index from scratch. Blocks until done; call it on a background thread.
   */
  public void rebuild() {
    logger().info("Keyword index rebuild started");
    keywordIndexService.rebuildAll(persistenceService::findAllImages,
                                   persistenceService::findAnalysis,
                                   persistenceService::findLocation,
                                   persistenceService::findOcrResult);
    logger().info("Keyword index rebuild complete");
  }

  /**
   * (Re-)indexes a single image; idempotent.
   *
   * @return {@code true} if the document was written
   */
  public boolean reindex(ImageAsset asset) {
    try {
      keywordIndexService.index(KeywordIndexDocument.from(
          asset,
          persistenceService.findAnalysis(asset.getId()).orElse(null),
          persistenceService.findLocation(asset.getId()).orElse(null),
          persistenceService.findOcrResult(asset.getId()).orElse(null)));
      return true;
    } catch (RuntimeException e) {
      logger().warn("Could not index keyword document for image {}: {}",
                    asset.getId(), e.getMessage());
      return false;
    }
  }
}
