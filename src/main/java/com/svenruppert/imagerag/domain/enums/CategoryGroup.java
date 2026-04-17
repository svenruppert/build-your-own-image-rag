package com.svenruppert.imagerag.domain.enums;

/**
 * Coarse, user-visible category groupings for the image archive.
 * <p>Each {@link SourceCategory} fine-grained value maps to exactly one
 * {@code CategoryGroup} via {@code CategoryRegistry}.  The UI exposes only
 * these nine groups for filtering and search; fine-grained classification is
 * handled internally by the AI pipeline.
 */
public enum CategoryGroup {
  NATURE("Nature"),
  ANIMALS("Animals"),
  PEOPLE("People"),
  URBAN("Urban & Architecture"),
  VEHICLES("Vehicles & Transport"),
  TECHNOLOGY("Technology & Industry"),
  OBJECTS_MEDIA("Objects & Media"),
  ACTIVITIES("Activities & Events"),
  UNCATEGORIZED("Uncategorized");

  private final String label;

  CategoryGroup(String label) {
    this.label = label;
  }

  /**
   * Human-readable label suitable for display in filter dropdowns and tile cards.
   */
  public String getLabel() {
    return label;
  }
}
