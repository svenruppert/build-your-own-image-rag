package com.svenruppert.imagerag.service.impl;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.imagerag.service.PreviewService;
import com.vaadin.flow.server.StreamResource;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Disk-caching implementation of {@link PreviewService}.
 *
 * <p>Previews are generated as JPEG files in {@code _data_images_previews} following the
 * naming pattern {@code <uuid>_<size>.jpg} where {@code <size>} is the
 * {@link PreviewService.PreviewSize} suffix ({@code table}, {@code tile}, {@code detail}).
 *
 * <p>The first call for a given image+size combination generates the file; subsequent
 * calls reuse the cached file.  PNG images with an alpha channel are composited onto a
 * white background before JPEG encoding.  Original images are never modified.
 */
public class PreviewServiceImpl
    implements PreviewService, HasLogger {

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
  public StreamResource getPreview(UUID imageId, Path originalPath,
                                   String resourceName, PreviewSize size) {
    Path previewPath = previewPath(imageId, size);

    // Generate preview if not yet cached
    if (!Files.exists(previewPath)) {
      try {
        generatePreview(originalPath, previewPath, size.getMaxWidth(), size.getMaxHeight());
        logger().debug("Generated {} preview for imageId={}", size, imageId);
      } catch (Exception e) {
        logger().warn("Could not generate {} preview for imageId={}: {}", size, imageId, e.getMessage());
        return null; // caller falls back to original
      }
    }

    // Build a StreamResource backed by the cached JPEG
    final String resourceId = imageId + "_" + size.getSuffix() + ".jpg";
    return new StreamResource(resourceId, () -> {
      try {
        return Files.newInputStream(previewPath);
      } catch (IOException ex) {
        return InputStream.nullInputStream();
      }
    });
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
    BufferedImage original = ImageIO.read(source.toFile());
    if (original == null) {
      throw new IOException("ImageIO could not read: " + source);
    }

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

    if (!ImageIO.write(scaled, "JPEG", dest.toFile())) {
      throw new IOException("No JPEG writer available for: " + dest);
    }
  }
}
