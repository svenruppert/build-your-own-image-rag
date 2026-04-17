package com.svenruppert.imagerag.service;

import com.vaadin.flow.server.StreamResource;

import java.nio.file.Path;
import java.util.UUID;

/**
 * Generates and caches scaled image previews for multiple UI contexts.
 *
 * <p>Three sizes are supported:
 * <ul>
 *   <li><b>TABLE</b> ({@code 80×60 px}) — compact thumbnails in the table/list view.</li>
 *   <li><b>TILE</b> ({@code 400×300 px}) — tile-view cards (existing default).</li>
 *   <li><b>DETAIL</b> ({@code 800×600 px}) — full-size preview in dialogs.</li>
 * </ul>
 *
 * <p>Previews are stored on disk as JPEG files in {@code _data_images_previews} using
 * the naming scheme {@code <uuid>_<size>.jpg}.  They are generated lazily on first demand
 * and reused on subsequent calls.  Original images are never modified.
 */
public interface PreviewService {

  /**
   * Returns the path to the cached preview file for the given image and size.
   * The file may not exist yet.
   */
  Path previewPath(UUID imageId, PreviewSize size);

  // ── Size-independent helpers ───────────────────────────────────────────────

  /**
   * Returns {@code true} if a cached preview for the given size already exists.
   */
  boolean previewExists(UUID imageId, PreviewSize size);

  /**
   * Returns a Vaadin {@link StreamResource} for a preview of the requested size.
   *
   * <p>If no cached file exists, the preview is generated from {@code originalPath} and
   * written to disk before the resource is opened.  Returns {@code null} on error so
   * callers can fall back to streaming the original.
   */
  StreamResource getPreview(UUID imageId, Path originalPath, String resourceName,
                            PreviewSize size);

  /**
   * @deprecated Use {@link #previewPath(UUID, PreviewSize)} with {@link PreviewSize#TILE}.
   */
  @Deprecated
  default Path tilePreviewPath(UUID imageId) {
    return previewPath(imageId, PreviewSize.TILE);
  }

  // ── Convenience shorthands ────────────────────────────────────────────────

  /**
   * @deprecated Use {@link #previewExists(UUID, PreviewSize)} with {@link PreviewSize#TILE}.
   */
  @Deprecated
  default boolean tilePreviewExists(UUID imageId) {
    return previewExists(imageId, PreviewSize.TILE);
  }

  /**
   * Returns a {@link StreamResource} for the TILE-sized preview.
   * Kept for backward compatibility — delegates to {@link #getPreview} with {@link PreviewSize#TILE}.
   */
  default StreamResource getTilePreview(UUID imageId, Path originalPath, String resourceName) {
    return getPreview(imageId, originalPath, resourceName, PreviewSize.TILE);
  }

  /**
   * Deletes all cached preview files for the given image from disk.
   *
   * <p>Called as part of permanent image deletion to reclaim disk space.
   * Safe to call even if no cached previews exist — missing files are silently ignored.
   *
   * @param imageId the image whose preview cache should be cleared
   */
  void deletePreviewCache(UUID imageId);

  /**
   * Available preview sizes.  Each size has distinct pixel dimensions and a
   * corresponding on-disk file suffix.
   */
  enum PreviewSize {
    /**
     * Compact thumbnail for table/list rows — 80 × 60 px.
     */
    TABLE("table", 80, 60),
    /**
     * Medium thumbnail for tile-view cards — 400 × 300 px.
     */
    TILE("tile", 400, 300),
    /**
     * Large preview for detail dialogs — 800 × 600 px.
     */
    DETAIL("detail", 800, 600);

    private final String suffix;
    private final int maxWidth;
    private final int maxHeight;

    PreviewSize(String suffix, int maxWidth, int maxHeight) {
      this.suffix = suffix;
      this.maxWidth = maxWidth;
      this.maxHeight = maxHeight;
    }

    /**
     * File-name suffix used for the cached JPEG, e.g. {@code "tile"}.
     */
    public String getSuffix() {
      return suffix;
    }

    /**
     * Maximum width in pixels.
     */
    public int getMaxWidth() {
      return maxWidth;
    }

    /**
     * Maximum height in pixels.
     */
    public int getMaxHeight() {
      return maxHeight;
    }
  }
}
