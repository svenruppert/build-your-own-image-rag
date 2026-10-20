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
