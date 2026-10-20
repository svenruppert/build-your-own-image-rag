package com.svenruppert.flow.views.pipeline;

import com.svenruppert.imagerag.settings.Setting;
import com.svenruppert.imagerag.settings.SettingsService;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.select.Select;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Bounded selector for an integer parallelism setting with an explanatory note.
 * A user change is written through the {@link SettingsService}, which persists the value
 * and resizes the affected executor.
 */
class ParallelismSelector
    extends HorizontalLayout {

  /**
   * @param settings   settings service that stores the value
   * @param setting    the integer setting; its min/max bound the selectable values
   * @param labelKey   translation key of the select label
   * @param updatedKey translation key of the success notification ({0} = new value)
   * @param noteKey    translation key of the note next to the select
   */
  ParallelismSelector(SettingsService settings, Setting<Integer> setting,
                      String labelKey, String updatedKey, String noteKey) {
    Select<Integer> select = new Select<>();
    select.setLabel(getTranslation(labelKey));
    select.setItems(range(setting));
    select.setValue(settings.get(setting));
    select.setWidth("160px");

    select.addValueChangeListener(e -> {
      if (e.getValue() == null || !e.isFromClient()) return;
      // The settings service persists the value and resizes the executor
      settings.set(setting, e.getValue());
      Notification n = Notification.show(
          getTranslation(updatedKey, e.getValue()),
          3000, Notification.Position.BOTTOM_END);
      n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    });

    Span note = new Span(getTranslation(noteKey));
    note.getStyle()
        .set("font-size", "var(--lumo-font-size-s)")
        .set("color", "var(--lumo-secondary-text-color)")
        .set("align-self", "flex-end")
        .set("padding-bottom", "var(--lumo-space-xs)");

    add(select, note);
    setAlignItems(Alignment.END);
  }

  /**
   * All integer values allowed by the bounds of a setting, for a selection list.
   */
  private static List<Integer> range(Setting<Integer> setting) {
    return IntStream.rangeClosed(setting.min(), setting.max()).boxed().toList();
  }
}
