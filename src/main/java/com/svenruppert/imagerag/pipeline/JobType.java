package com.svenruppert.imagerag.pipeline;

/**
 * Distinguishes how an {@link IngestionJob} entered the pipeline.
 *
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

  /** Fresh upload from the user — full pipeline including storage and deduplication. */
  INGEST_UPLOAD,

  /** Reprocessing of an already-stored image — AI stages only, storage skipped. */
  REPROCESS_EXISTING
}
