package com.svenruppert.imagerag.service.impl;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.imagerag.domain.CategoryMetadata;
import com.svenruppert.imagerag.domain.SemanticAnalysis;
import com.svenruppert.imagerag.domain.TaxonomySuggestion;
import com.svenruppert.imagerag.domain.enums.CategoryLifecycleState;
import com.svenruppert.imagerag.domain.enums.SourceCategory;
import com.svenruppert.imagerag.domain.enums.SuggestionStatus;
import com.svenruppert.imagerag.domain.enums.TaxonomySuggestionType;
import com.svenruppert.imagerag.persistence.PersistenceService;
import com.svenruppert.imagerag.service.TaxonomySuggestionService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages the review and application lifecycle for {@link TaxonomySuggestion} objects.
 * <p>Each {@link com.svenruppert.imagerag.domain.enums.TaxonomySuggestionType} is applied
 * by a distinct strategy that mutates the relevant persisted domain objects.
 */
public class TaxonomySuggestionServiceImpl
    implements TaxonomySuggestionService, HasLogger {

  private final PersistenceService persistenceService;

  public TaxonomySuggestionServiceImpl(PersistenceService persistenceService) {
    this.persistenceService = persistenceService;
  }

  // ── Lifecycle mutations ───────────────────────────────────────────────────

  @Override
  public void accept(UUID suggestionId) {
    persistenceService.findSuggestion(suggestionId).ifPresent(s -> {
      if (s.getStatus() == SuggestionStatus.OPEN) {
        s.setStatus(SuggestionStatus.ACCEPTED);
        persistenceService.saveTaxonomySuggestion(s);
        logger().info("Taxonomy suggestion {} accepted", suggestionId);
      }
    });
  }

  @Override
  public void reject(UUID suggestionId) {
    persistenceService.findSuggestion(suggestionId).ifPresent(s -> {
      if (s.getStatus() != SuggestionStatus.APPLIED) {
        s.setStatus(SuggestionStatus.REJECTED);
        persistenceService.saveTaxonomySuggestion(s);
        logger().info("Taxonomy suggestion {} rejected", suggestionId);
      }
    });
  }

  @Override
  public void apply(UUID suggestionId) {
    Optional<TaxonomySuggestion> opt = persistenceService.findSuggestion(suggestionId);
    if (opt.isEmpty()) {
      logger().warn("Suggestion {} not found — cannot apply", suggestionId);
      return;
    }
    TaxonomySuggestion s = opt.get();
    if (s.getStatus() == SuggestionStatus.REJECTED || s.getStatus() == SuggestionStatus.APPLIED) {
      logger().debug("Skipping suggestion {} (status={})", suggestionId, s.getStatus());
      return;
    }
    try {
      applyInternal(s);
      s.setStatus(SuggestionStatus.APPLIED);
      s.setAppliedAt(Instant.now());
      persistenceService.saveTaxonomySuggestion(s);
      logger().info("Applied taxonomy suggestion {} (type={})", suggestionId, s.getType());
    } catch (Exception e) {
      logger().error("Failed to apply suggestion {}: {}", suggestionId, e.getMessage(), e);
    }
  }

  // ── Bulk operations ───────────────────────────────────────────────────────

  @Override
  public void bulkAccept(List<UUID> suggestionIds) {
    if (suggestionIds == null) return;
    suggestionIds.forEach(this::accept);
  }

  @Override
  public void bulkReject(List<UUID> suggestionIds) {
    if (suggestionIds == null) return;
    suggestionIds.forEach(this::reject);
  }

  @Override
  public int bulkApply(List<UUID> suggestionIds) {
    if (suggestionIds == null) return 0;
    int applied = 0;
    for (UUID id : suggestionIds) {
      Optional<TaxonomySuggestion> opt = persistenceService.findSuggestion(id);
      if (opt.isPresent()) {
        SuggestionStatus status = opt.get().getStatus();
        if (status == SuggestionStatus.OPEN || status == SuggestionStatus.ACCEPTED) {
          apply(id);
          applied++;
        }
      }
    }
    return applied;
  }

  // ── Queries ───────────────────────────────────────────────────────────────

  @Override
  public List<TaxonomySuggestion> findAll() {
    return persistenceService.findAllSuggestions();
  }

  @Override
  public List<TaxonomySuggestion> findByStatus(SuggestionStatus status) {
    return persistenceService.findSuggestionsByStatus(status);
  }

  // ── Internal apply dispatch ───────────────────────────────────────────────

  private void applyInternal(TaxonomySuggestion s) {
    TaxonomySuggestionType type = s.getType();
    if (type == null) {
      logger().warn("Suggestion {} has null type — skipping", s.getId());
      return;
    }
    switch (type) {
      case RECLASSIFY_IMAGE -> applyReclassify(s);
      case ADD_SECONDARY_CATEGORY -> applyAddSecondary(s);
      case REMOVE_SECONDARY_CATEGORY -> applyRemoveSecondary(s);
      case DEPRECATE_CATEGORY -> applyDeprecate(s);
      case REASSIGN_DEPRECATED_CATEGORY -> applyReclassify(s);
      case MERGE_CATEGORIES -> applyMerge(s);
      case ADD_ALIAS -> {
        // CategoryRegistry.ALIASES is static/in-memory only;
        // log the alias for operator awareness, no runtime mutation possible without restart
        logger().info("ADD_ALIAS suggestion applied: '{}' → {}. "
                          + "Add this alias to CategoryRegistry.ALIASES for persistence.",
                      s.getSuggestedAlias(), s.getCurrentCategory());
      }
      default -> logger().warn("Unknown suggestion type {} — no action taken", type);
    }
  }

  private void applyReclassify(TaxonomySuggestion s) {
    UUID imageId = s.getTargetImageId();
    SourceCategory newCat = s.getSuggestedCategory();
    if (imageId == null || newCat == null) {
      logger().warn("RECLASSIFY suggestion {} missing imageId or suggestedCategory", s.getId());
      return;
    }
    persistenceService.updateSourceCategory(imageId, newCat);
    logger().info("Reclassified image {} → {}", imageId, newCat);
  }

  private void applyAddSecondary(TaxonomySuggestion s) {
    UUID imageId = s.getTargetImageId();
    SourceCategory toAdd = s.getSuggestedCategory();
    if (imageId == null || toAdd == null) {
      logger().warn("ADD_SECONDARY suggestion {} missing imageId or suggestedCategory", s.getId());
      return;
    }
    Optional<SemanticAnalysis> opt = persistenceService.findAnalysis(imageId);
    if (opt.isEmpty()) {
      logger().warn("ADD_SECONDARY: no analysis found for image {}", imageId);
      return;
    }
    SemanticAnalysis analysis = opt.get();
    List<SourceCategory> secondary = new ArrayList<>(analysis.getSecondaryCategories());
    if (!secondary.contains(toAdd)) {
      secondary.add(toAdd);
      persistenceService.updateSecondaryCategories(imageId, secondary);
      logger().info("Added secondary category {} to image {}", toAdd, imageId);
    }
  }

  private void applyRemoveSecondary(TaxonomySuggestion s) {
    UUID imageId = s.getTargetImageId();
    SourceCategory toRemove = s.getSuggestedCategory();
    if (imageId == null || toRemove == null) {
      logger().warn("REMOVE_SECONDARY suggestion {} missing imageId or suggestedCategory", s.getId());
      return;
    }
    Optional<SemanticAnalysis> opt = persistenceService.findAnalysis(imageId);
    if (opt.isEmpty()) return;
    SemanticAnalysis analysis = opt.get();
    List<SourceCategory> secondary = new ArrayList<>(analysis.getSecondaryCategories());
    if (secondary.remove(toRemove)) {
      persistenceService.updateSecondaryCategories(imageId, secondary);
      logger().info("Removed secondary category {} from image {}", toRemove, imageId);
    }
  }

  private void applyDeprecate(TaxonomySuggestion s) {
    SourceCategory toDeprecate = s.getCurrentCategory();
    if (toDeprecate == null) {
      logger().warn("DEPRECATE_CATEGORY suggestion {} missing currentCategory", s.getId());
      return;
    }
    CategoryMetadata meta = persistenceService.findCategoryMetadata(toDeprecate)
        .orElseGet(() -> new CategoryMetadata(toDeprecate, CategoryLifecycleState.ACTIVE, null, null));
    meta.setCategory(toDeprecate);
    meta.setLifecycleState(CategoryLifecycleState.DEPRECATED);
    meta.setReplacementCategory(s.getSuggestedCategory());
    if (s.getRationale() != null) {
      meta.setNotes(s.getRationale());
    }
    persistenceService.saveCategoryMetadata(meta);
    logger().info("Deprecated category {} (replacement={})", toDeprecate, s.getSuggestedCategory());
  }

  private void applyMerge(TaxonomySuggestion s) {
    // MERGE: deprecate current, reassign all images that have current as primary
    SourceCategory source = s.getCurrentCategory();
    SourceCategory target = s.getSuggestedCategory();
    if (source == null || target == null) {
      logger().warn("MERGE_CATEGORIES suggestion {} missing current or suggested category", s.getId());
      return;
    }
    // Deprecate the source category
    CategoryMetadata meta = persistenceService.findCategoryMetadata(source)
        .orElseGet(() -> new CategoryMetadata(source, CategoryLifecycleState.ACTIVE, null, null));
    meta.setCategory(source);
    meta.setLifecycleState(CategoryLifecycleState.DEPRECATED);
    meta.setReplacementCategory(target);
    meta.setNotes("Merged into " + target + " via taxonomy maintenance");
    persistenceService.saveCategoryMetadata(meta);

    // Reassign all images whose primary is the deprecated source
    int reassigned = 0;
    for (var asset : persistenceService.findAllImages()) {
      Optional<SemanticAnalysis> analysisOpt = persistenceService.findAnalysis(asset.getId());
      if (analysisOpt.isPresent() && analysisOpt.get().getSourceCategory() == source) {
        persistenceService.updateSourceCategory(asset.getId(), target);
        reassigned++;
      }
    }
    logger().info("Merged category {} → {}: {} images reassigned", source, target, reassigned);
  }
}
