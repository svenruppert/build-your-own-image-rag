package com.svenruppert.imagerag.service.impl;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.imagerag.domain.*;
import com.svenruppert.imagerag.domain.enums.RiskLevel;
import com.svenruppert.imagerag.domain.enums.SeasonHint;
import com.svenruppert.imagerag.dto.KeywordSearchHit;
import com.svenruppert.imagerag.dto.SearchResult;
import com.svenruppert.imagerag.dto.VectorSearchHit;
import com.svenruppert.imagerag.persistence.PersistenceService;
import com.svenruppert.imagerag.service.*;

import java.util.*;

public class SearchServiceImpl
    implements SearchService, HasLogger {

  /**
   * Minimum cosine similarity for a candidate to be included in results.
   * nomic-embed-text typically scores unrelated texts at 0.3–0.5 and related
   * texts at 0.6–0.9. A threshold of 0.45 removes clearly unrelated images
   * while keeping semantically adjacent results.
   */
  private static final double MIN_SCORE = 0.65;

  private static final int VECTOR_CANDIDATES = 50;
  private static final int MAX_RESULTS = 5;
  private static final int RRF_K = 60;

  private final QueryUnderstandingService queryUnderstandingService;
  private final EmbeddingService embeddingService;
  private final VectorIndexService vectorIndexService;
  private final PersistenceService persistenceService;
  private final KeywordIndexService keywordIndexService;

  public SearchServiceImpl(QueryUnderstandingService queryUnderstandingService, EmbeddingService embeddingService, VectorIndexService vectorIndexService, PersistenceService persistenceService, KeywordIndexService keywordIndexService) {
    this.queryUnderstandingService = queryUnderstandingService;
    this.embeddingService = embeddingService;
    this.vectorIndexService = vectorIndexService;
    this.persistenceService = persistenceService;
    this.keywordIndexService = keywordIndexService;
  }

  @Override
  public List<SearchResultItem> search(String naturalLanguageQuery) {
    logger().info("Processing natural language query: {}", naturalLanguageQuery);
    SearchPlan plan = queryUnderstandingService.understand(naturalLanguageQuery);
    return search(plan).items();
  }

  @Override
  public SearchResult search(SearchPlan plan) {
    // Step 1: Embed the semantic query text
    float[] queryVector = embeddingService.embed(plan.getEmbeddingText());

    // Step 2: Vector similarity search — candidates sorted by descending score
    List<VectorSearchHit> vectorHits = vectorIndexService.search(queryVector, VECTOR_CANDIDATES);
    logger().info("Vector search returned {} candidates for query: {}", vectorHits.size(), plan.getOriginalQuery());

    // Step 3: BM25 keyword search for hybrid retrieval
    List<KeywordSearchHit> keywordHits = keywordIndexService.search(plan.getEmbeddingText(), VECTOR_CANDIDATES * 2);
    logger().info("Keyword search returned {} candidates", keywordHits.size());

    int vectorCands = vectorHits.size();
    int keywordCands = keywordHits.size();

    // Step 4: Reciprocal Rank Fusion — merge vector and keyword rankings
    Map<UUID, Double> rrfScores = computeRrfScores(vectorHits, keywordHits);

    // Normalize RRF scores to [0, 1]
    double maxRrf = rrfScores.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);

    // Step 5: Apply score threshold + structural filters.
    double effectiveMin = (plan.getMinScore() != null) ? Math.max(0.0, Math.min(1.0, plan.getMinScore())) : MIN_SCORE;

    // Sort candidates by descending RRF score
    List<UUID> sortedIds = rrfScores.entrySet().stream().sorted(Map.Entry.<UUID, Double>comparingByValue().reversed()).map(Map.Entry::getKey).toList();

    List<SearchResultItem> results = new ArrayList<>();

    for (UUID imageId : sortedIds) {
      double rrfScore = rrfScores.get(imageId);
      double normalizedScore = maxRrf > 0 ? rrfScore / maxRrf : 0.0;

      if (normalizedScore < effectiveMin) {
        logger().debug("RRF score below threshold {} — stopping", effectiveMin);
        break;
      }

      // Privacy gate: only approved images appear in search results
      if (!persistenceService.isApproved(imageId)) {
        logger().debug("Skipping non-approved image {}", imageId);
        continue;
      }

      Optional<SemanticAnalysis> analysisOpt = persistenceService.findAnalysis(imageId);
      if (analysisOpt.isEmpty()) continue;

      SemanticAnalysis analysis = analysisOpt.get();
      SensitivityAssessment assessment = persistenceService.findAssessment(imageId).orElse(null);

      if (!passesFilters(plan, analysis, assessment)) continue;

      String title = persistenceService.findImage(imageId).map(ImageAsset::getOriginalFilename).orElse(imageId.toString());

      RiskLevel riskLevel = assessment != null ? assessment.getRiskLevel() : RiskLevel.SAFE;

      results.add(new SearchResultItem(imageId, title, analysis.getShortSummary(), normalizedScore, analysis.getSourceCategory(), analysis.getSeasonHint(), analysis.getContainsPerson(), riskLevel));
    }

    List<SearchResultItem> finalResults = results.subList(0, Math.min(MAX_RESULTS, results.size()));
    logger().info("Search returned {} results after score+filter pass (threshold={})", finalResults.size(), effectiveMin);
    return new SearchResult(finalResults, vectorCands, keywordCands);
  }

  /**
   * Computes Reciprocal Rank Fusion scores across vector and keyword channels.
   * RRF_score(d) = sum_channel( 1 / (RRF_K + rank_in_channel) )
   */
  private Map<UUID, Double> computeRrfScores(List<VectorSearchHit> vectorHits, List<KeywordSearchHit> keywordHits) {
    Map<UUID, Double> scores = new LinkedHashMap<>();
    for (int i = 0; i < vectorHits.size(); i++) {
      UUID id = vectorHits.get(i).imageId();
      scores.merge(id, 1.0 / (RRF_K + i + 1), Double::sum);
    }
    for (int i = 0; i < keywordHits.size(); i++) {
      UUID id = keywordHits.get(i).imageId();
      scores.merge(id, 1.0 / (RRF_K + i + 1), Double::sum);
    }
    return scores;
  }

  private boolean passesFilters(SearchPlan plan, SemanticAnalysis analysis, SensitivityAssessment assessment) {
    // --- Person presence ---
    if (Boolean.TRUE.equals(plan.getContainsPerson()) && !Boolean.TRUE.equals(analysis.getContainsPerson())) {
      return false;
    }
    if (Boolean.FALSE.equals(plan.getContainsPerson()) && Boolean.TRUE.equals(analysis.getContainsPerson())) {
      return false;
    }

    // --- Vehicle presence ---
    // If the query requires a vehicle, the image must contain one
    if (Boolean.TRUE.equals(plan.getContainsVehicle()) && !Boolean.TRUE.equals(analysis.getContainsVehicle())) {
      return false;
    }

    // --- License plate ---
    // If the query explicitly asks for license plates, filter to images where
    // the vision analysis detected a plate hint
    if (Boolean.TRUE.equals(plan.getContainsLicensePlate()) && !Boolean.TRUE.equals(analysis.getContainsLicensePlateHint())) {
      return false;
    }

    // --- Season ---
    if (plan.getSeasonHint() != null && plan.getSeasonHint() != SeasonHint.UNKNOWN && plan.getSeasonHint() != analysis.getSeasonHint()) {
      return false;
    }

    // --- Category group ---
    // Filter by coarse category group: an image passes if its fine-grained SourceCategory
    // maps to the same CategoryGroup that the search plan requests.
    if (plan.getCategoryGroup() != null) {
      if (CategoryRegistry.getGroup(analysis.getSourceCategory()) != plan.getCategoryGroup()) {
        return false;
      }
    }

    // --- Privacy level ---
    if (plan.getPrivacyLevel() != null && assessment != null && assessment.getRiskLevel().ordinal() > plan.getPrivacyLevel().ordinal()) {
      return false;
    }

    return true;
  }
}
