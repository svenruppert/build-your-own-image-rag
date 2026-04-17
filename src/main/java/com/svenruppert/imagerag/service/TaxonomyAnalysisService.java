package com.svenruppert.imagerag.service;

import com.svenruppert.imagerag.domain.TaxonomyAnalysisScope;
import com.svenruppert.imagerag.domain.TaxonomySuggestion;

import java.util.List;
import java.util.function.Consumer;

/**
 * Analyses a set of images (defined by a {@link TaxonomyAnalysisScope}) and produces
 * a list of reviewable {@link TaxonomySuggestion} objects.
 *
 * <p>Two operating modes:
 * <ul>
 *   <li><b>Live run</b> ({@link #analyze}): persists generated suggestions so they appear
 *       in the Taxonomy Maintenance view for review.</li>
 *   <li><b>Dry run</b> ({@link #analyzeDryRun}): returns suggestions without persisting
 *       them — useful for previewing the analysis impact before committing.</li>
 * </ul>
 *
 * <p>Suggestions are never applied automatically.  The user must review and accept /
 * apply them via {@link TaxonomySuggestionService}.
 *
 * <p>Both modes optionally accept a {@link Consumer}&lt;{@link TaxonomyAnalysisProgress}&gt;
 * progress callback.  The callback is invoked after each state transition so the UI can
 * refresh its step inspector and progress bar without polling.
 */
public interface TaxonomyAnalysisService {

  /**
   * Runs a full analysis pass for the given scope, persists the results, and returns
   * the generated suggestions.
   *
   * <p>Any existing OPEN suggestions are cleared before the new batch is written, so
   * successive analysis runs on the same scope do not accumulate stale proposals.
   *
   * @param scope            defines which images to include in the analysis
   * @param progressCallback invoked after each progress state change; may be {@code null}
   * @return newly generated suggestions (may be empty if no issues were found)
   */
  List<TaxonomySuggestion> analyze(TaxonomyAnalysisScope scope,
                                   Consumer<TaxonomyAnalysisProgress> progressCallback);

  /**
   * Runs the same analysis logic as {@link #analyze} but does <em>not</em> persist
   * the results.  Use this to preview what would be generated.
   *
   * @param scope            defines which images to include in the analysis
   * @param progressCallback invoked after each progress state change; may be {@code null}
   * @return generated suggestions (transient — not saved)
   */
  List<TaxonomySuggestion> analyzeDryRun(TaxonomyAnalysisScope scope,
                                         Consumer<TaxonomyAnalysisProgress> progressCallback);

  /** Convenience overload — no progress reporting. */
  default List<TaxonomySuggestion> analyze(TaxonomyAnalysisScope scope) {
    return analyze(scope, null);
  }

  /** Convenience overload — no progress reporting. */
  default List<TaxonomySuggestion> analyzeDryRun(TaxonomyAnalysisScope scope) {
    return analyzeDryRun(scope, null);
  }
}
