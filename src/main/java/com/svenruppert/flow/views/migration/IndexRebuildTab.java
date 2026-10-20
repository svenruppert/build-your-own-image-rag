package com.svenruppert.flow.views.migration;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.views.shared.ViewServices;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;

import java.util.function.Supplier;

import static com.svenruppert.flow.views.migration.MigrationUi.sectionHeading;

/**
 * "Index rebuild" tab of the migration center: confirmed vector-index and keyword-index
 * rebuilds running on a virtual thread with an indeterminate progress indicator.
 */
class IndexRebuildTab
    extends VerticalLayout
    implements HasLogger {

  private final ViewServices services;
  private final ProgressBar rebuildProgress = new ProgressBar();
  private final Span rebuildStatus = new Span();

  private volatile boolean rebuilding = false;

  IndexRebuildTab(ViewServices services) {
    this.services = services;
    MigrationUi.styleTab(this);

    add(sectionHeading(getTranslation("migration.rebuild.title")));

    Paragraph info = new Paragraph(getTranslation("migration.rebuild.description"));
    info.getStyle().set("color", "var(--lumo-secondary-text-color)").set("font-size", "var(--lumo-font-size-s)");
    add(info);

    // Progress bar (hidden by default)
    rebuildProgress.setIndeterminate(true);
    rebuildProgress.setVisible(false);
    rebuildProgress.setWidthFull();
    rebuildStatus.getStyle()
        .set("font-size", "var(--lumo-font-size-xs)")
        .set("color", "var(--lumo-secondary-text-color)");

    add(rebuildProgress, rebuildStatus);

    // Action cards
    add(buildRebuildCard(
        getTranslation("migration.rebuild.vector.title"),
        getTranslation("migration.rebuild.vector.description"),
        getTranslation("migration.rebuild.vector.button"),
        () -> {
          long count = services.catalog().findAllIndexedImageIds().size();
          return getTranslation("migration.rebuild.vector.confirm")
              + "  " + getTranslation("migration.rebuild.affected.images", count);
        },
        () -> triggerRebuild("migration-vector-rebuild", "Vector",
                             "migration.rebuild.vector.running", "migration.rebuild.vector.done",
                             services::rebuildVectorIndex)));

    add(buildRebuildCard(
        getTranslation("migration.rebuild.keyword.title"),
        getTranslation("migration.rebuild.keyword.description"),
        getTranslation("migration.rebuild.keyword.button"),
        () -> {
          long count = services.catalog().findAllImages().size();
          return getTranslation("migration.rebuild.keyword.confirm")
              + "  " + getTranslation("migration.rebuild.affected.images", count);
        },
        () -> triggerRebuild("migration-keyword-rebuild", "Keyword",
                             "migration.rebuild.keyword.running", "migration.rebuild.keyword.done",
                             services::rebuildKeywordIndex)));
  }

  private Component buildRebuildCard(String title, String description,
                                     String buttonLabel,
                                     Supplier<String> confirmText,
                                     Runnable action) {
    Div card = new Div();
    card.getStyle()
        .set("border", "1px solid var(--lumo-contrast-10pct)")
        .set("border-radius", "var(--lumo-border-radius-m)")
        .set("padding", "0.75rem 1rem")
        .set("margin-bottom", "0.5rem")
        .set("background", "var(--lumo-base-color)");

    H4 cardTitle = new H4(title);
    cardTitle.getStyle().set("margin", "0 0 0.25rem 0");

    Paragraph desc = new Paragraph(description);
    desc.getStyle()
        .set("font-size", "var(--lumo-font-size-xs)")
        .set("color", "var(--lumo-secondary-text-color)")
        .set("margin", "0 0 0.5rem 0");

    Button btn = new Button(buttonLabel, VaadinIcon.REFRESH.create());
    btn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    btn.addClickListener(e -> {
      ConfirmDialog confirm = new ConfirmDialog();
      confirm.setHeader(getTranslation("migration.rebuild.confirm.title"));
      confirm.setText(confirmText.get()); // evaluated lazily so the count is current
      confirm.setConfirmText(getTranslation("migration.rebuild.confirm.yes"));
      confirm.setCancelText(getTranslation("migration.rebuild.confirm.no"));
      confirm.setCancelable(true);
      confirm.addConfirmListener(ev -> action.run());
      confirm.open();
    });

    card.add(cardTitle, desc, btn);
    return card;
  }

  /**
   * Runs one index rebuild on a virtual thread unless another rebuild is running.
   *
   * @param threadName  name of the worker thread
   * @param indexName   index name used in log messages
   * @param runningKey  translation key of the status text while the rebuild runs
   * @param doneKey     translation key of the status and notification text after success
   * @param rebuild     the rebuild operation
   */
  private void triggerRebuild(String threadName, String indexName,
                              String runningKey, String doneKey, Runnable rebuild) {
    if (rebuilding) {
      Notification.show(getTranslation("migration.rebuild.already.running"),
                        2000, Notification.Position.MIDDLE);
      return;
    }
    rebuilding = true;
    UI ui = UI.getCurrent();
    ui.access(() -> {
      rebuildProgress.setVisible(true);
      rebuildStatus.setText(getTranslation(runningKey));
    });

    Thread.ofVirtual().name(threadName).start(() -> {
      try {
        rebuild.run();
        ui.access(() -> {
          rebuildProgress.setVisible(false);
          rebuildStatus.setText(getTranslation(doneKey));
          Notification n = Notification.show(getTranslation(doneKey),
                                             3000, Notification.Position.BOTTOM_START);
          n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
          rebuilding = false;
        });
      } catch (RuntimeException ex) {
        // Worker-thread boundary: no Vaadin error handler runs here, so every failure is
        // logged here and shown to the user.
        logger().warn("{} rebuild failed: {}", indexName, ex.getMessage(), ex);
        ui.access(() -> {
          rebuildProgress.setVisible(false);
          rebuildStatus.setText(getTranslation("migration.rebuild.error", ex.getMessage()));
          Notification.show(getTranslation("migration.rebuild.error", ex.getMessage()),
                            5000, Notification.Position.MIDDLE);
          rebuilding = false;
        });
      }
    });
  }
}
