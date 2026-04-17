package com.svenruppert.imagerag.service.impl;

import com.svenruppert.imagerag.domain.PromptTemplateVersion;
import com.svenruppert.imagerag.persistence.PersistenceService;
import com.svenruppert.imagerag.service.PromptTemplateService;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Manages versioned prompt templates backed by {@link PersistenceService}.
 * <p>Thread-safe: all mutations go through {@code PersistenceService.store()} which is
 * synchronised at the EclipseStore level.
 */
public class PromptTemplateServiceImpl
    implements PromptTemplateService {

  private static final Logger LOG = Logger.getLogger(PromptTemplateServiceImpl.class.getName());

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
    // Find the currently active version for lineage tracking
    UUID previousId = persistenceService.findActivePromptVersion(promptKey)
        .map(PromptTemplateVersion::getId).orElse(null);

    PromptTemplateVersion draft = PromptTemplateVersion.create(
        promptKey, version, content, description, previousId);
    persistenceService.savePromptVersion(draft);
    LOG.info("Saved prompt draft: key=" + promptKey + " version=" + version);
    return draft;
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
    LOG.info("Activated prompt version: key=" + target.getPromptKey() + " version=" + target.getVersion());
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
    LOG.info("Deleted prompt version: " + versionId);
  }

  @Override
  public void importBuiltInIfAbsent(String promptKey, String builtInVersion, String content) {
    List<PromptTemplateVersion> existing = persistenceService.findPromptVersions(promptKey);
    if (!existing.isEmpty()) return; // already has versions — do not overwrite

    PromptTemplateVersion builtin = PromptTemplateVersion.createBuiltIn(
        promptKey, builtInVersion, content);
    persistenceService.savePromptVersion(builtin);
    LOG.info("Imported built-in prompt: key=" + promptKey + " version=" + builtInVersion);
  }

  // ── Helpers ──────────────────────────────────────────────────────────────

  private PromptTemplateVersion findOrThrow(UUID id) {
    return persistenceService.findAllPromptVersions().stream()
        .filter(v -> id.equals(v.getId()))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Prompt version not found: " + id));
  }
}
