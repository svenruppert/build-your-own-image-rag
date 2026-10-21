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
package com.svenruppert.imagerag.service.impl;

import com.svenruppert.imagerag.settings.SettingsService;
import com.svenruppert.imagerag.settings.Settings;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.imagerag.domain.*;
import com.svenruppert.imagerag.domain.enums.RetrievalMode;
import com.svenruppert.imagerag.domain.enums.RiskLevel;
import com.svenruppert.imagerag.domain.enums.SeasonHint;
import com.svenruppert.imagerag.dto.*;
import com.svenruppert.imagerag.persistence.PersistenceService;
import com.svenruppert.imagerag.service.*;
import com.svenruppert.imagerag.vector.RrfFusion;
import com.svenruppert.imagerag.vector.VectorMath;

import java.util.*;

public class SearchServiceImpl
    implements SearchService, HasLogger {

  /**
   * Fusion weight of each channel in the standard (unweighted) search.
   */
  private static final double STANDARD_CHANNEL_WEIGHT = 1.0;

  private final QueryUnderstandingService queryUnderstandingService;
  private final EmbeddingService embeddingService;
  private final VectorIndexService vectorIndexService;
  private final PersistenceService persistenceService;
  private final KeywordIndexService keywordIndexService;
  private final MultimodalSearchExecutor multimodalSearch;

  public SearchServiceImpl(QueryUnderstandingService queryUnderstandingService, EmbeddingService embeddingService, VectorIndexService vectorIndexService, PersistenceService persistenceService, KeywordIndexService keywordIndexService) {
    this.queryUnderstandingService = queryUnderstandingService;
    this.embeddingService = embeddingService;
    this.vectorIndexService = vectorIndexService;
    this.persistenceService = persistenceService;
    this.keywordIndexService = keywordIndexService;
    this.multimodalSearch = new MultimodalSearchExecutor(embeddingService, vectorIndexService,
                                                         persistenceService, keywordIndexService);
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
    float[] queryVector = embedQuery(plan.getEmbeddingText());

    // Step 2: Vector similarity search — candidates sorted by descending score
    List<VectorSearchHit> vectorHits = VectorMath.isUsable(queryVector)
        ? vectorIndexService.search(queryVector, SearchScoring.vectorCandidates())
        : List.of();
    logger().info("Vector search returned {} candidates for query: {}", vectorHits.size(), plan.getOriginalQuery());

    // Step 3: BM25 keyword search for hybrid retrieval
    List<KeywordSearchHit> keywordHits = keywordIndexService.search(plan.getEmbeddingText(), SearchScoring.keywordCandidates());
    logger().info("Keyword search returned {} candidates", keywordHits.size());

    // Step 4: Reciprocal Rank Fusion — merge vector and keyword rankings
    List<RrfFusion.Candidate> fused = RrfFusion.fuse(SearchScoring.vectorIds(vectorHits), SearchScoring.keywordIds(keywordHits),
                                                     STANDARD_CHANNEL_WEIGHT, STANDARD_CHANNEL_WEIGHT,
                                                     SearchScoring.rrfK());
    double norm = RrfFusion.normalizer(fused);

    // Step 5: Apply score threshold + structural filters.
    double effectiveMin = (plan.getMinScore() != null) ? Math.clamp(plan.getMinScore(), 0.0, 1.0) : settings().get(Settings.SEARCH_MIN_SCORE);

    List<SearchResultItem> results = new ArrayList<>();
    for (RrfFusion.Candidate candidate : fused) {
      UUID imageId = candidate.id();
      double normalizedScore = candidate.total() / norm;

      // Category-confidence boost: when the query targets a specific category group and
      // the image's stored confidence for that primary category is known, reward high-
      // confidence classifications so they rank above lower-confidence results in the
      // same score band.
      if (plan.getCategoryGroup() != null) {
        Optional<SemanticAnalysis> boostAnalysis = persistenceService.findAnalysis(imageId);
        if (boostAnalysis.isPresent()) {
          CategoryConfidence cc = boostAnalysis.get().getCategoryConfidence();
          if (cc != null
              && CategoryRegistry.getGroup(cc.getPrimaryCategory()) == plan.getCategoryGroup()) {
            normalizedScore = SearchScoring.boosted(normalizedScore,
                                                    SearchScoring.standardConfidenceWeight(), cc);
          }
        }
      }

      if (normalizedScore < effectiveMin) {
        logger().debug("RRF score below threshold {} — stopping", effectiveMin);
        break;
      }

      // Privacy gate: only approved, non-archived images appear in search results
      if (!SearchScoring.isSearchable(persistenceService, imageId)) {
        logger().debug("Skipping non-searchable image {}", imageId);
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

    List<SearchResultItem> finalResults = results.subList(0, Math.min(settings().get(Settings.SEARCH_MAX_RESULTS), results.size()));
    logger().info("Search returned {} results after score+filter pass (threshold={})", finalResults.size(), effectiveMin);
    return new SearchResult(finalResults, vectorHits.size(), keywordHits.size());
  }

  // ── Search Tuning Lab ─────────────────────────────────────────────────────

  @Override
  public TuningSearchResponse searchWithTuning(String query, SearchTuningConfig config) {
    if (query == null || query.isBlank())
      return new TuningSearchResponse(List.of(), 0, 0, null, 0);
    if (config == null) config = new SearchTuningConfig();

    logger().info("Tuning search: query='{}' config={}", query, config);

    // ── Step 1a: Query-intent detection ──────────────────────────────────
    SearchScoring.EffectiveWeights weights = SearchScoring.effectiveWeights(query, config);
    if (weights.intent() != null) {
      logger().debug("Intent detected: {} → sem={} bm25={}", weights.intent(),
                     weights.semantic(), weights.keyword());
    }

    // ── Step 1b: resolve query vector (QBE takes priority over text embed) ─
    float[] queryVector = null;
    if (config.getRetrievalMode() != RetrievalMode.BM25_ONLY) {
      UUID qbeId = config.getQueryByExampleImageId();
      if (qbeId != null) {
        queryVector = persistenceService.findRawVector(qbeId).filter(VectorMath::isUsable).orElse(null);
        if (queryVector != null) {
          logger().debug("QBE mode — using stored vector for imageId={}", qbeId);
        } else {
          logger().warn("QBE imageId={} has no stored vector — falling back to text embed", qbeId);
        }
      }
      if (queryVector == null) {
        queryVector = embedQuery(query);
      }
    }

    // ── Step 2: retrieve vector candidates (skip for BM25_ONLY) ──────────
    List<VectorSearchHit> vectorHits = List.of();
    if (config.getRetrievalMode() != RetrievalMode.BM25_ONLY && VectorMath.isUsable(queryVector)) {
      vectorHits = vectorIndexService.search(queryVector, SearchScoring.vectorCandidates(),
                                             config.getSimilarityFunction());
    }

    // ── Step 3: retrieve BM25 candidates (skip for SEMANTIC_ONLY) ────────
    List<KeywordSearchHit> keywordHits = List.of();
    if (config.getRetrievalMode() != RetrievalMode.SEMANTIC_ONLY) {
      keywordHits = keywordIndexService.search(query, SearchScoring.keywordCandidates());
    }

    // ── Step 4: weighted RRF fusion + normalisation ───────────────────────
    List<RrfFusion.Candidate> fused = RrfFusion.fuse(SearchScoring.vectorIds(vectorHits), SearchScoring.keywordIds(keywordHits),
                                                     weights.semantic(), weights.keyword(),
                                                     SearchScoring.rrfK());
    double norm = RrfFusion.normalizer(fused);

    // ── Step 5: prepare relevance-feedback session snapshot ───────────────
    final FeedbackSession feedbackSnap =
        (config.isFeedbackEnabled() && config.getFeedbackSession() != null
            && !config.getFeedbackSession().isEmpty())
            ? config.getFeedbackSession()   // already a snapshot from the view
            : null;
    final double maxFbScore = feedbackSnap != null ? feedbackSnap.maxPositiveScore() : 0.0;
    final double fbWeight = config.getFeedbackWeight();
    final int fbUsedCount = feedbackSnap != null ? feedbackSnap.size() : 0;

    // ── Step 6: apply confidence boost, feedback, cutoff, privacy gate ────
    List<TuningSearchResult> results = new ArrayList<>();
    for (RrfFusion.Candidate rf : fused) {
      if (!SearchScoring.isSearchable(persistenceService, rf.id())) continue;

      double normSem = rf.semanticRaw() / norm;
      double normBm25 = rf.keywordRaw() / norm;
      double baseScore = normSem + normBm25;

      // Category confidence boost
      Optional<SemanticAnalysis> analysisOpt = persistenceService.findAnalysis(rf.id());
      CategoryConfidence cc = analysisOpt.map(SemanticAnalysis::getCategoryConfidence).orElse(null);
      double boostFraction = SearchScoring.boostFraction(config.getConfidenceWeight(), cc);
      double afterBoost = SearchScoring.boosted(baseScore, config.getConfidenceWeight(), cc);

      // Relevance feedback contribution
      double feedbackContrib = 0.0;
      if (feedbackSnap != null && maxFbScore > 0) {
        float[] candidateVec = persistenceService.findRawVector(rf.id()).orElse(null);
        if (VectorMath.isUsable(candidateVec)) {
          double rawFb = feedbackSnap.computeRawScore(candidateVec);
          feedbackContrib = fbWeight * (rawFb / maxFbScore);  // normalised, weighted
        }
      }
      double finalScore = Math.min(1.0, Math.max(0.0, afterBoost + feedbackContrib));

      // Score cutoff (applied after feedback adjustment)
      if (finalScore < config.getScoreCutoff()) continue;

      // Build SearchResultItem
      if (analysisOpt.isEmpty()) continue;
      SemanticAnalysis analysis = analysisOpt.get();
      String title = persistenceService.findImage(rf.id())
          .map(ImageAsset::getOriginalFilename).orElse(rf.id().toString());
      RiskLevel riskLevel = persistenceService.findAssessment(rf.id())
          .map(SensitivityAssessment::getRiskLevel).orElse(RiskLevel.SAFE);

      SearchResultItem item = new SearchResultItem(
          rf.id(), title, analysis.getShortSummary(), finalScore,
          analysis.getSourceCategory(), analysis.getSeasonHint(),
          analysis.getContainsPerson(), riskLevel);

      results.add(new TuningSearchResult(
          item,
          new ScoreBreakdown(normSem, normBm25, boostFraction, feedbackContrib, finalScore),
          rf.vectorRank(),
          rf.keywordRank()));

      if (results.size() >= config.getMaxResults()) break;
    }

    logger().info("Tuning search returned {} results (vectorCands={}, bm25Cands={}, intent={}, fbEntries={})",
                  results.size(), vectorHits.size(), keywordHits.size(), weights.intent(), fbUsedCount);
    return new TuningSearchResponse(
        Collections.unmodifiableList(results),
        vectorHits.size(),
        keywordHits.size(),
        weights.intent(),
        fbUsedCount);
  }

  // ── Multimodal Search ─────────────────────────────────────────────────────

  @Override
  public List<MultimodalSearchResult> multimodalSearch(MultimodalSearchConfig config) {
    return multimodalSearch.search(config);
  }

  // ── Structural filters ────────────────────────────────────────────────────

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
    // An image passes if its primary OR any secondary SourceCategory maps to the
    // requested CategoryGroup.  This ensures multi-category images are discoverable
    // via any of their assigned categories.
    if (plan.getCategoryGroup() != null) {
      boolean primaryMatches =
          CategoryRegistry.getGroup(analysis.getSourceCategory()) == plan.getCategoryGroup();
      boolean secondaryMatches = analysis.getSecondaryCategories().stream()
          .anyMatch(sc -> CategoryRegistry.getGroup(sc) == plan.getCategoryGroup());
      if (!primaryMatches && !secondaryMatches) {
        return false;
      }
    }

    // --- Privacy level ---
    if (plan.getPrivacyLevel() != null && assessment != null && assessment.getRiskLevel().ordinal() > plan.getPrivacyLevel().ordinal()) {
      return false;
    }

    return true;
  }

  /**
   * Embeds a query; on failure the reason is logged and an empty vector is returned,
   * which the callers treat as "no vector retrieval" (keyword retrieval still runs).
   */
  private float[] embedQuery(String text) {
    return embeddingService.embed(text)
        .peekFailure(reason -> logger().warn("Query embedding unavailable: {}", reason))
        .getOrElse(new float[0]);
  }

  private static SettingsService settings() {
    return SettingsService.getInstance();
  }
}
