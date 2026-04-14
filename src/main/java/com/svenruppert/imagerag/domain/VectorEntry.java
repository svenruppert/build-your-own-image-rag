package com.svenruppert.imagerag.domain;

import java.time.Instant;
import java.util.UUID;

public class VectorEntry {

  private UUID imageId;
  private String embeddingModel;
  private int dimensions;
  private Instant indexedAt;

  public VectorEntry() {
  }

  public VectorEntry(UUID imageId, String embeddingModel, int dimensions, Instant indexedAt) {
    this.imageId = imageId;
    this.embeddingModel = embeddingModel;
    this.dimensions = dimensions;
    this.indexedAt = indexedAt;
  }

  public UUID getImageId() {
    return imageId;
  }

  public void setImageId(UUID imageId) {
    this.imageId = imageId;
  }

  public String getEmbeddingModel() {
    return embeddingModel;
  }

  public void setEmbeddingModel(String embeddingModel) {
    this.embeddingModel = embeddingModel;
  }

  public int getDimensions() {
    return dimensions;
  }

  public void setDimensions(int dimensions) {
    this.dimensions = dimensions;
  }

  public Instant getIndexedAt() {
    return indexedAt;
  }

  public void setIndexedAt(Instant indexedAt) {
    this.indexedAt = indexedAt;
  }
}
