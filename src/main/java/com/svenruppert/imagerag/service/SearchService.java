package com.svenruppert.imagerag.service;

import com.svenruppert.imagerag.domain.SearchPlan;
import com.svenruppert.imagerag.domain.SearchResultItem;

import java.util.List;

public interface SearchService {

  List<SearchResultItem> search(String naturalLanguageQuery);

  List<SearchResultItem> search(SearchPlan plan);
}
