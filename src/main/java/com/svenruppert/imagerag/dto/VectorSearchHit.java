package com.svenruppert.imagerag.dto;

import java.util.UUID;

public record VectorSearchHit(
    UUID imageId,
    double score
)
    implements Comparable<VectorSearchHit> {

  @Override
  public int compareTo(VectorSearchHit other) {
    return Double.compare(other.score(), this.score()); // descending
  }
}
