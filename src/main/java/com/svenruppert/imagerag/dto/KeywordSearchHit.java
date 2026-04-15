package com.svenruppert.imagerag.dto;

import java.util.UUID;

/**
 * A single hit from the BM25/Lucene keyword index.
 * Score is normalized to [0, 1] by the index service.
 */
public record KeywordSearchHit(UUID imageId, float score)
    implements Comparable<KeywordSearchHit> {
  @Override
  public int compareTo(KeywordSearchHit other) {
    return Float.compare(other.score(), this.score()); // descending
  }
}
