package com.svenruppert.imagerag.service.impl;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.imagerag.domain.*;
import com.svenruppert.imagerag.domain.enums.RiskLevel;
import com.svenruppert.imagerag.domain.enums.SeasonHint;
import com.svenruppert.imagerag.domain.enums.SourceCategory;
import com.svenruppert.imagerag.dto.VectorSearchHit;
import com.svenruppert.imagerag.persistence.PersistenceService;
import com.svenruppert.imagerag.service.EmbeddingService;
import com.svenruppert.imagerag.service.QueryUnderstandingService;
import com.svenruppert.imagerag.service.SearchService;
import com.svenruppert.imagerag.service.VectorIndexService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SearchServiceImpl
    implements SearchService, HasLogger {

  /**
   * Minimum cosine similarity for a candidate to be included in results.
   * nomic-embed-text typically scores unrelated texts at 0.3–0.5 and related
   * texts at 0.6–0.9. A threshold of 0.45 removes clearly unrelated images
   * while keeping semantically adjacent results.
   */
  private static final double MIN_SCORE = 0.45;

  private static final int VECTOR_CANDIDATES = 50;
  private static final int MAX_RESULTS       = 20;

  private final QueryUnderstandingService queryUnderstandingService;
  private final EmbeddingService          embeddingService;
  private final VectorIndexService        vectorIndexService;
  private final PersistenceService        persistenceService;

  public SearchServiceImpl(QueryUnderstandingService queryUnderstandingService,
                           EmbeddingService embeddingService,
                           VectorIndexService vectorIndexService,
                           PersistenceService persistenceService) {
    this.queryUnderstandingService = queryUnderstandingService;
    this.embeddingService          = embeddingService;
    this.vectorIndexService        = vectorIndexService;
    this.persistenceService        = persistenceService;
  }

  @Override
  public List<SearchResultItem> search(String naturalLanguageQuery) {
    logger().info("Processing natural language query: {}", naturalLanguageQuery);
    SearchPlan plan = queryUnderstandingService.understand(naturalLanguageQuery);
    return search(plan);
  }

  @Override
  public List<SearchResultItem> search(SearchPlan plan) {
    // Step 1: Embed the semantic query text
    float[] queryVector = embeddingService.embed(plan.getEmbeddingText());

    // Step 2: Vector similarity search — candidates are sorted by descending score
    List<VectorSearchHit> candidates = vectorIndexService.search(queryVector, VECTOR_CANDIDATES);
    logger().info("Vector search returned {} candidates for query: {}",
                  candidates.size(), plan.getOriginalQuery());

    // Step 3: Apply score threshold + structural filters.
    // The effective threshold uses the plan's user-supplied value when present;
    // falls back to the server default (MIN_SCORE = 0.45) when unset.
    // The value is clamped to [0.0, 1.0] server-side regardless of UI input.
    double effectiveMin = (plan.getMinScore() != null)
        ? Math.max(0.0, Math.min(1.0, plan.getMinScore()))
        : MIN_SCORE;
    List<SearchResultItem> results = new ArrayList<>();

    for (VectorSearchHit hit : candidates) {
      // Candidates are sorted descending — break as soon as score drops below threshold
      if (hit.score() < effectiveMin) {
        logger().debug("Score below threshold {} — stopping early after {} candidates",
                       effectiveMin, results.size());
        break;
      }

      UUID imageId = hit.imageId();

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

      String title = persistenceService.findImage(imageId)
          .map(ImageAsset::getOriginalFilename)
          .orElse(imageId.toString());

      RiskLevel riskLevel = assessment != null ? assessment.getRiskLevel() : RiskLevel.SAFE;

      results.add(new SearchResultItem(
          imageId,
          title,
          analysis.getShortSummary(),
          hit.score(),
          analysis.getSourceCategory(),
          analysis.getSeasonHint(),
          analysis.getContainsPerson(),
          riskLevel
      ));
    }

    logger().info("Search returned {} results after score+filter pass (threshold={})",
                  results.size(), effectiveMin);
    return results.subList(0, Math.min(MAX_RESULTS, results.size()));
  }

  private boolean passesFilters(SearchPlan plan, SemanticAnalysis analysis,
                                SensitivityAssessment assessment) {
    // --- Person presence ---
    if (Boolean.TRUE.equals(plan.getContainsPerson())
        && !Boolean.TRUE.equals(analysis.getContainsPerson())) {
      return false;
    }
    if (Boolean.FALSE.equals(plan.getContainsPerson())
        && Boolean.TRUE.equals(analysis.getContainsPerson())) {
      return false;
    }

    // --- Vehicle presence ---
    // If the query requires a vehicle, the image must contain one
    if (Boolean.TRUE.equals(plan.getContainsVehicle())
        && !Boolean.TRUE.equals(analysis.getContainsVehicle())) {
      return false;
    }

    // --- License plate ---
    // If the query explicitly asks for license plates, filter to images where
    // the vision analysis detected a plate hint
    if (Boolean.TRUE.equals(plan.getContainsLicensePlate())
        && !Boolean.TRUE.equals(analysis.getContainsLicensePlateHint())) {
      return false;
    }

    // --- Season ---
    if (plan.getSeasonHint() != null
        && plan.getSeasonHint() != SeasonHint.UNKNOWN
        && plan.getSeasonHint() != analysis.getSeasonHint()) {
      return false;
    }

    // --- Source category ---
    if (plan.getSourceCategory() != null
        && plan.getSourceCategory() != SourceCategory.UNKNOWN
        && plan.getSourceCategory() != analysis.getSourceCategory()) {
      return false;
    }

    // --- Privacy level ---
    if (plan.getPrivacyLevel() != null && assessment != null
        && assessment.getRiskLevel().ordinal() > plan.getPrivacyLevel().ordinal()) {
      return false;
    }

    return true;
  }
}
