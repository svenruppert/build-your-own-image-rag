package com.svenruppert.imagerag.service.impl;

import com.svenruppert.imagerag.domain.CategoryClusterSuggestion;
import com.svenruppert.imagerag.domain.CategoryClusterSuggestion.SuggestionType;
import com.svenruppert.imagerag.domain.SemanticAnalysis;
import com.svenruppert.imagerag.domain.enums.SourceCategory;
import com.svenruppert.imagerag.persistence.PersistenceService;
import com.svenruppert.imagerag.service.ClusterDiscoveryService;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Simple k-means clustering over raw image embedding vectors.
 * <p>Uses cosine distance (converted to a similarity-like measure) as the distance
 * metric since all stored embeddings are produced by the same model.  No external
 * ML library is required — the implementation is self-contained.
 * <p>The algorithm runs a fixed number of iterations (default 15) or until
 * centroid movement falls below a convergence threshold.
 */
public class ClusterDiscoveryServiceImpl
    implements ClusterDiscoveryService {

  private static final int MAX_ITERATIONS = 15;
  private static final double CONVERGENCE_THRESHOLD = 1e-4;
  private static final Random RNG = new Random(42L); // deterministic

  private final PersistenceService persistenceService;

  public ClusterDiscoveryServiceImpl(PersistenceService persistenceService) {
    this.persistenceService = persistenceService;
  }

  // ── Public API ───────────────────────────────────────────────────────────

  private static double cosineSim(float[] a, float[] b) {
    double dot = 0, normA = 0, normB = 0;
    int len = Math.min(a.length, b.length);
    for (int i = 0; i < len; i++) {
      dot += (double) a[i] * b[i];
      normA += (double) a[i] * a[i];
      normB += (double) b[i] * b[i];
    }
    double denom = Math.sqrt(normA) * Math.sqrt(normB);
    return denom > 1e-9 ? dot / denom : 0.0;
  }

  private static void normalize(float[] v) {
    double norm = 0;
    for (float f : v) norm += (double) f * f;
    norm = Math.sqrt(norm);
    if (norm > 1e-9) for (int i = 0; i < v.length; i++) v[i] /= (float) norm;
  }

  // ── K-means ──────────────────────────────────────────────────────────────

  private static void notify(Consumer<String> cb, String msg) {
    if (cb != null) cb.accept(msg);
  }

  @Override
  public List<CategoryClusterSuggestion> discover(
      int maxClusters, int minClusterSize,
      double similarityThreshold, Consumer<String> progress) {

    notify(progress, "Loading image vectors…");
    Map<UUID, float[]> vectors = persistenceService.findAllRawVectors();

    if (vectors.size() < minClusterSize * 2) {
      notify(progress, "Not enough vectors to form clusters (need at least " + (minClusterSize * 2) + ").");
      return List.of();
    }

    int k = Math.min(maxClusters, Math.max(2, vectors.size() / 10));
    notify(progress, String.format("Running k-means with k=%d over %d images…", k, vectors.size()));

    List<UUID> ids = new ArrayList<>(vectors.keySet());
    List<float[]> vecs = ids.stream().map(vectors::get).toList();

    int[] assignments = kMeans(vecs, k, progress);

    notify(progress, "Analysing clusters…");
    return buildSuggestions(ids, vecs, assignments, k, minClusterSize, similarityThreshold);
  }

  // ── Suggestion generation ─────────────────────────────────────────────

  @Override
  public List<CategoryClusterSuggestion> discoverForCategory(
      SourceCategory category, int minClusterSize) {

    // Collect image IDs for this category
    List<UUID> categoryIds = persistenceService.findAllImages().stream()
        .filter(img -> !img.isDeleted())
        .filter(img -> {
          Optional<SemanticAnalysis> a = persistenceService.findAnalysis(img.getId());
          return a.isPresent() && category.equals(a.get().getSourceCategory());
        })
        .map(img -> img.getId())
        .toList();

    if (categoryIds.size() < minClusterSize * 2) return List.of();

    Map<UUID, float[]> vectors = new HashMap<>();
    for (UUID id : categoryIds) {
      persistenceService.findRawVector(id).ifPresent(v -> vectors.put(id, v));
    }
    if (vectors.size() < minClusterSize * 2) return List.of();

    int k = Math.min(5, Math.max(2, vectors.size() / 8));
    List<UUID> ids = new ArrayList<>(vectors.keySet());
    List<float[]> vecs = ids.stream().map(vectors::get).toList();

    int[] assignments = kMeans(vecs, k, null);
    return buildSuggestions(ids, vecs, assignments, k, minClusterSize, 0.70);
  }

  private int[] kMeans(List<float[]> vectors, int k, Consumer<String> progress) {
    int n = vectors.size();
    int dims = vectors.get(0).length;

    // Initialise centroids via k-means++ seeding
    float[][] centroids = initCentroids(vectors, k);
    int[] assignments = new int[n];

    for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
      // Assign step
      boolean changed = false;
      for (int i = 0; i < n; i++) {
        int best = 0;
        double bestSim = -2.0;
        for (int c = 0; c < k; c++) {
          double sim = cosineSim(vectors.get(i), centroids[c]);
          if (sim > bestSim) {
            bestSim = sim;
            best = c;
          }
        }
        if (assignments[i] != best) {
          assignments[i] = best;
          changed = true;
        }
      }
      if (!changed) break;

      // Update step — recompute centroids as mean of assigned vectors
      float[][] newCentroids = new float[k][dims];
      int[] counts = new int[k];
      for (int i = 0; i < n; i++) {
        int c = assignments[i];
        counts[c]++;
        for (int d = 0; d < dims; d++) newCentroids[c][d] += vectors.get(i)[d];
      }
      double maxShift = 0.0;
      for (int c = 0; c < k; c++) {
        if (counts[c] == 0) {
          newCentroids[c] = centroids[c];
          continue;
        }
        for (int d = 0; d < dims; d++) newCentroids[c][d] /= counts[c];
        normalize(newCentroids[c]);
        maxShift = Math.max(maxShift, 1.0 - cosineSim(centroids[c], newCentroids[c]));
      }
      centroids = newCentroids;
      if (maxShift < CONVERGENCE_THRESHOLD) break;
      notify(progress, "K-means iteration " + (iter + 1) + "…");
    }
    return assignments;
  }

  private float[][] initCentroids(List<float[]> vectors, int k) {
    int n = vectors.size();
    float[][] centroids = new float[k][];
    // First centroid: random
    centroids[0] = Arrays.copyOf(vectors.get(RNG.nextInt(n)), vectors.get(0).length);
    normalize(centroids[0]);

    for (int c = 1; c < k; c++) {
      // k-means++ — choose next centroid proportional to distance from nearest existing centroid
      double[] distances = new double[n];
      double total = 0;
      for (int i = 0; i < n; i++) {
        double minDist = Double.MAX_VALUE;
        for (int j = 0; j < c; j++) {
          double dist = 1.0 - cosineSim(vectors.get(i), centroids[j]);
          minDist = Math.min(minDist, dist);
        }
        distances[i] = minDist * minDist;
        total += distances[i];
      }
      double r = RNG.nextDouble() * total;
      int chosen = n - 1;
      for (int i = 0; i < n; i++) {
        r -= distances[i];
        if (r <= 0) {
          chosen = i;
          break;
        }
      }
      centroids[c] = Arrays.copyOf(vectors.get(chosen), vectors.get(chosen).length);
      normalize(centroids[c]);
    }
    return centroids;
  }

  // ── Math helpers ──────────────────────────────────────────────────────────

  private List<CategoryClusterSuggestion> buildSuggestions(
      List<UUID> ids, List<float[]> vecs, int[] assignments,
      int k, int minClusterSize, double simThreshold) {

    List<CategoryClusterSuggestion> results = new ArrayList<>();

    for (int c = 0; c < k; c++) {
      // Collect cluster members
      List<Integer> memberIdx = new ArrayList<>();
      for (int i = 0; i < assignments.length; i++) {
        if (assignments[i] == c) memberIdx.add(i);
      }
      if (memberIdx.size() < minClusterSize) continue;

      List<UUID> clusterIds = memberIdx.stream().map(ids::get).toList();

      // Category distribution
      Map<SourceCategory, Integer> catDist = new HashMap<>();
      for (UUID id : clusterIds) {
        persistenceService.findAnalysis(id).ifPresent(a -> {
          if (a.getSourceCategory() != null) {
            catDist.merge(a.getSourceCategory(), 1, Integer::sum);
          }
        });
      }
      if (catDist.isEmpty()) continue;

      // Dominant category
      SourceCategory dominant = catDist.entrySet().stream()
          .max(Map.Entry.comparingByValue())
          .map(Map.Entry::getKey)
          .orElse(SourceCategory.UNKNOWN);

      // Intra-cluster similarity
      double avgSim = computeIntraClusterSim(memberIdx, vecs);

      // Generate cluster label from common tags
      String clusterLabel = generateClusterLabel(clusterIds, dominant);

      // Determine suggestion type
      SuggestionType type;
      String rationale;

      if (catDist.size() > 2) {
        // Cluster spans many categories → check for merge
        type = SuggestionType.PROPOSE_MERGE;
        rationale = String.format(
            "Cluster of %d images spans %d categories (dominant: %s) with avg similarity %.2f." +
                " Images may be over-classified — consider merging or adding a cross-category alias.",
            clusterIds.size(), catDist.size(), dominant.name(), avgSim);
      } else if (catDist.size() == 2) {
        type = SuggestionType.PROPOSE_ALIAS;
        String catList = catDist.keySet().stream()
            .map(SourceCategory::name).collect(Collectors.joining(" / "));
        rationale = String.format(
            "Cluster of %d images splits between %s (avg similarity %.2f). " +
                "Consider a cross-category alias or merging these categories.",
            clusterIds.size(), catList, avgSim);
      } else if (avgSim >= simThreshold && clusterIds.size() >= minClusterSize * 2) {
        // Tight single-category cluster → subcategory proposal
        type = SuggestionType.PROPOSE_SUBCATEGORY;
        rationale = String.format(
            "Tight cluster of %d images within %s (avg similarity %.2f ≥ threshold %.2f). " +
                "These images form a visually distinct subgroup: \"%s\". " +
                "Consider adding a fine-grained subcategory.",
            clusterIds.size(), dominant.name(), avgSim, simThreshold, clusterLabel);
      } else {
        type = SuggestionType.PROPOSE_SUBCATEGORY;
        rationale = String.format(
            "Cluster of %d images within %s (avg similarity %.2f). " +
                "Possible subcategory candidate: \"%s\".",
            clusterIds.size(), dominant.name(), avgSim, clusterLabel);
      }

      results.add(CategoryClusterSuggestion.create(
          type, dominant, clusterLabel, clusterIds, catDist, avgSim, rationale));
    }

    return Collections.unmodifiableList(results);
  }

  private double computeIntraClusterSim(List<Integer> memberIdx, List<float[]> vecs) {
    if (memberIdx.size() < 2) return 1.0;
    double total = 0;
    int count = 0;
    int n = Math.min(memberIdx.size(), 20); // sample up to 20 pairs for speed
    for (int i = 0; i < n; i++) {
      for (int j = i + 1; j < n; j++) {
        total += cosineSim(vecs.get(memberIdx.get(i)), vecs.get(memberIdx.get(j)));
        count++;
      }
    }
    return count > 0 ? total / count : 0.5;
  }

  private String generateClusterLabel(List<UUID> clusterIds, SourceCategory dominant) {
    // Collect most common tags across cluster members
    Map<String, Integer> tagFreq = new HashMap<>();
    for (UUID id : clusterIds) {
      persistenceService.findAnalysis(id).ifPresent(a -> {
        if (a.getTags() != null) {
          a.getTags().forEach(tag -> tagFreq.merge(tag.toLowerCase(), 1, Integer::sum));
        }
      });
    }
    String topTags = tagFreq.entrySet().stream()
        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
        .limit(3)
        .map(Map.Entry::getKey)
        .collect(Collectors.joining(", "));
    return topTags.isBlank() ? dominant.name() : dominant.name() + " — " + topTags;
  }
}
