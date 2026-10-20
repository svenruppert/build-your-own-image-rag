package com.svenruppert.imagerag.persistence;

import com.svenruppert.imagerag.settings.SettingsStore;

import java.util.Map;

/**
 * Persisted user overrides of runtime settings (setting key → serialized value).
 */
final class SettingsOverrideStore
    implements SettingsStore {

  private final PersistenceContext ctx;

  SettingsOverrideStore(PersistenceContext ctx) {
    this.ctx = ctx;
  }

  @Override
  public Map<String, String> loadSettingOverrides() {
    return ctx.read(() -> Map.copyOf(ctx.root().getSettingOverrides()));
  }

  @Override
  public void putSettingOverride(String key, String value) {
    ctx.write(() -> {
      ctx.root().getSettingOverrides().put(key, value);
      ctx.store(ctx.root().getSettingOverrides());
    });
  }

  @Override
  public void removeSettingOverride(String key) {
    ctx.write(() -> {
      if (ctx.root().getSettingOverrides().remove(key) != null) {
        ctx.store(ctx.root().getSettingOverrides());
      }
    });
  }

  @Override
  public void clearSettingOverrides() {
    ctx.write(() -> {
      ctx.root().getSettingOverrides().clear();
      ctx.store(ctx.root().getSettingOverrides());
    });
  }
}
