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
package com.svenruppert.imagerag.pipeline;

/**
 * Distinguishes how an {@link IngestionJob} entered the pipeline.
 * <ul>
 *   <li>{@link #INGEST_UPLOAD} — the job was triggered by a fresh image upload from the user.
 *       All pipeline stages run, including file storage and SHA-256 duplicate detection.</li>
 *   <li>{@link #REPROCESS_EXISTING} — the job was triggered by the "Reprocess" action on an
 *       image that is already stored on disk.  File storage and duplicate detection are
 *       skipped; only the AI-heavy stages (vision, semantics, sensitivity, embedding) are
 *       re-executed so the derived data is refreshed.</li>
 * </ul>
 */
public enum JobType {

  /**
   * Fresh upload from the user — full pipeline including storage and deduplication.
   */
  INGEST_UPLOAD,

  /**
   * Reprocessing of an already-stored image — AI stages only, storage skipped.
   */
  REPROCESS_EXISTING
}
