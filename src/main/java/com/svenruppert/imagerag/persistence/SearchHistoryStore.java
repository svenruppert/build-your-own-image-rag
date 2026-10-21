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
import com.svenruppert.imagerag.domain.RecentSearchEntry;
import com.svenruppert.imagerag.domain.SavedSearchView;
import com.svenruppert.imagerag.domain.SearchTuningPreset;
import com.svenruppert.imagerag.domain.enums.SearchMode;
import com.svenruppert.imagerag.settings.Settings;
import com.svenruppert.imagerag.settings.SettingsService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Recent searches, saved search views and Search Tuning Lab presets.
 */
final class SearchHistoryStore
    implements HasLogger {

  private final PersistenceContext ctx;

  SearchHistoryStore(PersistenceContext ctx) {
    this.ctx = ctx;
  }

  /**
   * Prepends the search to the recent-search list (case-insensitive deduplication on the
   * original query), trims the list to {@link Settings#SEARCH_RECENT_MAX} and persists.
   *
   * @param query      the original user query
   * @param finalQuery the LLM-transformed embedding text (may be null for transform-only runs)
   * @param mode       which search mode was used
   */
  void addRecentSearch(String query, String finalQuery, SearchMode mode) {
    ctx.write(() -> {
      if (query == null || query.isBlank()) {
        return;
      }
      String trimmed = query.trim();
      List<RecentSearchEntry> recent = ctx.root().getRecentSearches();
      // Remove an existing entry with the same query so the new one moves to the top
      for (int i = recent.size() - 1; i >= 0; i--) {
        if (trimmed.equalsIgnoreCase(recent.get(i).getQuery())) {
          recent.remove(i);
        }
      }
      recent.addFirst(new RecentSearchEntry(trimmed, finalQuery, mode, Instant.now()));
      int maxRecent = SettingsService.getInstance().get(Settings.SEARCH_RECENT_MAX);
      while (recent.size() > maxRecent) {
        recent.removeLast();
      }
      ctx.store(recent);
      logger().debug("Recent search history updated: {} entries", recent.size());
    });
  }

  /**
   * Removes the given entries; matching uses {@link Object#equals}, so callers pass the
   * instances returned by {@link #getRecentSearches()}.
   */
  void deleteRecentSearches(List<RecentSearchEntry> toDelete) {
    ctx.write(() -> {
      if (toDelete == null || toDelete.isEmpty()) {
        return;
      }
      ctx.root().getRecentSearches().removeAll(toDelete);
      ctx.store(ctx.root().getRecentSearches());
      logger().info("Deleted {} recent search entries", toDelete.size());
    });
  }

  /**
   * Returns an unmodifiable snapshot of the recent searches (newest first).
   */
  List<RecentSearchEntry> getRecentSearches() {
    return ctx.read(() -> List.copyOf(ctx.root().getRecentSearches()));
  }

  void clearRecentSearches() {
    ctx.write(() -> {
      ctx.root().getRecentSearches().clear();
      ctx.store(ctx.root().getRecentSearches());
      logger().info("Recent search history cleared");
    });
  }

  // ── Saved search views ────────────────────────────────────────────────────

  void saveSavedSearchView(SavedSearchView view) {
    ctx.write(() -> {
      ctx.root().getSavedSearchViews().add(view);
      ctx.store(ctx.root().getSavedSearchViews());
    });
  }

  List<SavedSearchView> getSavedSearchViews() {
    return ctx.read(() -> List.copyOf(ctx.root().getSavedSearchViews()));
  }

  void deleteSavedSearchView(UUID viewId) {
    ctx.write(() -> {
      ctx.root().getSavedSearchViews().removeIf(v -> viewId.equals(v.getId()));
      ctx.store(ctx.root().getSavedSearchViews());
    });
  }

  // ── Search Tuning Lab presets ─────────────────────────────────────────────

  /**
   * Replaces the preset with the same id, or appends a preset that was never saved.
   */
  void saveTuningPreset(SearchTuningPreset preset) {
    ctx.write(() -> {
      List<SearchTuningPreset> list = ctx.root().getTuningPresets();
      boolean replaced = false;
      for (int i = 0; i < list.size(); i++) {
        if (preset.getId().equals(list.get(i).getId())) {
          list.set(i, preset);
          replaced = true;
          break;
        }
      }
      if (!replaced) {
        list.add(preset);
      }
      ctx.store(list);
      logger().info("Saved tuning preset '{}' (id={})", preset.getName(), preset.getId());
    });
  }

  /**
   * Returns an unmodifiable snapshot of all presets in insertion order (oldest first).
   */
  List<SearchTuningPreset> findAllTuningPresets() {
    return ctx.read(() -> List.copyOf(ctx.root().getTuningPresets()));
  }

  void deleteTuningPreset(UUID presetId) {
    ctx.write(() -> {
      boolean removed = ctx.root().getTuningPresets().removeIf(p -> presetId.equals(p.getId()));
      if (removed) {
        ctx.store(ctx.root().getTuningPresets());
        logger().info("Deleted tuning preset id={}", presetId);
      }
    });
  }
}
