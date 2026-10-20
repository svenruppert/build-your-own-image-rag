package com.svenruppert.imagerag.service.impl;

import com.svenruppert.imagerag.settings.SettingsService;
import com.svenruppert.imagerag.settings.Settings;
import com.svenruppert.imagerag.domain.CategoryConfidence;
import com.svenruppert.imagerag.domain.ImageAsset;
import com.svenruppert.imagerag.domain.SearchTuningConfig;
import com.svenruppert.imagerag.domain.SemanticAnalysis;
import com.svenruppert.imagerag.domain.SensitivityAssessment;
import com.svenruppert.imagerag.domain.enums.RetrievalMode;
import com.svenruppert.imagerag.dto.KeywordSearchHit;
import com.svenruppert.imagerag.dto.VectorSearchHit;
import com.svenruppert.imagerag.dto.WhyNotFoundAnalysis;
import com.svenruppert.imagerag.persistence.PersistenceService;
import com.svenruppert.imagerag.service.EmbeddingService;
import com.svenruppert.imagerag.service.KeywordIndexService;
import com.svenruppert.imagerag.service.VectorIndexService;
import com.svenruppert.imagerag.service.WhyNotFoundService;
import com.svenruppert.imagerag.vector.RrfFusion;
import com.svenruppert.imagerag.vector.VectorMath;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Produces a {@link WhyNotFoundAnalysis} by re-running the relevant pipeline
 * sub-steps for a specific image against a specific query.
 * <p>No LLM calls are made.  The analysis covers:
 * <ul>
 *   <li>Approval and archive state</li>
 *   <li>Vector availability</li>
 *   <li>Semantic score (cosine between query embedding and image vector)</li>
 *   <li>BM25 score (keyword index lookup)</li>
 *   <li>Fused score, computed with the same weighted RRF, normalisation and
 *       confidence boost as the Search Tuning Lab ({@link SearchScoring})</li>
 *   <li>Score cutoff comparison</li>
 *   <li>Structural exclusions (category, risk, visibility)</li>
 * </ul>
 * Relevance feedback is not part of the estimate.
 */
