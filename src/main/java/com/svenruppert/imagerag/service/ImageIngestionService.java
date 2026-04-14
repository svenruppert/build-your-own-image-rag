package com.svenruppert.imagerag.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

public interface ImageIngestionService {

  UUID ingest(InputStream inputStream, String filename, String mimeType)
      throws IOException;
}
