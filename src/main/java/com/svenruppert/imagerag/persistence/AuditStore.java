package com.svenruppert.imagerag.persistence;

import com.svenruppert.imagerag.domain.AuditEntry;
import com.svenruppert.imagerag.settings.Settings;
import com.svenruppert.imagerag.settings.SettingsService;

import java.util.List;

/**
 * Audit log of state-changing actions, newest first and capped at
 * {@link Settings#AUDIT_MAX_ENTRIES}.
 */
final class AuditStore {

  private final PersistenceContext ctx;

  AuditStore(PersistenceContext ctx) {
    this.ctx = ctx;
  }

  void addAuditEntry(AuditEntry entry) {
    ctx.write(() -> {
      List<AuditEntry> log = ctx.root().getAuditLog();
      log.addFirst(entry);
      int maxEntries = SettingsService.getInstance().get(Settings.AUDIT_MAX_ENTRIES);
      while (log.size() > maxEntries) log.removeLast();
      ctx.store(log);
    });
  }

  List<AuditEntry> getAuditLog() {
    return ctx.read(() -> List.copyOf(ctx.root().getAuditLog()));
  }
}
