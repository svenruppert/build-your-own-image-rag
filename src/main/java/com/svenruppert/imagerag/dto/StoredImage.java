package com.svenruppert.imagerag.dto;

import java.nio.file.Path;
import java.util.UUID;

public record StoredImage(
    UUID imageId,
    String originalFilename,
    String storedFilename,
    Path storagePath,
    long fileSize,
    String sha256
) {
}
