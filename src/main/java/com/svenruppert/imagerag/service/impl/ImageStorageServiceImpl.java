/*
 * Copyright © 2026 Sven Ruppert (sven.ruppert@gmail.com)
 *
 * Licensed under the EUPL, Version 1.2 (the "Licence");
 * you may not use this file except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *     https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */
package com.svenruppert.imagerag.service.impl;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.functional.result.Result;
import com.svenruppert.functional.result.Unit;
import com.svenruppert.functional.result.functions.CheckedExecutor;
import com.svenruppert.functional.result.functions.CheckedSupplier;
import com.svenruppert.imagerag.dto.StoredImage;
import com.svenruppert.imagerag.service.ImageStorageService;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
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
  public Result<StoredImage, String> store(InputStream inputStream, String originalFilename,
                                           String mimeType) {
    UUID imageId = UUID.randomUUID();

    // Write to a temp file first to verify the content and compute the hash, then move.
    // The stored extension is derived from the detected content format, never from the
    // client-supplied filename, so it cannot carry path separators or unexpected types.
    Path tempPath = storageRoot.resolve(imageId + ".tmp");
    CheckedSupplier<StoredImage> write = () -> {
      Files.copy(inputStream, tempPath, StandardCopyOption.REPLACE_EXISTING);
      String extension = detectImageExtension(tempPath).orElseThrow(() -> new IOException(
          "Unsupported or invalid image content (declared type: " + mimeType + ")"));
      String storedFilename = imageId + extension;
      Path targetPath = storageRoot.resolve(storedFilename);
      String sha256 = computeSha256(tempPath);
      Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
      long fileSize = Files.size(targetPath);
      return new StoredImage(imageId, originalFilename, storedFilename, targetPath, fileSize, sha256);
    };
    return write.get()
        .peekFailure(error -> deleteTempFile(tempPath))
        .mapError(error -> "Failed to store image " + originalFilename + ": " + describe(error))
        .peek(stored -> logger().info("Stored image {} -> {} ({} bytes)",
                                      originalFilename, stored.storedFilename(), stored.fileSize()));
  }

  @Override
  public Result<InputStream, String> load(UUID imageId) {
    Path path = resolvePath(imageId);
    if (!Files.exists(path)) {
      return Result.failure("Image not found for id: " + imageId);
    }
    CheckedSupplier<InputStream> open = () -> Files.newInputStream(path);
    return open.get().mapError(error -> "Could not open image " + imageId + ": " + describe(error));
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
    } catch (SecurityException e) {
      // Treat an unresolvable path as outside the root: deny rather than guess.
      logger().warn("Cannot resolve {} against the storage root: {}", candidate, e.getMessage());
      return false;
    }
  }

  @Override
  public Result<Unit, String> delete(UUID imageId) {
    Path path = resolvePath(imageId);
    CheckedSupplier<Boolean> removal = () -> Files.deleteIfExists(path);
    return removal.get()
        .mapError(error -> "Could not delete image file " + path.getFileName() + ": " + describe(error))
        .map(deleted -> {
          if (deleted) {
            logger().info("Deleted image file for imageId={}", imageId);
          } else {
            logger().warn("Image file not found for deletion, imageId={}", imageId);
          }
          return Unit.INSTANCE;
        });
  }

  private void deleteTempFile(Path tempPath) {
    CheckedExecutor removal = () -> Files.deleteIfExists(tempPath);
    removal.execute().peekFailure(error -> logger().warn(
        "Could not delete temp file {}: {}", tempPath, describe(error)));
  }

  private static String describe(Throwable error) {
    return error.getClass().getSimpleName() + ": " + error.getMessage();
  }

  /**
   * Detects the image format from the file's magic bytes.
   *
   * @return the file extension for the detected format, or empty when the content is
   * not one of the supported image formats
   */
  static Optional<String> detectImageExtension(Path path)
      throws IOException {
    byte[] head = new byte[12];
    int read;
    try (InputStream in = Files.newInputStream(path)) {
      read = in.readNBytes(head, 0, head.length);
    }
    if (read >= 3 && startsWith(head, 0xFF, 0xD8, 0xFF)) return Optional.of(".jpg");
    if (read >= 8 && startsWith(head, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) return Optional.of(".png");
    if (read >= 4 && startsWith(head, 'G', 'I', 'F', '8')) return Optional.of(".gif");
    if (read >= 12 && startsWith(head, 'R', 'I', 'F', 'F')
        && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') {
      return Optional.of(".webp");
    }
    if (read >= 2 && startsWith(head, 'B', 'M')) return Optional.of(".bmp");
    if (read >= 4 && (startsWith(head, 'I', 'I', 0x2A, 0x00) || startsWith(head, 'M', 'M', 0x00, 0x2A))) {
      return Optional.of(".tiff");
    }
    return Optional.empty();
  }

  private static boolean startsWith(byte[] data, int... prefix) {
    for (int i = 0; i < prefix.length; i++) {
      if ((data[i] & 0xFF) != prefix[i]) return false;
    }
    return true;
  }

  private String computeSha256(Path path)
      throws IOException {
    byte[] bytes = Files.readAllBytes(path);
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      // Every Java platform is required to provide SHA-256.
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
