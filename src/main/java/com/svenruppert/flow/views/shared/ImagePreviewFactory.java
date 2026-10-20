package com.svenruppert.flow.views.shared;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.imagerag.domain.ImageAsset;
import com.svenruppert.imagerag.service.ImageStorageService;
import com.svenruppert.imagerag.service.PreviewService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.server.streams.DownloadHandler;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Builds Vaadin image-preview components with a consistent cached-preview fallback.
 */
public class ImagePreviewFactory
    implements HasLogger {

  private final ImageStorageService imageStorageService;
  private final PreviewService previewService;

  public ImagePreviewFactory(ImageStorageService imageStorageService,
                             PreviewService previewService) {
    this.imageStorageService = imageStorageService;
    this.previewService = previewService;
  }

  public Component tile(ImageAsset asset, String height) {
    return image(asset, PreviewService.PreviewSize.TILE, "100%", height, "cover", null);
  }

  public Component table(ImageAsset asset, String height) {
    return image(asset, PreviewService.PreviewSize.TABLE, null, height, "contain", null);
  }

  public Component image(ImageAsset asset,
                         PreviewService.PreviewSize size,
                         String width,
                         String height,
                         String objectFit,
                         String altText) {
    if (asset == null) {
      return placeholder();
    }

    try {
      Path originalPath = imageStorageService.resolvePath(asset.getId());
      if (!Files.exists(originalPath)) {
        return placeholder();
      }

      // Serve the cached preview; fall back to the original when it cannot be generated.
      Path file = previewService.ensurePreview(asset.getId(), originalPath, size)
          .getOrElse(originalPath);
      DownloadHandler handler = DownloadHandler.forFile(
          file.toFile(), file.getFileName().toString());

      Image image = new Image(handler, altText != null ? altText : asset.getOriginalFilename());
      if (width != null) {
        image.setWidth(width);
      }
      if (height != null) {
        image.setHeight(height);
      }
      image.getStyle()
          .set("object-fit", objectFit != null ? objectFit : "cover")
          .set("border-radius", "4px")
          .set("display", "block");
      return image;
    } catch (RuntimeException e) {
      // Renderer boundary: one broken image must not break the whole grid or gallery.
      logger().warn("Could not build image preview for {}", asset.getId(), e);
      return placeholder();
    }
  }

  public Span placeholder() {
    Span placeholder = new Span("-");
    placeholder.getStyle()
        .set("color", "var(--lumo-contrast-30pct)")
        .set("font-size", "1.1rem");
    return placeholder;
  }
}
