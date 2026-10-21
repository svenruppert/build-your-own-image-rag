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
import com.svenruppert.imagerag.service.PreviewService;
import com.svenruppert.functional.result.Result;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.UUID;

/**
 * Disk-caching implementation of {@link PreviewService}.
 * <p>Previews are generated as JPEG files in {@code _data_images_previews} following the
 * naming pattern {@code <uuid>_<size>.jpg} where {@code <size>} is the
 * {@link PreviewService.PreviewSize} suffix ({@code table}, {@code tile}, {@code detail}).
 * <p>The first call for a given image+size combination generates the file; subsequent
 * calls reuse the cached file.  PNG images with an alpha channel are composited onto a
 * white background before JPEG encoding.  Original images are never modified.
 */
public class PreviewServiceImpl
    implements PreviewService, HasLogger {

  /**
   * Upper bound for the decoded size of a source image (100 megapixels).
   */
  private static final long MAX_SOURCE_PIXELS = 100_000_000L;

  private final Path previewRoot;

  public PreviewServiceImpl(Path previewRoot)
      throws IOException {
    this.previewRoot = previewRoot;
    Files.createDirectories(previewRoot);
    logger().info("Preview cache directory: {}", previewRoot.toAbsolutePath());
  }

  // ── PreviewService interface ───────────────────────────────────────────────

  @Override
  public Path previewPath(UUID imageId, PreviewSize size) {
    return previewRoot.resolve(imageId + "_" + size.getSuffix() + ".jpg");
  }

  @Override
  public boolean previewExists(UUID imageId, PreviewSize size) {
    return Files.exists(previewPath(imageId, size));
  }

  @Override
  public Result<Path, String> ensurePreview(UUID imageId, Path originalPath, PreviewSize size) {
    Path previewPath = previewPath(imageId, size);
    if (Files.exists(previewPath)) {
      return Result.success(previewPath);
    }
    try {
      generatePreview(originalPath, previewPath, size.getMaxWidth(), size.getMaxHeight());
      logger().debug("Generated {} preview for imageId={}", size, imageId);
      return Result.success(previewPath);
    } catch (IOException | RuntimeException e) {
      logger().warn("Could not generate {} preview for imageId={}: {}", size, imageId, e.getMessage());
      return Result.failure("preview generation failed: " + e.getMessage());
    }
  }

  @Override
  public void deletePreviewCache(UUID imageId) {
    int deleted = 0;
    for (PreviewSize size : PreviewSize.values()) {
      Path p = previewPath(imageId, size);
      try {
        if (Files.deleteIfExists(p)) {
          deleted++;
        }
      } catch (IOException e) {
        logger().warn("Could not delete preview cache file {}: {}", p, e.getMessage());
      }
    }
    if (deleted > 0) {
      logger().debug("Deleted {} preview cache file(s) for imageId={}", deleted, imageId);
    }
  }

  // ── Internal generation ────────────────────────────────────────────────────

  /**
   * Reads the image at {@code source}, scales it to fit within
   * {@code maxWidth × maxHeight} (preserving aspect ratio, never up-scaling),
   * and writes a JPEG to {@code dest}.
   */
  private void generatePreview(Path source, Path dest, int maxWidth, int maxHeight)
      throws IOException {
    BufferedImage original = readWithPixelLimit(source);

    // Compute scale to fit within target box; never up-scale tiny images
    double scale = Math.min(
        maxWidth / (double) original.getWidth(),
        maxHeight / (double) original.getHeight());
    scale = Math.min(scale, 1.0);

    int newW = Math.max(1, (int) (original.getWidth() * scale));
    int newH = Math.max(1, (int) (original.getHeight() * scale));

    BufferedImage scaled = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = scaled.createGraphics();
    try {
      g.setBackground(Color.WHITE);
      g.clearRect(0, 0, newW, newH);
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                         RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g.setRenderingHint(RenderingHints.KEY_RENDERING,
                         RenderingHints.VALUE_RENDER_QUALITY);
      g.drawImage(original, 0, 0, newW, newH, null);
    } finally {
      g.dispose();
    }

    // Write to a sibling temp file and move it into place atomically, so concurrent
    // readers never see a half-written preview.
    Path temp = Files.createTempFile(dest.getParent(), dest.getFileName().toString(), ".part");
    try {
      if (!ImageIO.write(scaled, "JPEG", temp.toFile())) {
        throw new IOException("No JPEG writer available for: " + dest);
      }
      Files.move(temp, dest, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  /**
   * Reads the image after checking its declared dimensions, so a small file that
   * decodes to a huge raster (decompression bomb) is rejected before any pixels
   * are allocated.
   */
  private BufferedImage readWithPixelLimit(Path source)
      throws IOException {
    try (ImageInputStream input = ImageIO.createImageInputStream(source.toFile())) {
      if (input == null) {
        throw new IOException("ImageIO could not open: " + source);
      }
      Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) {
        throw new IOException("No ImageIO reader for: " + source);
      }
      ImageReader reader = readers.next();
      try {
        reader.setInput(input, true, true);
        long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
        if (pixels > MAX_SOURCE_PIXELS) {
          throw new IOException("Image " + source.getFileName() + " has " + pixels
                                    + " pixels, exceeding the limit of " + MAX_SOURCE_PIXELS);
        }
        return reader.read(0);
      } finally {
        reader.dispose();
      }
    }
  }
}
