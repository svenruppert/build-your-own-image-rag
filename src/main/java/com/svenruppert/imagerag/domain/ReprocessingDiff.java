package com.svenruppert.imagerag.domain;

import com.svenruppert.imagerag.domain.enums.RiskLevel;
import com.svenruppert.imagerag.domain.enums.SourceCategory;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Immutable diff between the derived state of an image <em>before</em> and <em>after</em>
 * a reprocessing run.
 * <p>Created by {@link com.svenruppert.imagerag.pipeline.IngestionPipeline} at the end of
 * each successful {@code reprocess()} run and stored on the corresponding
 * {@link com.svenruppert.imagerag.pipeline.IngestionJob} so the UI can display it.
 * <p>All fields may be {@code null} when the value was not available before or after
 * processing (e.g. no prior analysis existed, or the new run produced no output).
 */
public record ReprocessingDiff(

    // ── Summary / description ───────────────────────────────────────────────
    String previousSummary,
    String newSummary,

    // ── Primary category ────────────────────────────────────────────────────
    SourceCategory previousCategory,
    SourceCategory newCategory,

    // ── Secondary categories ─────────────────────────────────────────────────
    List<SourceCategory> previousSecondaryCategories,
    List<SourceCategory> newSecondaryCategories,

    // ── Risk / sensitivity ──────────────────────────────────────────────────
    RiskLevel previousRisk,
    RiskLevel newRisk,

    // ── Tags ────────────────────────────────────────────────────────────────
    List<String> previousTags,
    List<String> newTags,

    // ── Vision model ────────────────────────────────────────────────────────
    String previousVisionModel,
    String newVisionModel,

    // ── Prompt version ──────────────────────────────────────────────────────
    String previousPromptVersion,
    String newPromptVersion,

    // ── OCR text ────────────────────────────────────────────────────────────
    String previousOcrText,
    String newOcrText

) {

  /**
   * Returns {@code true} if the primary category changed.
   */
  public boolean categoryChanged() {
    return !Objects.equals(previousCategory, newCategory);
  }

  /**
   * Returns {@code true} if the set of secondary categories changed (additions or removals).
   */
  public boolean secondaryCategoriesChanged() {
    List<SourceCategory> prev = previousSecondaryCategories != null ? previousSecondaryCategories : List.of();
    List<SourceCategory> next = newSecondaryCategories != null ? newSecondaryCategories : List.of();
    return !new HashSet<>(prev).equals(new HashSet<>(next));
  }

  /**
   * Returns categories that were added to secondaries after reprocessing.
   */
  public List<SourceCategory> secondaryAdded() {
    HashSet<SourceCategory> prev = new HashSet<>(
        previousSecondaryCategories != null ? previousSecondaryCategories : List.of());
    return (newSecondaryCategories != null ? newSecondaryCategories : List.<SourceCategory>of())
        .stream().filter(c -> !prev.contains(c)).toList();
  }

  /**
   * Returns categories that were removed from secondaries after reprocessing.
   */
  public List<SourceCategory> secondaryRemoved() {
    HashSet<SourceCategory> next = new HashSet<>(
        newSecondaryCategories != null ? newSecondaryCategories : List.of());
    return (previousSecondaryCategories != null ? previousSecondaryCategories : List.<SourceCategory>of())
        .stream().filter(c -> !next.contains(c)).toList();
  }

  /**
   * Returns {@code true} if the risk level changed.
   */
  public boolean riskChanged() {
    return !Objects.equals(previousRisk, newRisk);
  }

  /**
   * Returns {@code true} if the full summary/description changed.
   */
  public boolean summaryChanged() {
    return !Objects.equals(previousSummary, newSummary);
  }

  /**
   * Returns {@code true} if the tag list changed.
   */
  public boolean tagsChanged() {
    return !Objects.equals(previousTags, newTags);
  }

  /**
   * Returns {@code true} if the extracted OCR text changed.
   */
  public boolean ocrChanged() {
    return !Objects.equals(previousOcrText, newOcrText);
  }

  /**
   * Returns {@code true} if the vision model or prompt version changed.
   */
  public boolean provenanceChanged() {
    return !Objects.equals(previousVisionModel, newVisionModel)
        || !Objects.equals(previousPromptVersion, newPromptVersion);
  }

  /**
   * Returns {@code true} if at least one tracked field changed between the two runs.
   */
  public boolean anyChanged() {
    return categoryChanged() || secondaryCategoriesChanged() || riskChanged()
        || summaryChanged() || tagsChanged() || ocrChanged() || provenanceChanged();
  }
}
