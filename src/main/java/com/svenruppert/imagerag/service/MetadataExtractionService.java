package com.svenruppert.imagerag.service;

import com.svenruppert.imagerag.dto.ExtractedMetadata;

import java.io.IOException;
import java.nio.file.Path;

public interface MetadataExtractionService {

  ExtractedMetadata extract(Path imagePath)
      throws IOException;
}