public class WhyNotFoundServiceImpl
    implements WhyNotFoundService {

  private final PersistenceService persistenceService;
  private final EmbeddingService embeddingService;
  private final VectorIndexService vectorIndexService;
  private final KeywordIndexService keywordIndexService;

  public WhyNotFoundServiceImpl(PersistenceService persistenceService,
                                EmbeddingService embeddingService,
                                VectorIndexService vectorIndexService,
                                KeywordIndexService keywordIndexService) {
    this.persistenceService = persistenceService;
    this.embeddingService = embeddingService;
    this.vectorIndexService = vectorIndexService;
    this.keywordIndexService = keywordIndexService;
  }

  @Override
  public WhyNotFoundAnalysis analyze(UUID imageId, String query, SearchTuningConfig config) {

    // ── Basic state checks ────────────────────────────────────────────────
    Optional<ImageAsset> assetOpt = persistenceService.findImage(imageId);
    String filename = assetOpt.map(ImageAsset::getOriginalFilename)
        .orElse(imageId.toString());

    boolean archived = SearchScoring.isArchived(persistenceService, imageId);
    boolean approved = persistenceService.isApproved(imageId);

    List<String> exclusionReasons = new ArrayList<>();
    List<String> diagnosticNotes = new ArrayList<>();

    if (archived) {
      exclusionReasons.add("Image is archived (soft-deleted) — not visible in any search.");
    }
    if (!approved) {
      exclusionReasons.add("Image is not approved for search (locked or pending approval).");
    }

    // ── Vector availability ───────────────────────────────────────────────
    Optional<float[]> rawVec = persistenceService.findRawVector(imageId).filter(VectorMath::isUsable);
    boolean vectorAvailable = rawVec.isPresent();
    if (!vectorAvailable) {
      exclusionReasons.add("No embedding vector found — image may not have been indexed yet.");
    }

    boolean hasQuery = query != null && !query.isBlank();

    // ── Semantic channel ──────────────────────────────────────────────────
    double semanticScore = 0.0;
    List<VectorSearchHit> vectorHits = List.of();
    if (hasQuery && config.getRetrievalMode() != RetrievalMode.BM25_ONLY) {
      try {
        float[] queryVec = resolveQueryVector(query, config);
        if (!VectorMath.isUsable(queryVec)) {
          diagnosticNotes.add("Query embedding unavailable — semantic channel skipped.");
        } else {
          vectorHits = vectorIndexService.search(queryVec, SearchScoring.vectorCandidates(),
                                                 config.getSimilarityFunction());
          if (vectorAvailable) {
            float[] imageVec = rawVec.get();
            if (!VectorMath.compatible(queryVec, imageVec)) {
              exclusionReasons.add(String.format(
                  "Image vector dimension %d differs from query embedding dimension %d — "
                      + "rebuild the vector index after changing the embedding model.",
                  imageVec.length, queryVec.length));
            }
            semanticScore = VectorMath.cosine(queryVec, imageVec);
          }
          int vectorRank = indexOf(vectorHits.stream().map(VectorSearchHit::imageId).toList(), imageId);
          if (vectorRank < 0) {
            diagnosticNotes.add(String.format(
                "Not in top %d vector candidates (semantic score %.3f — below retrieval threshold).",
                SearchScoring.vectorCandidates(), semanticScore));
          } else {
            diagnosticNotes.add(String.format(
                "Vector rank: %d of %d candidates (semantic score %.3f).",
                vectorRank + 1, vectorHits.size(), semanticScore));
          }
        }
      } catch (Exception e) {
        diagnosticNotes.add("Could not compute semantic score: " + e.getMessage());
      }
    }

    // ── Keyword channel ───────────────────────────────────────────────────
    double bm25Score = 0.0;
    List<KeywordSearchHit> keywordHits = List.of();
    if (hasQuery && config.getRetrievalMode() != RetrievalMode.SEMANTIC_ONLY) {
      try {
        keywordHits = keywordIndexService.search(query, SearchScoring.keywordCandidates());
        for (KeywordSearchHit hit : keywordHits) {
          if (imageId.equals(hit.imageId())) {
            bm25Score = hit.score();
            break;
          }
        }
        if (bm25Score <= 0) {
          diagnosticNotes.add(String.format(
              "Not in top %d BM25 keyword candidates — no keyword overlap with query.",
              SearchScoring.keywordCandidates()));
        } else {
          diagnosticNotes.add(String.format("BM25 raw score: %.3f", bm25Score));
        }
      } catch (Exception e) {
        diagnosticNotes.add("Could not compute BM25 score: " + e.getMessage());
      }
    }

    // ── Fused score — same formula as the tuning search ───────────────────
    double estimatedFinal = 0.0;
    if (hasQuery) {
      SearchScoring.EffectiveWeights weights = SearchScoring.effectiveWeights(query, config);
      List<RrfFusion.Candidate> fused = RrfFusion.fuse(
          vectorHits.stream().map(VectorSearchHit::imageId).toList(),
          keywordHits.stream().map(KeywordSearchHit::imageId).toList(),
          weights.semantic(), weights.keyword(), SearchScoring.rrfK());
      double norm = RrfFusion.normalizer(fused);
      Optional<RrfFusion.Candidate> candidate = fused.stream()
          .filter(c -> c.id().equals(imageId)).findFirst();
      if (candidate.isPresent()) {
        double baseScore = candidate.get().total() / norm;
        CategoryConfidence cc = persistenceService.findAnalysis(imageId)
            .map(SemanticAnalysis::getCategoryConfidence).orElse(null);
        double boostFraction = SearchScoring.boostFraction(config.getConfidenceWeight(), cc);
        estimatedFinal = SearchScoring.boosted(baseScore, config.getConfidenceWeight(), cc);
        if (boostFraction > 0) {
          diagnosticNotes.add(String.format(
              "Category confidence %.2f → confidence boost ×%.3f",
              cc.getPrimaryScore(), 1.0 + boostFraction));
        }
      } else {
        diagnosticNotes.add("Image is in neither candidate list — fused score is 0.");
      }
      if (config.isFeedbackEnabled()) {
        diagnosticNotes.add("Relevance feedback is not included in this estimate.");
      }
    }

    boolean aboveThreshold = estimatedFinal >= config.getScoreCutoff();

    if (!aboveThreshold && vectorAvailable && approved && !archived) {
      exclusionReasons.add(String.format(
          "Estimated score %.3f is below the active cutoff %.2f.",
          estimatedFinal, config.getScoreCutoff()));
    }

    // ── Structural filter analysis ────────────────────────────────────────
    analyseStructuralFilters(imageId, diagnosticNotes);

    // ── Analysis / category notes ─────────────────────────────────────────
    persistenceService.findAnalysis(imageId).ifPresent(analysis -> {
      if (analysis.getSourceCategory() != null) {
        diagnosticNotes.add("Primary category: " + analysis.getSourceCategory().name());
      }
      var conf = analysis.getCategoryConfidence();
      if (conf != null && conf.isAmbiguous(SettingsService.getInstance().get(Settings.WHY_NOT_FOUND_AMBIGUITY_THRESHOLD))) {
        conf.getBetterAlternative().ifPresent(alt ->
                                                  diagnosticNotes.add(String.format(
                                                      "Ambiguous category confidence — alternative: %s (%.0f%%)",
                                                      alt.getCategory().name(), alt.getScore() * 100)));
      }
    });

    return new WhyNotFoundAnalysis(
        imageId, filename, query,
        semanticScore, bm25Score, estimatedFinal,
        config.getScoreCutoff(), aboveThreshold,
        approved, archived,
        List.copyOf(exclusionReasons),
        List.copyOf(diagnosticNotes),
        vectorAvailable);
  }

  /**
   * Mirrors the tuning search: a query-by-example image vector takes priority over
   * the text embedding.
   */
  private float[] resolveQueryVector(String query, SearchTuningConfig config) {
    UUID qbeId = config.getQueryByExampleImageId();
    if (qbeId != null) {
      Optional<float[]> qbe = persistenceService.findRawVector(qbeId).filter(VectorMath::isUsable);
      if (qbe.isPresent()) return qbe.get();
    }
    return embeddingService.embed(query);
  }

  private static int indexOf(List<UUID> ranking, UUID imageId) {
    return ranking.indexOf(imageId);
  }

  private void analyseStructuralFilters(UUID imageId, List<String> notes) {
    Optional<SemanticAnalysis> analysis = persistenceService.findAnalysis(imageId);
    Optional<SensitivityAssessment> assess = persistenceService.findAssessment(imageId);

    assess.ifPresent(a -> {
      if (a.getRiskLevel() != null) {
        notes.add("Risk level: " + a.getRiskLevel().name()
                      + (a.getRiskLevel().name().equals("SENSITIVE")
            ? " — sensitive images may be excluded by privacy filters" : ""));
      }
    });

    analysis.ifPresent(a -> {
      if (Boolean.TRUE.equals(a.getContainsPerson())) {
        notes.add("Image contains persons — excluded by person-filter queries.");
      }
      if (Boolean.TRUE.equals(a.getContainsVehicle())) {
        notes.add("Image contains vehicles.");
      }
      if (Boolean.TRUE.equals(a.getContainsLicensePlateHint())) {
        notes.add("Image may contain a readable license plate.");
      }
    });
  }
}
