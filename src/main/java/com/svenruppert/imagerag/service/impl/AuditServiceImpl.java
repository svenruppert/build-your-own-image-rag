package com.svenruppert.imagerag.service.impl;

import com.svenruppert.imagerag.domain.AuditEntry;
import com.svenruppert.imagerag.persistence.PersistenceService;
import com.svenruppert.imagerag.service.AuditService;

import java.util.List;
import java.util.UUID;

public class AuditServiceImpl
    implements AuditService {

  private final PersistenceService persistenceService;

  public AuditServiceImpl(PersistenceService persistenceService) {
    this.persistenceService = persistenceService;
  }

  @Override
  public void log(String action, UUID imageId, String imageFilename, String detail) {
    persistenceService.addAuditEntry(new AuditEntry(action, imageId, imageFilename, detail));
  }

  @Override
  public List<AuditEntry> getRecentEntries(int maxEntries) {
    List<AuditEntry> all = persistenceService.getAuditLog();
    if (all.size() <= maxEntries) return all;
    return all.subList(0, maxEntries);
  }
}
