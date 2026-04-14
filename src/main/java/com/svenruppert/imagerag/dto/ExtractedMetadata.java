package com.svenruppert.imagerag.dto;

import java.time.Instant;

public record ExtractedMetadata(
    int width,
    int height,
    boolean exifPresent,
    boolean gpsPresent,
    Double latitude,
    Double longitude,
    Instant captureTimestamp,
    String cameraModel
) {
  public static ExtractedMetadata minimal(int width, int height) {
    return new ExtractedMetadata(width, height, false, false, null, null, null, null);
  }
}
