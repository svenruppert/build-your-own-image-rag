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
package com.svenruppert.imagerag.domain.enums;

/**
 * Actions recorded in the audit log.
 * <p>The enum name is what gets persisted in {@code AuditEntry.action}, so the
 * constants must not be renamed once entries exist.
 */
public enum AuditAction {

  /**
   * Image moved to the archive (soft delete).
   */
  SOFT_DELETE,

  /**
   * Archived image restored.
   */
  RESTORE,

  /**
   * Image and all derived data removed permanently.
   */
  PERMANENT_DELETE,

  /**
   * Image made visible in search.
   */
  APPROVE,

  /**
   * Image hidden from search (locked).
   */
  UNAPPROVE,

  /**
   * Risk level changed manually.
   */
  RISK_CHANGE,

  /**
   * Runtime setting overridden by the user.
   */
  SETTING_CHANGED,

  /**
   * Runtime setting reset to its default.
   */
  SETTING_RESET
}
