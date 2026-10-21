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
 * The configuration layer a setting's effective value comes from.
 */
public enum SettingSource {
  /**
   * Value changed by the user in the running application and persisted.
   */
  OVERRIDE,
  /**
   * JVM system property ({@code -Dkey=value}).
   */
  SYSTEM_PROPERTY,
  /**
   * {@code imagerag.properties} on the classpath.
   */
  PROPERTIES_FILE,
  /**
   * Built-in default.
   */
  DEFAULT
}
