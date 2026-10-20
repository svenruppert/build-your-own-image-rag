package com.svenruppert.imagerag.service;

import com.svenruppert.imagerag.dto.ExtractedMetadata;

import java.nio.file.Path;

public interface MetadataExtractionService {

  /**
   * Extracts dimensions, EXIF and GPS data on a best-effort basis. Never fails: values
   * that cannot be read stay empty (zero dimensions, {@code null} fields).
   */
  ExtractedMetadata extract(Path imagePath);
}
