package com.svenruppert.imagerag.bootstrap;

import com.svenruppert.dependencies.core.logger.HasLogger;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Thin configuration reader that loads {@code imagerag.properties} from the
 * classpath root and exposes typed accessors for every recognised key.
 * <h3>Override precedence (highest → lowest)</h3>
 * <ol>
 *   <li>JVM system property ({@code -Dkey=value}) — useful for CI / Docker environments.</li>
 *   <li>Value in {@code imagerag.properties} on the classpath.</li>
 *   <li>Hard-coded default built into each accessor.</li>
 * </ol>
 * <p>The singleton is initialised once at startup by {@link ServiceRegistry} and
 * is thereafter read-only.
 */
public final class AppConfig
    implements HasLogger {

  /**
   * Classpath location of the properties file.
   */
  private static final String RESOURCE_PATH = "/imagerag.properties";

  private static volatile AppConfig instance;

  private final Properties props;

  // ── Singleton ─────────────────────────────────────────────────────────────

  private AppConfig() {
    props = new Properties();
    try (InputStream is = AppConfig.class.getResourceAsStream(RESOURCE_PATH)) {
      if (is == null) {
        logger().warn("'{}' not found on the classpath — all settings will use defaults "
                          + "or JVM system properties.", RESOURCE_PATH);
      } else {
        props.load(is);
        logger().info("Loaded application configuration from '{}'", RESOURCE_PATH);
      }
    } catch (IOException e) {
      logger().warn("Could not read '{}': {} — defaults will be used.", RESOURCE_PATH, e.getMessage());
    }
  }

  /**
   * Returns the singleton instance, creating it on first call.
   */
  public static AppConfig getInstance() {
    if (instance == null) {
      synchronized (AppConfig.class) {
        if (instance == null) {
          instance = new AppConfig();
        }
      }
    }
    return instance;
  }

  // ── Generic accessors ─────────────────────────────────────────────────────

  /**
   * Returns the value for {@code key}, applying the override precedence:
   * JVM system property &gt; {@code imagerag.properties} &gt; {@code defaultValue}.
   *
   * @param key          the property key
   * @param defaultValue fallback when neither system property nor file contains the key
   * @return the resolved value, never {@code null}
   */
  public String get(String key, String defaultValue) {
    // 1. JVM system property wins
    String sysProp = System.getProperty(key);
    if (sysProp != null && !sysProp.isBlank()) {
      return sysProp.trim();
    }
    // 2. Properties file
    String fileProp = props.getProperty(key);
    if (fileProp != null && !fileProp.isBlank()) {
      return fileProp.trim();
    }
    // 3. Default
    return defaultValue;
  }

  /**
   * Returns the value for {@code key}, applying the override precedence.
   * Returns {@code null} if the key is absent and no default is given.
   */
  public String get(String key) {
    return get(key, null);
  }

  // ── Typed accessors ───────────────────────────────────────────────────────

  /**
   * Returns the configured vector-search backend identifier.
   * Defaults to {@code "in-memory"} when the property is absent.
   *
   * @return lower-cased, trimmed backend identifier
   */
  public String getVectorBackend() {
    return get("vector.backend", "in-memory").toLowerCase();
  }

  /**
   * Returns the Ollama embedding model to use for semantic-search vectors.
   * Defaults to {@code "bge-m3"} when the property {@code embedding.model} is absent.
   * <p>Changing this value after vectors have been stored requires a full vector-index
   * rebuild so that all persisted embeddings are produced by the same model.
   *
   * @return the embedding model name, never {@code null}
   */
  public String getEmbeddingModel() {
    return get("embedding.model", "bge-m3");
  }

  /**
   * Returns the default minimum RRF score for the Search view's threshold field.
   * Configurable via {@code search.min.score} in {@code imagerag.properties} or
   * {@code -Dsearch.min.score=...}.
   * Defaults to {@code 0.65}.
   *
   * @return default score threshold in [0.0, 1.0]
   */
  public double getSearchMinScore() {
    try {
      return Double.parseDouble(get("search.min.score", "0.65"));
    } catch (NumberFormatException e) {
      logger().warn("Invalid value for 'search.min.score' — using default 0.65");
      return 0.65;
    }
  }

  /**
   * Returns the default confidence threshold for the Taxonomy Maintenance view's
   * LOW_CONFIDENCE scope field.
   * Configurable via {@code taxonomy.confidence.threshold} in {@code imagerag.properties}
   * or {@code -Dtaxonomy.confidence.threshold=...}.
   * Defaults to {@code 0.60}.
   *
   * @return default confidence threshold in [0.0, 1.0]
   */
  public double getTaxonomyConfidenceThreshold() {
    try {
      return Double.parseDouble(get("taxonomy.confidence.threshold", "0.60"));
    } catch (NumberFormatException e) {
      logger().warn("Invalid value for 'taxonomy.confidence.threshold' — using default 0.60");
      return 0.60;
    }
  }
}
