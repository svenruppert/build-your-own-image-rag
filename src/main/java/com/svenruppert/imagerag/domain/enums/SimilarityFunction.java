package com.svenruppert.imagerag.domain.enums;

/**
 * Vector similarity / distance functions available in the Search Tuning Lab.
 *
 * <p>The selected function affects how the vector index computes the match score
 * between the query embedding and stored image embeddings:
 * <ul>
 *   <li>{@link #COSINE} — angle between vectors; scale-invariant; default for dense
 *       embedding models (bge-m3, etc.).</li>
 *   <li>{@link #DOT_PRODUCT} — raw inner product; scale-sensitive; equivalent to
 *       cosine for unit-normalised vectors.</li>
 *   <li>{@link #EUCLIDEAN} — L2 distance converted to a similarity score via
 *       {@code 1 / (1 + L2)}, so higher values still mean closer vectors.</li>
 * </ul>
 *
 * <p>When the active vector backend is the JVector/GigaMap HNSW index, COSINE uses
 * the fast approximate graph search.  DOT_PRODUCT and EUCLIDEAN fall back to a
 * brute-force scan so the selected function is always applied accurately.
 */
public enum SimilarityFunction {

  COSINE,
  DOT_PRODUCT,
  EUCLIDEAN;

  public String getLabel() {
    return switch (this) {
      case COSINE      -> "Cosine Similarity";
      case DOT_PRODUCT -> "Dot Product";
      case EUCLIDEAN   -> "Euclidean (L2)";
    };
  }

  public String getDescription() {
    return switch (this) {
      case COSINE      -> "Angle-based, scale-invariant — recommended for bge-m3";
      case DOT_PRODUCT -> "Raw inner product — scale-sensitive";
      case EUCLIDEAN   -> "L2 distance inverted to [0,1] score";
    };
  }
}
