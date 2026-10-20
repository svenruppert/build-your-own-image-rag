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

  public SearchServiceImpl(QueryUnderstandingService queryUnderstandingService, EmbeddingService embeddingService, VectorIndexService vectorIndexService, PersistenceService persistenceService, KeywordIndexService keywordIndexService) {
    this.queryUnderstandingService = queryUnderstandingService;
    this.embeddingService = embeddingService;
    this.vectorIndexService = vectorIndexService;
    this.persistenceService = persistenceService;
    this.keywordIndexService = keywordIndexService;
  }

  private static List<UUID> vectorIds(List<VectorSearchHit> hits) {
    return hits.stream().map(VectorSearchHit::imageId).toList();
  }

  private static List<UUID> keywordIds(List<KeywordSearchHit> hits) {
    return hits.stream().map(KeywordSearchHit::imageId).toList();
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
    List<VectorSearchHit> vectorHits = VectorMath.isUsable(queryVector)
        ? vectorIndexService.search(queryVector, SearchScoring.vectorCandidates())
        : List.of();
    logger().info("Vector search returned {} candidates for query: {}", vectorHits.size(), plan.getOriginalQuery());

    // Step 3: BM25 keyword search for hybrid retrieval
    List<KeywordSearchHit> keywordHits = keywordIndexService.search(plan.getEmbeddingText(), SearchScoring.keywordCandidates());
    logger().info("Keyword search returned {} candidates", keywordHits.size());

    // Step 4: Reciprocal Rank Fusion — merge vector and keyword rankings
    List<RrfFusion.Candidate> fused = RrfFusion.fuse(vectorIds(vectorHits), keywordIds(keywordHits),
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
        queryVector = embeddingService.embed(query);
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
    List<RrfFusion.Candidate> fused = RrfFusion.fuse(vectorIds(vectorHits), keywordIds(keywordHits),
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
        text.add(embeddingService.embed(sig.textContent()), sig.weight());
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
    List<RrfFusion.Candidate> fused = RrfFusion.fuse(vectorIds(vectorHits), keywordIds(keywordHits),
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

  private static SettingsService settings() {
    return SettingsService.getInstance();
  }
}
