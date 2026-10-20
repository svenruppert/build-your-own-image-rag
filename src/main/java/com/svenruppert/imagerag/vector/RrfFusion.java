package com.svenruppert.imagerag.vector;

import java.util.*;

/**
 * Weighted Reciprocal Rank Fusion over a semantic (vector) and a keyword (BM25) ranking.
 * <p>For a candidate at zero-based rank {@code r} in a channel with weight {@code w}:
 * <pre>
 *   contribution = w / (k + r + 1)
 * </pre>
 * The fused score is the sum over both channels. Unweighted RRF is the special case
 * {@code w = 1.0} for both channels.
 */
public final class RrfFusion {

  /**
   * Standard RRF damping constant from the original paper (Cormack et al., 2009).
   */
  public static final int DEFAULT_K = 60;

  private RrfFusion() {
  }

  /**
   * Contribution of a single channel; {@code 0.0} when the candidate is absent ({@code rank < 0}).
   */
  public static double contribution(int rank, double weight, int k) {
    return rank >= 0 ? weight / (k + rank + 1) : 0.0;
  }

  /**
   * Fuses both rankings and returns all candidates sorted by descending fused score.
   * Ties keep first-seen order (vector ranking first, then keyword ranking).
   *
   * @param vectorRanking  image ids in descending semantic order
   * @param keywordRanking image ids in descending BM25 order
   */
  public static List<Candidate> fuse(List<UUID> vectorRanking, List<UUID> keywordRanking,
                                     double semanticWeight, double keywordWeight, int k) {
    Map<UUID, Integer> vectorRanks = rankMap(vectorRanking);
    Map<UUID, Integer> keywordRanks = rankMap(keywordRanking);

    Set<UUID> allIds = new LinkedHashSet<>(vectorRanks.keySet());
    allIds.addAll(keywordRanks.keySet());

    List<Candidate> fused = new ArrayList<>(allIds.size());
    for (UUID id : allIds) {
      int vRank = vectorRanks.getOrDefault(id, -1);
      int kRank = keywordRanks.getOrDefault(id, -1);
      fused.add(new Candidate(id,
                              contribution(vRank, semanticWeight, k),
                              contribution(kRank, keywordWeight, k),
                              vRank, kRank));
    }
    fused.sort(Comparator.comparingDouble(Candidate::total).reversed());
    return fused;
  }

  /**
   * Highest fused score in {@code candidates}, used to normalize scores to [0, 1];
   * {@code 1.0} when the list is empty or all scores are zero.
   */
  public static double normalizer(List<Candidate> candidates) {
    double max = candidates.stream().mapToDouble(Candidate::total).max().orElse(1.0);
    return max > 0 ? max : 1.0;
  }

  private static Map<UUID, Integer> rankMap(List<UUID> ranking) {
    Map<UUID, Integer> ranks = new LinkedHashMap<>();
    for (int i = 0; i < ranking.size(); i++) ranks.putIfAbsent(ranking.get(i), i);
    return ranks;
  }

  /**
   * One fused candidate with its per-channel contributions and ranks ({@code -1} = absent).
   */
  public record Candidate(UUID id, double semanticRaw, double keywordRaw,
                          int vectorRank, int keywordRank) {

    public double total() {
      return semanticRaw + keywordRaw;
    }
  }
}
