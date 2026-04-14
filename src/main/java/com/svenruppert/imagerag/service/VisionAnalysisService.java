package com.svenruppert.imagerag.service;

import com.svenruppert.imagerag.dto.VisionAnalysisResponse;

import java.nio.file.Path;

public interface VisionAnalysisService {

  VisionAnalysisResponse analyzeImage(Path imagePath);
}
