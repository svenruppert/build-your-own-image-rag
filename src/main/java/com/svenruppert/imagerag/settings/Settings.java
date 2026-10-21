package com.svenruppert.imagerag.settings;

import com.svenruppert.imagerag.domain.enums.VectorBackendType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.svenruppert.imagerag.settings.SettingCategory.*;
import static com.svenruppert.imagerag.settings.SettingEffect.*;

/**
 * Catalog of all runtime-tunable settings with their built-in defaults and bounds.
 * <p>Consumers read the current value at use time through
 * {@link SettingsService#get(Setting)}; nothing should cache a value in a final field.
 */
public final class Settings {

  // ── Search ────────────────────────────────────────────────────────────────

  /**
   * Minimum normalised RRF score of a result. bge-m3 places unrelated pairs well below
   * 0.5 after RRF normalisation and related pairs between 0.6 and 1.0, so 0.65 removes
   * clearly unrelated images while keeping semantically adjacent ones.
   */
  public static final Setting<Double> SEARCH_MIN_SCORE =
      Setting.ofDouble("search.min.score", 0.65, 0.0, 1.0, 0.01, SEARCH, IMMEDIATE);
  public static final Setting<Integer> SEARCH_MAX_RESULTS =
      Setting.ofInt("search.max.results", 5, 1, 100, SEARCH, IMMEDIATE);
  public static final Setting<Integer> SEARCH_VECTOR_CANDIDATES =
      Setting.ofInt("search.vector.candidates", 50, 5, 1000, SEARCH, IMMEDIATE);
  public static final Setting<Integer> SEARCH_KEYWORD_CANDIDATES =
      Setting.ofInt("search.keyword.candidates", 100, 5, 2000, SEARCH, IMMEDIATE);
  public static final Setting<Double> SEARCH_CONFIDENCE_WEIGHT =
      Setting.ofDouble("search.confidence.weight", 0.15, 0.0, 1.0, 0.01, SEARCH, IMMEDIATE);
  public static final Setting<Integer> SEARCH_RRF_K =
      Setting.ofInt("search.rrf.k", 60, 1, 1000, SEARCH, IMMEDIATE);
  public static final Setting<Integer> SEARCH_PARALLELISM =
      Setting.ofInt("search.parallelism", 1, 1, 4, SEARCH, IMMEDIATE);

  // ── Diagnostics and taxonomy ──────────────────────────────────────────────

  public static final Setting<Double> WHY_NOT_FOUND_AMBIGUITY_THRESHOLD =
      Setting.ofDouble("whynotfound.ambiguity.threshold", 0.6, 0.0, 1.0, 0.01, DIAGNOSTICS, IMMEDIATE);
  public static final Setting<Double> TAXONOMY_CONFIDENCE_THRESHOLD =
      Setting.ofDouble("taxonomy.confidence.threshold", 0.60, 0.0, 1.0, 0.01, DIAGNOSTICS, IMMEDIATE);

  // ── Cluster discovery ─────────────────────────────────────────────────────

  public static final Setting<Integer> CLUSTER_IMAGES_PER_CLUSTER =
      Setting.ofInt("cluster.images.per.cluster", 10, 2, 1000, CLUSTERING, IMMEDIATE);
  public static final Setting<Integer> CLUSTER_CATEGORY_IMAGES_PER_CLUSTER =
      Setting.ofInt("cluster.category.images.per.cluster", 8, 2, 1000, CLUSTERING, IMMEDIATE);
  public static final Setting<Integer> CLUSTER_CATEGORY_MAX_CLUSTERS =
      Setting.ofInt("cluster.category.max.clusters", 5, 2, 50, CLUSTERING, IMMEDIATE);
  public static final Setting<Double> CLUSTER_CATEGORY_SIMILARITY_THRESHOLD =
      Setting.ofDouble("cluster.category.similarity.threshold", 0.70, 0.0, 1.0, 0.01, CLUSTERING, IMMEDIATE);
  public static final Setting<Integer> CLUSTER_MAX_ITERATIONS =
      Setting.ofInt("cluster.max.iterations", 15, 1, 200, CLUSTERING, IMMEDIATE);

