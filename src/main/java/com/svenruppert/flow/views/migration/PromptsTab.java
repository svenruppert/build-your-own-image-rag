package com.svenruppert.flow.views.migration;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.views.shared.ViewServices;
import com.svenruppert.imagerag.domain.PromptTemplateVersion;
import com.svenruppert.imagerag.service.PromptTemplateService;
import com.svenruppert.imagerag.service.impl.SemanticDerivationServiceImpl;
import com.svenruppert.imagerag.service.impl.VisionAnalysisServiceImpl;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;

import java.util.List;

import static com.svenruppert.flow.views.migration.MigrationUi.sectionHeading;
import static com.svenruppert.flow.views.migration.MigrationUi.styledPanel;

/**
 * "Prompts" tab of the migration center: version history per prompt key with
 * activate/rollback/delete on the left, the draft editor on the right.
 */
class PromptsTab
    extends VerticalLayout
    implements HasLogger {

  private final ViewServices services;
  private final PromptTemplateService promptService;

  private final Select<PromptTemplateVersion> visionSelect = new Select<>();
  private final Select<PromptTemplateVersion> semanticSelect = new Select<>();
  private final TextArea promptEditor = new TextArea();
  private final TextField versionLabel = new TextField();
  private final TextField descField = new TextField();
  private final Span editorStatus = new Span();

  /**
   * Currently selected version in the prompt editor.
   */
  private PromptTemplateVersion selectedVersion = null;

  PromptsTab(ViewServices services) {
    this.services = services;
    this.promptService = services.promptTemplates();
    MigrationUi.styleTab(this);

    // ── History panel (left) + Editor panel (right) ────────────────────────
    HorizontalLayout split = new HorizontalLayout(buildPromptHistoryPanel(), buildPromptEditorPanel());
    split.setWidthFull();
    split.setAlignItems(FlexComponent.Alignment.START);
    split.setSpacing(true);
    split.setPadding(false);
    add(split);
  }

  private VerticalLayout buildPromptHistoryPanel() {
    VerticalLayout panel = styledPanel("340px");

    // Vision prompt section
    panel.add(sectionHeading(getTranslation("migration.prompt.vision")));
    visionSelect.setWidthFull();
    visionSelect.setItemLabelGenerator(PromptTemplateVersion::displayLabel);
    visionSelect.addValueChangeListener(e -> loadVersionIntoEditor(e.getValue()));
    panel.add(visionSelect, buildVersionButtons());

    // Semantic prompt section
    panel.add(sectionHeading(getTranslation("migration.prompt.semantic")));
    semanticSelect.setWidthFull();
    semanticSelect.setItemLabelGenerator(PromptTemplateVersion::displayLabel);
    semanticSelect.addValueChangeListener(e -> loadVersionIntoEditor(e.getValue()));
    panel.add(semanticSelect, buildVersionButtons());

    return panel;
  }

  /**
   * Activate / rollback / delete buttons; they act on the version selected last.
   */
  private HorizontalLayout buildVersionButtons() {
    HorizontalLayout buttons = new HorizontalLayout();
    buttons.setSpacing(false);
    buttons.getStyle().set("gap", "0.3rem");

    Button activate = new Button(getTranslation("migration.prompt.activate"),
                                 e -> activateSelected());
    activate.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
    Button rollback = new Button(getTranslation("migration.prompt.rollback"),
                                 e -> rollbackSelected());
    rollback.addThemeVariants(ButtonVariant.LUMO_SMALL);
    Button delete = new Button(getTranslation("migration.prompt.delete"),
                               e -> deleteSelected());
    delete.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY,
                            ButtonVariant.LUMO_ERROR);
    buttons.add(activate, rollback, delete);
    return buttons;
  }

  private VerticalLayout buildPromptEditorPanel() {
    VerticalLayout panel = new VerticalLayout();
    panel.getStyle().set("flex", "1").set("min-width", "0");
    panel.setSpacing(true);
    panel.setPadding(false);

    H4 heading = new H4(getTranslation("migration.editor.title"));
    heading.getStyle()
        .set("margin", "0 0 0.3rem 0")
        .set("font-size", "var(--lumo-font-size-s)");

    versionLabel.setLabel(getTranslation("migration.editor.version"));
    versionLabel.setWidthFull();
    versionLabel.setPlaceholder(getTranslation("migration.prompt.version.placeholder"));

    descField.setLabel(getTranslation("migration.editor.description"));
    descField.setWidthFull();
    descField.setPlaceholder(getTranslation("migration.editor.description.placeholder"));

    promptEditor.setLabel(getTranslation("migration.editor.content"));
    promptEditor.setWidthFull();
    promptEditor.setMinHeight("300px");
    promptEditor.setMaxHeight("500px");
    promptEditor.setPlaceholder(getTranslation("migration.editor.content.placeholder"));

    editorStatus.getStyle()
        .set("font-size", "var(--lumo-font-size-xs)")
        .set("color", "var(--lumo-secondary-text-color)");

    HorizontalLayout editorBtns = new HorizontalLayout();
    editorBtns.setSpacing(false);
    editorBtns.getStyle().set("gap", "0.4rem");

    Button saveDraftBtn = new Button(getTranslation("migration.editor.save.draft"),
                                     e -> saveDraft());
    saveDraftBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    Button testDraftBtn = new Button(getTranslation("migration.editor.test"),
                                     e -> openTestDraftDialog());
    testDraftBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

    Button newBtn = new Button(getTranslation("migration.editor.new"), e -> clearEditor());
    newBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

    editorBtns.add(saveDraftBtn, testDraftBtn, newBtn);

    panel.add(heading, versionLabel, descField, promptEditor, editorStatus, editorBtns);
    return panel;
  }

  /**
   * Reloads both version selectors and preselects the active version of each key.
   */
  void refreshPromptSelectors() {
    List<PromptTemplateVersion> visionHistory =
        promptService.getHistory(VisionAnalysisServiceImpl.PROMPT_KEY);
    List<PromptTemplateVersion> semanticHistory =
        promptService.getHistory(SemanticDerivationServiceImpl.PROMPT_KEY);

    visionSelect.setItems(visionHistory);
    if (!visionHistory.isEmpty()) {
      visionHistory.stream().filter(PromptTemplateVersion::isActive).findFirst()
          .ifPresentOrElse(visionSelect::setValue, () -> visionSelect.setValue(visionHistory.get(0)));
    }

    semanticSelect.setItems(semanticHistory);
    if (!semanticHistory.isEmpty()) {
      semanticHistory.stream().filter(PromptTemplateVersion::isActive).findFirst()
          .ifPresentOrElse(semanticSelect::setValue, () -> semanticSelect.setValue(semanticHistory.get(0)));
    }
  }

  private void loadVersionIntoEditor(PromptTemplateVersion v) {
    if (v == null) return;
    selectedVersion = v;
    versionLabel.setValue(v.getVersion() != null ? v.getVersion() : "");
    descField.setValue(v.getDescription() != null ? v.getDescription() : "");
    promptEditor.setValue(v.getContent() != null ? v.getContent() : "");
    editorStatus.setText(getTranslation("migration.editor.loaded", v.displayLabel()));
  }

  private void clearEditor() {
    selectedVersion = null;
    versionLabel.setValue("");
    descField.setValue("");
    promptEditor.setValue("");
    editorStatus.setText(getTranslation("migration.editor.cleared"));
  }

  private void saveDraft() {
    String content = promptEditor.getValue();
    String version = versionLabel.getValue();
    String desc = descField.getValue();

    if (content.isBlank()) {
      Notification.show(getTranslation("migration.editor.error.empty"),
                        2500, Notification.Position.MIDDLE);
      return;
    }
    if (version.isBlank()) {
      Notification.show(getTranslation("migration.editor.error.no.version"),
                        2500, Notification.Position.MIDDLE);
      return;
    }

    // Determine prompt key from which selector triggered the load
    String promptKey = resolvePromptKeyFromEditor();
    if (promptKey == null) {
      Notification.show(getTranslation("migration.editor.error.no.key"),
                        2500, Notification.Position.MIDDLE);
      return;
    }

    try {
      PromptTemplateVersion draft = promptService.saveDraft(promptKey, version, content, desc);
      editorStatus.setText(getTranslation("migration.editor.draft.saved", draft.displayLabel()));
      Notification n = Notification.show(
          getTranslation("migration.editor.draft.saved", draft.displayLabel()),
          2500, Notification.Position.BOTTOM_START);
      n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
      refreshPromptSelectors();
    } catch (IllegalArgumentException ex) {
      // Expected: the draft failed template validation (for example a missing %s).
      logger().warn("Failed to save prompt draft: {}", ex.getMessage());
      Notification.show(getTranslation("migration.editor.error.save", ex.getMessage()),
                        3000, Notification.Position.MIDDLE);
    }
  }

  private String resolvePromptKeyFromEditor() {
    if (selectedVersion != null) return selectedVersion.getPromptKey();
    // Fallback: whichever selector has a value
    if (visionSelect.getValue() != null) return VisionAnalysisServiceImpl.PROMPT_KEY;
    if (semanticSelect.getValue() != null) return SemanticDerivationServiceImpl.PROMPT_KEY;
    return null;
  }

  private void openTestDraftDialog() {
    String draftContent = promptEditor.getValue();
    if (draftContent == null || draftContent.isBlank()) {
      Notification.show(getTranslation("migration.editor.error.empty"),
                        2500, Notification.Position.MIDDLE);
      return;
    }
    String promptKey = resolvePromptKeyFromEditor();
    if (promptKey == null) {
      Notification.show(getTranslation("migration.editor.error.no.key"),
                        2500, Notification.Position.MIDDLE);
      return;
    }
    new PromptTestDialog(services, promptKey, draftContent).open();
  }

  // ── Version actions ───────────────────────────────────────────────────────

  private void activateSelected() {
    PromptTemplateVersion v = getSelectedFromActiveSelector();
    if (v == null) {
      Notification.show(getTranslation("migration.prompt.error.none.selected"),
                        2000, Notification.Position.MIDDLE);
      return;
    }
    try {
      promptService.activate(v.getId());
      Notification n = Notification.show(
          getTranslation("migration.prompt.activated", v.displayLabel()),
          2500, Notification.Position.BOTTOM_START);
      n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
      refreshPromptSelectors();
    } catch (IllegalArgumentException ex) {
      // Expected: the selected version no longer exists.
      logger().warn("Activating prompt version {} failed: {}", v.getId(), ex.getMessage());
      Notification.show(getTranslation("migration.prompt.error.activate", ex.getMessage()),
                        3000, Notification.Position.MIDDLE);
    }
  }

  private void rollbackSelected() {
    PromptTemplateVersion v = getSelectedFromActiveSelector();
    if (v == null) {
      Notification.show(getTranslation("migration.prompt.error.none.selected"),
                        2000, Notification.Position.MIDDLE);
      return;
    }
    try {
      promptService.rollback(v.getId());
      Notification n = Notification.show(
          getTranslation("migration.prompt.rolled.back", v.displayLabel()),
          2500, Notification.Position.BOTTOM_START);
      n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
      refreshPromptSelectors();
    } catch (IllegalArgumentException ex) {
      // Expected: the selected version no longer exists.
      logger().warn("Rolling back to prompt version {} failed: {}", v.getId(), ex.getMessage());
      Notification.show(getTranslation("migration.prompt.error.rollback", ex.getMessage()),
                        3000, Notification.Position.MIDDLE);
    }
  }

  private void deleteSelected() {
    PromptTemplateVersion v = getSelectedFromActiveSelector();
    if (v == null) {
      Notification.show(getTranslation("migration.prompt.error.none.selected"),
                        2000, Notification.Position.MIDDLE);
      return;
    }
    ConfirmDialog confirm = new ConfirmDialog();
    confirm.setHeader(getTranslation("migration.prompt.delete.confirm.title"));
    confirm.setText(getTranslation("migration.prompt.delete.confirm.text", v.displayLabel()));
    confirm.setConfirmText(getTranslation("migration.prompt.delete.confirm.yes"));
    confirm.setCancelText(getTranslation("migration.prompt.delete.confirm.no"));
    confirm.setCancelable(true);
    confirm.setConfirmButtonTheme("error primary");
    confirm.addConfirmListener(e -> {
      try {
        promptService.delete(v.getId());
        Notification.show(getTranslation("migration.prompt.deleted", v.displayLabel()),
                          2500, Notification.Position.BOTTOM_START);
        clearEditor();
        refreshPromptSelectors();
      } catch (IllegalStateException ex) {
        Notification.show(
            getTranslation("migration.prompt.error.delete", ex.getMessage()),
            3000, Notification.Position.MIDDLE);
      }
    });
    confirm.open();
  }

  /**
   * Returns the version currently selected in whichever selector was last interacted with.
   */
  private PromptTemplateVersion getSelectedFromActiveSelector() {
    if (selectedVersion != null) return selectedVersion;
    PromptTemplateVersion v = visionSelect.getValue();
    if (v != null) return v;
    return semanticSelect.getValue();
  }
}
