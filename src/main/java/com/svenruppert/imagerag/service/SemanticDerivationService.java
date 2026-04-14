package com.svenruppert.imagerag.service;

import com.svenruppert.imagerag.domain.SemanticAnalysis;
import com.svenruppert.imagerag.dto.VisionAnalysisResponse;

import java.util.UUID;

public interface SemanticDerivationService {

  SemanticAnalysis derive(UUID imageId, VisionAnalysisResponse response);
}
