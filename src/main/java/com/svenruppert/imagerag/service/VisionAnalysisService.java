package com.svenruppert.imagerag.service;

import com.svenruppert.functional.result.Result;
import com.svenruppert.imagerag.dto.VisionAnalysisResponse;

import java.nio.file.Path;

public interface VisionAnalysisService {

  VisionAnalysisResponse analyzeImage(Path imagePath);

  /**
   * Extracts the readable text visible in the image (OCR) with a dedicated prompt.
   *
   * @return the extracted text verbatim, an empty string if the image contains no
   * readable text, or a failure carrying the reason when the model call failed
   */
  Result<String, String> extractText(Path imagePath);
}
