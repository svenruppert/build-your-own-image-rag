package com.svenruppert.imagerag.service.impl;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.imagerag.domain.enums.SimilarityFunction;
import com.svenruppert.imagerag.dto.VectorSearchHit;
import com.svenruppert.imagerag.service.VectorIndexService;
import com.svenruppert.imagerag.vector.VectorMath;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory vector index.
 * Supports all three {@link SimilarityFunction} values natively.
 * Suitable for demo / development. Replace with JVector or Qdrant for production.
 */
public class VectorIndexServiceImpl
    implements VectorIndexService, HasLogger {

  private final Map<UUID, float[]> store = new ConcurrentHashMap<>();

  @Override
  public void index(UUID imageId, float[] vector) {
    if (!VectorMath.isUsable(vector)) {
      logger().warn("Skipping indexing for {} — empty vector", imageId);
      return;
    }
    store.put(imageId, vector);
    logger().debug("Indexed vector for {} (dim={})", imageId, vector.length);
  }

  @Override
  public List<VectorSearchHit> search(float[] queryVector, int limit) {
    return search(queryVector, limit, SimilarityFunction.COSINE);
  }

  @Override
  public List<VectorSearchHit> search(float[] queryVector, int limit, SimilarityFunction fn) {
    if (!VectorMath.isUsable(queryVector) || store.isEmpty()) {
      return List.of();
    }

    List<VectorSearchHit> results = new ArrayList<>(store.size());
    int skipped = 0;
    for (Map.Entry<UUID, float[]> entry : store.entrySet()) {
      if (!VectorMath.compatible(queryVector, entry.getValue())) {
        skipped++;
        continue;
      }
      double score = switch (fn) {
        case COSINE -> VectorMath.cosine(queryVector, entry.getValue());
        case DOT_PRODUCT -> VectorMath.dot(queryVector, entry.getValue());
        case EUCLIDEAN -> VectorMath.euclideanSimilarity(queryVector, entry.getValue());
      };
      results.add(new VectorSearchHit(entry.getKey(), score));
    }
    if (skipped > 0) {
      logger().warn("Skipped {} stored vectors whose dimension differs from the query (dim={})",
                    skipped, queryVector.length);
    }

    results.sort(Comparator.naturalOrder()); // VectorSearchHit.compareTo() = descending
    return new ArrayList<>(results.subList(0, Math.min(limit, results.size())));
  }

  @Override
  public void remove(UUID imageId) {
    store.remove(imageId);
  }
}
