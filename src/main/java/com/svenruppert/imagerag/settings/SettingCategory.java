package com.svenruppert.imagerag.settings;

/**
 * Groups settings for display in the Settings view.
 */
public enum SettingCategory {
  SEARCH,
  DIAGNOSTICS,
  CLUSTERING,
  VECTOR_INDEX,
  PIPELINE,
  HISTORY,
  OLLAMA,
  GEOCODING,
  MODELS;

  /**
   * Translation key of the category heading.
   */
  public String labelKey() {
    return "settings.category." + name().toLowerCase();
  }
}
