package com.svenruppert.imagerag.service;

import com.svenruppert.imagerag.domain.AuditEntry;

import java.util.List;
import java.util.UUID;

/**
 * Records and retrieves audit log entries for critical state-changing actions.
 */
public interface AuditService {
  void log(String action, UUID imageId, String imageFilename, String detail);

  List<AuditEntry> getRecentEntries(int maxEntries);
}
