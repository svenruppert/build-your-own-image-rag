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
package com.svenruppert.flow.views.migration;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.views.shared.ViewServices;
import com.svenruppert.imagerag.domain.ImageAsset;
import com.svenruppert.imagerag.service.PromptTestService;
import com.svenruppert.imagerag.service.impl.VisionAnalysisServiceImpl;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Dialog that tests a draft prompt against a sample image without activating it.
 * Results are shown inline so the user can compare the draft output with the existing
 * analysis before deciding to activate.
 */
class PromptTestDialog
    extends Dialog
    implements HasLogger {

  /**
   * Upper bound on prompt-draft test execution so a hung model can't freeze the dialog.
   */
  private static final long PROMPT_TEST_TIMEOUT_SECONDS = 120L;

  private final ViewServices services;
  private final String promptKey;
  private final String draftContent;

  private final TextField imageIdField = new TextField();
  private final TextArea resultArea = new TextArea();
  private final ProgressBar testProgress = new ProgressBar();

  PromptTestDialog(ViewServices services, String promptKey, String draftContent) {
    this.services = services;
    this.promptKey = promptKey;
    this.draftContent = draftContent;

    setHeaderTitle(getTranslation("migration.test.title",
                                  promptKey.equals(VisionAnalysisServiceImpl.PROMPT_KEY)
                                      ? getTranslation("migration.prompt.vision")
                                      : getTranslation("migration.prompt.semantic")));
    setWidth("640px");
    setMaxHeight("82vh");

    imageIdField.setLabel(getTranslation("migration.test.image.label"));
    imageIdField.setWidthFull();
    imageIdField.setPlaceholder(getTranslation("migration.test.image.placeholder"));

    resultArea.setLabel(getTranslation("migration.test.result.label"));
    resultArea.setWidthFull();
    resultArea.setMinHeight("200px");
    resultArea.setReadOnly(true);

    testProgress.setIndeterminate(true);
    testProgress.setVisible(false);
    testProgress.setWidthFull();

    Button runBtn = new Button(getTranslation("migration.test.run"), e -> runTest());
    runBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    VerticalLayout content = new VerticalLayout(imageIdField, testProgress, runBtn, resultArea);
    content.setPadding(false);
    content.setSpacing(true);
    add(content);

    Button closeBtn = new Button(getTranslation("common.close"), ev -> close());
    getFooter().add(closeBtn);
  }

  private void runTest() {
    String idStr = imageIdField.getValue();
    if (idStr == null || idStr.isBlank()) {
      Notification.show(getTranslation("migration.test.image.required"),
                        2000, Notification.Position.MIDDLE);
      return;
    }
    UUID imageId = resolveImageIdForTest(idStr);
    if (imageId == null) {
      Notification.show(getTranslation("migration.test.image.not.found"),
                        2000, Notification.Position.MIDDLE);
      return;
    }
    testProgress.setVisible(true);
    resultArea.setValue(getTranslation("migration.test.running"));
    UI ui = UI.getCurrent();
    final Locale locale = getLocale();
    Thread.ofVirtual().name("prompt-test").start(() -> {
      ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor();
      try {
        Future<String> fut =
            exec.submit(() -> executePromptTest(imageId, locale));
        String result;
        try {
          result = fut.get(PROMPT_TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException te) {
          fut.cancel(true);
          ui.access(() -> {
            testProgress.setVisible(false);
            resultArea.setValue(getTranslation("migration.test.timeout",
                                               String.valueOf(PROMPT_TEST_TIMEOUT_SECONDS)));
          });
          return;
        }
        final String r = result;
        ui.access(() -> {
          testProgress.setVisible(false);
          resultArea.setValue(r != null ? r : getTranslation("migration.test.no.result"));
        });
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        logger().warn("Prompt test interrupted");
        ui.access(() -> {
          testProgress.setVisible(false);
          resultArea.setValue(getTranslation("migration.test.error", ex.getMessage()));
        });
      } catch (ExecutionException | RuntimeException ex) {
        // Worker-thread boundary: no Vaadin error handler runs here, so every failure is
        // logged here and shown to the user.
        Throwable cause = ex instanceof ExecutionException && ex.getCause() != null
            ? ex.getCause() : ex;
        logger().warn("Prompt test failed: {}", cause.getMessage(), cause);
        ui.access(() -> {
          testProgress.setVisible(false);
          resultArea.setValue(getTranslation("migration.test.error", cause.getMessage()));
        });
      } finally {
        exec.shutdownNow();
      }
    });
  }

  /**
   * Resolves an image UUID from a raw UUID string or a filename fragment.
   */
  private UUID resolveImageIdForTest(String idStr) {
    if (idStr == null || idStr.isBlank()) return null;
    try {
      return UUID.fromString(idStr.trim());
    } catch (IllegalArgumentException ignored) {
    }
    String lower = idStr.toLowerCase();
    return services.catalog().findAllImages().stream()
        .filter(a -> a.getOriginalFilename() != null
            && a.getOriginalFilename().toLowerCase().contains(lower))
        .map(ImageAsset::getId)
        .findFirst().orElse(null);
  }

  /**
   * Executes the draft prompt against the chosen image via the {@link PromptTestService}
   * and returns the raw model output, or a translated message when there is none.
   * <p>Runs on a worker thread; the fallback texts are resolved with the locale captured
   * by the caller.
   */
  private String executePromptTest(UUID imageId, Locale locale) {
    return services.promptTests()
        .run(promptKey, imageId, draftContent,
             getTranslation(locale, "migration.test.no.description"))
        .getOrElse(failure -> switch (failure) {
          case IMAGE_NOT_FOUND -> getTranslation(locale, "migration.test.error.image.not.found");
          case FILE_NOT_FOUND -> getTranslation(locale, "migration.test.error.file.not.found");
          case NO_RESULT -> getTranslation(locale, "migration.test.no.result");
        });
  }
}
