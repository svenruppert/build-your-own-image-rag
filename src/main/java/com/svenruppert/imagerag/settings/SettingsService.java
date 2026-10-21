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

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.functional.result.Result;
import com.svenruppert.functional.result.Unit;
import com.svenruppert.imagerag.bootstrap.AppConfig;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Resolves the effective value of every {@link Setting} and manages user overrides.
 * <h3>Precedence (highest first)</h3>
 * <ol>
 *   <li>User override changed in the running application, persisted in the
 *       attached {@link SettingsStore}.</li>
 *   <li>JVM system property ({@code -Dkey=value}).</li>
 *   <li>{@code imagerag.properties} on the classpath.</li>
 *   <li>The built-in default of the setting.</li>
 * </ol>
 * <p>Resetting a setting removes its override, so the next lower layer applies again.
 * <p>The service is a process-wide singleton like {@link AppConfig}, so that static
 * helpers can read the current value at use time. Until a store is attached
 * (see {@link #attachStore(SettingsStore)}) only the configuration layers apply.
 */
public final class SettingsService
    implements HasLogger {

  private static final SettingsService INSTANCE = new SettingsService(AppConfig.getInstance());

  private final AppConfig config;
  private final Map<String, String> overrides = new ConcurrentHashMap<>();
  private final List<Consumer<SettingChange>> listeners = new CopyOnWriteArrayList<>();
  /**
   * Keys whose configured value was invalid; used to log the warning only once.
   */
  private final Set<String> reportedInvalidKeys = ConcurrentHashMap.newKeySet();
  private volatile SettingsStore store;

  private SettingsService(AppConfig config) {
    this.config = config;
  }

  public static SettingsService getInstance() {
    return INSTANCE;
  }

  /**
   * Attaches the durable store and loads the persisted overrides. Overrides for
   * unknown, read-only or invalid settings are ignored with a warning.
   */
  public synchronized void attachStore(SettingsStore settingsStore) {
    this.store = settingsStore;
    overrides.clear();
    settingsStore.loadSettingOverrides().forEach((key, value) -> {
      var setting = Settings.byKey(key);
      if (setting.isEmpty() || !setting.get().editable()) {
        logger().warn("Ignoring stored override for unknown or read-only setting '{}'", key);
        return;
      }
      setting.get().parse(value)
          .peek(ignored -> overrides.put(key, value))
          .peekFailure(reason -> logger().warn("Ignoring stored override: {}", reason));
    });
    logger().info("Settings store attached: {} override(s) active", overrides.size());
  }

  /**
   * Detaches the store and forgets all overrides and listeners (application shutdown).
   */
  public synchronized void detach() {
    store = null;
    overrides.clear();
    listeners.clear();
  }

  /**
   * Returns the effective value of a setting.
   */
  public <T extends Comparable<T>> T get(Setting<T> setting) {
    String override = overrides.get(setting.key());
    if (override != null) {
      Result<T, String> parsed = setting.parse(override);
      if (parsed instanceof Result.Success<T, String>(T value)) {
        return value;
      }
    }
    String configured = config.get(setting.key());
    if (configured != null) {
      Result<T, String> parsed = setting.parse(configured);
      if (parsed instanceof Result.Success<T, String>(T value)) {
        return value;
      }
      if (reportedInvalidKeys.add(setting.key())) {
        logger().warn("Configured value '{}' for '{}' is invalid — using default {}",
                      configured, setting.key(), setting.defaultValue());
      }
    }
    return setting.defaultValue();
  }

  /**
   * Returns the effective value of a setting as text.
   */
  public String getAsText(Setting<?> setting) {
    return textOf(setting);
  }

  /**
   * Returns the layer that provides the effective value.
   */
  public SettingSource source(Setting<?> setting) {
    if (overrides.containsKey(setting.key())) {
      return SettingSource.OVERRIDE;
    }
    String sys = System.getProperty(setting.key());
    if (sys != null && !sys.isBlank() && setting.parse(sys).isSuccess()) {
      return SettingSource.SYSTEM_PROPERTY;
    }
    String configured = config.get(setting.key());
    if (configured != null && setting.parse(configured).isSuccess()) {
      return SettingSource.PROPERTIES_FILE;
    }
    return SettingSource.DEFAULT;
  }

  /**
   * Returns the value the setting falls back to when its override is removed.
   */
  public <T extends Comparable<T>> T valueWithoutOverride(Setting<T> setting) {
    String configured = config.get(setting.key());
    if (configured != null && setting.parse(configured) instanceof Result.Success<T, String>(T value)) {
      return value;
    }
    return setting.defaultValue();
  }

  public boolean hasOverride(Setting<?> setting) {
    return overrides.containsKey(setting.key());
  }

  /**
   * Validates, persists and applies a user override.
   */
  public <T extends Comparable<T>> Result<Unit, String> set(Setting<T> setting, T value) {
    if (!setting.editable()) {
      return Result.failure(setting.key() + " is read-only");
    }
    return setting.validate(value).map(valid -> {
      String text = setting.format(valid);
      String before;
      synchronized (this) {
        before = textOf(setting);
        SettingsStore current = store;
        if (current != null) {
          current.putSettingOverride(setting.key(), text);
        }
        overrides.put(setting.key(), text);
      }
      logger().info("Setting '{}' changed from {} to {}", setting.key(), before, text);
      fire(new SettingChange(setting, before, textOf(setting), false));
      return Unit.INSTANCE;
    });
  }

  /**
   * Removes the user override of a setting, if any.
   */
  public void reset(Setting<?> setting) {
    String before;
    synchronized (this) {
      if (!overrides.containsKey(setting.key())) {
        return;
      }
      before = textOf(setting);
      SettingsStore current = store;
      if (current != null) {
        current.removeSettingOverride(setting.key());
      }
      overrides.remove(setting.key());
    }
    logger().info("Setting '{}' reset (was {})", setting.key(), before);
    fire(new SettingChange(setting, before, textOf(setting), true));
  }

  /**
   * Removes every user override.
   */
  public void resetAll() {
    Map<Setting<?>, String> before = new LinkedHashMap<>();
    synchronized (this) {
      for (Setting<?> setting : Settings.ALL) {
        if (overrides.containsKey(setting.key())) {
          before.put(setting, textOf(setting));
        }
      }
      SettingsStore current = store;
      if (current != null) {
        current.clearSettingOverrides();
      }
      overrides.clear();
    }
    logger().info("All settings reset to configured defaults ({} override(s) removed)", before.size());
    before.forEach((setting, old) -> fire(new SettingChange(setting, old, textOf(setting), true)));
  }

  /**
   * Registers a listener for changes of effective values.
   *
   * @return an action that removes the listener again
   */
  public Runnable addListener(Consumer<SettingChange> listener) {
    listeners.add(listener);
    return () -> listeners.remove(listener);
  }

  private <T extends Comparable<T>> String textOf(Setting<T> setting) {
    return setting.format(get(setting));
  }

  private void fire(SettingChange change) {
    for (Consumer<SettingChange> listener : listeners) {
      try {
        listener.accept(change);
      } catch (RuntimeException e) {
        // Listener isolation: one failing listener must not stop the others or the change.
        logger().warn("Settings listener failed for '{}'", change.setting().key(), e);
      }
    }
  }
}