  // ── Vector index (HNSW) ───────────────────────────────────────────────────

  public static final Setting<Integer> HNSW_M =
      Setting.ofInt("hnsw.m", 16, 2, 128, VECTOR_INDEX, NEXT_REBUILD);
  public static final Setting<Integer> HNSW_EF_CONSTRUCTION =
      Setting.ofInt("hnsw.ef.construction", 100, 10, 1000, VECTOR_INDEX, NEXT_REBUILD);
  public static final Setting<Double> HNSW_NEIGHBOR_OVERFLOW =
      Setting.ofDouble("hnsw.neighbor.overflow", 1.2, 1.0, 3.0, 0.05, VECTOR_INDEX, NEXT_REBUILD);
  public static final Setting<Double> HNSW_ALPHA =
      Setting.ofDouble("hnsw.alpha", 1.2, 1.0, 3.0, 0.05, VECTOR_INDEX, NEXT_REBUILD);

  // ── Pipeline and upload ───────────────────────────────────────────────────

  public static final Setting<Integer> PIPELINE_INGESTION_PARALLELISM =
      Setting.ofInt("pipeline.ingestion.parallelism", 1, 1, 6, PIPELINE, IMMEDIATE);
  public static final Setting<Integer> PIPELINE_MAX_FINISHED_JOBS =
      Setting.ofInt("pipeline.max.finished.jobs", 200, 10, 10_000, PIPELINE, IMMEDIATE);
  public static final Setting<Integer> UPLOAD_MAX_FILES =
      Setting.ofInt("upload.max.files", 200, 1, 10_000, PIPELINE, IMMEDIATE);
  public static final Setting<Integer> UPLOAD_MAX_FILE_SIZE_MB =
      Setting.ofInt("upload.max.file.size.mb", 50, 1, 2048, PIPELINE, IMMEDIATE);

  // ── History limits ────────────────────────────────────────────────────────

  public static final Setting<Integer> AUDIT_MAX_ENTRIES =
      Setting.ofInt("audit.max.entries", 500, 50, 100_000, HISTORY, IMMEDIATE);
  public static final Setting<Integer> SEARCH_RECENT_MAX =
      Setting.ofInt("search.recent.max", 20, 1, 500, HISTORY, IMMEDIATE);

  // ── Ollama ────────────────────────────────────────────────────────────────

  public static final Setting<Integer> OLLAMA_TIMEOUT_SECONDS =
      Setting.ofInt("ollama.timeout.seconds", 300, 10, 3600, OLLAMA, IMMEDIATE);
  public static final Setting<Integer> OLLAMA_CONNECT_TIMEOUT_SECONDS =
      Setting.ofInt("ollama.connect.timeout.seconds", 30, 1, 300, OLLAMA, IMMEDIATE);
  public static final Setting<Integer> OLLAMA_PING_TIMEOUT_SECONDS =
      Setting.ofInt("ollama.ping.timeout.seconds", 5, 1, 120, OLLAMA, IMMEDIATE);
  public static final Setting<Integer> OLLAMA_VISION_MAX_IMAGE_MB =
      Setting.ofInt("ollama.vision.max.image.mb", 50, 1, 1024, OLLAMA, IMMEDIATE);
  /**
   * Lets reasoning models (e.g. gemma4) think before answering text/JSON requests.
   * Off by default: thinking made query understanding take 70–130 s instead of ~15 s.
   */
  public static final Setting<Boolean> OLLAMA_TEXT_THINK =
      Setting.ofBoolean("ollama.text.think", false, OLLAMA, IMMEDIATE);
  /**
   * Lets reasoning models think before describing an image. Off by default: thinking
   * made one vision analysis take ~235 s instead of ~30 s, at the cost of shorter texts.
   */
  public static final Setting<Boolean> OLLAMA_VISION_THINK =
      Setting.ofBoolean("ollama.vision.think", false, OLLAMA, IMMEDIATE);

  // ── Reverse geocoding ─────────────────────────────────────────────────────

