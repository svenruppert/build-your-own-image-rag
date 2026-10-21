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

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.imagerag.domain.PromptTemplateVersion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Managed prompt template versions of the migration center.
 */
final class PromptVersionStore
    implements HasLogger {

  private final PersistenceContext ctx;

  PromptVersionStore(PersistenceContext ctx) {
    this.ctx = ctx;
  }

  /**
   * Replaces the version with the same id, or appends it.
   */
  void savePromptVersion(PromptTemplateVersion version) {
    ctx.write(() -> {
      List<PromptTemplateVersion> list = ctx.root().getPromptVersions();
      list.removeIf(x -> version.getId().equals(x.getId()));
      list.add(version);
      ctx.store(list);
      logger().info("Saved prompt version: key={} version={} active={}",
                    version.getPromptKey(), version.getVersion(), version.isActive());
    });
  }

  List<PromptTemplateVersion> findPromptVersions(String promptKey) {
    return ctx.read(() -> ctx.root().getPromptVersions().stream()
        .filter(v -> promptKey.equals(v.getPromptKey()))
        .collect(Collectors.toList()));
  }

  List<PromptTemplateVersion> findAllPromptVersions() {
    return ctx.read(() -> List.copyOf(ctx.root().getPromptVersions()));
  }

  Optional<PromptTemplateVersion> findActivePromptVersion(String promptKey) {
    return ctx.read(() -> ctx.root().getPromptVersions().stream()
        .filter(v -> promptKey.equals(v.getPromptKey()) && v.isActive())
        .findFirst());
  }

  void deletePromptVersion(UUID id) {
    ctx.write(() -> {
      boolean removed = ctx.root().getPromptVersions().removeIf(v -> id.equals(v.getId()));
      if (removed) ctx.store(ctx.root().getPromptVersions());
    });
  }
}
