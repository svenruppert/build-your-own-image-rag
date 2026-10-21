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
