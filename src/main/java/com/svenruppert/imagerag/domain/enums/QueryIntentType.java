package com.svenruppert.imagerag.domain.enums;

/**
 * Detected intent class of a tuning-lab query.
 * <p>The intent is resolved by {@link com.svenruppert.imagerag.service.QueryIntentResolver}
 * using lightweight heuristics (no LLM call).  When query-intent detection is enabled in
 * {@link com.svenruppert.imagerag.domain.SearchTuningConfig}, the resolved intent adjusts
 * the default channel weights before fusion:
 * <ul>
 *   <li>{@link #DESCRIPTIVE} → semantic-heavy (×1.3 semantic, ×0.8 BM25)</li>
 *   <li>{@link #EXACT_TERM} → BM25-heavy (×0.6 semantic, ×1.8 BM25)</li>
 *   <li>{@link #OCR_HEAVY} → BM25-dominant (×0.4 semantic, ×2.5 BM25)</li>
 *   <li>{@link #CATEGORY_DRIVEN} → balanced with confidence boost (×1.2 semantic, ×0.9 BM25)</li>
 *   <li>{@link #UNKNOWN} → no adjustment</li>
 * </ul>
 * <p>The user can always override the intent-adjusted weights manually in the UI.
 */
public enum QueryIntentType {

  /**
   * Long natural-language phrase describing a scene or concept.
   */
  DESCRIPTIVE,

  /**
   * Contains quoted strings or highly specific keywords.
   */
  EXACT_TERM,

  /**
   * Short alphanumeric codes, license plates, or text-heavy patterns.
   */
  OCR_HEAVY,

  /**
   * References a recognisable image category name.
   */
  CATEGORY_DRIVEN,

  /**
   * Intent unclear — no weight adjustment applied.
   */
  UNKNOWN;

  public String getLabel() {
    return switch (this) {
      case DESCRIPTIVE -> "Descriptive";
      case EXACT_TERM -> "Exact-Term";
      case OCR_HEAVY -> "OCR / Text";
      case CATEGORY_DRIVEN -> "Category";
      case UNKNOWN -> "Unknown";
    };
  }

  public String getHint() {
    return switch (this) {
      case DESCRIPTIVE -> "Descriptive query — boosting semantic weight";
      case EXACT_TERM -> "Exact-term query — boosting BM25 weight";
      case OCR_HEAVY -> "OCR/text query — strongly boosting BM25";
      case CATEGORY_DRIVEN -> "Category-driven query — balanced weights";
      case UNKNOWN -> "Intent unclear — weights unchanged";
    };
  }
}
