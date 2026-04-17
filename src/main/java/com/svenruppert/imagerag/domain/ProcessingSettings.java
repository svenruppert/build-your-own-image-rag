package com.svenruppert.imagerag.domain;

/**
 * User-configurable runtime settings for the ingestion pipeline and search.
 * <p>Held as a singleton in {@link com.svenruppert.imagerag.bootstrap.ServiceRegistry}.
 * Changes via {@link #setIngestionParallelism} are picked up by
 * {@link com.svenruppert.imagerag.pipeline.IngestionPipeline#updateParallelism}
 * and take effect immediately for newly submitted jobs.
 * <p>{@link #searchParallelism} is stored here for future use: currently each
 * UI session runs searches on a single virtual thread (the polling architecture
 * makes that correct and safe); this setting reserves the concept for when
 * {@code SearchService} may dispatch parallel sub-queries internally.
 * <p>Allowed parallelism values: 1, 2, 4.  Any other value is silently clamped.
 */
public class ProcessingSettings {

  /**
   * Allowed values for both ingestion and search parallelism.
   */
  public static final int[] ALLOWED_PARALLELISM = {1, 2, 4};

  private volatile int ingestionParallelism = 1;

  /**
   * Max number of concurrent sub-queries the search service may issue.
   * Currently informational — the per-session executor remains single-threaded.
   */
  private volatile int searchParallelism = 1;

  private static int clamp(int requested) {
    // Return the largest allowed value that does not exceed requested
    int result = 1;
    for (int allowed : ALLOWED_PARALLELISM) {
      if (allowed <= requested) {
        result = allowed;
      }
    }
    return result;
  }

  public int getIngestionParallelism() {
    return ingestionParallelism;
  }

  /**
   * Sets the number of concurrent ingestion workers.
   * The value is clamped to the closest allowed value (1 / 2 / 4).
   */
  public void setIngestionParallelism(int value) {
    this.ingestionParallelism = clamp(value);
  }

  public int getSearchParallelism() {
    return searchParallelism;
  }

  /**
   * Sets the target search parallelism.
   * The value is clamped to the closest allowed value (1 / 2 / 4).
   */
  public void setSearchParallelism(int value) {
    this.searchParallelism = clamp(value);
  }
}
