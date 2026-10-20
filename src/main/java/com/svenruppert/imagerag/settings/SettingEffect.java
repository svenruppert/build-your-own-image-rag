package com.svenruppert.imagerag.settings;

/**
 * Describes when a changed setting takes effect.
 */
public enum SettingEffect {
  /**
   * The next operation that reads the setting uses the new value.
   */
  IMMEDIATE,
  /**
   * The new value is used the next time the vector index is rebuilt.
   */
  NEXT_REBUILD,
  /**
   * The value can only change with a restart, and changing it requires a full reindex.
   */
  NEEDS_REINDEX,
  /**
   * The value can only change with a restart.
   */
  NEEDS_RESTART
}
