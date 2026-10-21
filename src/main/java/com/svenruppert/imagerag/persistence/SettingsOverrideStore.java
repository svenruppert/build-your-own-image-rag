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
