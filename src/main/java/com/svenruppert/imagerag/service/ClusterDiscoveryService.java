package com.svenruppert.imagerag.service;

import com.svenruppert.imagerag.domain.CategoryClusterSuggestion;
import com.svenruppert.imagerag.domain.enums.SourceCategory;

import java.util.List;
import java.util.function.Consumer;

/**
 * Service that discovers potential taxonomy improvements by clustering the image
 * inventory's semantic vectors.
 * <p>Results are returned as {@link CategoryClusterSuggestion} proposals and are
 * <em>not</em> automatically applied — they must be reviewed in the Taxonomy
 * Maintenance view.
 */
public interface ClusterDiscoveryService {

  /**
   * Runs k-means clustering across all indexed images and returns taxonomy
   * improvement suggestions.
   *
   * @param maxClusters         maximum number of clusters to form
   * @param minClusterSize      ignore clusters with fewer than this many images
   * @param similarityThreshold intra-cluster average similarity below which a
   *                            cluster is considered too loose to suggest a subcategory
   * @param progressCallback    receives status messages during the (potentially slow)
   *                            computation; safe to pass {@code null}
   * @return unmodifiable list of cluster suggestions
   */
  List<CategoryClusterSuggestion> discover(
      int maxClusters,
      int minClusterSize,
      double similarityThreshold,
      Consumer<String> progressCallback);

  /**
   * Runs clustering restricted to images in the given category and returns
   * subcategory/split/alias suggestions for that category only.
   *
   * @param category       the category to analyse
   * @param minClusterSize minimum cluster size
   * @return unmodifiable list of suggestions
   */
  List<CategoryClusterSuggestion> discoverForCategory(
      SourceCategory category,
      int minClusterSize);
}
