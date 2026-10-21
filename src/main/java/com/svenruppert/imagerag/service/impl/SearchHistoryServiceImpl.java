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

import com.svenruppert.imagerag.domain.enums.SearchMode;
import com.svenruppert.imagerag.domain.RecentSearchEntry;
import com.svenruppert.imagerag.domain.SavedSearchView;
import com.svenruppert.imagerag.domain.SearchTuningPreset;
import com.svenruppert.imagerag.persistence.PersistenceService;
import com.svenruppert.imagerag.service.SearchHistoryService;

import java.util.List;
import java.util.UUID;

/**
 * {@link SearchHistoryService} backed by the EclipseStore {@link PersistenceService}.
 */
public class SearchHistoryServiceImpl
    implements SearchHistoryService {

  private final PersistenceService persistenceService;

  public SearchHistoryServiceImpl(PersistenceService persistenceService) {
    this.persistenceService = persistenceService;
  }

  @Override
  public List<RecentSearchEntry> getRecentSearches() {
    return persistenceService.getRecentSearches();
  }

  @Override
  public void addRecentSearch(String query, String finalQuery, SearchMode mode) {
    persistenceService.addRecentSearch(query, finalQuery, mode);
  }

  @Override
  public void clearRecentSearches() {
    persistenceService.clearRecentSearches();
  }

  @Override
  public void deleteRecentSearches(List<RecentSearchEntry> entries) {
    persistenceService.deleteRecentSearches(entries);
  }

  @Override
  public List<SavedSearchView> getSavedSearchViews() {
    return persistenceService.getSavedSearchViews();
  }

  @Override
  public void saveSavedSearchView(SavedSearchView view) {
    persistenceService.saveSavedSearchView(view);
  }

  @Override
  public void deleteSavedSearchView(UUID viewId) {
    persistenceService.deleteSavedSearchView(viewId);
  }

  @Override
  public List<SearchTuningPreset> findAllTuningPresets() {
    return persistenceService.findAllTuningPresets();
  }

  @Override
  public void saveTuningPreset(SearchTuningPreset preset) {
    persistenceService.saveTuningPreset(preset);
  }

  @Override
  public void deleteTuningPreset(UUID presetId) {
    persistenceService.deleteTuningPreset(presetId);
  }
}
