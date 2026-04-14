package com.svenruppert.imagerag.service;

import com.svenruppert.imagerag.domain.*;

public interface SensitivityAssessmentService {

  SensitivityAssessment assess(ImageAsset asset,
                               ImageMetadataInfo metadataInfo,
                               LocationSummary locationSummary,
                               SemanticAnalysis semanticAnalysis);
}
