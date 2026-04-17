package com.svenruppert.imagerag.service.impl;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.imagerag.domain.enums.SimilarityFunction;
import com.svenruppert.imagerag.dto.VectorSearchHit;
import com.svenruppert.imagerag.service.VectorIndexService;

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
    if (vector == null || vector.length == 0) {
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
    if (queryVector == null || queryVector.length == 0 || store.isEmpty()) {
      return List.of();
    }

    List<VectorSearchHit> results = new ArrayList<>(store.size());

    for (Map.Entry<UUID, float[]> entry : store.entrySet()) {
      double score = switch (fn) {
        case COSINE      -> cosineSimilarity(queryVector, entry.getValue());
        case DOT_PRODUCT -> dotProduct(queryVector, entry.getValue());
        case EUCLIDEAN   -> euclideanSimilarity(queryVector, entry.getValue());
      };
      results.add(new VectorSearchHit(entry.getKey(), score));
    }

    results.sort(Comparator.naturalOrder()); // VectorSearchHit.compareTo() = descending
    return new ArrayList<>(results.subList(0, Math.min(limit, results.size())));
  }

  @Override
  public void remove(UUID imageId) {
    store.remove(imageId);
  }

  // ── Similarity functions ──────────────────────────────────────────────────

  private double cosineSimilarity(float[] a, float[] b) {
    int len = Math.min(a.length, b.length);
    double dot = 0.0, normA = 0.0, normB = 0.0;
    for (int i = 0; i < len; i++) {
      dot   += (double) a[i] * b[i];
      normA += (double) a[i] * a[i];
      normB += (double) b[i] * b[i];
    }
    double denom = Math.sqrt(normA) * Math.sqrt(normB);
    return denom < 1e-10 ? 0.0 : dot / denom;
  }

  /** Raw inner product — scale-sensitive; equivalent to cosine for unit-norm vectors. */
  private double dotProduct(float[] a, float[] b) {
    int len = Math.min(a.length, b.length);
    double dot = 0.0;
    for (int i = 0; i < len; i++) {
      dot += (double) a[i] * b[i];
    }
    return dot;
  }

  /**
   * L2 distance converted to a similarity in (0, 1]: {@code 1 / (1 + L2)}.
   * Smaller distance → score closer to 1.0.
   */
  private double euclideanSimilarity(float[] a, float[] b) {
    int len = Math.min(a.length, b.length);
    double sumSq = 0.0;
    for (int i = 0; i < len; i++) {
      double diff = (double) a[i] - b[i];
      sumSq += diff * diff;
    }
    return 1.0 / (1.0 + Math.sqrt(sumSq));
  }
}
