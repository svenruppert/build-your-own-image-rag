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
import com.svenruppert.imagerag.vector.VectorMath;

import java.util.*;

/**
 * Session-scoped relevance-feedback state for the Search Tuning Lab.
 * <p>An instance lives in {@link com.svenruppert.flow.views.tuning.SearchTuningView}
 * for the lifetime of the view.  It is <em>not</em> persisted globally — it resets
 * when the user navigates away.
 * <h3>Scoring model</h3>
 * <p>For each candidate image vector {@code v}:
 * <pre>
 *   rawScore = Σ(GOOD entries)          cosine(v, entry.vector) × 1.0
 *            + Σ(MORE_LIKE_THIS entries) cosine(v, entry.vector) × 2.0
 *            − Σ(BAD entries)            cosine(v, entry.vector) × 1.0
 * </pre>
 * <p>Normalise by {@link #maxPositiveScore()} to map the positive range to [0,1]
 * before multiplying by the user-configured {@code feedbackWeight}.
 * <h3>Thread safety</h3>
 * <p>The session object must be mutated only on the Vaadin UI thread.  Before
 * passing it to a background search thread, call {@link #snapshot()} to obtain
 * an isolated copy.
 */
public class FeedbackSession {

  private final Map<UUID, FeedbackEntry> entries = new LinkedHashMap<>();

  // ── Public mutation API (call on UI thread only) ──────────────────────────

  /**
   * Adds or replaces a feedback mark for the given image.
   *
   * @param imageId unique image identifier
   * @param label   filename / short display label
   * @param type    feedback type (GOOD / BAD / MORE_LIKE_THIS)
   * @param vector  embedding snapshot; pass {@code new float[0]} if unavailable
   */
  public void mark(UUID imageId, String label, FeedbackType type, float[] vector) {
    entries.put(imageId, new FeedbackEntry(imageId, label, type,
                                           vector != null ? vector : new float[0]));
  }

  /**
   * Removes any feedback mark for the given image.
   */
  public void remove(UUID imageId) {
    entries.remove(imageId);
  }

  // ── Query API ─────────────────────────────────────────────────────────────

  /**
   * Removes all feedback marks.
   */
  public void clear() {
    entries.clear();
  }

  public boolean isEmpty() {
    return entries.isEmpty();
  }

  public boolean hasAny() {
    return !entries.isEmpty();
  }

  public int size() {
    return entries.size();
  }

  public boolean isMarked(UUID imageId) {
    return entries.containsKey(imageId);
  }

  public Optional<FeedbackType> getType(UUID imageId) {
    FeedbackEntry e = entries.get(imageId);
    return e == null ? Optional.empty() : Optional.of(e.type());
  }

  /**
   * All entries, unmodifiable.
   */
  public Collection<FeedbackEntry> getAll() {
    return Collections.unmodifiableCollection(entries.values());
  }

  // ── Scoring ───────────────────────────────────────────────────────────────

  /**
   * Entries filtered to the given feedback type.
   */
  public List<FeedbackEntry> getByType(FeedbackType type) {
    return entries.values().stream()
        .filter(e -> e.type() == type)
        .toList();
  }

  /**
   * Computes the unnormalized feedback score for {@code candidateVector}.
   * <p>The score is positive when the candidate is similar to GOOD/MORE_LIKE_THIS
   * examples, and negative when similar to BAD examples.  Entries with empty
   * vectors are skipped.
   * <p>Divide by {@link #maxPositiveScore()} (if > 0) to normalize to [−1, 1]
   * before applying the feedback weight.
   */
  public double computeRawScore(float[] candidateVector) {
    if (entries.isEmpty() || candidateVector == null || candidateVector.length == 0) {
      return 0.0;
    }
    double score = 0.0;
    for (FeedbackEntry entry : entries.values()) {
      float[] ev = entry.vector();
      if (!VectorMath.compatible(candidateVector, ev)) continue;
      double sim = VectorMath.cosine(candidateVector, ev);
      score += switch (entry.type()) {
        case GOOD -> sim;
        case MORE_LIKE_THIS -> 2.0 * sim;
        case BAD -> -sim;
      };
    }
    return score;
  }

  // ── Thread safety ─────────────────────────────────────────────────────────

  /**
   * Maximum positive score achievable when all positive examples are perfectly similar.
   * Used as the normalization denominator.  Returns 0 if there are no positive examples.
   */
  public double maxPositiveScore() {
    return entries.values().stream()
        .mapToDouble(e -> switch (e.type()) {
          case GOOD -> 1.0;
          case MORE_LIKE_THIS -> 2.0;
          case BAD -> 0.0;
        })
        .sum();
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  /**
   * Returns an isolated copy of this session for use in a background search thread.
   * The snapshot shares the same {@link FeedbackEntry} references (which are immutable
   * records), so it is safe to read from any thread.
   */
  public FeedbackSession snapshot() {
    FeedbackSession snap = new FeedbackSession();
    snap.entries.putAll(this.entries);
    return snap;
  }
}