  /**
   * Lower bound of 1000 ms keeps the Nominatim usage policy (one request per second).
   */
  public static final Setting<Integer> GEOCODING_MIN_INTERVAL_MILLIS =
      Setting.ofInt("geocoding.min.interval.millis", 1100, 1000, 60_000, GEOCODING, IMMEDIATE);
  public static final Setting<Integer> GEOCODING_TIMEOUT_SECONDS =
      Setting.ofInt("geocoding.timeout.seconds", 10, 1, 120, GEOCODING, IMMEDIATE);

  // ── Models and backend (read-only) ────────────────────────────────────────

  public static final Setting<String> OLLAMA_HOST =
      Setting.readOnlyText("ollama.host", "localhost", MODELS, NEEDS_RESTART);
  public static final Setting<String> OLLAMA_PORT =
      Setting.readOnlyText("ollama.port", "11434", MODELS, NEEDS_RESTART);
  public static final Setting<String> OLLAMA_VISION_MODEL =
      Setting.readOnlyText("ollama.vision.model", "gemma4:31b", MODELS, NEEDS_REINDEX);
  public static final Setting<String> OLLAMA_TEXT_MODEL =
      Setting.readOnlyText("ollama.text.model", "gemma4:31b", MODELS, NEEDS_REINDEX);
  public static final Setting<String> EMBEDDING_MODEL =
      Setting.readOnlyText("embedding.model", "bge-m3", MODELS, NEEDS_REINDEX);
  public static final Setting<String> VECTOR_BACKEND =
      Setting.readOnlyText("vector.backend", VectorBackendType.VALUE_IN_MEMORY, MODELS, NEEDS_REINDEX);

  /**
   * All settings in display order.
   */
  public static final List<Setting<?>> ALL = List.of(
      SEARCH_MIN_SCORE, SEARCH_MAX_RESULTS, SEARCH_VECTOR_CANDIDATES, SEARCH_KEYWORD_CANDIDATES,
      SEARCH_CONFIDENCE_WEIGHT, SEARCH_RRF_K, SEARCH_PARALLELISM,
      WHY_NOT_FOUND_AMBIGUITY_THRESHOLD, TAXONOMY_CONFIDENCE_THRESHOLD,
      CLUSTER_IMAGES_PER_CLUSTER, CLUSTER_CATEGORY_IMAGES_PER_CLUSTER, CLUSTER_CATEGORY_MAX_CLUSTERS,
      CLUSTER_CATEGORY_SIMILARITY_THRESHOLD, CLUSTER_MAX_ITERATIONS,
      HNSW_M, HNSW_EF_CONSTRUCTION, HNSW_NEIGHBOR_OVERFLOW, HNSW_ALPHA,
      PIPELINE_INGESTION_PARALLELISM, PIPELINE_MAX_FINISHED_JOBS, UPLOAD_MAX_FILES, UPLOAD_MAX_FILE_SIZE_MB,
      AUDIT_MAX_ENTRIES, SEARCH_RECENT_MAX,
      OLLAMA_TIMEOUT_SECONDS, OLLAMA_CONNECT_TIMEOUT_SECONDS, OLLAMA_PING_TIMEOUT_SECONDS,
      OLLAMA_VISION_MAX_IMAGE_MB, OLLAMA_TEXT_THINK, OLLAMA_VISION_THINK,
      GEOCODING_MIN_INTERVAL_MILLIS, GEOCODING_TIMEOUT_SECONDS,
      OLLAMA_HOST, OLLAMA_PORT, OLLAMA_VISION_MODEL, OLLAMA_TEXT_MODEL, EMBEDDING_MODEL, VECTOR_BACKEND);

  private static final Map<String, Setting<?>> BY_KEY = indexByKey();

  private Settings() {
  }

  private static Map<String, Setting<?>> indexByKey() {
    Map<String, Setting<?>> map = new LinkedHashMap<>();
    for (Setting<?> setting : ALL) {
      if (map.put(setting.key(), setting) != null) {
        throw new IllegalStateException("Duplicate setting key: " + setting.key());
      }
    }
    return Map.copyOf(map);
  }

  /**
   * Looks up a setting by its configuration key.
   */
  public static Optional<Setting<?>> byKey(String key) {
    return Optional.ofNullable(BY_KEY.get(key));
  }
}
