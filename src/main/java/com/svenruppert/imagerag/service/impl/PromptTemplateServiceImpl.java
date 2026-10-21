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
package com.svenruppert.imagerag.service.impl;

import com.svenruppert.functional.result.Result;
import com.svenruppert.functional.result.Unit;
import com.svenruppert.imagerag.ollama.PromptTemplates;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.imagerag.domain.PromptTemplateVersion;
import com.svenruppert.imagerag.persistence.PersistenceService;
import com.svenruppert.imagerag.service.PromptTemplateService;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Manages versioned prompt templates backed by {@link PersistenceService}.
 * <p>Thread-safe: all reads and mutations go through the thread-safe {@link PersistenceService}.
 */
public class PromptTemplateServiceImpl
    implements PromptTemplateService, HasLogger {

  /**
   * Prompt keys whose templates embed input text and therefore need a placeholder.
   */
  private static final Set<String> KEYS_REQUIRING_PLACEHOLDER =
      Set.of(SemanticDerivationServiceImpl.PROMPT_KEY);

  private final PersistenceService persistenceService;

  public PromptTemplateServiceImpl(PersistenceService persistenceService) {
    this.persistenceService = persistenceService;
  }

  // ── PromptTemplateService ────────────────────────────────────────────────

  @Override
  public Optional<String> getActiveContent(String promptKey) {
    return persistenceService.findActivePromptVersion(promptKey)
        .map(PromptTemplateVersion::getContent);
  }

  @Override
  public List<PromptTemplateVersion> getHistory(String promptKey) {
    return persistenceService.findPromptVersions(promptKey).stream()
        .sorted(Comparator.comparing(PromptTemplateVersion::getCreatedAt).reversed())
        .toList();
  }

  @Override
  public List<PromptTemplateVersion> findAll() {
    return persistenceService.findAllPromptVersions().stream()
        .sorted(Comparator.comparing(PromptTemplateVersion::getCreatedAt).reversed())
        .toList();
  }

  @Override
  public PromptTemplateVersion saveDraft(String promptKey, String version,
                                         String content, String description) {
    if (validateContent(promptKey, content) instanceof Result.Failure<Unit, String>(String reason)) {
      throw new IllegalArgumentException(reason);
    }
    // Find the currently active version for lineage tracking
    UUID previousId = persistenceService.findActivePromptVersion(promptKey)
        .map(PromptTemplateVersion::getId).orElse(null);

    PromptTemplateVersion draft = PromptTemplateVersion.create(
        promptKey, version, content, description, previousId);
    persistenceService.savePromptVersion(draft);
    logger().info("Saved prompt draft: key={} version={}", promptKey, version);
    return draft;
  }

  @Override
  public Result<Unit, String> validateContent(String promptKey, String content) {
    if (content == null || content.isBlank()) {
      return Result.failure("Prompt content is empty");
    }
    if (KEYS_REQUIRING_PLACEHOLDER.contains(promptKey)
        && PromptTemplates.countPlaceholders(content) == 0) {
      return Result.failure("Prompt '" + promptKey + "' must contain the "
                                + PromptTemplates.PLACEHOLDER + " placeholder for the input text");
    }
    return Result.success(Unit.INSTANCE);
  }

  @Override
  public PromptTemplateVersion activate(UUID versionId) {
    PromptTemplateVersion target = findOrThrow(versionId);

    // Deactivate current active version for the same key
    persistenceService.findActivePromptVersion(target.getPromptKey())
        .ifPresent(current -> {
          current.deactivate();
          persistenceService.savePromptVersion(current);
        });

    target.activate();
    persistenceService.savePromptVersion(target);
    logger().info("Activated prompt version: key={} version={}", target.getPromptKey(), target.getVersion());
    return target;
  }

  @Override
  public PromptTemplateVersion rollback(UUID versionId) {
    // Rollback is equivalent to activating an older version
    return activate(versionId);
  }

  @Override
  public void delete(UUID versionId) {
    PromptTemplateVersion v = findOrThrow(versionId);
    if (v.isActive()) {
      throw new IllegalStateException("Cannot delete the currently active prompt version.");
    }
    long remaining = persistenceService.findPromptVersions(v.getPromptKey()).size();
    if (remaining <= 1) {
      throw new IllegalStateException(
          "Cannot delete the last remaining version for prompt key '" + v.getPromptKey() + "'.");
    }
    persistenceService.deletePromptVersion(versionId);
    logger().info("Deleted prompt version: {}", versionId);
  }

  @Override
  public void importBuiltInIfAbsent(String promptKey, String builtInVersion, String content) {
    List<PromptTemplateVersion> existing = persistenceService.findPromptVersions(promptKey);
    if (!existing.isEmpty()) return; // already has versions — do not overwrite

    PromptTemplateVersion builtin = PromptTemplateVersion.createBuiltIn(
        promptKey, builtInVersion, content);
    persistenceService.savePromptVersion(builtin);
    logger().info("Imported built-in prompt: key={} version={}", promptKey, builtInVersion);
  }

  // ── Helpers ──────────────────────────────────────────────────────────────

  private PromptTemplateVersion findOrThrow(UUID id) {
    return persistenceService.findAllPromptVersions().stream()
        .filter(v -> id.equals(v.getId()))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Prompt version not found: " + id));
  }
}
