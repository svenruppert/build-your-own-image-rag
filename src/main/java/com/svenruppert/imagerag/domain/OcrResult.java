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
package com.svenruppert.imagerag.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Persisted result of the OCR / text-extraction step for a single image.
 * Stored separately so it can be updated without touching the full semantic analysis.
 */
public class OcrResult {

  private UUID imageId;
  /**
   * Raw text extracted from the image, or null / empty if no text was found.
   */
  private String extractedText;
  /**
   * Whether any readable text was found at all.
   */
  private boolean textFound;
  /**
   * Model used to extract the text.
   */
  private String model;
  /**
   * When OCR was performed.
   */
  private Instant extractedAt;

  public OcrResult() {
  }

  public OcrResult(UUID imageId, String extractedText, boolean textFound,
                   String model, Instant extractedAt) {
    this.imageId = imageId;
    this.extractedText = extractedText;
    this.textFound = textFound;
    this.model = model;
    this.extractedAt = extractedAt;
  }

  public UUID getImageId() {
    return imageId;
  }

  public void setImageId(UUID imageId) {
    this.imageId = imageId;
  }

  public String getExtractedText() {
    return extractedText;
  }

  public void setExtractedText(String extractedText) {
    this.extractedText = extractedText;
  }

  public boolean isTextFound() {
    return textFound;
  }

  public void setTextFound(boolean textFound) {
    this.textFound = textFound;
  }

  public String getModel() {
    return model;
  }

  public void setModel(String model) {
    this.model = model;
  }

  public Instant getExtractedAt() {
    return extractedAt;
  }

  public void setExtractedAt(Instant extractedAt) {
    this.extractedAt = extractedAt;
  }
}
