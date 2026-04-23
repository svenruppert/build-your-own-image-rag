package com.svenruppert.imagerag.dto;

import com.svenruppert.imagerag.domain.ImageAsset;
import com.svenruppert.imagerag.domain.LocationSummary;
import com.svenruppert.imagerag.domain.OcrResult;
import com.svenruppert.imagerag.domain.SemanticAnalysis;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Normalized payload for one Lucene keyword-index document.
 */
public record KeywordIndexDocument(UUID imageId,
                                   String filename,
                                   String summary,
                                   List<String> tags,
                                   String categoryLabel,
                                   String locationText,
                                   String ocrText) {

  public static KeywordIndexDocument from(ImageAsset asset,
                                          SemanticAnalysis analysis,
                                          LocationSummary location,
                                          OcrResult ocr) {
    return new KeywordIndexDocument(
        asset.getId(),
        asset.getOriginalFilename(),
        analysis != null ? analysis.getSummary() : null,
        analysis != null && analysis.getTags() != null ? analysis.getTags() : List.of(),
        categoryLabel(analysis),
        location != null ? location.toHumanReadable() : null,
        ocr != null ? ocr.getExtractedText() : null);
  }

  private static String categoryLabel(SemanticAnalysis analysis) {
    if (analysis == null) {
      return null;
    }

    String primary = analysis.getSourceCategory() != null
        ? analysis.getSourceCategory().name()
        : null;

    if (analysis.getSecondaryCategories() == null
        || analysis.getSecondaryCategories().isEmpty()) {
      return primary;
    }

    String secondary = analysis.getSecondaryCategories().stream()
        .map(Enum::name)
        .collect(Collectors.joining(" "));
    return primary != null ? primary + " " + secondary : secondary;
  }
}
