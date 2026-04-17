package com.svenruppert.imagerag.domain.enums;

/**
 * Review lifecycle for a {@link com.svenruppert.imagerag.domain.TaxonomySuggestion}.
 * <ul>
 *   <li>{@code OPEN} — newly generated, awaiting user review.</li>
 *   <li>{@code ACCEPTED} — user has approved this suggestion but has not yet
 *       applied it (queued for bulk apply).</li>
 *   <li>{@code REJECTED} — user dismissed this suggestion; it will not be applied.</li>
 *   <li>{@code APPLIED} — the suggestion has been executed and the underlying data
 *       has been mutated accordingly.</li>
 * </ul>
 */
public enum SuggestionStatus {
  OPEN,
  ACCEPTED,
  REJECTED,
  APPLIED
}
