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
package com.svenruppert.flow.views.tuning;

import com.svenruppert.imagerag.domain.SearchTuningConfig;
import com.svenruppert.imagerag.domain.SearchTuningPreset;
import com.svenruppert.imagerag.service.SearchHistoryService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Save and load dialogs for search tuning presets.
 */
class TuningPresetDialogs {

  private final SearchHistoryService searchHistory;
  private final Supplier<String> currentQuery;
  private final Supplier<SearchTuningConfig> currentConfig;
  private final Consumer<SearchTuningPreset> onApply;

  /**
   * @param searchHistory service storing the presets
   * @param currentQuery  current query field content
   * @param currentConfig configuration built from the current controls
   * @param onApply       applies a loaded preset to the view
   */
  TuningPresetDialogs(SearchHistoryService searchHistory,
                      Supplier<String> currentQuery,
                      Supplier<SearchTuningConfig> currentConfig,
                      Consumer<SearchTuningPreset> onApply) {
    this.searchHistory = searchHistory;
    this.currentQuery = currentQuery;
    this.currentConfig = currentConfig;
    this.onApply = onApply;
  }

  void openSave() {
    Dialog dlg = new Dialog();
    dlg.setHeaderTitle(dlg.getTranslation("tuning.preset.save.title"));

    TextField nameField = new TextField(dlg.getTranslation("tuning.preset.name"));
    nameField.setWidthFull();
    nameField.setPlaceholder(dlg.getTranslation("tuning.preset.name.placeholder"));

    Button saveBtn = new Button(dlg.getTranslation("tuning.preset.save"), e -> {
      String name = nameField.getValue();
      if (name == null || name.isBlank()) {
        nameField.setInvalid(true);
        nameField.setErrorMessage(dlg.getTranslation("tuning.preset.name.required"));
        return;
      }
      String q = currentQuery.get() != null ? currentQuery.get() : "";
      searchHistory.saveTuningPreset(
          SearchTuningPreset.from(name.trim(), q, currentConfig.get()));
      dlg.close();
      Notification.show(dlg.getTranslation("tuning.preset.saved", name.trim()),
                        2500, Notification.Position.BOTTOM_START);
    });
    saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    Button cancelBtn = new Button(dlg.getTranslation("tuning.preset.cancel"), e -> dlg.close());
    dlg.add(nameField);
    dlg.getFooter().add(cancelBtn, saveBtn);
    dlg.open();
  }

  void openLoad() {
    Dialog dlg = new Dialog();
    List<SearchTuningPreset> presets = searchHistory.findAllTuningPresets();
    if (presets.isEmpty()) {
      Notification.show(dlg.getTranslation("tuning.preset.none"),
                        2500, Notification.Position.BOTTOM_START);
      return;
    }

    dlg.setHeaderTitle(dlg.getTranslation("tuning.preset.load.title"));
    dlg.setWidth("420px");

    Select<SearchTuningPreset> selector = new Select<>();
    selector.setItems(presets);
    selector.setItemLabelGenerator(SearchTuningPreset::getName);
    selector.setWidthFull();
    selector.setLabel(dlg.getTranslation("tuning.preset.select"));
    selector.setValue(presets.get(presets.size() - 1));

    Button loadBtn = new Button(dlg.getTranslation("tuning.preset.load"), e -> {
      SearchTuningPreset p = selector.getValue();
      if (p == null) return;
      onApply.accept(p);
      dlg.close();
      Notification.show(dlg.getTranslation("tuning.preset.loaded", p.getName()),
                        2500, Notification.Position.BOTTOM_START);
    });
    loadBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    Button deleteBtn = new Button(dlg.getTranslation("tuning.preset.delete"), e -> {
      SearchTuningPreset p = selector.getValue();
      if (p == null) return;
      searchHistory.deleteTuningPreset(p.getId());
      dlg.close();
      Notification.show(dlg.getTranslation("tuning.preset.deleted", p.getName()),
                        2500, Notification.Position.BOTTOM_START);
    });
    deleteBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);

    Button cancelBtn = new Button(dlg.getTranslation("tuning.preset.cancel"), e -> dlg.close());
    dlg.add(selector);
    dlg.getFooter().add(deleteBtn, cancelBtn, loadBtn);
    dlg.open();
  }
}
