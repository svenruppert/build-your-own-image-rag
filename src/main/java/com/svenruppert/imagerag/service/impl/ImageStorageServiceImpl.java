package com.svenruppert.imagerag.service.impl;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.imagerag.dto.StoredImage;
import com.svenruppert.imagerag.service.ImageStorageService;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

public class ImageStorageServiceImpl
    implements ImageStorageService, HasLogger {

  private final Path storageRoot;

  public ImageStorageServiceImpl(Path storageRoot)
      throws IOException {
    this.storageRoot = storageRoot;
    Files.createDirectories(storageRoot);
    logger().info("Image storage directory: {}", storageRoot.toAbsolutePath());
  }

  @Override
  public StoredImage store(InputStream inputStream, String originalFilename, String mimeType)
      throws IOException {
    UUID imageId = UUID.randomUUID();
    String extension = extractExtension(originalFilename);
    String storedFilename = imageId + extension;
    Path targetPath = storageRoot.resolve(storedFilename);

    // Write to a temp file first to compute hash, then move
    Path tempPath = storageRoot.resolve(imageId + ".tmp");
    try {
      Files.copy(inputStream, tempPath, StandardCopyOption.REPLACE_EXISTING);
      String sha256 = computeSha256(tempPath);
      Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
      long fileSize = Files.size(targetPath);

      logger().info("Stored image {} -> {} ({} bytes)", originalFilename, storedFilename, fileSize);
      return new StoredImage(imageId, originalFilename, storedFilename, targetPath, fileSize, sha256);

    } catch (Exception e) {
      Files.deleteIfExists(tempPath);
      throw new IOException("Failed to store image: " + originalFilename, e);
    }
  }

  @Override
  public InputStream load(UUID imageId)
      throws IOException {
    Path path = resolvePath(imageId);
    if (!Files.exists(path)) {
      throw new IOException("Image not found for id: " + imageId);
    }
    return Files.newInputStream(path);
  }

  @Override
  public Path resolvePath(UUID imageId) {
    // Try common extensions. All candidates are canonicalised against the storage
    // root to ensure no resolved path escapes the configured directory. Since the
    // id is a UUID this is already structurally safe, but the guard is kept as
    // defence-in-depth in case resolvePath is ever called with a tampered value
    // via reflection or refactoring.
    for (String ext : new String[]{".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp", ".tiff"}) {
      Path candidate = storageRoot.resolve(imageId + ext);
      if (isWithinRoot(candidate) && Files.exists(candidate)) return candidate;
    }
    Path fallback = storageRoot.resolve(imageId.toString());
    if (!isWithinRoot(fallback)) {
      throw new IllegalStateException("Resolved path escapes storage root: " + fallback);
    }
    return fallback;
  }

  private boolean isWithinRoot(Path candidate) {
    try {
      Path normalizedRoot = storageRoot.toAbsolutePath().normalize();
      Path normalizedCandidate = candidate.toAbsolutePath().normalize();
      return normalizedCandidate.startsWith(normalizedRoot);
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public void delete(UUID imageId)
      throws IOException {
    Path path = resolvePath(imageId);
    boolean deleted = Files.deleteIfExists(path);
    if (deleted) {
      logger().info("Deleted image file for imageId={}", imageId);
    } else {
      logger().warn("Image file not found for deletion, imageId={}", imageId);
    }
  }

  private String extractExtension(String filename) {
    if (filename == null) return ".jpg";
    int dot = filename.lastIndexOf('.');
    return (dot >= 0) ? filename.substring(dot).toLowerCase() : ".jpg";
  }

  private String computeSha256(Path path)
      throws IOException {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = Files.readAllBytes(path);
      byte[] hash = digest.digest(bytes);
      return HexFormat.of().formatHex(hash);
    } catch (Exception e) {
      throw new IOException("SHA-256 computation failed", e);
    }
  }
}
