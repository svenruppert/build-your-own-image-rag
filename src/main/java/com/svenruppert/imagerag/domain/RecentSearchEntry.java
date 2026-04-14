package com.svenruppert.imagerag.domain;

import java.time.Instant;

/**
 * A single entry in the recent-search history.
 *
 * <p>Stored inside {@link com.svenruppert.imagerag.persistence.AppDataRoot} via
 * EclipseStore.  All fields are non-final so that EclipseStore's Unsafe-based
 * reconstruction works correctly after schema changes.
 */
public class RecentSearchEntry {

  /** The query string that the user typed. */
  private String query;

  /** Wall-clock time at which the search was executed. */
  private Instant timestamp;

  /** No-arg constructor required for EclipseStore Unsafe reconstruction. */
  public RecentSearchEntry() {
  }

  public RecentSearchEntry(String query, Instant timestamp) {
    this.query = query;
    this.timestamp = timestamp;
  }

  public String getQuery() {
    return query;
  }

  public Instant getTimestamp() {
    return timestamp;
  }
}
