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
package com.svenruppert.imagerag.service;

import com.svenruppert.functional.result.Result;

import java.nio.file.Path;
import java.util.UUID;

/**
 * Generates and caches scaled image previews for multiple UI contexts.
 * <p>Three sizes are supported:
 * <ul>
 *   <li><b>TABLE</b> ({@code 80×60 px}) — compact thumbnails in the table/list view.</li>
 *   <li><b>TILE</b> ({@code 400×300 px}) — tile-view cards (existing default).</li>
 *   <li><b>DETAIL</b> ({@code 800×600 px}) — full-size preview in dialogs.</li>
 * </ul>
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
   * Returns the cached preview file of the requested size, generating it from
   * {@code originalPath} first when it does not exist yet.
   * <p>The result is framework-free; the UI layer decides how to serve the file.
   * A failure carries the reason, so callers can fall back to the original image.
   */
  Result<Path, String> ensurePreview(UUID imageId, Path originalPath, PreviewSize size);

  /**
   * Deletes all cached preview files for the given image from disk.
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
