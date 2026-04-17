package com.svenruppert.imagerag.dto;

import java.util.UUID;

/**
 * Represents a single input signal in a multimodal search.
 * <p>A multimodal search combines multiple signals — text query, image example,
 * OCR terms, or category constraint — each contributing to the final result ranking.
 * Signals are weighted independently so users can tune the influence of each input type.
 * <p>Instances are created via the static factory methods and are immutable.
 */
public record MultimodalSignal(
    SignalType type,
    /** Text content: used for TEXT and OCR_TERMS signals. */
    String textContent,
    /** Image ID: used for IMAGE_EXAMPLE signals. */
    UUID imageId,
    /**
     * Relative weight of this signal during fusion (0.0–1.0).
     * Weights are normalised across all active signals of the same retrieval channel.
     */
    double weight
) {

  // ── Signal types ────────────────────────────────────────────────────────

  /**
   * Text-query signal with the given weight.
   */
  public static MultimodalSignal text(String query, double weight) {
    return new MultimodalSignal(SignalType.TEXT, query, null, clamp(weight));
  }

  // ── Factories ───────────────────────────────────────────────────────────

  /**
   * Image-example signal referencing an already-stored image.
   */
  public static MultimodalSignal imageExample(UUID imageId, double weight) {
    return new MultimodalSignal(SignalType.IMAGE_EXAMPLE, null, imageId, clamp(weight));
  }

  /**
   * OCR/keyword-boost signal.
   */
  public static MultimodalSignal ocrTerms(String terms, double weight) {
    return new MultimodalSignal(SignalType.OCR_TERMS, terms, null, clamp(weight));
  }

  /**
   * Category-filter signal using the category's display label.
   */
  public static MultimodalSignal categoryFilter(String categoryLabel) {
    return new MultimodalSignal(SignalType.CATEGORY_FILTER, categoryLabel, null, 1.0);
  }

  private static double clamp(double w) {
    return Math.min(1.0, Math.max(0.0, w));
  }

  // ── Helpers ─────────────────────────────────────────────────────────────

  private static String truncate(String s, int max) {
    if (s == null) return "";
    return s.length() > max ? s.substring(0, max - 1) + "…" : s;
  }

  /**
   * Human-readable summary of this signal for display in the UI.
   */
  public String displayLabel() {
    return switch (type) {
      case TEXT -> "Text: \"" + truncate(textContent, 40) + "\"";
      case IMAGE_EXAMPLE -> "Image: " + (imageId != null ? imageId.toString().substring(0, 8) + "\u2026" : "?");
      case OCR_TERMS -> "OCR: \"" + truncate(textContent, 40) + "\"";
      case CATEGORY_FILTER -> "Category: " + textContent;
    };
  }

  public enum SignalType {
    /**
     * Natural-language description; embedded with the embedding model.
     */
    TEXT("Text query", "var(--lumo-primary-color)"),
    /**
     * Reference image whose stored raw vector drives semantic similarity.
     */
    IMAGE_EXAMPLE("Image example", "var(--lumo-success-color)"),
    /**
     * Keywords/OCR terms that boost BM25 retrieval.
     */
    OCR_TERMS("OCR / keyword terms", "#f59e0b"),
    /**
     * Limits results to a specific category label.
     */
    CATEGORY_FILTER("Category filter", "var(--lumo-contrast-50pct)");

    private final String label;
    private final String color;

    SignalType(String label, String color) {
      this.label = label;
      this.color = color;
    }

    public String getLabel() {
      return label;
    }

    public String getColor() {
      return color;
    }
  }
}
