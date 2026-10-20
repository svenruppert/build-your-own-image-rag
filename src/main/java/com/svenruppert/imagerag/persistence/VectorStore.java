package com.svenruppert.imagerag.persistence;

import com.svenruppert.imagerag.domain.VectorEntry;

import java.util.*;

/**
 * Vector metadata entries and the raw embedding vectors.
 * <p>The raw vectors are the durable source of truth for the {@code GIGAMAP_JVECTOR}
 * backend; its HNSW index is rebuilt from them without calling the embedding model.
 */
final class VectorStore {

  private final PersistenceContext ctx;

  VectorStore(PersistenceContext ctx) {
    this.ctx = ctx;
  }

  void saveVectorEntry(UUID imageId, VectorEntry entry) {
    ctx.write(() -> {
      ctx.root().getVectorEntries().put(imageId, entry);
      ctx.store(ctx.root().getVectorEntries());
    });
  }

  Optional<VectorEntry> findVectorEntry(UUID imageId) {
    return ctx.read(() -> Optional.ofNullable(ctx.root().getVectorEntries().get(imageId)));
  }

  List<UUID> findAllIndexedImageIds() {
    return ctx.read(() -> new ArrayList<>(ctx.root().getVectorEntries().keySet()));
  }

  void saveRawVector(UUID imageId, float[] vector) {
    ctx.write(() -> {
      ctx.root().getRawVectorStore().put(imageId, vector);
      ctx.store(ctx.root().getRawVectorStore());
    });
  }

  void removeRawVector(UUID imageId) {
    ctx.write(() -> {
      ctx.root().getRawVectorStore().remove(imageId);
      ctx.store(ctx.root().getRawVectorStore());
    });
  }

  /**
   * Returns a snapshot copy of all persisted raw vectors.
   */
  Map<UUID, float[]> findAllRawVectors() {
    return ctx.read(() -> new HashMap<>(ctx.root().getRawVectorStore()));
  }

  int getRawVectorCount() {
    return ctx.read(() -> ctx.root().getRawVectorStore().size());
  }

  /**
   * Looks up a single raw vector without copying the whole map.
   */
  Optional<float[]> findRawVector(UUID imageId) {
    return ctx.read(() -> Optional.ofNullable(ctx.root().getRawVectorStore().get(imageId)));
  }
}
