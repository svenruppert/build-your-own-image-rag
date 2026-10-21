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
