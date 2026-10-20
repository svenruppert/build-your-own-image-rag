package com.svenruppert.imagerag.service;

import com.svenruppert.imagerag.domain.enums.AuditAction;
import com.svenruppert.imagerag.domain.AuditEntry;

import java.util.List;
import java.util.UUID;

/**
 * Records and retrieves audit log entries for critical state-changing actions.
 */
public interface AuditService {
  void log(String action, UUID imageId, String imageFilename, String detail);

  /**
   * Records an entry for one of the well-known {@link AuditAction}s.
   * The detail text is stored as given and must not depend on the UI language.
   */
  default void log(AuditAction action, UUID imageId, String imageFilename, String detail) {
    log(action.name(), imageId, imageFilename, detail);
  }

  List<AuditEntry> getRecentEntries(int maxEntries);
}
