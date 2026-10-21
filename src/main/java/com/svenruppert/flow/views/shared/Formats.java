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
package com.svenruppert.flow.views.shared;

/**
 * Small, stateless text formatting helpers shared by the views.
 */
public final class Formats {

  /**
   * Placeholder shown for a missing value.
   */
  public static final String EMPTY = "—";

  private static final String ELLIPSIS = "…";
  private static final long KIB = 1024L;
  private static final long MIB = KIB * KIB;

  private Formats() {
  }

  /**
   * Returns the value, or the {@link #EMPTY} placeholder when it is {@code null}.
   */
  public static String nvl(String value) {
    return value != null ? value : EMPTY;
  }

  /**
   * Returns {@code value.toString()}, or the {@link #EMPTY} placeholder when it is {@code null}.
   */
  public static String nvl(Object value) {
    return value != null ? value.toString() : EMPTY;
  }

  /**
   * Shortens the text to at most {@code max} characters plus an ellipsis.
   * A {@code null} text yields the {@link #EMPTY} placeholder.
   */
  public static String truncate(String text, int max) {
    if (text == null) return EMPTY;
    return text.length() > max ? text.substring(0, max) + ELLIPSIS : text;
  }

  /**
   * Formats a byte count as {@code B}, {@code KB} or {@code MB} with one decimal place.
   */
  public static String fileSize(long bytes) {
    if (bytes < KIB) return bytes + " B";
    if (bytes < MIB) return String.format("%.1f KB", bytes / (double) KIB);
    return String.format("%.1f MB", bytes / (double) MIB);
  }

  /**
   * Formats a retrieval score with three decimal places.
   */
  public static String score(double score) {
    return String.format("%.3f", score);
  }

  /**
   * Formats a weight, threshold or similarity with two decimal places.
   */
  public static String decimal(double value) {
    return String.format("%.2f", value);
  }
}
