package com.svenruppert.flow.views.upload;

import com.svenruppert.imagerag.settings.Settings;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.dependencies.core.net.MediaType;
import com.svenruppert.flow.MainLayout;
import com.svenruppert.flow.views.pipeline.PipelineView;
import com.svenruppert.flow.views.shared.ViewServices;
import com.svenruppert.imagerag.pipeline.IngestionJob;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.streams.UploadHandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Route(value = UploadView.PATH, layout = MainLayout.class)
public class UploadView
    extends VerticalLayout
    implements HasLogger, HasDynamicTitle {

  public static final String PATH = "upload";
  private final ViewServices services;

  public UploadView() {
    this.services = ViewServices.current();

    setWidthFull();
    setPadding(true);
    setSpacing(true);

    add(new H2(getTranslation("upload.title")));
    add(new Paragraph(getTranslation("upload.description")));

    RouterLink pipelineLink = new RouterLink(getTranslation("upload.pipeline.link"), PipelineView.class);
    add(pipelineLink);

    // Each upload is streamed to its own temp file, so two files with the same
    // name cannot overwrite each other. The pipeline reads the file lazily at
    // processing time and deletes it afterwards. The callback runs on the UI thread.
    Upload upload = new Upload(UploadHandler.toTempFile(
        (metadata, file) -> submitToQueue(metadata.fileName(), metadata.contentType(), file.toPath())));
    // MediaType has no IMAGE_BMP constant yet, so that one stays a literal.
    upload.setAcceptedFileTypes(MediaType.IMAGE_JPEG.mime(), MediaType.IMAGE_PNG.mime(),
        MediaType.IMAGE_WEBP.mime(), MediaType.IMAGE_GIF.mime(), "image/bmp");
    // Limits are read when the view is created (Settings view, category PIPELINE)
    upload.setMaxFiles(services.settings().get(Settings.UPLOAD_MAX_FILES));
    upload.setMaxFileSize(services.settings().get(Settings.UPLOAD_MAX_FILE_SIZE_MB) * 1024 * 1024);
    upload.setWidthFull();

    upload.addAllFinishedListener(event ->
                                      Notification.show(getTranslation("upload.all.finished"),
                                                        4000, Notification.Position.BOTTOM_END));

    upload.addFileRejectedListener(event -> {
      Notification n = Notification.show(
          getTranslation("upload.rejected", event.getErrorMessage()), 5000, Notification.Position.MIDDLE);
      n.addThemeVariants(NotificationVariant.LUMO_ERROR);
    });

    add(upload);

    Button goToPipeline = new Button(getTranslation("upload.queue.button"),
                                     e -> UI.getCurrent().navigate(PipelineView.class));
    goToPipeline.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    add(goToPipeline);
  }

  /**
   * Hands the temp-file path to {@link com.svenruppert.imagerag.pipeline.IngestionPipeline}.
   * This runs on the Vaadin UI thread and is fast — no bytes are read here.
   * The pipeline reads and deletes the temp file when the job is actually processed,
   * so at most {@code parallelism} files occupy RAM at any given time regardless of
   * how many files are queued.
   */
  private void submitToQueue(String filename, String mimeType, Path tempFile) {
    try {
      IngestionJob job = services
          .ingestionPipeline()
          .submitFromPath(tempFile, filename, mimeType);

      Notification n = Notification.show(
          getTranslation("upload.queued", filename, job.getJobId().toString().substring(0, 8)),
          3000, Notification.Position.BOTTOM_END);
      n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);

      logger().info("Queued: {} → jobId={}", filename, job.getJobId());

    } catch (IOException e) {
      logger().error("Failed to queue {}: {}", filename, e.getMessage(), e);
      deleteQuietly(tempFile);
      Notification n = Notification.show(
          getTranslation("upload.error", filename, e.getMessage()),
          5000, Notification.Position.MIDDLE);
      n.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
  }

  private void deleteQuietly(Path tempFile) {
    try {
      Files.deleteIfExists(tempFile);
    } catch (IOException e) {
      logger().warn("Could not delete temp upload file {}: {}", tempFile, e.getMessage());
    }
  }

  @Override
  public String getPageTitle() {
    return getTranslation("upload.page.title");
  }
}
