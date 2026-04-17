package com.svenruppert.imagerag.service.impl;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.imagerag.domain.*;
import com.svenruppert.imagerag.domain.enums.QueryIntentType;
import com.svenruppert.imagerag.domain.enums.RiskLevel;
import com.svenruppert.imagerag.domain.enums.RetrievalMode;
import com.svenruppert.imagerag.domain.enums.SeasonHint;
import com.svenruppert.imagerag.dto.*;
import com.svenruppert.imagerag.persistence.PersistenceService;
import com.svenruppert.imagerag.service.*;

import java.util.*;

public class SearchServiceImpl
    implements SearchService, HasLogger {

  /**
   * Minimum normalised RRF score for a candidate to be included in results.
   * bge-m3 produces dense 1024-dim vectors; typical unrelated pairs score well
   * below 0.5 after RRF normalisation, while semantically related pairs sit
   * between 0.6–1.0.  A threshold of 0.65 removes clearly unrelated images
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
    double effectiveMin = (plan.getMinScore() != null) ? Math.clamp(plan.getMinScore(), 0.0, 1.0) : MIN_SCORE;

    // Sort candidates by descending RRF score
    List<UUID> sortedIds = rrfScores.entrySet().stream().sorted(Map.Entry.<UUID, Double>comparingByValue().reversed()).map(Map.Entry::getKey).toList();

    List<SearchResultItem> results = new ArrayList<>();

    for (UUID imageId : sortedIds) {
      double rrfScore = rrfScores.get(imageId);
      double normalizedScore = maxRrf > 0 ? rrfScore / maxRrf : 0.0;

      // Category-confidence boost: when the query targets a specific category group and
      // the image's stored confidence for that primary category is known, reward high-
      // confidence classifications by up to +15 % so they rank above lower-confidence
      // results in the same score band.
      if (plan.getCategoryGroup() != null) {
        Optional<SemanticAnalysis> boostAnalysis = persistenceService.findAnalysis(imageId);
        if (boostAnalysis.isPresent()) {
          CategoryConfidence cc =
              boostAnalysis.get().getCategoryConfidence();
          if (cc != null
              && CategoryRegistry.getGroup(cc.getPrimaryCategory()) == plan.getCategoryGroup()) {
            normalizedScore = Math.min(1.0, normalizedScore * (1.0 + 0.15 * cc.getPrimaryScore()));
          }
        }
      }

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

  // ── Search Tuning Lab ─────────────────────────────────────────────────────

  private static final QueryIntentResolver INTENT_RESOLVER = new QueryIntentResolver();

  @Override
  public TuningSearchResponse searchWithTuning(String query, SearchTuningConfig config) {
    if (query == null || query.isBlank())
      return new TuningSearchResponse(List.of(), 0, 0, null, 0);
    if (config == null) config = new SearchTuningConfig();

    logger().info("Tuning search: query='{}' config={}", query, config);

    // ── Step 1a: Query-intent detection ──────────────────────────────────
    QueryIntentType detectedIntent = null;
    double effectiveSemanticWeight = config.getSemanticWeight();
    double effectiveBm25Weight     = config.getBm25Weight();
    if (config.isQueryIntentEnabled()) {
      detectedIntent = INTENT_RESOLVER.resolve(query);
      double[] adjusted = INTENT_RESOLVER.adjustedWeights(
          detectedIntent, effectiveSemanticWeight, effectiveBm25Weight);
      effectiveSemanticWeight = adjusted[0];
      effectiveBm25Weight     = adjusted[1];
      logger().debug("Intent detected: {} → sem={} bm25={}", detectedIntent,
          effectiveSemanticWeight, effectiveBm25Weight);
    }

    // ── Step 1b: resolve query vector (QBE takes priority over text embed) ─
    float[] queryVector = null;
    if (config.getRetrievalMode() != RetrievalMode.BM25_ONLY) {
      UUID qbeId = config.getQueryByExampleImageId();
      if (qbeId != null) {
        queryVector = persistenceService.findRawVector(qbeId).orElse(null);
        if (queryVector != null) {
          logger().debug("QBE mode — using stored vector for imageId={}", qbeId);
        } else {
          logger().warn("QBE imageId={} has no stored vector — falling back to text embed", qbeId);
        }
      }
      if (queryVector == null) {
        queryVector = embeddingService.embed(query);
      }
    }

    // ── Step 2: retrieve vector candidates (skip for BM25_ONLY) ──────────
    List<VectorSearchHit> vectorHits = List.of();
    if (config.getRetrievalMode() != RetrievalMode.BM25_ONLY
        && queryVector != null && queryVector.length > 0) {
      vectorHits = vectorIndexService.search(queryVector, VECTOR_CANDIDATES,
                                             config.getSimilarityFunction());
    }

    // ── Step 3: retrieve BM25 candidates (skip for SEMANTIC_ONLY) ────────
    List<KeywordSearchHit> keywordHits = List.of();
    if (config.getRetrievalMode() != RetrievalMode.SEMANTIC_ONLY) {
      keywordHits = keywordIndexService.search(query, VECTOR_CANDIDATES * 2);
    }

    // ── Step 4: build rank maps and collect unique candidate IDs ──────────
    Map<UUID, Integer> vectorRankMap = new LinkedHashMap<>();
    for (int i = 0; i < vectorHits.size(); i++) vectorRankMap.put(vectorHits.get(i).imageId(), i);

    Map<UUID, Integer> bm25RankMap = new LinkedHashMap<>();
    for (int i = 0; i < keywordHits.size(); i++) bm25RankMap.put(keywordHits.get(i).imageId(), i);

    Set<UUID> allIds = new LinkedHashSet<>();
    allIds.addAll(vectorRankMap.keySet());
    allIds.addAll(bm25RankMap.keySet());

    // ── Step 5: weighted RRF fusion + track per-channel contributions ─────
    record RawFusion(UUID id, double semRaw, double bm25Raw, int vRank, int kRank) {
      double total() {
        return semRaw + bm25Raw;
      }
    }
    final double semW  = effectiveSemanticWeight;
    final double bm25W = effectiveBm25Weight;
    List<RawFusion> fused = new ArrayList<>(allIds.size());
    for (UUID id : allIds) {
      int vRank = vectorRankMap.getOrDefault(id, -1);
      int kRank = bm25RankMap.getOrDefault(id, -1);
      double semRaw  = vRank >= 0 ? semW  / (RRF_K + vRank + 1) : 0.0;
      double bm25Raw = kRank >= 0 ? bm25W / (RRF_K + kRank + 1) : 0.0;
      fused.add(new RawFusion(id, semRaw, bm25Raw, vRank, kRank));
    }

    // ── Step 6: normalise to [0,1] relative to max weighted-RRF score ─────
    double maxRrf = fused.stream().mapToDouble(RawFusion::total).max().orElse(1.0);
    if (maxRrf <= 0) maxRrf = 1.0;
    final double norm = maxRrf;

    // Sort descending by total score before iterating
    fused.sort(Comparator.comparingDouble(RawFusion::total).reversed());

    // ── Step 7: prepare relevance-feedback session snapshot ───────────────
    final FeedbackSession feedbackSnap =
        (config.isFeedbackEnabled() && config.getFeedbackSession() != null
            && !config.getFeedbackSession().isEmpty())
        ? config.getFeedbackSession()   // already a snapshot from the view
        : null;
    final double maxFbScore  = feedbackSnap != null ? feedbackSnap.maxPositiveScore() : 0.0;
    final double fbWeight    = config.getFeedbackWeight();
    final int    fbUsedCount = feedbackSnap != null ? feedbackSnap.size() : 0;

    // ── Step 8: apply confidence boost, feedback, cutoff, privacy gate ────
    List<TuningSearchResult> results = new ArrayList<>();
    for (RawFusion rf : fused) {
      if (!persistenceService.isApproved(rf.id())) continue;

      double normSem  = rf.semRaw()  / norm;
      double normBm25 = rf.bm25Raw() / norm;
      double baseScore = normSem + normBm25;

      // Category confidence boost
      double boostFraction = 0.0;
      Optional<SemanticAnalysis> analysisOpt = persistenceService.findAnalysis(rf.id());
      if (analysisOpt.isPresent() && config.getConfidenceWeight() > 0) {
        CategoryConfidence cc = analysisOpt.get().getCategoryConfidence();
        if (cc != null) {
          boostFraction = config.getConfidenceWeight() * cc.getPrimaryScore();
        }
      }
      double afterBoost = Math.min(1.0, baseScore * (1.0 + boostFraction));

      // Relevance feedback contribution
      double feedbackContrib = 0.0;
      if (feedbackSnap != null && maxFbScore > 0) {
        float[] candidateVec = persistenceService.findRawVector(rf.id()).orElse(null);
        if (candidateVec != null && candidateVec.length > 0) {
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
          .map(a -> a.getRiskLevel()).orElse(RiskLevel.SAFE);

      SearchResultItem item = new SearchResultItem(
          rf.id(), title, analysis.getShortSummary(), finalScore,
          analysis.getSourceCategory(), analysis.getSeasonHint(),
          analysis.getContainsPerson(), riskLevel);

      results.add(new TuningSearchResult(
          item,
          new ScoreBreakdown(normSem, normBm25, boostFraction, feedbackContrib, finalScore),
          rf.vRank(),
          rf.kRank()));

      if (results.size() >= config.getMaxResults()) break;
    }

    logger().info("Tuning search returned {} results (vectorCands={}, bm25Cands={}, intent={}, fbEntries={})",
                  results.size(), vectorHits.size(), keywordHits.size(), detectedIntent, fbUsedCount);
    return new TuningSearchResponse(
        Collections.unmodifiableList(results),
        vectorHits.size(),
        keywordHits.size(),
        detectedIntent,
        fbUsedCount);
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
}
