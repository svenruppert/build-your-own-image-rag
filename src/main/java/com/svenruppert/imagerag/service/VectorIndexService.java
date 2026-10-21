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
package com.svenruppert.imagerag.service;

import com.svenruppert.imagerag.domain.enums.SimilarityFunction;
import com.svenruppert.imagerag.dto.VectorSearchHit;

import java.util.List;
import java.util.UUID;

public interface VectorIndexService {

  void index(UUID imageId, float[] vector);

  /**
   * Standard search using the backend's native similarity function (COSINE).
   */
  List<VectorSearchHit> search(float[] queryVector, int limit);

  /**
   * Searches using the specified similarity function.
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
