package com.svenruppert.imagerag.service;

import com.svenruppert.imagerag.domain.enums.SearchMode;
import com.svenruppert.imagerag.domain.RecentSearchEntry;
import com.svenruppert.imagerag.domain.SavedSearchView;
import com.svenruppert.imagerag.domain.SearchTuningPreset;

import java.util.List;
import java.util.UUID;

/**
 * Recent searches, saved search views and search tuning presets.
 */
public interface SearchHistoryService {

  List<RecentSearchEntry> getRecentSearches();

  void addRecentSearch(String query, String finalQuery, SearchMode mode);

  void clearRecentSearches();

  void deleteRecentSearches(List<RecentSearchEntry> entries);

  List<SavedSearchView> getSavedSearchViews();

  void saveSavedSearchView(SavedSearchView view);

  void deleteSavedSearchView(UUID viewId);

  List<SearchTuningPreset> findAllTuningPresets();

  void saveTuningPreset(SearchTuningPreset preset);

  void deleteTuningPreset(UUID presetId);
}
