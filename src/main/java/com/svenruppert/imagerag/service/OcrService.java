package com.svenruppert.imagerag.service;

import com.svenruppert.imagerag.domain.OcrResult;

import java.nio.file.Path;
import java.util.UUID;

/**
 * Extracts readable text from images (OCR).
 */
public interface OcrService {
  OcrResult extract(UUID imageId, Path imagePath);
}
