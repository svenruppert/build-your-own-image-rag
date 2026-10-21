/*
 * Copyright © 2026 Sven Ruppert (sven.ruppert@gmail.com)
 *
 * Licensed under the EUPL, Version 1.2 (the "Licence");
 * you may not use this file except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *     https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */
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
