package com.svenruppert.imagerag.service;

import com.svenruppert.imagerag.domain.SearchPlan;

public interface QueryUnderstandingService {

  SearchPlan understand(String query);
}
