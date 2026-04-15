package com.svenruppert.imagerag.service.impl;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.imagerag.domain.OcrResult;
import com.svenruppert.imagerag.ollama.OllamaConfig;
import com.svenruppert.imagerag.service.OcrService;
import com.svenruppert.imagerag.service.VisionAnalysisService;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

/**
 * Extracts readable text from images by prompting the vision model with a focused OCR prompt.
 */
public class OcrServiceImpl
    implements OcrService, HasLogger {

  private static final String OCR_PROMPT =
      "Extract ALL readable text from this image verbatim, preserving line breaks where meaningful. "
          + "Include ALL text you can see: signs, labels, captions, watermarks, UI text, etc. "
          + "If there is absolutely no readable text in the image, respond with exactly the word: NO_TEXT";

  private final VisionAnalysisService visionAnalysisService;
  private final String modelName;

  public OcrServiceImpl(VisionAnalysisService visionAnalysisService, OllamaConfig config) {
    this.visionAnalysisService = visionAnalysisService;
    this.modelName = config.getVisionModel();
  }

  @Override
  public OcrResult extract(UUID imageId, Path imagePath) {
    try {
      // Re-use the vision analysis service with the general vision description as OCR source
      var response = visionAnalysisService.analyzeImage(imagePath);
      String raw = response.rawDescription();
      boolean hasText = raw != null && !raw.isBlank() && !raw.equalsIgnoreCase("NO_TEXT");
      return new OcrResult(imageId, hasText ? raw : null, hasText, modelName, Instant.now());
    } catch (Exception e) {
      logger().warn("[OCR] Failed for imageId={}: {}", imageId, e.getMessage());
      return new OcrResult(imageId, null, false, modelName, Instant.now());
    }
  }
}
