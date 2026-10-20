package com.svenruppert.imagerag.maintenance;

import com.svenruppert.functional.result.Result;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.imagerag.domain.VectorEntry;
import com.svenruppert.imagerag.domain.enums.VectorBackendType;
import com.svenruppert.imagerag.ollama.OllamaConfig;
import com.svenruppert.imagerag.persistence.PersistenceService;
import com.svenruppert.imagerag.pipeline.EmbeddingTextBuilder;
import com.svenruppert.imagerag.service.EmbeddingService;
import com.svenruppert.imagerag.service.VectorIndexService;
import com.svenruppert.imagerag.service.impl.EclipseStoreGigaMapJVectorBackend;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Startup preparation and explicit rebuilds of the vector index.
 * <p>All paths re-embed through {@link #reembedAll}, so every rebuild uses the same
 * embedding text ({@link EmbeddingTextBuilder}) as ingestion.
 */
public class VectorIndexMaintenance
    implements HasLogger {

  private final PersistenceService persistenceService;
  private final EmbeddingService embeddingService;
  private final VectorIndexService vectorIndexService;
  private final OllamaConfig ollamaConfig;
  private final VectorBackendType backendType;

  public VectorIndexMaintenance(PersistenceService persistenceService,
                                EmbeddingService embeddingService,
                                VectorIndexService vectorIndexService,
                                OllamaConfig ollamaConfig,
                                VectorBackendType backendType) {
    this.persistenceService = persistenceService;
    this.embeddingService = embeddingService;
    this.vectorIndexService = vectorIndexService;
    this.ollamaConfig = ollamaConfig;
    this.backendType = backendType;
  }

  /**
   * Brings the vector index into a usable state at startup.
   * <ul>
   *   <li>{@link VectorBackendType#GIGAMAP_JVECTOR}: migrates by re-embedding on the
   *       first run with this backend, re-embeds when the configured embedding model
   *       changed, then builds the HNSW index from the persisted raw vectors.</li>
   *   <li>{@link VectorBackendType#IN_MEMORY}: re-embeds all analysed images, because the
   *       in-memory index does not survive a restart.</li>
   * </ul>
   */
  public void prepareAtStartup() {
    if (vectorIndexService instanceof EclipseStoreGigaMapJVectorBackend jvector) {
      boolean hasIndexedImages = !persistenceService.findAllIndexedImageIds().isEmpty();
      if (jvector.isEmpty() && hasIndexedImages) {
        logger().info("GIGAMAP_JVECTOR backend: no persisted raw vectors found "
                          + "— running one-time migration by re-embedding existing analyses.");
        int migrated = reembedAll(persistenceService::saveRawVector, true);
        logger().info("Migration complete: {} vectors written to EclipseStore", migrated);
      } else if (hasIndexedImages && hasModelMismatch()) {
        logger().warn("EMBEDDING MODEL MISMATCH: configured model is '{}' but some persisted "
                          + "vectors were created by a different model. Re-embedding all images "
                          + "before building the HNSW index...", ollamaConfig.getEmbeddingModel());
        int migrated = reembedAll(persistenceService::saveRawVector, true);
        logger().info("Stale-vector migration complete: {} images re-embedded", migrated);
      }
      jvector.initialize();
    } else {
      logger().info("Restoring in-memory vector index...");
      int restored = reembedAll(vectorIndexService::index, false);
      logger().info("Restored {} vector entries into in-memory index", restored);
    }
  }

  /**
   * Re-embeds every analysed image and rebuilds the active vector index.
   * Blocks until done; call it on a background thread.
   */
  public void rebuild() {
    logger().info("Vector index rebuild started (backend={})", backendType);
    int rebuilt;
    if (vectorIndexService instanceof EclipseStoreGigaMapJVectorBackend jvector) {
      // Persist raw vectors only; the HNSW index is built once at the end.
      rebuilt = reembedAll(persistenceService::saveRawVector, true);
      jvector.initialize();
    } else {
      rebuilt = reembedAll(vectorIndexService::index, true);
    }
    logger().info("Vector index rebuild complete — {} images indexed", rebuilt);
  }

  private boolean hasModelMismatch() {
    String configuredModel = ollamaConfig.getEmbeddingModel();
    boolean mismatch = persistenceService.findAllIndexedImageIds().stream()
        .anyMatch(id -> persistenceService.findVectorEntry(id)
            .map(e -> !Objects.equals(configuredModel, e.getEmbeddingModel()))
            .orElse(false));
    if (!mismatch) {
      logger().info("Embedding model '{}' matches all stored vectors — no migration needed.",
                    configuredModel);
    }
    return mismatch;
  }

  /**
   * Re-embeds the analysis text of every indexed image and hands each non-empty vector
   * to {@code sink}.
   *
   * @param sink              receives image id and vector
   * @param recordVectorEntry whether to update the {@link VectorEntry} metadata (model,
   *                          dimension, timestamp) for each re-embedded image
   * @return number of images that received a vector
   */
  private int reembedAll(BiConsumer<UUID, float[]> sink, boolean recordVectorEntry) {
    String model = ollamaConfig.getEmbeddingModel();
    var imageIds = persistenceService.findAllIndexedImageIds();
    int count = 0;
    for (UUID imageId : imageIds) {
      var analysis = persistenceService.findAnalysis(imageId);
      if (analysis.isEmpty()) {
        continue;
      }
      try {
        Result<float[], String> embedding =
            embeddingService.embed(EmbeddingTextBuilder.build(analysis.get()));
        if (!(embedding instanceof Result.Success<float[], String>(float[] vector))) {
          embedding.peekFailure(reason -> logger().debug(
              "Skipping re-embedding of image {}: {}", imageId, reason));
          continue;
        }
        sink.accept(imageId, vector);
        if (recordVectorEntry) {
          persistenceService.saveVectorEntry(
              imageId, new VectorEntry(imageId, model, vector.length, Instant.now()));
        }
        count++;
      } catch (RuntimeException e) {
        logger().warn("Could not re-embed image {}: {}", imageId, e.getMessage());
      }
    }
    logger().debug("Re-embedded {}/{} images", count, imageIds.size());
    return count;
  }
}
