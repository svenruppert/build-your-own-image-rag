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
package com.svenruppert.imagerag.domain.enums;

/**
 * Per-result relevance feedback that the user assigns during a tuning session.
 * <p>Feedback is session-scoped (not globally persisted) and acts as an
 * additional ranking signal that the user can switch on/off via the tuning
 * configuration.  Each type maps to a different scoring multiplier in
 * {@link com.svenruppert.imagerag.dto.FeedbackSession}:
 * <ul>
 *   <li>{@link #GOOD} → +1 × cosine similarity to the candidate</li>
 *   <li>{@link #MORE_LIKE_THIS} → +2 × cosine similarity</li>
 *   <li>{@link #BAD} → −1 × cosine similarity</li>
 * </ul>
 */
public enum FeedbackType {

  /**
   * The result is relevant — use as a mild positive signal.
   */
  GOOD,

  /**
   * Strongly positive — boost results similar to this one more aggressively.
   */
  MORE_LIKE_THIS,

  /**
   * The result is irrelevant — penalise similar candidates.
   */
  BAD;

  /**
   * Short user-facing label, suitable for button text.
   */
  public String getLabel() {
    return switch (this) {
      case GOOD -> "Good";
      case MORE_LIKE_THIS -> "More Like This";
      case BAD -> "Bad";
    };
  }

  /**
   * Single-character icon for compact display.
   */
  public String getIcon() {
    return switch (this) {
      case GOOD -> "\u2713";   // ✓
      case MORE_LIKE_THIS -> "\u2605";   // ★
      case BAD -> "\u2717";   // ✗
    };
  }

  /**
   * Lumo CSS color token for the type (background / border).
   */
  public String getColor() {
    return switch (this) {
      case GOOD -> "var(--lumo-success-color)";
      case MORE_LIKE_THIS -> "#f59e0b";  // amber — not in standard Lumo palette
      case BAD -> "var(--lumo-error-color)";
    };
  }

  /**
   * Lumo CSS 10 % tint token for badge backgrounds.
   */
  public String getBackground() {
    return switch (this) {
      case GOOD -> "var(--lumo-success-color-10pct)";
      case MORE_LIKE_THIS -> "#fef3c7";  // amber-100
      case BAD -> "var(--lumo-error-color-10pct)";
    };
  }
}
