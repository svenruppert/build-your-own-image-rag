package com.svenruppert.imagerag.service.impl;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.imagerag.domain.*;
import com.svenruppert.imagerag.domain.enums.RetrievalMode;
import com.svenruppert.imagerag.domain.enums.RiskLevel;
import com.svenruppert.imagerag.dto.*;
import com.svenruppert.imagerag.persistence.PersistenceService;
import com.svenruppert.imagerag.service.EmbeddingService;
import com.svenruppert.imagerag.service.KeywordIndexService;
import com.svenruppert.imagerag.service.VectorIndexService;
import com.svenruppert.imagerag.vector.RrfFusion;
import com.svenruppert.imagerag.vector.VectorMath;

import java.util.*;

/**
 * Executes a multimodal search: combines TEXT and IMAGE_EXAMPLE signals into one
 * query vector, TEXT and OCR_TERMS signals into one BM25 query, fuses both rankings
 * and attributes each result's score to the contributing signal types.
 */
final class MultimodalSearchExecutor
    implements HasLogger {

  private final EmbeddingService embeddingService;
  private final VectorIndexService vectorIndexService;
  private final PersistenceService persistenceService;
  private final KeywordIndexService keywordIndexService;

  MultimodalSearchExecutor(EmbeddingService embeddingService, VectorIndexService vectorIndexService,
                           PersistenceService persistenceService, KeywordIndexService keywordIndexService) {
    this.embeddingService = embeddingService;
    this.vectorIndexService = vectorIndexService;
    this.persistenceService = persistenceService;
    this.keywordIndexService = keywordIndexService;
  }

  List<MultimodalSearchResult> search(MultimodalSearchConfig config) {
    if (config == null || config.getSignals().isEmpty()) return List.of();

    logger().info("Multimodal search: {} signals, mode={}", config.getSignals().size(), config.getRetrievalMode());

    // ── Step 1: build combined query vector from TEXT + IMAGE_EXAMPLE signals ─
    float[] combinedVector = null;
    double textWeightSum = 0.0;
    double imageWeightSum = 0.0;

    if (config.getRetrievalMode() != RetrievalMode.BM25_ONLY) {
      WeightedSum text = new WeightedSum("text");
      for (MultimodalSignal sig : config.getSignals(MultimodalSignal.SignalType.TEXT)) {
        if (sig.textContent() == null || sig.textContent().isBlank()) continue;
        embeddingService.embed(sig.textContent())
            .peek(v -> text.add(v, sig.weight()))
            .peekFailure(reason -> logger().warn("Skipping text signal: {}", reason));
      }

      WeightedSum image = new WeightedSum("image example");
      for (MultimodalSignal sig : config.getSignals(MultimodalSignal.SignalType.IMAGE_EXAMPLE)) {
        if (sig.imageId() == null) continue;
        float[] v = persistenceService.findRawVector(sig.imageId()).orElse(null);
        if (v == null) {
          logger().warn("No vector for QBE image {}", sig.imageId());
          continue;
        }
        image.add(v, sig.weight());
      }

      // Combine text and image vectors; incompatible dimensions keep the text side,
      // which always comes from the current embedding model.
      if (text.isPresent() && image.isPresent()
          && !VectorMath.compatible(text.vector(), image.vector())) {
        logger().warn("Text vector (dim={}) and image vector (dim={}) are incompatible — ignoring image examples",
                      text.vector().length, image.vector().length);
        image = new WeightedSum("image example");
      }
      textWeightSum = text.weightSum();
      imageWeightSum = image.weightSum();
      double totalW = textWeightSum + imageWeightSum;
      if (totalW > 0) {
        if (text.isPresent() && image.isPresent()) {
          // Normalise contribution by weight sum so neither dominates by accident
          float[] combined = VectorMath.scaled(text.vector(), textWeightSum / totalW);
          VectorMath.addWeightedInPlace(combined, image.vector(), imageWeightSum / totalW);
          combinedVector = combined;
        } else {
          combinedVector = text.isPresent() ? text.vector() : image.vector();
        }
        VectorMath.normalizeInPlace(combinedVector);
      }
    }

    // ── Step 2: build BM25 query (TEXT + OCR_TERMS signals) ──────────────
    StringBuilder bm25Query = new StringBuilder();
    double textKeywordWeight = appendTerms(bm25Query, config.getSignals(MultimodalSignal.SignalType.TEXT));
    double ocrKeywordWeight = appendTerms(bm25Query, config.getSignals(MultimodalSignal.SignalType.OCR_TERMS));

    // ── Step 3: vector and BM25 retrieval ────────────────────────────────
    List<VectorSearchHit> vectorHits = List.of();
    if (config.getRetrievalMode() != RetrievalMode.BM25_ONLY && VectorMath.isUsable(combinedVector)) {
      vectorHits = vectorIndexService.search(combinedVector, SearchScoring.vectorCandidates(),
                                             config.getSimilarityFunction());
    }

    List<KeywordSearchHit> keywordHits = List.of();
    if (config.getRetrievalMode() != RetrievalMode.SEMANTIC_ONLY
        && !bm25Query.isEmpty()) {
      keywordHits = keywordIndexService.search(bm25Query.toString(), SearchScoring.keywordCandidates());
    }

    // ── Step 4: RRF fusion ────────────────────────────────────────────────
    List<RrfFusion.Candidate> fused = RrfFusion.fuse(SearchScoring.vectorIds(vectorHits),
                                                     SearchScoring.keywordIds(keywordHits),
                                                     config.getSemanticWeight(), config.getBm25Weight(),
                                                     SearchScoring.rrfK());
    double norm = RrfFusion.normalizer(fused);

    // ── Step 5: category-filter signal ────────────────────────────────────
    Set<String> categoryFilters = new HashSet<>();
    config.getSignals(MultimodalSignal.SignalType.CATEGORY_FILTER).forEach(sig -> {
      if (sig.textContent() != null) categoryFilters.add(sig.textContent().toLowerCase());
    });

    // ── Step 6: score, filter, assemble results ───────────────────────────
    List<MultimodalSearchResult> results = new ArrayList<>();
    for (RrfFusion.Candidate rf : fused) {
      if (!SearchScoring.isSearchable(persistenceService, rf.id())) continue;

      Optional<SemanticAnalysis> analysisOpt = persistenceService.findAnalysis(rf.id());
      if (analysisOpt.isEmpty()) continue;
      SemanticAnalysis analysis = analysisOpt.get();

      if (!matchesCategoryFilters(analysis, categoryFilters)) continue;

      double normSem = rf.semanticRaw() / norm;
      double normBm25 = rf.keywordRaw() / norm;
      double baseScore = normSem + normBm25;

      // Confidence boost
      CategoryConfidence cc = analysis.getCategoryConfidence();
      double boostFraction = SearchScoring.boostFraction(config.getConfidenceWeight(), cc);
      double finalScore = SearchScoring.boosted(baseScore, config.getConfidenceWeight(), cc);

      if (finalScore < config.getScoreCutoff()) continue;

      Map<String, Double> signalContribs = attributeSignals(normSem, normBm25,
                                                            textWeightSum, imageWeightSum,
                                                            textKeywordWeight, ocrKeywordWeight);

      String title = persistenceService.findImage(rf.id())
          .map(ImageAsset::getOriginalFilename).orElse(rf.id().toString());
      RiskLevel riskLevel = persistenceService.findAssessment(rf.id())
          .map(SensitivityAssessment::getRiskLevel).orElse(RiskLevel.SAFE);

      SearchResultItem item = new SearchResultItem(
          rf.id(), title, analysis.getShortSummary(), finalScore,
          analysis.getSourceCategory(), analysis.getSeasonHint(),
          analysis.getContainsPerson(), riskLevel);

      results.add(new MultimodalSearchResult(
          item,
          new ScoreBreakdown(normSem, normBm25, boostFraction, 0.0, finalScore),
          rf.vectorRank(), rf.keywordRank(),
          Collections.unmodifiableMap(signalContribs)));

      if (results.size() >= config.getMaxResults()) break;
    }

    logger().info("Multimodal search returned {} results (vCands={}, bm25Cands={})",
                  results.size(), vectorHits.size(), keywordHits.size());
    return Collections.unmodifiableList(results);
  }

  /**
   * Appends the non-blank text of {@code signals} to the BM25 query and returns
   * the summed weight of the signals that contributed.
   */
  private double appendTerms(StringBuilder bm25Query, List<MultimodalSignal> signals) {
    double weightSum = 0.0;
    for (MultimodalSignal sig : signals) {
      if (sig.textContent() == null || sig.textContent().isBlank()) continue;
      if (!bm25Query.isEmpty()) bm25Query.append(' ');
      bm25Query.append(sig.textContent());
      weightSum += sig.weight();
    }
    return weightSum;
  }

  private boolean matchesCategoryFilters(SemanticAnalysis analysis, Set<String> categoryFilters) {
    if (categoryFilters.isEmpty()) return true;
    String catName = analysis.getSourceCategory() != null
        ? analysis.getSourceCategory().name().toLowerCase() : "";
    return categoryFilters.stream().anyMatch(catName::contains)
        || (analysis.getSecondaryCategories() != null
        && analysis.getSecondaryCategories().stream()
        .anyMatch(sc -> categoryFilters.stream().anyMatch(sc.name().toLowerCase()::contains)));
  }

  /**
   * Splits the semantic share between TEXT and IMAGE_EXAMPLE by their vector weights,
   * and the keyword share between TEXT and OCR_TERMS by the weights of the signals
   * that formed the BM25 query.
   */
  private Map<String, Double> attributeSignals(double normSem, double normBm25,
                                               double textVectorWeight, double imageVectorWeight,
                                               double textKeywordWeight, double ocrKeywordWeight) {
    Map<String, Double> contribs = new LinkedHashMap<>();
    double vectorTotal = textVectorWeight + imageVectorWeight;
    if (vectorTotal > 0 && normSem > 0) {
      if (textVectorWeight > 0) {
        contribs.merge(MultimodalSignal.SignalType.TEXT.name(), normSem * (textVectorWeight / vectorTotal), Double::sum);
      }
      if (imageVectorWeight > 0) {
        contribs.merge(MultimodalSignal.SignalType.IMAGE_EXAMPLE.name(), normSem * (imageVectorWeight / vectorTotal), Double::sum);
      }
    }
    double keywordTotal = textKeywordWeight + ocrKeywordWeight;
    if (keywordTotal > 0 && normBm25 > 0) {
      if (textKeywordWeight > 0) {
        contribs.merge(MultimodalSignal.SignalType.TEXT.name(), normBm25 * (textKeywordWeight / keywordTotal), Double::sum);
      }
      if (ocrKeywordWeight > 0) {
        contribs.merge(MultimodalSignal.SignalType.OCR_TERMS.name(), normBm25 * (ocrKeywordWeight / keywordTotal), Double::sum);
      }
    }
    return contribs;
  }

  /**
   * Accumulates weighted query vectors of one signal type. Empty vectors and vectors
   * whose dimension differs from the first accepted one are skipped and logged, so a
   * failed embedding or a vector from an older model cannot corrupt the sum.
   */
  private final class WeightedSum {

    private final String label;
    private float[] vector;
    private double weightSum;

    private WeightedSum(String label) {
      this.label = label;
    }

    void add(float[] v, double weight) {
      if (!VectorMath.isUsable(v)) {
        logger().warn("Skipping empty {} vector", label);
        return;
      }
      if (vector == null) {
        vector = VectorMath.scaled(v, weight);
      } else if (VectorMath.compatible(vector, v)) {
        VectorMath.addWeightedInPlace(vector, v, weight);
      } else {
        logger().warn("Skipping {} vector with dimension {} (expected {})", label, v.length, vector.length);
        return;
      }
      weightSum += weight;
    }

    boolean isPresent() {
      return vector != null;
    }

    float[] vector() {
      return vector;
    }

    double weightSum() {
      return weightSum;
    }
  }
}
