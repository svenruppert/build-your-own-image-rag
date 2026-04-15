package com.svenruppert.imagerag.pipeline;

/**
 * Each value represents one discrete stage of the ingestion pipeline.
 * COMPLETED and FAILED are terminal states.
 */
public enum JobStep {

  QUEUED("Queued", false),
  STORING("Storing image file", false),
  LOADING_IMAGE("Loading image from store", false),
  EXTRACTING_METADATA("Extracting metadata (EXIF)", false),
  GEOCODING("Reverse geocoding", false),
  OCR_TEXT("Extracting text (OCR)", false),
  ANALYZING_VISION("Vision analysis · LLM", false),
  DERIVING_SEMANTICS("Deriving semantic fields · LLM", false),
  ASSESSING_SENSITIVITY("Privacy assessment", false),
  EMBEDDING("Computing embedding", false),
  INDEXING("Indexing vector", false),
  KEYWORD_INDEXING("Indexing keywords (BM25)", false),
  COMPLETED("Completed", true),
  FAILED("Failed", true),
  CANCELLED("Cancelled", true),
  /**
   * The uploaded image was identified as a duplicate of an already-stored asset
   * (same SHA-256 hash).  All expensive processing steps were skipped.
   * Use {@link com.svenruppert.imagerag.pipeline.IngestionJob#getDuplicateOfId()}
   * and {@link com.svenruppert.imagerag.pipeline.IngestionJob#getDuplicateOfFilename()}
   * to obtain details about the existing asset.
   */
  DUPLICATE("Duplicate — already in archive", true);

  private final String label;
  private final boolean terminal;

  JobStep(String label, boolean terminal) {
    this.label = label;
    this.terminal = terminal;
  }

  public String getLabel() {
    return label;
  }

  public boolean isTerminal() {
    return terminal;
  }
}
