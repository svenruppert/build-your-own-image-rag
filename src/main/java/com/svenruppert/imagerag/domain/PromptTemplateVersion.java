package com.svenruppert.imagerag.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * A versioned prompt template stored in EclipseStore.
 * <p>Each record represents one version of a named prompt (identified by {@link #promptKey}).
 * At most one version per key is {@code active} at any time; drafts may coexist alongside the
 * active version until explicitly activated.  When a new version is activated the previously
 * active version is retained for history / rollback — it is never deleted automatically.
 * <p>EclipseStore-persisted (no-arg constructor, non-final fields, lazy-init collections in root).
 * <h3>Standard prompt keys</h3>
 * <ul>
 *   <li>{@code "vision"} — vision-analysis prompt used by {@link com.svenruppert.imagerag.service.impl.VisionAnalysisServiceImpl}</li>
 *   <li>{@code "semantic"} — semantic-derivation prompt used by {@link com.svenruppert.imagerag.service.impl.SemanticDerivationServiceImpl}</li>
 *   <li>{@code "query"} — query-understanding prompt used by {@link com.svenruppert.imagerag.service.impl.QueryUnderstandingServiceImpl}</li>
 * </ul>
 */
public class PromptTemplateVersion {

  // ── Fields (EclipseStore-compatible) ────────────────────────────────────

  private UUID id;
  /**
   * Logical name identifying which prompt this is a version of (e.g. "vision").
   */
  private String promptKey;
  /**
   * Human-readable version label (e.g. "v1", "v2-draft-2024-04").
   */
  private String version;
  /**
   * Full prompt template text. May contain {@code %s} or named placeholders.
   */
  private String content;
  /**
   * True if this version is currently served to the analysis services.
   */
  private boolean active;
  /**
   * True if this is an unreviewed draft, not yet activated.
   */
  private boolean draft;
  private Instant createdAt;
  /**
   * Optional description / change notes.
   */
  private String description;
  /**
   * ID of the version this was based on (null for the first version).
   */
  private UUID previousVersionId;
  /**
   * The user/system that created this version (display purposes).
   */
  private String createdBy;

  public PromptTemplateVersion() {
  }

  // ── Factory ─────────────────────────────────────────────────────────────

  /**
   * Creates an initial version (not yet active, not yet a draft — caller sets state).
   */
  public static PromptTemplateVersion create(
      String promptKey, String version, String content,
      String description, UUID previousVersionId) {

    PromptTemplateVersion v = new PromptTemplateVersion();
    v.id = UUID.randomUUID();
    v.promptKey = promptKey;
    v.version = version;
    v.content = content;
    v.active = false;
    v.draft = true;
    v.createdAt = Instant.now();
    v.description = description;
    v.previousVersionId = previousVersionId;
    v.createdBy = "admin";
    return v;
  }

  /**
   * Creates the initial built-in version for a prompt key.
   */
  public static PromptTemplateVersion createBuiltIn(
      String promptKey, String builtInVersion, String content) {

    PromptTemplateVersion v = new PromptTemplateVersion();
    v.id = UUID.randomUUID();
    v.promptKey = promptKey;
    v.version = builtInVersion;
    v.content = content;
    v.active = true;
    v.draft = false;
    v.createdAt = Instant.now();
    v.description = "Built-in default prompt (imported from source)";
    v.createdBy = "system";
    return v;
  }

  // ── Accessors ───────────────────────────────────────────────────────────

  public UUID getId() {
    return id;
  }

  public String getPromptKey() {
    return promptKey;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public boolean isActive() {
    return active;
  }

  public boolean isDraft() {
    return draft;
  }

  public void setDraft(boolean draft) {
    this.draft = draft;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  // ── Mutators ─────────────────────────────────────────────────────────────

  public String getDescription() {
    return description;
  }

  public void setDescription(String desc) {
    this.description = desc;
  }

  public UUID getPreviousVersionId() {
    return previousVersionId;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public void activate() {
    this.active = true;
    this.draft = false;
  }

  public void deactivate() {
    this.active = false;
  }

  /**
   * Display label for UI lists.
   */
  public String displayLabel() {
    String suffix = active ? " ✓ active" : draft ? " (draft)" : " (inactive)";
    return version + suffix;
  }
}
