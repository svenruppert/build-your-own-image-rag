package com.svenruppert.imagerag.dto;

public record VisionAnalysisResponse(
    String rawDescription,
    String model,
    boolean successful
) {
  public static VisionAnalysisResponse failed(String model) {
    return new VisionAnalysisResponse("Image analysis unavailable", model, false);
  }
}
