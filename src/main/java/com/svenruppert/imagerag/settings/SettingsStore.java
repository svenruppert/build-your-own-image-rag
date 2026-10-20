package com.svenruppert.imagerag.settings;

import java.util.Map;

/**
 * Durable storage for user overrides of settings, keyed by setting key.
 */
public interface SettingsStore {

  /**
   * Returns a snapshot of all stored overrides.
   */
  Map<String, String> loadSettingOverrides();

  void putSettingOverride(String key, String value);

  void removeSettingOverride(String key);

  void clearSettingOverrides();
}
