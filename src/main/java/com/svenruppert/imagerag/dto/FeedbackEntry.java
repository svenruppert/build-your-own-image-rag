package com.svenruppert.imagerag.dto;

import com.svenruppert.imagerag.domain.enums.FeedbackType;

import java.util.UUID;

/**
 * Immutable record of a single relevance-feedback mark within a tuning session.
 *
 * <p>The {@code vector} field holds a snapshot of the image's embedding at the time
 * the mark was added.  This snapshot is used by
 * {@link FeedbackSession#computeRawScore(float[])} to compute cosine similarity
 * against other candidate images.
 *
 * <p>An empty {@code vector} ({@code length == 0}) means the raw vector was not
 * found in the store (e.g. for the in-memory backend without persistence).  The
 * scoring logic treats this gracefully by returning 0 for that entry.
 *
 * @param imageId  the image that was marked
 * @param label    short user-facing identifier (typically the filename)
 * @param type     the feedback type assigned by the user
 * @param vector   embedding vector snapshot; may be empty but never {@code null}
 */
public record FeedbackEntry(
    UUID imageId,
    String label,
    FeedbackType type,
    float[] vector
) {
  /** Compact summary for display in the feedback-session panel. */
  public String displayLabel() {
    String l = label != null ? label : imageId.toString();
    return l.length() > 40 ? l.substring(0, 37) + "\u2026" : l;
  }
}
