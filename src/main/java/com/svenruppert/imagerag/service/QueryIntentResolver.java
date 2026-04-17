package com.svenruppert.imagerag.service;

import com.svenruppert.imagerag.domain.enums.QueryIntentType;
import com.svenruppert.imagerag.domain.enums.SourceCategory;

import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * Lightweight heuristic classifier that detects the dominant intent of a
 * Search Tuning Lab query without making any LLM call.
 * <h3>Detection rules (applied in priority order)</h3>
 * <ol>
 *   <li>Query contains quoted substrings → {@link QueryIntentType#EXACT_TERM}</li>
 *   <li>Query is short (≤ 4 words) and matches an OCR-like alphanumeric pattern
 *       (e.g. license plates, product codes) → {@link QueryIntentType#OCR_HEAVY}</li>
 *   <li>Query contains a word that matches a known {@link SourceCategory} display
 *       name → {@link QueryIntentType#CATEGORY_DRIVEN}</li>
 *   <li>Query is a long natural-language phrase (≥ 5 words) → {@link QueryIntentType#DESCRIPTIVE}</li>
 *   <li>Otherwise → {@link QueryIntentType#UNKNOWN}</li>
 * </ol>
 * <h3>Weight adjustment</h3>
 * <p>Call {@link #adjustedWeights(QueryIntentType, double, double)} to obtain
 * intent-tuned [semanticWeight, bm25Weight] pairs.  The caller may still override
 * these with the manual UI controls.
 */
public class QueryIntentResolver {

  /**
   * Alphanumeric OCR pattern: 3–9 uppercase letters/digits as an isolated word.
   */
  private static final Pattern OCR_PATTERN =
      Pattern.compile("\\b[A-Z0-9]{3,9}\\b");

  /**
   * Category display strings derived from {@link SourceCategory} names, lower-cased.
   */
  private static final String[] CATEGORY_KEYWORDS =
      Arrays.stream(SourceCategory.values())
          .map(sc -> sc.name().toLowerCase().replace("_", " "))
          .toArray(String[]::new);

  // ── Public API ────────────────────────────────────────────────────────────

  /**
   * Resolves the dominant intent of {@code query}.
   *
   * @param query raw query string; may be blank
   * @return detected intent, never {@code null}
   */
  public QueryIntentType resolve(String query) {
    if (query == null || query.isBlank()) return QueryIntentType.UNKNOWN;

    String trimmed = query.trim();
    String lower = trimmed.toLowerCase();

    // 1. Quoted substring → user wants exact keyword match
    if (lower.contains("\"") || lower.contains("'")) {
      return QueryIntentType.EXACT_TERM;
    }

    // 2. Short + OCR-pattern → alphanumeric code/plate search
    String[] words = trimmed.split("\\s+");
    if (words.length <= 4 && OCR_PATTERN.matcher(trimmed.toUpperCase()).find()) {
      return QueryIntentType.OCR_HEAVY;
    }

    // 3. Contains a known category name → category-driven
    for (String kw : CATEGORY_KEYWORDS) {
      if (!kw.equals("unknown") && !kw.equals("mixed") && lower.contains(kw)) {
        return QueryIntentType.CATEGORY_DRIVEN;
      }
    }

    // 4. Long natural-language phrase → descriptive
    if (words.length >= 5) {
      return QueryIntentType.DESCRIPTIVE;
    }

    return QueryIntentType.UNKNOWN;
  }

  /**
   * Returns adjusted [semanticWeight, bm25Weight] based on the detected intent.
   * <p>The original weights are scaled by intent-specific factors so that the
   * relative balance between the channels is preserved while the emphasis shifts.
   *
   * @param intent         the intent returned by {@link #resolve(String)}
   * @param semanticWeight current user-configured semantic weight
   * @param bm25Weight     current user-configured BM25 weight
   * @return two-element array {@code [adjustedSemantic, adjustedBm25]}; never null
   */
  public double[] adjustedWeights(QueryIntentType intent,
                                  double semanticWeight,
                                  double bm25Weight) {
    return switch (intent) {
      case DESCRIPTIVE -> new double[]{semanticWeight * 1.3, bm25Weight * 0.8};
      case EXACT_TERM -> new double[]{semanticWeight * 0.6, bm25Weight * 1.8};
      case OCR_HEAVY -> new double[]{semanticWeight * 0.4, bm25Weight * 2.5};
      case CATEGORY_DRIVEN -> new double[]{semanticWeight * 1.2, bm25Weight * 0.9};
      case UNKNOWN -> new double[]{semanticWeight, bm25Weight};
    };
  }
}
