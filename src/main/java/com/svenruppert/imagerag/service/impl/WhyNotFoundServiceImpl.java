package com.svenruppert.imagerag.service.impl;

import com.svenruppert.imagerag.domain.ImageAsset;
import com.svenruppert.imagerag.domain.SearchTuningConfig;
import com.svenruppert.imagerag.domain.SemanticAnalysis;
import com.svenruppert.imagerag.domain.SensitivityAssessment;
import com.svenruppert.imagerag.domain.enums.RetrievalMode;
import com.svenruppert.imagerag.dto.WhyNotFoundAnalysis;
import com.svenruppert.imagerag.persistence.PersistenceService;
import com.svenruppert.imagerag.service.EmbeddingService;
import com.svenruppert.imagerag.service.KeywordIndexService;
import com.svenruppert.imagerag.service.VectorIndexService;
import com.svenruppert.imagerag.service.WhyNotFoundService;

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
 *   <li>Score fusion estimate</li>
 *   <li>Score cutoff comparison</li>
 *   <li>Structural exclusions (category, risk, visibility)</li>
 * </ul>
 */
public class WhyNotFoundServiceImpl
    implements WhyNotFoundService {

  private static final int RRF_K = 60;
  private static final int VECTOR_CANDS = 50;
  private static final int KEYWORD_CANDS = 100;

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

  private static double cosineSimilarity(float[] a, float[] b) {
    if (a == null || b == null || a.length != b.length) return 0.0;
    double dot = 0, normA = 0, normB = 0;
    for (int i = 0; i < a.length; i++) {
      dot += (double) a[i] * b[i];
      normA += (double) a[i] * a[i];
      normB += (double) b[i] * b[i];
    }
    double denom = Math.sqrt(normA) * Math.sqrt(normB);
    return denom > 1e-9 ? dot / denom : 0.0;
  }

  // ── Helpers ──────────────────────────────────────────────────────────────

  @Override
  public WhyNotFoundAnalysis analyze(UUID imageId, String query, SearchTuningConfig config) {

    // ── Basic state checks ────────────────────────────────────────────────
    Optional<ImageAsset> assetOpt = persistenceService.findImage(imageId);
    String filename = assetOpt.map(ImageAsset::getOriginalFilename)
        .orElse(imageId.toString());

    boolean archived = assetOpt.map(ImageAsset::isDeleted).orElse(false);
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
    Optional<float[]> rawVec = persistenceService.findRawVector(imageId);
    boolean vectorAvailable = rawVec.isPresent();
    if (!vectorAvailable) {
      exclusionReasons.add("No embedding vector found — image may not have been indexed yet.");
    }

    // ── Semantic score ────────────────────────────────────────────────────
    double semanticScore = 0.0;
    if (vectorAvailable && query != null && !query.isBlank()
        && config.getRetrievalMode() != RetrievalMode.BM25_ONLY) {
      try {
        float[] queryVec = embeddingService.embed(query);
        float[] imageVec = rawVec.get();
        semanticScore = cosineSimilarity(queryVec, imageVec);

        // Check where the image would rank in vector search
        var vectorHits = vectorIndexService.search(queryVec, VECTOR_CANDS,
                                                   config.getSimilarityFunction());
        int vectorRank = -1;
        for (int i = 0; i < vectorHits.size(); i++) {
          if (imageId.equals(vectorHits.get(i).imageId())) {
            vectorRank = i;
            break;
          }
        }
        if (vectorRank < 0) {
          diagnosticNotes.add(String.format(
              "Not in top %d vector candidates (semantic score %.3f — below retrieval threshold).",
              VECTOR_CANDS, semanticScore));
        } else {
          diagnosticNotes.add(String.format(
              "Vector rank: %d of %d candidates (semantic score %.3f).",
              vectorRank + 1, vectorHits.size(), semanticScore));
        }
      } catch (Exception e) {
        diagnosticNotes.add("Could not compute semantic score: " + e.getMessage());
      }
    }

    // ── BM25 score ────────────────────────────────────────────────────────
    double bm25Score = 0.0;
    if (query != null && !query.isBlank()
        && config.getRetrievalMode() != RetrievalMode.SEMANTIC_ONLY) {
      try {
        var keywordHits = keywordIndexService.search(query, KEYWORD_CANDS);
        for (var hit : keywordHits) {
          if (imageId.equals(hit.imageId())) {
            bm25Score = hit.score();
            break;
          }
        }
        if (bm25Score <= 0) {
          diagnosticNotes.add(String.format(
              "Not in top %d BM25 keyword candidates — no keyword overlap with query.",
              KEYWORD_CANDS));
        } else {
          diagnosticNotes.add(String.format("BM25 raw score: %.3f", bm25Score));
        }
      } catch (Exception e) {
        diagnosticNotes.add("Could not compute BM25 score: " + e.getMessage());
      }
    }

    // ── Confidence boost ──────────────────────────────────────────────────
    double confidenceBoost = 0.0;
    if (config.getConfidenceWeight() > 0) {
      var analysis = persistenceService.findAnalysis(imageId);
      if (analysis.isPresent() && analysis.get().getCategoryConfidence() != null) {
        double primaryScore = analysis.get().getCategoryConfidence().getPrimaryScore();
        confidenceBoost = config.getConfidenceWeight() * primaryScore * 0.15;
        diagnosticNotes.add(String.format(
            "Category confidence: %.2f → confidence boost: +%.3f", primaryScore, confidenceBoost));
      }
    }

    // ── Estimate combined score ───────────────────────────────────────────
    // Simplified RRF — single-candidate approximation
    double semRrf = semanticScore > 0.01 ? config.getSemanticWeight() / (RRF_K + 1.0) : 0;
    double bm25Rrf = bm25Score > 0.01 ? config.getBm25Weight() / (RRF_K + 1.0) : 0;
    double totalRrf = semRrf + bm25Rrf;
    // Normalise to 0–1 range approximation
    double maxPossibleRrf = (config.getSemanticWeight() + config.getBm25Weight()) / (RRF_K + 1.0);
    double normalised = maxPossibleRrf > 0 ? totalRrf / maxPossibleRrf : 0;
    double estimatedFinal = Math.min(1.0, normalised + confidenceBoost);

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
      if (conf != null && conf.isAmbiguous(0.6)) {
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
