package com.svenruppert.imagerag.service;

import com.vaadin.flow.server.StreamResource;

import java.nio.file.Path;
import java.util.UUID;

/**
 * Generates and caches scaled tile previews for the image tile view.
 *
 * <p>Previews are stored on disk in {@code _data_images_previews} as JPEG files named
 * {@code <uuid>_tile.jpg}.  They are generated lazily on first demand and reused on
 * subsequent calls, so the Vaadin UI can call {@link #getTilePreview} repeatedly
 * without reprocessing.
 *
 * <p>Original images are never modified.
 */
public interface PreviewService {

  /**
   * Returns the path to the tile-preview file for the given image.
   * The file may not yet exist; call {@link #tilePreviewExists} first if needed.
   */
  Path tilePreviewPath(UUID imageId);

  /**
   * Returns {@code true} if a cached tile preview already exists on disk.
   */
  boolean tilePreviewExists(UUID imageId);

  /**
   * Returns a Vaadin {@link StreamResource} for the tile preview.
   *
   * <p>If the preview does not yet exist it is generated from the original image at
   * {@code originalPath} and written to disk before the stream is opened.
   * If generation fails the method returns {@code null}; callers should fall back
   * to streaming the original or showing a placeholder.
   *
   * @param imageId       image identifier
   * @param originalPath  path to the full-resolution original image
   * @param resourceName  name used for the {@link StreamResource} (typically
   *                      the stored filename)
   * @return a {@link StreamResource} for the preview, or {@code null} on error
   */
  StreamResource getTilePreview(UUID imageId, Path originalPath, String resourceName);
}
