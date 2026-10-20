package com.svenruppert.imagerag.settings;

/**
 * Notification about a changed effective setting value.
 *
 * @param setting  the changed setting
 * @param oldValue effective value before the change, as text
 * @param newValue effective value after the change, as text
 * @param reset    {@code true} when the change removed a user override
 */
public record SettingChange(Setting<?> setting, String oldValue, String newValue, boolean reset) {
}
