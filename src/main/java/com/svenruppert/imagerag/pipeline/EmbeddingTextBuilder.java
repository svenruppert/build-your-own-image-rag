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
