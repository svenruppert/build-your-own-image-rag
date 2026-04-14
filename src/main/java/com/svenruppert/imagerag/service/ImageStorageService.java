package com.svenruppert.imagerag.service;

import com.svenruppert.imagerag.dto.StoredImage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.UUID;

public interface ImageStorageService {

  StoredImage store(InputStream inputStream, String originalFilename, String mimeType)
      throws IOException;

  InputStream load(UUID imageId)
      throws IOException;

  Path resolvePath(UUID imageId);

  /**
   * Deletes the stored image file for the given id. No-op if the file does not exist.
   */
  void delete(UUID imageId)
      throws IOException;
}
