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
 * <p>Tile previews are generated as JPEG files ({@code <uuid>_tile.jpg}) inside
 * {@code _data_images_previews}.  The first call for a given image generates the
 * file; subsequent calls reuse the cached file on disk.
 *
 * <p>Thumbnail dimensions target 400 × 300 px while preserving the original aspect
 * ratio.  PNG images with an alpha channel are composited onto a white background
 * before JPEG encoding.
 */
public class PreviewServiceImpl
    implements PreviewService, HasLogger {

  /** Maximum width of a tile preview in pixels. */
  private static final int TILE_MAX_WIDTH  = 400;
  /** Maximum height of a tile preview in pixels. */
  private static final int TILE_MAX_HEIGHT = 300;

  private final Path previewRoot;

  public PreviewServiceImpl(Path previewRoot)
      throws IOException {
    this.previewRoot = previewRoot;
    Files.createDirectories(previewRoot);
    logger().info("Preview cache directory: {}", previewRoot.toAbsolutePath());
  }

  // ── PreviewService interface ───────────────────────────────────────────────

  @Override
  public Path tilePreviewPath(UUID imageId) {
    return previewRoot.resolve(imageId + "_tile.jpg");
  }

  @Override
  public boolean tilePreviewExists(UUID imageId) {
    return Files.exists(tilePreviewPath(imageId));
  }

  @Override
  public StreamResource getTilePreview(UUID imageId, Path originalPath, String resourceName) {
    Path previewPath = tilePreviewPath(imageId);

    // Generate preview if not yet cached
    if (!Files.exists(previewPath)) {
      try {
        generateTilePreview(originalPath, previewPath);
        logger().info("Generated tile preview for imageId={}", imageId);
      } catch (Exception e) {
        logger().warn("Could not generate tile preview for imageId={}: {}", imageId, e.getMessage());
        return null; // caller falls back to original
      }
    }

    // Build a StreamResource backed by the cached JPEG
    return new StreamResource(imageId + "_tile.jpg", () -> {
      try {
        return Files.newInputStream(previewPath);
      } catch (IOException ex) {
        return InputStream.nullInputStream();
      }
    });
  }

  // ── Internal generation ────────────────────────────────────────────────────

  /**
   * Reads the image at {@code source}, scales it to fit within
   * {@link #TILE_MAX_WIDTH} × {@link #TILE_MAX_HEIGHT} keeping aspect ratio,
   * and writes a JPEG to {@code dest}.
   */
  private void generateTilePreview(Path source, Path dest)
      throws IOException {
    BufferedImage original = ImageIO.read(source.toFile());
    if (original == null) {
      throw new IOException("ImageIO could not read: " + source);
    }

    // Compute scale to fit within target box
    double scale = Math.min(
        TILE_MAX_WIDTH  / (double) original.getWidth(),
        TILE_MAX_HEIGHT / (double) original.getHeight());

    // Never up-scale tiny images
    scale = Math.min(scale, 1.0);

    int newW = Math.max(1, (int) (original.getWidth()  * scale));
    int newH = Math.max(1, (int) (original.getHeight() * scale));

    // Scale with bilinear interpolation
    BufferedImage scaled = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = scaled.createGraphics();
    try {
      // White background for transparent (PNG/GIF) sources
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

    // Write JPEG (float compression quality via ImageWriteParam not needed here —
    // the default quality is good enough for tile previews)
    if (!ImageIO.write(scaled, "JPEG", dest.toFile())) {
      throw new IOException("No JPEG writer available for: " + dest);
    }
  }
}
