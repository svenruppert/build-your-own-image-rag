package com.svenruppert.imagerag.dto;

import com.svenruppert.imagerag.domain.SearchResultItem;
import com.svenruppert.imagerag.dto.MultimodalSignal.SignalType;

import java.util.Map;

/**
 * A single result from a multimodal search, including per-signal score attribution.
 * <p>Wraps an existing {@link SearchResultItem} (compatible with all existing result
 * rendering code) and adds the detailed {@link ScoreBreakdown} plus a map of
 * signal-type → contribution fraction so the explainability panel can show which
 * input signal most influenced the ranking of this result.
 */
public record MultimodalSearchResult(
    /** Standard search result item (title, summary, imageId, score, category, …). */
    SearchResultItem item,
    /** Standard five-field score breakdown. */
    ScoreBreakdown breakdown,
    /** Rank in the vector-search pass (-1 if not retrieved by vector). */
    int vectorRank,
    /** Rank in the BM25 pass (-1 if not retrieved by BM25). */
    int bm25Rank,
    /**
     * Attribution map: for each active signal type, what fraction (0–1) of the
     * semantic vector score came from that signal's contribution.
     * Keys are the {@link SignalType} names (e.g., "TEXT", "IMAGE_EXAMPLE").
     */
    Map<String, Double> signalContributions
) {

  /**
   * Convenience: fraction from the TEXT signal (0 if not present).
   */
  public double textContribution() {
    return signalContributions.getOrDefault(SignalType.TEXT.name(), 0.0);
  }

  /**
   * Convenience: fraction from the IMAGE_EXAMPLE signal (0 if not present).
   */
  public double imageContribution() {
    return signalContributions.getOrDefault(SignalType.IMAGE_EXAMPLE.name(), 0.0);
  }

  /**
   * True if the result benefited from an image-example signal.
   */
  public boolean hasImageSignal() {
    return imageContribution() > 0.001;
  }

  /**
   * True if the result benefited from an OCR/keyword signal.
   */
  public boolean hasOcrSignal() {
    return signalContributions.getOrDefault(SignalType.OCR_TERMS.name(), 0.0) > 0.001;
  }
}
