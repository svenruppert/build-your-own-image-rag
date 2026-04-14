package com.svenruppert.imagerag.service.impl;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.imagerag.dto.VectorSearchHit;
import com.svenruppert.imagerag.service.VectorIndexService;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory vector index using cosine similarity.
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
    if (queryVector == null || queryVector.length == 0 || store.isEmpty()) {
      return List.of();
    }

    List<VectorSearchHit> results = new ArrayList<>(store.size());

    for (Map.Entry<UUID, float[]> entry : store.entrySet()) {
      double similarity = cosineSimilarity(queryVector, entry.getValue());
      results.add(new VectorSearchHit(entry.getKey(), similarity));
    }

    results.sort(Comparator.naturalOrder()); // descending by score
    return results.subList(0, Math.min(limit, results.size()));
  }

  @Override
  public void remove(UUID imageId) {
    store.remove(imageId);
  }

  private double cosineSimilarity(float[] a, float[] b) {
    int len = Math.min(a.length, b.length);
    double dot = 0.0;
    double normA = 0.0;
    double normB = 0.0;

    for (int i = 0; i < len; i++) {
      dot += (double) a[i] * b[i];
      normA += (double) a[i] * a[i];
      normB += (double) b[i] * b[i];
    }

    double denom = Math.sqrt(normA) * Math.sqrt(normB);
    return denom < 1e-10 ? 0.0 : dot / denom;
  }
}
