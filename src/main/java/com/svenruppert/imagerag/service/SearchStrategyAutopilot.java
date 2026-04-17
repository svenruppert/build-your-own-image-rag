package com.svenruppert.imagerag.service;

import com.svenruppert.imagerag.domain.SearchTuningConfig;
import com.svenruppert.imagerag.domain.enums.QueryIntentType;
import com.svenruppert.imagerag.domain.enums.RetrievalMode;
import com.svenruppert.imagerag.domain.enums.SimilarityFunction;
import com.svenruppert.imagerag.dto.MultimodalSignal;
import com.svenruppert.imagerag.dto.SearchStrategyPlan;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Heuristic advisor that analyses a query and the active search signals to recommend
 * a retrieval strategy without calling any LLM.
 * <p>This class is intentionally a concrete utility (not an interface) — it is
 * stateless and instantiated directly by views and services, following the same
 * pattern as {@link QueryIntentResolver}.
 * <h3>Decision logic</h3>
 * <ol>
 *   <li>Detect intent via {@link QueryIntentResolver}.</li>
 *   <li>Check for active multimodal signals (image example, OCR terms).</li>
 *   <li>Apply per-intent weight adjustments — identical to QueryIntentResolver but
 *       extended with multimodal context.</li>
 *   <li>Select similarity function based on intent and vector backend hints.</li>
 * </ol>
 */
public class SearchStrategyAutopilot {

  private static final QueryIntentResolver INTENT_RESOLVER = new QueryIntentResolver();

  // Short-term / exact-match indicators
  private static final Pattern QUOTED_PATTERN = Pattern.compile("\"[^\"]+\"");
  private static final Pattern NUMBER_PATTERN = Pattern.compile("\\b\\d{3,}\\b");
  private static final Pattern OCR_PATTERN = Pattern.compile(
      "(?i)\\b(plate|kennzeichen|sign|text|number|ocr|code|barcode|label)\\b");

  // ── Public API ──────────────────────────────────────────────────────────

  /**
   * Detects if the query looks like it targets OCR / license-plate content.
   */
  public static boolean looksOcrHeavy(String query) {
    if (query == null) return false;
    return QUOTED_PATTERN.matcher(query).find()
        || NUMBER_PATTERN.matcher(query).find()
        || OCR_PATTERN.matcher(query).find();
  }

  /**
   * Analyses the query string alone (no multimodal context).
   * Used when autopilot is triggered from the Search Tuning Lab.
   *
   * @param query         the raw search query
   * @param currentConfig current user-configured parameters (used as baseline)
   */
  public SearchStrategyPlan analyze(String query, SearchTuningConfig currentConfig) {
    return analyze(query, currentConfig.getSemanticWeight(), currentConfig.getBm25Weight(), null);
  }

  // ── Helpers ─────────────────────────────────────────────────────────────

