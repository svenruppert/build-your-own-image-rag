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
package com.svenruppert.imagerag.image;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;

/**
 * Shared image reading and down-scaling for preview generation and vision analysis.
 */
public final class ImageScaling {

  /**
   * Upper bound for decoded source images (100 megapixels) — protects against
   * decompression bombs before any pixels are allocated.
   */
  public static final long MAX_SOURCE_PIXELS = 100_000_000L;

  private ImageScaling() {
  }

  /**
   * Reads the image after checking its declared dimensions, so a small file that
   * decodes to a huge raster is rejected before any pixels are allocated.
   */
  public static BufferedImage readWithPixelLimit(Path source)
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

  /**
   * Scales {@code original} to fit within {@code maxWidth × maxHeight}, preserving the
   * aspect ratio and never up-scaling. Transparent areas become white.
   */
  public static BufferedImage scaleToFit(BufferedImage original, int maxWidth, int maxHeight) {
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
    return scaled;
  }

  /**
   * Encodes the image as JPEG.
   */
  public static byte[] toJpeg(BufferedImage image)
      throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    if (!ImageIO.write(image, "JPEG", out)) {
      throw new IOException("No JPEG writer available");
    }
    return out.toByteArray();
  }
}
