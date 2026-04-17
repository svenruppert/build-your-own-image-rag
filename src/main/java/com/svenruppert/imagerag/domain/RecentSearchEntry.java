package com.svenruppert.imagerag.domain;

import com.svenruppert.imagerag.domain.enums.SearchMode;

import java.time.Instant;

/**
 * A single entry in the recent-search history.
 * <p>Stores both the user's original query and the LLM-derived transformed query,
 * allowing the search history to serve as a learning aid — the user can compare
 * what they typed against what the system actually searched for.
 * <p>Stored inside {@link com.svenruppert.imagerag.persistence.AppDataRoot} via
 * EclipseStore.  All fields are non-final so that EclipseStore's Unsafe-based
 * reconstruction works correctly after schema changes.  New fields added after the
 * first persistence run will be {@code null} in loaded data; callers must handle that.
 */
public class RecentSearchEntry {

  /**
   * The exact query string that the user typed into the search field.
   */
  private String query;

  /**
   * The LLM-transformed embedding text derived from the original query.
   * May be {@code null} for entries persisted before this field was added, or for
   * TRANSFORM_ONLY runs that did not yet produce a plan.
   */
  private String finalQuery;

  /**
   * Which mode was used for this search.
   * May be {@code null} for entries persisted before this field was added.
   */
  private SearchMode mode;

  /**
   * Wall-clock time at which the search was executed.
   */
  private Instant timestamp;

  /**
   * No-arg constructor required for EclipseStore Unsafe reconstruction.
   */
  public RecentSearchEntry() {
  }

  public RecentSearchEntry(String query, String finalQuery, SearchMode mode, Instant timestamp) {
    this.query = query;
    this.finalQuery = finalQuery;
    this.mode = mode;
    this.timestamp = timestamp;
  }

  public String getQuery() {
    return query;
  }

  public void setQuery(String query) {
    this.query = query;
  }

  public String getFinalQuery() {
    return finalQuery;
  }

  public void setFinalQuery(String finalQuery) {
    this.finalQuery = finalQuery;
  }

  public SearchMode getMode() {
    return mode;
  }

  public void setMode(SearchMode mode) {
    this.mode = mode;
  }

  public Instant getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(Instant timestamp) {
    this.timestamp = timestamp;
  }
}
