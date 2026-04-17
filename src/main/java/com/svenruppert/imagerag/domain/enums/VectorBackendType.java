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
   * Resolves the backend type using the full configuration precedence:
   * JVM system property &gt; {@code imagerag.properties} &gt; default ({@link #IN_MEMORY}).
   * <p>This is the preferred factory method.  It delegates to
   * {@link com.svenruppert.imagerag.bootstrap.AppConfig#getVectorBackend()},
   * which applies the same override rules.
   *
   * @return the resolved backend type, never {@code null}
   */
  public static VectorBackendType fromConfig() {
    String raw = com.svenruppert.imagerag.bootstrap.AppConfig.getInstance()
        .getVectorBackend();
    return switch (raw) {
      case VALUE_GIGAMAP_JVECTOR -> GIGAMAP_JVECTOR;
      default -> IN_MEMORY;
    };
  }

  /**
   * Resolves the backend type from the {@value #PROPERTY_KEY} JVM system property only.
   * Falls back to {@link #IN_MEMORY} when the property is absent or unrecognised.
   *
   * @return the resolved backend type, never {@code null}
   * @deprecated Prefer {@link #fromConfig()} which also reads {@code imagerag.properties}.
   */
  @Deprecated
  public static VectorBackendType fromSystemProperty() {
    String raw = System.getProperty(PROPERTY_KEY, VALUE_IN_MEMORY).trim().toLowerCase();
    return switch (raw) {
      case VALUE_GIGAMAP_JVECTOR -> GIGAMAP_JVECTOR;
      default -> IN_MEMORY;
    };
  }
}
