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
package com.svenruppert.imagerag.domain.enums;

/**
 * Selects which vector-search backend is active at startup.
 * <p>The active backend is resolved from the application configuration file
 * {@code imagerag.properties} (classpath resource) via the key
 * {@code vector.backend}.  A JVM system property with the same key overrides
 * the file value — useful for CI / Docker environments.
 * If the key is absent everywhere the default is {@link #IN_MEMORY}.
 * <ul>
 *   <li>{@link #IN_MEMORY} – existing in-memory cosine-similarity implementation.
 *       Fast for small collections; vectors are lost on restart and must be
 *       re-embedded via Ollama.</li>
 *   <li>{@link #GIGAMAP_JVECTOR} – EclipseStore-backed raw-vector store (the
 *       "GigaMap" role) combined with a JVector HNSW approximate-nearest-neighbour
 *       index rebuilt in memory from the persisted vectors at startup.
 *       No Ollama call is required on restart; EclipseStore remains the durable
 *       source of truth for both domain data and raw embeddings.</li>
 * </ul>
 */
public enum VectorBackendType {

  /**
   * In-memory cosine similarity — existing implementation.
   */
  IN_MEMORY,

  /**
   * EclipseStore GigaMap (durable Map&lt;UUID,float[]&gt;) + JVector HNSW index.
   * EclipseStore persists the raw vectors; JVector provides fast ANN search.
   */
  GIGAMAP_JVECTOR;

  /**
   * Configuration key used to select the backend at startup.
   */
  public static final String PROPERTY_KEY = "vector.backend";

  /**
   * Property value for {@link #IN_MEMORY}.
   */
  public static final String VALUE_IN_MEMORY = "in-memory";

  /**
   * Property value for {@link #GIGAMAP_JVECTOR}.
   */
  public static final String VALUE_GIGAMAP_JVECTOR = "gigamap-jvector";

  /**
   * Maps a configured property value to the backend type; unknown values fall back to
   * {@link #IN_MEMORY}. Resolving the value from the configuration is the job of
   * the bootstrap layer.
   *
   * @param raw the configured value, for example {@value #VALUE_GIGAMAP_JVECTOR}
   * @return the backend type, never {@code null}
   */
  public static VectorBackendType fromValue(String raw) {
    return switch (raw == null ? "" : raw.trim().toLowerCase()) {
      case VALUE_GIGAMAP_JVECTOR -> GIGAMAP_JVECTOR;
      default -> IN_MEMORY;
    };
  }
}
