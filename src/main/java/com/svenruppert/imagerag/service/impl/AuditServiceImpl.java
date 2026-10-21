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
