package com.svenruppert.imagerag.domain.enums;

/**
 * Lifecycle state for a taxonomy category.
 *
 * <ul>
 *   <li>{@code ACTIVE}     — in normal use; images may be assigned to this category.</li>
 *   <li>{@code CANDIDATE}  — proposed addition, not yet promoted to the live taxonomy.</li>
 *   <li>{@code DEPRECATED} — category is being phased out.  Existing assignments are
 *       preserved but new ingestion should avoid this category.  A migration suggestion
 *       is expected to exist pointing to a replacement category.</li>
 * </ul>
 */
public enum CategoryLifecycleState {
  ACTIVE,
  CANDIDATE,
  DEPRECATED
}
