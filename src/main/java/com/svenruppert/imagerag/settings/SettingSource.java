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
