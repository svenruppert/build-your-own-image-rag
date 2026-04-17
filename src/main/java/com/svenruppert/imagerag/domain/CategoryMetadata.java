package com.svenruppert.imagerag.domain;

import com.svenruppert.imagerag.domain.enums.CategoryLifecycleState;
import com.svenruppert.imagerag.domain.enums.SourceCategory;

import java.time.Instant;

/**
 * Administrative metadata for a single {@link SourceCategory}.
 *
 * <p>Tracks the lifecycle state of categories, enabling safe deprecation and
 * migration workflows without hard-deleting taxonomy nodes that are still
 * referenced by existing image analyses.
 *
 * <p>Persisted in {@code AppDataRoot.categoryMetadata} keyed by
 * {@link SourceCategory#name()} for EclipseStore compatibility.
 *
 * <p>Not declared final — EclipseStore Unsafe reconstruction compatibility.
 */
public class CategoryMetadata {

  /**
   * The category this metadata describes.
   */
  private SourceCategory category;

  /**
   * Current lifecycle state; defaults to {@link CategoryLifecycleState#ACTIVE}.
   */
  private CategoryLifecycleState lifecycleState;

  /**
   * Replacement category to use when this category is deprecated.
   * Null unless {@link #lifecycleState} is {@link CategoryLifecycleState#DEPRECATED}.
   */
  private SourceCategory replacementCategory;

  /**
   * Optional human-readable notes explaining why this category was deprecated,
   * or other maintenance context.
   */
  private String notes;

  /**
   * When this metadata record was last updated.
   */
  private Instant updatedAt;

  public CategoryMetadata() {
    this.lifecycleState = CategoryLifecycleState.ACTIVE;
  }

  public CategoryMetadata(SourceCategory category,
                          CategoryLifecycleState lifecycleState,
                          SourceCategory replacementCategory,
                          String notes) {
    this.category = category;
    this.lifecycleState = lifecycleState != null ? lifecycleState : CategoryLifecycleState.ACTIVE;
    this.replacementCategory = replacementCategory;
    this.notes = notes;
    this.updatedAt = Instant.now();
  }

  // ── Accessors ──────────────────────────────────────────────────────────────

  public SourceCategory getCategory() {
    return category;
  }

  public void setCategory(SourceCategory category) {
    this.category = category;
  }

  public CategoryLifecycleState getLifecycleState() {
    return lifecycleState != null ? lifecycleState : CategoryLifecycleState.ACTIVE;
  }

  public void setLifecycleState(CategoryLifecycleState lifecycleState) {
    this.lifecycleState = lifecycleState;
    this.updatedAt = Instant.now();
  }

  public SourceCategory getReplacementCategory() {
    return replacementCategory;
  }

  public void setReplacementCategory(SourceCategory replacementCategory) {
    this.replacementCategory = replacementCategory;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public boolean isDeprecated() {
    return lifecycleState == CategoryLifecycleState.DEPRECATED;
  }
}
