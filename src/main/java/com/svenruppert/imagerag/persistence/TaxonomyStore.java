package com.svenruppert.imagerag.persistence;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.imagerag.domain.CategoryClusterSuggestion;
import com.svenruppert.imagerag.domain.CategoryMetadata;
import com.svenruppert.imagerag.domain.TaxonomySuggestion;
import com.svenruppert.imagerag.domain.enums.SourceCategory;
import com.svenruppert.imagerag.domain.enums.SuggestionStatus;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Taxonomy maintenance data: taxonomy suggestions, category lifecycle metadata and
 * cluster-discovery suggestions.
 */
final class TaxonomyStore
    implements HasLogger {

  private final PersistenceContext ctx;

  TaxonomyStore(PersistenceContext ctx) {
    this.ctx = ctx;
  }

  /**
   * Replaces the suggestion with the same id, or appends it.
   */
  void saveTaxonomySuggestion(TaxonomySuggestion suggestion) {
    ctx.write(() -> {
      List<TaxonomySuggestion> list = ctx.root().getTaxonomySuggestions();
      list.removeIf(s -> suggestion.getId().equals(s.getId()));
      list.add(suggestion);
      ctx.store(list);
    });
  }

  List<TaxonomySuggestion> findAllSuggestions() {
    return ctx.read(() -> List.copyOf(ctx.root().getTaxonomySuggestions()));
  }

  List<TaxonomySuggestion> findSuggestionsByStatus(SuggestionStatus status) {
    return ctx.read(() -> ctx.root().getTaxonomySuggestions().stream()
        .filter(s -> s.getStatus() == status)
        .collect(Collectors.toList()));
  }

  Optional<TaxonomySuggestion> findSuggestion(UUID suggestionId) {
    return ctx.read(() -> ctx.root().getTaxonomySuggestions().stream()
        .filter(s -> suggestionId.equals(s.getId()))
        .findFirst());
  }

  /**
   * Removes all suggestions before a fresh analysis run.
   */
  void clearTaxonomySuggestions() {
    ctx.write(() -> {
      ctx.root().getTaxonomySuggestions().clear();
      ctx.store(ctx.root().getTaxonomySuggestions());
    });
  }

  // ── Category lifecycle metadata ───────────────────────────────────────────

  void saveCategoryMetadata(CategoryMetadata meta) {
    ctx.write(() -> {
      ctx.root().getCategoryMetadata().put(meta.getCategory().name(), meta);
      ctx.store(ctx.root().getCategoryMetadata());
    });
  }

  /**
   * Returns the metadata of the category, or empty when none was created
   * (the category is then implicitly ACTIVE).
   */
  Optional<CategoryMetadata> findCategoryMetadata(SourceCategory category) {
    return ctx.read(() -> {
      if (category == null) return Optional.empty();
      return Optional.ofNullable(ctx.root().getCategoryMetadata().get(category.name()));
    });
  }

  List<CategoryMetadata> findAllCategoryMetadata() {
    return ctx.read(() -> Collections.unmodifiableList(
        new ArrayList<>(ctx.root().getCategoryMetadata().values())));
  }

  // ── Cluster suggestions ───────────────────────────────────────────────────

  void saveClusterSuggestion(CategoryClusterSuggestion suggestion) {
    ctx.write(() -> {
      List<CategoryClusterSuggestion> list = ctx.root().getClusterSuggestions();
      list.removeIf(x -> suggestion.getId().equals(x.getId()));
      list.add(suggestion);
      ctx.store(list);
      logger().debug("Saved cluster suggestion id={}", suggestion.getId());
    });
  }

  List<CategoryClusterSuggestion> findAllClusterSuggestions() {
    return ctx.read(() -> List.copyOf(ctx.root().getClusterSuggestions()));
  }

  /**
   * Replaces all cluster suggestions with the result of a discovery run.
   */
  void replaceAllClusterSuggestions(List<CategoryClusterSuggestion> suggestions) {
    ctx.write(() -> {
      ctx.root().getClusterSuggestions().clear();
      ctx.root().getClusterSuggestions().addAll(suggestions);
      ctx.store(ctx.root().getClusterSuggestions());
      logger().info("Replaced cluster suggestions: {} entries", suggestions.size());
    });
  }

  void deleteClusterSuggestion(UUID id) {
    ctx.write(() -> {
      boolean removed = ctx.root().getClusterSuggestions().removeIf(s -> id.equals(s.getId()));
      if (removed) ctx.store(ctx.root().getClusterSuggestions());
    });
  }
}
