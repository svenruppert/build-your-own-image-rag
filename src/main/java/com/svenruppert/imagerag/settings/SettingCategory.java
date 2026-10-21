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
