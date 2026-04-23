package com.svenruppert.flow.views.upload;

import com.svenruppert.dependencies.core.logger.HasLogger;
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
import com.vaadin.flow.component.upload.receivers.MultiFileBuffer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;

import java.io.IOException;
import java.nio.file.Path;

@PageTitle("Upload Images")
@Route(value = UploadView.PATH, layout = MainLayout.class)
public class UploadView
    extends VerticalLayout
    implements HasLogger {

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

    // Disk-backed buffer — avoids loading all 200 files into memory simultaneously.
    // Each file is written to a temp file on disk; we pass the path to the pipeline
    // which reads it lazily at processing time.
    MultiFileBuffer buffer = new MultiFileBuffer();
    Upload upload = new Upload(buffer);
    upload.setAcceptedFileTypes("image/jpeg", "image/png", "image/webp", "image/gif", "image/bmp");
    upload.setMaxFiles(200);
    upload.setMaxFileSize(50 * 1024 * 1024); // 50 MB per file
    upload.setWidthFull();

    upload.addSucceededListener(event -> {
      String filename = event.getFileName();
      String mimeType = event.getMIMEType();
      Path tempFile = buffer.getFileData(filename).getFile().toPath();
      submitToQueue(filename, mimeType, tempFile);
    });

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
      Notification n = Notification.show(
          getTranslation("upload.error", filename, e.getMessage()),
          5000, Notification.Position.MIDDLE);
      n.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
  }
}
