package com.svenruppert.imagerag.dto;

import java.util.List;
import java.util.UUID;

/**
 * Result of a "Why not found?" diagnostic analysis for a specific image and query.
 * <p>Explains why {@code imageId} did not appear in the search results for
 * {@code query}, listing each exclusion reason and the key score signals that
 * would have applied.  Intended for display in {@link com.svenruppert.flow.views.shared.WhyNotFoundDialog}.
 *
 * @param imageId             the image that was analysed
 * @param imageFilename       human-readable filename for display
 * @param query               the query that was used
 * @param semanticScore       cosine similarity between the query embedding and the image
 *                            vector (0–1, or -1 if unavailable)
 * @param bm25Score           BM25 keyword match score (0–∞, normalised to 0–1 for display)
 * @param estimatedFinalScore estimated combined score after fusion and boosts (0–1)
 * @param scoreCutoff         the cutoff that was active for this search
 * @param aboveThreshold      whether the estimated score would have passed the cutoff
 * @param approved            whether the image is currently approved for search
 * @param archived            whether the image is soft-deleted / archived
 * @param exclusionReasons    ordered list of human-readable exclusion reasons
 *                            (empty if the image would have appeared but was ranked below
 *                            {@code maxResults})
 * @param diagnosticNotes     additional diagnostic observations (e.g. alternative categories)
 * @param vectorAvailable     whether a raw vector exists for this image
 */
public record WhyNotFoundAnalysis(
    UUID imageId,
    String imageFilename,
    String query,
    double semanticScore,
    double bm25Score,
    double estimatedFinalScore,
    double scoreCutoff,
    boolean aboveThreshold,
    boolean approved,
    boolean archived,
    List<String> exclusionReasons,
    List<String> diagnosticNotes,
    boolean vectorAvailable
) {

  /**
   * Primary verdict shown as the panel headline.
   */
  public String verdict() {
    if (archived) return "Image is archived / soft-deleted";
    if (!approved) return "Image is not approved for search";
    if (!vectorAvailable) return "No vector embedding found — image was never indexed";
    if (!aboveThreshold) return String.format(
        "Score %.3f is below cutoff %.2f", estimatedFinalScore, scoreCutoff);
    if (!exclusionReasons.isEmpty()) return "Filtered out by structural filters";
    return "Would appear — ranked below max-results limit";
  }

  /**
   * Whether the image was definitively excluded (not just ranked low).
   */
  public boolean isDefinitivelyExcluded() {
    return archived || !approved || !vectorAvailable
        || !aboveThreshold || !exclusionReasons.isEmpty();
  }
}
