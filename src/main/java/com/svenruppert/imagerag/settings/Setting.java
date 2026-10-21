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

import com.svenruppert.functional.result.Result;
import com.svenruppert.functional.result.functions.CheckedSupplier;

import java.util.Objects;
import java.util.function.Function;

/**
 * Typed description of one runtime-tunable application setting.
 * <p>A setting is identified by its configuration key, which is the same key that is
 * understood by {@code imagerag.properties} and JVM system properties. The value is
 * resolved by {@link SettingsService} with the precedence
 * user override &gt; system property &gt; properties file &gt; {@link #defaultValue()}.
 * <p>Numeric settings carry inclusive bounds; values outside the bounds are rejected
 * when set by the user and ignored (with a warning) when they come from a
 * configuration layer.
 *
 * @param <T> value type ({@link Integer}, {@link Double}, {@link Boolean} or {@link String})
 */
public final class Setting<T extends Comparable<T>> {

  private final String key;
  private final Class<T> type;
  private final T defaultValue;
  private final T min;
  private final T max;
  private final double step;
  private final SettingCategory category;
  private final SettingEffect effect;
  private final boolean editable;
  private final Function<String, T> parser;

  private Setting(String key, Class<T> type, T defaultValue, T min, T max, double step,
                  SettingCategory category, SettingEffect effect, boolean editable,
                  Function<String, T> parser) {
    this.key = Objects.requireNonNull(key);
    this.type = Objects.requireNonNull(type);
    this.defaultValue = Objects.requireNonNull(defaultValue);
    this.min = min;
    this.max = max;
    this.step = step;
    this.category = Objects.requireNonNull(category);
    this.effect = Objects.requireNonNull(effect);
    this.editable = editable;
    this.parser = Objects.requireNonNull(parser);
  }

  /**
   * Creates an editable integer setting with inclusive bounds.
   */
  public static Setting<Integer> ofInt(String key, int defaultValue, int min, int max,
                                       SettingCategory category, SettingEffect effect) {
    return new Setting<>(key, Integer.class, defaultValue, min, max, 1, category, effect, true,
                         text -> Integer.valueOf(text.trim()));
  }

  /**
   * Creates an editable decimal setting with inclusive bounds and an editor step size.
   */
  public static Setting<Double> ofDouble(String key, double defaultValue, double min, double max,
                                         double step, SettingCategory category, SettingEffect effect) {
    return new Setting<>(key, Double.class, defaultValue, min, max, step, category, effect, true,
                         text -> Double.valueOf(text.trim()));
  }

  /**
   * Creates an editable on/off setting. Only {@code true} and {@code false} are accepted,
   * so a typo in a configuration layer is reported instead of silently meaning "off".
   */
  public static Setting<Boolean> ofBoolean(String key, boolean defaultValue,
                                           SettingCategory category, SettingEffect effect) {
    return new Setting<>(key, Boolean.class, defaultValue, null, null, 0, category, effect, true,
                         Setting::parseStrictBoolean);
  }

  private static Boolean parseStrictBoolean(String text) {
    String value = text.trim();
    if (value.equalsIgnoreCase("true")) {
      return Boolean.TRUE;
    }
    if (value.equalsIgnoreCase("false")) {
      return Boolean.FALSE;
    }
    throw new IllegalArgumentException("not a boolean: " + text);
  }

  /**
   * Creates a read-only text setting. Its value is shown in the UI but can only be
   * changed through {@code imagerag.properties} or a system property and a restart.
   */
  public static Setting<String> readOnlyText(String key, String defaultValue,
                                             SettingCategory category, SettingEffect effect) {
    return new Setting<>(key, String.class, defaultValue, null, null, 0, category, effect, false,
                         String::trim);
  }

  public String key() {
    return key;
  }

  public Class<T> type() {
    return type;
  }

  public T defaultValue() {
    return defaultValue;
  }

  public T min() {
    return min;
  }

  public T max() {
    return max;
  }

  public double step() {
    return step;
  }

  public SettingCategory category() {
    return category;
  }

  public SettingEffect effect() {
    return effect;
  }

  public boolean editable() {
    return editable;
  }

  /**
   * Translation key of the human-readable label.
   */
  public String labelKey() {
    return "settings.item." + key + ".label";
  }

  /**
   * Translation key of the description shown below the label.
   */
  public String descriptionKey() {
    return "settings.item." + key + ".description";
  }

  /**
   * Parses and validates a textual value from a configuration layer or the store.
   */
  public Result<T, String> parse(String text) {
    if (text == null || text.isBlank()) {
      return Result.failure("empty value for " + key);
    }
    CheckedSupplier<T> parse = () -> parser.apply(text);
    return parse.get()
        .mapError(t -> "invalid value '" + text + "' for " + key)
        .flatMap(this::validate);
  }

  /**
   * Checks that a value lies within the bounds of this setting.
   */
  public Result<T, String> validate(T value) {
    if (value == null) {
      return Result.failure("missing value for " + key);
    }
    if (min != null && value.compareTo(min) < 0) {
      return Result.failure(key + " must be at least " + format(min));
    }
    if (max != null && value.compareTo(max) > 0) {
      return Result.failure(key + " must be at most " + format(max));
    }
    return Result.success(value);
  }

  /**
   * Converts a value to the textual form used by the store and the configuration layers.
   */
  public String format(T value) {
    return String.valueOf(value);
  }

  @Override
  public String toString() {
    return key;
  }
}
