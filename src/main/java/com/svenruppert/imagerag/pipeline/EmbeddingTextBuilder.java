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
package com.svenruppert.imagerag.pipeline;

import com.svenruppert.imagerag.domain.SemanticAnalysis;

import java.util.stream.Collectors;

/**
 * Builds the text that is sent to the embedding model for an analysed image.
 * <p>Ingestion, reprocessing, startup migrations and index rebuilds all use this single
 * definition, so a rebuilt index contains exactly the vectors ingestion would produce.
 */
public final class EmbeddingTextBuilder {

  private EmbeddingTextBuilder() {
  }

  public static String build(SemanticAnalysis analysis) {
    StringBuilder sb = new StringBuilder();
    if (analysis.getSummary() != null) {
      sb.append(analysis.getSummary());
    }
    if (analysis.getTags() != null && !analysis.getTags().isEmpty()) {
      sb.append(" Tags: ").append(String.join(", ", analysis.getTags()));
    }
    if (analysis.getSeasonHint() != null) {
      sb.append(" Season: ").append(analysis.getSeasonHint().name());
    }
    if (analysis.getSourceCategory() != null) {
      sb.append(" Category: ").append(analysis.getSourceCategory().name());
    }
    if (analysis.getSecondaryCategories() != null && !analysis.getSecondaryCategories().isEmpty()) {
      sb.append(" ").append(analysis.getSecondaryCategories().stream()
                                .map(Enum::name)
                                .collect(Collectors.joining(" ")));
    }
    return sb.toString();
  }
}
