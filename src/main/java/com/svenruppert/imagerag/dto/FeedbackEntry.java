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
package com.svenruppert.imagerag.dto;

import com.svenruppert.imagerag.domain.enums.FeedbackType;

import java.util.UUID;

/**
 * Immutable record of a single relevance-feedback mark within a tuning session.
 * <p>The {@code vector} field holds a snapshot of the image's embedding at the time
 * the mark was added.  This snapshot is used by
 * {@link FeedbackSession#computeRawScore(float[])} to compute cosine similarity
 * against other candidate images.
 * <p>An empty {@code vector} ({@code length == 0}) means the raw vector was not
 * found in the store (e.g. for the in-memory backend without persistence).  The
 * scoring logic treats this gracefully by returning 0 for that entry.
 *
 * @param imageId the image that was marked
 * @param label   short user-facing identifier (typically the filename)
 * @param type    the feedback type assigned by the user
 * @param vector  embedding vector snapshot; may be empty but never {@code null}
 */
public record FeedbackEntry(
    UUID imageId,
    String label,
    FeedbackType type,
    float[] vector
) {
  /**
   * Compact summary for display in the feedback-session panel.
   */
  public String displayLabel() {
    String l = label != null ? label : imageId.toString();
    return l.length() > 40 ? l.substring(0, 37) + "\u2026" : l;
  }
}
