package com.svenruppert.imagerag.service;

import com.svenruppert.imagerag.domain.PromptTemplateVersion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages versioned prompt templates for the analysis pipeline.
 * <p>Analysis services consult {@link #getActiveContent(String)} at the start of each
 * invocation.  If an active override exists, it is used; otherwise the service falls
 * back to the hardcoded default embedded in its source.
 * <p>Prompt changes follow a safe draft → activate → rollback lifecycle so that
 * live production prompts are never silently mutated.
 */
public interface PromptTemplateService {

  /**
   * Returns the content of the currently active version for the given prompt key,
   * or empty if no override is active (the service should use its built-in default).
   *
   * @param promptKey one of {@code "vision"}, {@code "semantic"}, {@code "query"}
   */
  Optional<String> getActiveContent(String promptKey);

  /**
   * Returns all versions for the given key, ordered by creation time (newest first).
   */
  List<PromptTemplateVersion> getHistory(String promptKey);

  /**
   * Returns ALL versions across all prompt keys.
   */
  List<PromptTemplateVersion> findAll();

  /**
   * Saves a new draft version for the given key.  The draft is NOT active until
   * {@link #activate(UUID)} is called.
   *
   * @param promptKey   the prompt key (e.g. "vision")
   * @param version     human-readable version label (e.g. "v2-draft")
   * @param content     the full prompt text
   * @param description change notes / rationale
   * @return the persisted draft
   */
  PromptTemplateVersion saveDraft(String promptKey, String version,
                                  String content, String description);

  /**
   * Activates the given version.  The previously active version for the same key
   * is deactivated (but retained for rollback).
   *
   * @param versionId id of the version to activate
   * @return the newly activated version
   * @throws IllegalArgumentException if the version is not found
   */
  PromptTemplateVersion activate(UUID versionId);

  /**
   * Re-activates a previously active version (rollback).
   *
   * @param versionId id of the older version to restore
   * @return the restored version
   */
  PromptTemplateVersion rollback(UUID versionId);

  /**
   * Deletes a version.  Active versions and the last remaining version for a key
   * cannot be deleted.
   *
   * @throws IllegalStateException if deletion is not permitted
   */
  void delete(UUID versionId);

  /**
   * Imports the built-in prompt from a service implementation and registers it as
   * the initial active version if no version for {@code promptKey} exists yet.
   * Idempotent — safe to call on every startup.
   */
  void importBuiltInIfAbsent(String promptKey, String builtInVersion, String content);
}
