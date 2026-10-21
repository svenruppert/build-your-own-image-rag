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
