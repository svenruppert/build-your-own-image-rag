package com.svenruppert.imagerag.service;

import java.util.UUID;

/**
 * Refreshes the derived analysis artifacts for an already-stored image asset.
 *
 * <p>Reprocessing re-runs the AI-heavy pipeline stages (vision analysis, semantic
 * derivation, sensitivity assessment, embedding, vector indexing) against the
 * original image file that is already on disk.  It does <em>not</em> create a new
 * {@link com.svenruppert.imagerag.domain.ImageAsset} entry; instead it overwrites the
 * derived data in-place for the existing asset.
 *
 * <p>Use cases:
 * <ul>
 *   <li>Re-running with a newer or different LLM model</li>
 *   <li>Rebuilding embeddings after a prompt or model change</li>
 *   <li>Recovering from a partially-failed original ingestion</li>
 * </ul>
 */
public interface ReprocessingService {

  /**
   * Schedules reprocessing of the given image asset.
   * The call returns immediately; processing happens asynchronously.
   *
   * @param imageId UUID of an existing, stored {@link com.svenruppert.imagerag.domain.ImageAsset}
   * @throws IllegalArgumentException if the image does not exist in the store
   */
  void reprocess(UUID imageId);
}