  /**
   * Analyses query plus a list of multimodal signals.
   * Used when autopilot is triggered from the Multimodal Search view.
   *
   * @param query         the raw query (may be blank if only image signals are present)
   * @param baseSemanticW current semantic weight baseline
   * @param baseBm25W     current BM25 weight baseline
   * @param signals       active multimodal signals (null = none)
   */
  public SearchStrategyPlan analyze(String query,
                                    double baseSemanticW,
                                    double baseBm25W,
                                    List<MultimodalSignal> signals) {

    List<String> reasons = new ArrayList<>();

    // ── Step 1: intent detection ──────────────────────────────────────────
    QueryIntentType intent = query != null && !query.isBlank()
        ? INTENT_RESOLVER.resolve(query)
        : QueryIntentType.UNKNOWN;

    double semW = baseSemanticW;
    double bm25W = baseBm25W;

    // ── Step 2: query-level adjustments ──────────────────────────────────
    switch (intent) {
      case DESCRIPTIVE -> {
        semW *= 1.3;
        bm25W *= 0.8;
        reasons.add("Descriptive query detected → boosting semantic weight ×1.3, reducing BM25 ×0.8");
      }
      case EXACT_TERM -> {
        semW *= 0.6;
        bm25W *= 1.8;
        reasons.add("Exact-term / quoted query detected → boosting BM25 ×1.8, reducing semantic ×0.6");
      }
      case OCR_HEAVY -> {
        semW *= 0.4;
        bm25W *= 2.5;
        reasons.add("OCR-heavy query detected → strongly boosting BM25 ×2.5 for keyword matching");
      }
      case CATEGORY_DRIVEN -> {
        semW *= 1.2;
        bm25W *= 0.9;
        reasons.add("Category-driven query detected → slightly boosting semantic weight");
      }
      default -> {
      }
    }

    // ── Step 3: multimodal signal adjustments ─────────────────────────────
    boolean hasImageSignal = false;
    boolean hasOcrSignal = false;
    boolean hasTextSignal = false;
    UUID firstImageId = null;

    if (signals != null) {
      for (MultimodalSignal sig : signals) {
        switch (sig.type()) {
          case IMAGE_EXAMPLE -> {
            hasImageSignal = true;
            if (firstImageId == null) firstImageId = sig.imageId();
          }
          case OCR_TERMS -> hasOcrSignal = true;
          case TEXT -> hasTextSignal = true;
          default -> {
          }
        }
      }
    } else if (query != null && !query.isBlank()) {
      hasTextSignal = true;
    }

    if (hasImageSignal && !hasTextSignal) {
      // Pure image-example search: strongly prefer semantic / vector retrieval
      semW = Math.max(semW, 1.5);
      bm25W = Math.min(bm25W, 0.3);
      reasons.add("Image-example signal without text → maximising vector similarity, minimising BM25");
    } else if (hasImageSignal && hasTextSignal) {
      // Combined text+image: boost semantic slightly
      semW *= 1.15;
      reasons.add("Combined text + image-example signals → boosting semantic weight ×1.15");
    }

    if (hasOcrSignal) {
      bm25W *= 1.4;
      reasons.add("OCR/keyword signal active → boosting BM25 ×1.4 for keyword matching");
    }

    // ── Step 4: recommend retrieval mode ──────────────────────────────────
    RetrievalMode mode;
    if (intent == QueryIntentType.OCR_HEAVY || hasOcrSignal && !hasTextSignal && !hasImageSignal) {
      mode = RetrievalMode.BM25_ONLY;
      reasons.add("BM25-only mode recommended for OCR / exact-keyword search");
    } else if (hasImageSignal && !hasOcrSignal && intent == QueryIntentType.UNKNOWN) {
      mode = RetrievalMode.SEMANTIC_ONLY;
      reasons.add("Semantic-only mode recommended for pure image-example search");
    } else {
      mode = RetrievalMode.HYBRID;
      if (reasons.isEmpty()) reasons.add("Hybrid mode — query balances semantic and keyword signals");
    }

    // ── Step 5: recommend similarity function ─────────────────────────────
    SimilarityFunction similarity = SimilarityFunction.COSINE; // safest default
    if (hasImageSignal) {
      similarity = SimilarityFunction.COSINE;
      reasons.add("Cosine similarity recommended for image-driven queries (normalised vectors)");
    } else if (intent == QueryIntentType.DESCRIPTIVE) {
      similarity = SimilarityFunction.COSINE;
    }

    // ── Step 6: normalise weights ─────────────────────────────────────────
    semW = Math.min(4.0, Math.max(0.1, semW));
    bm25W = Math.min(4.0, Math.max(0.1, bm25W));

    // ── Step 7: confidence ────────────────────────────────────────────────
    double confidence = reasons.size() >= 2 ? 0.85 : (reasons.isEmpty() ? 0.4 : 0.65);

    return new SearchStrategyPlan(intent, mode, similarity, semW, bm25W,
                                  List.copyOf(reasons), confidence);
  }
}
