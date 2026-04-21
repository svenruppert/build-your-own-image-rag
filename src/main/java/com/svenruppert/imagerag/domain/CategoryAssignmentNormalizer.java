package com.svenruppert.imagerag.domain;

import com.svenruppert.imagerag.domain.enums.SourceCategory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Stateless utility that enforces invariants on multi-category assignments:
 * <ul>
 *   <li>Secondary categories must not contain the primary category.</li>
 *   <li>Secondary categories must not contain duplicates.</li>
 *   <li>Null entries are removed from the secondary list.</li>
 * </ul>
 * All methods are null-safe and never modify their inputs.
 */
public final class CategoryAssignmentNormalizer {

  private CategoryAssignmentNormalizer() {
  }

  /**
   * Returns a de-duplicated, primary-free copy of {@code secondaries}.
   * Insertion order is preserved (first occurrence wins on duplicates).
   *
   * @param primary     the current primary category; null means no exclusion
   * @param secondaries the raw secondary list; may be null or contain nulls
   * @return a new mutable normalized list; never null
   */
  public static List<SourceCategory> normalizeSecondaries(SourceCategory primary,
                                                          List<SourceCategory> secondaries) {
    if (secondaries == null || secondaries.isEmpty()) return new ArrayList<>();
    LinkedHashSet<SourceCategory> seen = new LinkedHashSet<>();
    for (SourceCategory sc : secondaries) {
      if (sc != null && !sc.equals(primary)) {
        seen.add(sc);
      }
    }
    return new ArrayList<>(seen);
  }

  /**
   * Normalizes the secondary-category list of the given {@link SemanticAnalysis} in-place.
   *
   * @return {@code true} if any element was removed or reordered (caller may want to persist)
   */
  public static boolean normalize(SemanticAnalysis analysis) {
    if (analysis == null) return false;
    List<SourceCategory> before = analysis.getSecondaryCategories();
    List<SourceCategory> after = normalizeSecondaries(analysis.getSourceCategory(), before);
    if (!after.equals(before)) {
      analysis.setSecondaryCategories(after);
      return true;
    }
    return false;
  }
}
