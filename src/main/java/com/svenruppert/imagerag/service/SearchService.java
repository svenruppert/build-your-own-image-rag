package com.svenruppert.imagerag.service;

import com.svenruppert.imagerag.domain.SearchPlan;
import com.svenruppert.imagerag.domain.SearchResultItem;
import com.svenruppert.imagerag.dto.SearchResult;

import java.util.List;

public interface SearchService {

  List<SearchResultItem> search(String naturalLanguageQuery);

  /**
   * Executes a planned search and returns results with hybrid-retrieval diagnostics.
   */
  SearchResult search(SearchPlan plan);
}
