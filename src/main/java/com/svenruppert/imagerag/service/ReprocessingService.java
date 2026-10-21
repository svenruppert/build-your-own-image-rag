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
package com.svenruppert.imagerag.service;

import com.svenruppert.functional.result.Result;
import com.svenruppert.functional.result.Unit;

import java.util.UUID;

/**
 * Refreshes the derived analysis artifacts for an already-stored image asset.
 * <p>Reprocessing re-runs the AI-heavy pipeline stages (vision analysis, semantic
 * derivation, sensitivity assessment, embedding, vector indexing) against the
 * original image file that is already on disk.  It does <em>not</em> create a new
 * {@link com.svenruppert.imagerag.domain.ImageAsset} entry; instead it overwrites the
 * derived data in-place for the existing asset.
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
   * @return success once the job is queued, or a failure carrying the reason (image not
   * found, pipeline not accepting jobs)
   */
  Result<Unit, String> reprocess(UUID imageId);
}
