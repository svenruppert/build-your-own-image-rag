package com.svenruppert.imagerag.service;

import com.svenruppert.imagerag.domain.enums.SimilarityFunction;
import com.svenruppert.imagerag.dto.VectorSearchHit;

import java.util.List;
import java.util.UUID;

public interface VectorIndexService {

  void index(UUID imageId, float[] vector);

  /** Standard search using the backend's native similarity function (COSINE). */
  List<VectorSearchHit> search(float[] queryVector, int limit);

  /**
   * Searches using the specified similarity function.
   *
   * <p>For backends that cannot switch similarity functions dynamically (e.g., a
   * pre-built HNSW graph), COSINE falls through to the fast native index while
   * DOT_PRODUCT and EUCLIDEAN perform a brute-force scan over the stored vectors —
   * accurate but slower.  The default delegates to the native
   * {@link #search(float[], int)} so backends that have not overridden this method
   * still return valid (COSINE) results.
   */
  default List<VectorSearchHit> search(float[] queryVector, int limit,
                                       SimilarityFunction fn) {
    return search(queryVector, limit);
  }

  void remove(UUID imageId);
}
