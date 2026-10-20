package com.svenruppert.flow.views.settings;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.MainLayout;
import com.svenruppert.flow.views.shared.ViewServices;
import com.svenruppert.functional.result.Result;
import com.svenruppert.functional.result.Unit;
import com.svenruppert.imagerag.settings.Setting;
import com.svenruppert.imagerag.settings.SettingCategory;
import com.svenruppert.imagerag.settings.SettingEffect;
import com.svenruppert.imagerag.settings.SettingSource;
import com.svenruppert.imagerag.settings.Settings;
import com.svenruppert.imagerag.settings.SettingsService;
import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasValidation;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Runtime settings: every tunable parameter can be changed while the application runs
 * and reset to the value of the configuration layers (system property, properties file
 * or built-in default). Overrides are persisted and survive a restart.
 */
@Route(value = SettingsView.PATH, layout = MainLayout.class)
public class SettingsView
    extends VerticalLayout
    implements HasDynamicTitle, HasLogger {

  public static final String PATH = "settings";

  private final SettingsService settings;
  private final List<SettingRow<?>> rows = new ArrayList<>();

  public SettingsView() {
    this.settings = ViewServices.current().settings();

    setSpacing(false);
    setPadding(true);
    setWidthFull();
    setMaxWidth("1100px");

    add(buildHeader());
    for (SettingCategory category : SettingCategory.values()) {
      List<Setting<?>> inCategory = Settings.ALL.stream()
          .filter(s -> s.category() == category)
          .toList();
      if (inCategory.isEmpty()) {
        continue;
      }
      add(sectionHeading(getTranslation(category.labelKey())));
      for (Setting<?> setting : inCategory) {
        SettingRow<?> row = createRow(setting);
        rows.add(row);
        add(row.layout());
      }
    }
  }

  private static H3 sectionHeading(String text) {
    H3 h = new H3(text);
    h.getStyle()
        .set("font-size", "var(--lumo-font-size-s)")
        .set("text-transform", "uppercase")
        .set("letter-spacing", "0.05em")
        .set("color", "var(--lumo-secondary-text-color)")
        .set("margin", "1.25rem 0 0.25rem 0");
    return h;
  }

  @SuppressWarnings("unchecked")
  private <T extends Comparable<T>> SettingRow<T> createRow(Setting<T> setting) {
    if (setting.type() == Integer.class) {
      return (SettingRow<T>) new SettingRow<>((Setting<Integer>) setting, integerField((Setting<Integer>) setting));
    }
    if (setting.type() == Double.class) {
      return (SettingRow<T>) new SettingRow<>((Setting<Double>) setting, numberField((Setting<Double>) setting));
    }
    return new SettingRow<>(setting, null);
  }

  private static IntegerField integerField(Setting<Integer> setting) {
    IntegerField field = new IntegerField();
    field.setMin(setting.min());
    field.setMax(setting.max());
    field.setStep(1);
    field.setStepButtonsVisible(true);
    field.setWidth("11rem");
    return field;
  }

  private static NumberField numberField(Setting<Double> setting) {
    NumberField field = new NumberField();
    field.setMin(setting.min());
    field.setMax(setting.max());
    field.setStep(setting.step());
    field.setStepButtonsVisible(true);
    field.setWidth("11rem");
    return field;
  }

  private Component buildHeader() {
    H2 title = new H2(getTranslation("settings.title"));
    Paragraph desc = new Paragraph(getTranslation("settings.description"));
    desc.getStyle().set("color", "var(--lumo-secondary-text-color)");

    Button resetAll = new Button(getTranslation("settings.reset.all"), e -> confirmResetAll());
    resetAll.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);

    VerticalLayout text = new VerticalLayout(title, desc);
    text.setSpacing(false);
    text.setPadding(false);

    HorizontalLayout header = new HorizontalLayout(text, resetAll);
    header.setWidthFull();
    header.setAlignItems(FlexComponent.Alignment.END);
    header.expand(text);
    return header;
  }

  private void confirmResetAll() {
    ConfirmDialog dlg = new ConfirmDialog();
    dlg.setHeader(getTranslation("settings.reset.all.confirm.header"));
    dlg.setText(getTranslation("settings.reset.all.confirm.text"));
    dlg.setCancelable(true);
    dlg.setCancelText(getTranslation("common.cancel"));
    dlg.setConfirmText(getTranslation("settings.reset.all.confirm.button"));
    dlg.setConfirmButtonTheme("error primary");
    dlg.addConfirmListener(e -> {
      settings.resetAll();
      rows.forEach(SettingRow::refresh);
      notifySuccess(getTranslation("settings.reset.all.done"));
    });
    dlg.open();
  }

  private void notifySuccess(String text) {
    Notification n = Notification.show(text, 3000, Notification.Position.BOTTOM_END);
    n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
  }

  private void notifyError(String text) {
    Notification n = Notification.show(text, 5000, Notification.Position.MIDDLE);
    n.addThemeVariants(NotificationVariant.LUMO_ERROR);
  }

  @Override
  public String getPageTitle() {
    return getTranslation("settings.page.title");
  }

  /**
   * One setting: label, description, editor, source badge, default value and actions.
   * Read-only settings (no editor) only show their effective value.
   */
  private final class SettingRow<T extends Comparable<T>> {

    private final Setting<T> setting;
    private final AbstractField<?, T> field;
    private final Span valueText = new Span();
    private final Span sourceBadge = new Span();
    private final Span defaultText = new Span();
    private final Button resetButton;
    private final HorizontalLayout layout = new HorizontalLayout();

    SettingRow(Setting<T> setting, AbstractField<?, T> field) {
      this.setting = setting;
      this.field = field;

      Span label = new Span(getTranslation(setting.labelKey()));
      label.getStyle().set("font-weight", "600");
      Span key = new Span(setting.key());
      key.getStyle()
          .set("font-family", "monospace")
          .set("font-size", "var(--lumo-font-size-xs)")
          .set("color", "var(--lumo-tertiary-text-color)");
      Span description = new Span(getTranslation(setting.descriptionKey()));
      description.getStyle()
          .set("font-size", "var(--lumo-font-size-s)")
          .set("color", "var(--lumo-secondary-text-color)");

      VerticalLayout text = new VerticalLayout(new HorizontalLayout(label, key), description);
      text.setSpacing(false);
      text.setPadding(false);
      effectHint().ifPresent(text::add);

      defaultText.getStyle()
          .set("font-size", "var(--lumo-font-size-xs)")
          .set("color", "var(--lumo-secondary-text-color)");
      VerticalLayout status = new VerticalLayout(sourceBadge, defaultText);
      status.setSpacing(false);
      status.setPadding(false);
      status.setWidth("11rem");

      layout.setWidthFull();
      layout.setAlignItems(FlexComponent.Alignment.CENTER);
      layout.getStyle()
          .set("padding", "0.5rem 0")
          .set("border-bottom", "1px solid var(--lumo-contrast-10pct)");

      if (field != null) {
        Button save = new Button(getTranslation("settings.save"), e -> save());
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
        resetButton = new Button(getTranslation("settings.reset"), e -> reset());
        resetButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        layout.add(text, field, status, save, resetButton);
      } else {
        resetButton = null;
        valueText.getStyle().set("font-family", "monospace").set("width", "11rem");
        layout.add(text, valueText, status);
      }
      layout.expand(text);
      refresh();
    }

    HorizontalLayout layout() {
      return layout;
    }

    private Optional<Div> effectHint() {
      if (setting.effect() == SettingEffect.IMMEDIATE) {
        return Optional.empty();
      }
      Div hint = new Div(getTranslation("settings.effect." + setting.effect().name().toLowerCase()));
      hint.getStyle()
          .set("font-size", "var(--lumo-font-size-xs)")
          .set("color", "var(--lumo-warning-text-color, var(--lumo-secondary-text-color))");
      return Optional.of(hint);
    }

    void refresh() {
      T value = settings.get(setting);
      if (field != null) {
        field.setValue(value);
        resetButton.setEnabled(settings.hasOverride(setting));
      } else {
        valueText.setText(setting.format(value));
      }
      SettingSource source = settings.source(setting);
      sourceBadge.setText(getTranslation("settings.source." + source.name().toLowerCase()));
      sourceBadge.getElement().getThemeList().clear();
      sourceBadge.getElement().getThemeList().add("badge");
      sourceBadge.getElement().getThemeList().add(source == SettingSource.OVERRIDE ? "primary" : "contrast");
      if (setting.editable()) {
        defaultText.setText(getTranslation("settings.default",
                                           setting.format(settings.valueWithoutOverride(setting))));
      } else {
        defaultText.setText(getTranslation("settings.readonly"));
      }
    }

    private void save() {
      T value = field.getValue();
      if (value == null || (field instanceof HasValidation validated && validated.isInvalid())) {
        notifyError(getTranslation("settings.validation.range",
                                   getTranslation(setting.labelKey()),
                                   setting.format(setting.min()), setting.format(setting.max())));
        return;
      }
      Result<Unit, String> result = settings.set(setting, value);
      switch (result) {
        case Result.Success<Unit, String> ok ->
            notifySuccess(getTranslation("settings.saved", getTranslation(setting.labelKey()),
                                         setting.format(value)));
        case Result.Failure<Unit, String>(String reason) -> {
          logger().warn("Rejected setting change: {}", reason);
          notifyError(getTranslation("settings.validation.range",
                                     getTranslation(setting.labelKey()),
                                     setting.format(setting.min()), setting.format(setting.max())));
        }
      }
      refresh();
    }

    private void reset() {
      settings.reset(setting);
      refresh();
      notifySuccess(getTranslation("settings.reset.done", getTranslation(setting.labelKey())));
    }
  }
}
