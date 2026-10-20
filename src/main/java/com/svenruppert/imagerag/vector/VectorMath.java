package com.svenruppert.imagerag.vector;

/**
 * Shared vector arithmetic for embeddings.
 * <p>All similarity functions follow one rule for incompatible input: a
 * {@code null}, empty or dimension-mismatched pair is not comparable and scores
 * {@code 0.0}. Callers that must distinguish "not comparable" from "orthogonal"
 * check {@link #compatible(float[], float[])} first.
 */
public final class VectorMath {

  /**
   * Norms below this value are treated as zero to avoid division by zero.
   */
  private static final double EPSILON = 1e-9;

  private VectorMath() {
  }

  /**
   * Returns {@code true} when both vectors are non-empty and share the same dimension.
   */
  public static boolean compatible(float[] a, float[] b) {
    return isUsable(a) && isUsable(b) && a.length == b.length;
  }

  /**
   * Returns {@code true} when the vector is non-null and non-empty.
   */
  public static boolean isUsable(float[] v) {
    return v != null && v.length > 0;
  }

  /**
   * Cosine similarity in [-1, 1]; {@code 0.0} for incompatible or zero-norm vectors.
   */
  public static double cosine(float[] a, float[] b) {
    if (!compatible(a, b)) return 0.0;
    double dot = 0.0;
    double normA = 0.0;
    double normB = 0.0;
    for (int i = 0; i < a.length; i++) {
      dot += (double) a[i] * b[i];
      normA += (double) a[i] * a[i];
      normB += (double) b[i] * b[i];
    }
    double denom = Math.sqrt(normA) * Math.sqrt(normB);
    return denom > EPSILON ? dot / denom : 0.0;
  }

  /**
   * Raw inner product; {@code 0.0} for incompatible vectors.
   */
  public static double dot(float[] a, float[] b) {
    if (!compatible(a, b)) return 0.0;
    double dot = 0.0;
    for (int i = 0; i < a.length; i++) dot += (double) a[i] * b[i];
    return dot;
  }

  /**
   * L2 distance mapped to a similarity in (0, 1]: {@code 1 / (1 + L2)};
   * {@code 0.0} for incompatible vectors.
   */
  public static double euclideanSimilarity(float[] a, float[] b) {
    if (!compatible(a, b)) return 0.0;
    double sumSq = 0.0;
    for (int i = 0; i < a.length; i++) {
      double diff = (double) a[i] - b[i];
      sumSq += diff * diff;
    }
    return 1.0 / (1.0 + Math.sqrt(sumSq));
  }

  /**
   * Scales {@code v} to unit length in place; a zero vector is left unchanged.
   */
  public static void normalizeInPlace(float[] v) {
    if (!isUsable(v)) return;
    double norm = 0.0;
    for (float f : v) norm += (double) f * f;
    norm = Math.sqrt(norm);
    if (norm > EPSILON) {
      for (int i = 0; i < v.length; i++) v[i] /= (float) norm;
    }
  }

  /**
   * Returns a new vector {@code v * weight}.
   */
  public static float[] scaled(float[] v, double weight) {
    float[] result = new float[v.length];
    for (int i = 0; i < v.length; i++) result[i] = (float) (v[i] * weight);
    return result;
  }

  /**
   * Adds {@code v * weight} to {@code acc} in place. Both vectors must be compatible.
   *
   * @throws IllegalArgumentException if the dimensions differ
   */
  public static void addWeightedInPlace(float[] acc, float[] v, double weight) {
    if (!compatible(acc, v)) {
      throw new IllegalArgumentException("dimension mismatch: " + length(acc) + " vs " + length(v));
    }
    for (int i = 0; i < acc.length; i++) acc[i] += (float) (v[i] * weight);
  }

  /**
   * Returns the length of {@code v}, or {@code 0} for {@code null}.
   */
  public static int length(float[] v) {
    return v == null ? 0 : v.length;
  }
}
