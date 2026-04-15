package com.svenruppert.imagerag.service.impl;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.imagerag.domain.ImageAsset;
import com.svenruppert.imagerag.persistence.PersistenceService;
import com.svenruppert.imagerag.pipeline.IngestionPipeline;
import com.svenruppert.imagerag.service.ReprocessingService;

import java.util.UUID;

/**
 * Delegates reprocessing to the shared {@link IngestionPipeline}.
 *
 * <p>This makes reprocessing a first-class visible pipeline job: the job appears in the
 * Pipeline view with type {@link com.svenruppert.imagerag.pipeline.JobType#REPROCESS_EXISTING},
 * shows live step progress, can be cancelled, and contributes to the pipeline statistics.
 *
 * <p>Previously this class maintained its own hidden executor.  The pipeline executor is now
 * the single source of truth for all background processing, whether triggered by a fresh
 * upload or by a "Reprocess" action in the Overview or Detail views.
 */
public class ReprocessingServiceImpl
    implements ReprocessingService, HasLogger {

  private final IngestionPipeline pipeline;
  private final PersistenceService persistenceService;

  public ReprocessingServiceImpl(IngestionPipeline pipeline,
                                 PersistenceService persistenceService) {
    this.pipeline           = pipeline;
    this.persistenceService = persistenceService;
  }

  @Override
  public void reprocess(UUID imageId) {
    ImageAsset asset = persistenceService.findImage(imageId)
        .orElseThrow(() -> new IllegalArgumentException(
            "Cannot reprocess: image not found for id=" + imageId));
    logger().info("Reprocessing delegated to pipeline for imageId={} ({})",
                  imageId, asset.getOriginalFilename());
    pipeline.submitReprocess(asset);
  }
}
