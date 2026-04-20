package com.svenruppert.flow.views.migration;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.MainLayout;
import com.svenruppert.imagerag.bootstrap.ServiceRegistry;
import com.svenruppert.imagerag.domain.ImageAsset;
import com.svenruppert.imagerag.domain.PromptTemplateVersion;
import com.svenruppert.imagerag.domain.enums.VectorBackendType;
import com.svenruppert.imagerag.ollama.OllamaClient;
import com.svenruppert.imagerag.ollama.OllamaConfig;
import com.svenruppert.imagerag.service.PromptTemplateService;
import com.svenruppert.imagerag.service.impl.SemanticDerivationServiceImpl;
import com.svenruppert.imagerag.service.impl.VisionAnalysisServiceImpl;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.nio.file.Files;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Model &amp; Index Migration Center — operational maintenance view.
 * <p>Tabs:
 * <ol>
 *   <li><b>System Config</b> — read-only current active configuration (models, backend, paths)</li>
 *   <li><b>Prompts</b> — sub-tab per prompt key: version history, activate/rollback,
 *       editable draft editor</li>
 *   <li><b>Index Rebuild</b> — trigger vector-index rebuild, keyword-index rebuild,
 *       full reprocessing</li>
 * </ol>
 */
@Route(value = MigrationCenterView.PATH, layout = MainLayout.class)
@PageTitle("Migration Center")
public class MigrationCenterView
    extends VerticalLayout
    implements BeforeEnterObserver, HasLogger {

  public static final String PATH = "migration";

  /**
   * Upper bound on prompt-draft test execution so a hung model can't freeze the dialog.
   */
  private static final long PROMPT_TEST_TIMEOUT_SECONDS = 120L;

  private static final DateTimeFormatter DATE_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

  // ── Services ──────────────────────────────────────────────────────────────
  private final PromptTemplateService promptService;
  private final OllamaConfig ollamaConfig;
  private final ServiceRegistry registry;

  // ── Prompt editor state ───────────────────────────────────────────────────
  private final Select<PromptTemplateVersion> visionSelect = new Select<>();
  private final Select<PromptTemplateVersion> semanticSelect = new Select<>();
  private final TextArea promptEditor = new TextArea();
  private final TextField versionLabel = new TextField();
  private final TextField descField = new TextField();
  private final Span editorStatus = new Span();
  // ── Rebuild progress ──────────────────────────────────────────────────────
  private final ProgressBar rebuildProgress = new ProgressBar();
  private final Span rebuildStatus = new Span();
  /**
   * Currently selected version in the prompt editor.
   */
  private PromptTemplateVersion selectedVersion = null;
  private volatile boolean rebuilding = false;

  public MigrationCenterView() {
    this.registry = ServiceRegistry.getInstance();
    this.promptService = registry.getPromptTemplateService();
    this.ollamaConfig = registry.getOllamaConfig();

    setSpacing(false);
    setPadding(true);
    setWidthFull();

    add(buildHeader(), buildTabSheet());
  }

  private static int countLines(String s) {
    if (s == null || s.isEmpty()) return 0;
    return (int) s.chars().filter(c -> c == '\n').count() + 1;
  }

  // ── Header ────────────────────────────────────────────────────────────────

  private static VerticalLayout tabLayout() {
    VerticalLayout tab = new VerticalLayout();
    tab.setSpacing(false);
    tab.setPadding(true);
    tab.setWidthFull();
    tab.getStyle().set("padding-top", "0.75rem");
    return tab;
  }

  // ── Tab sheet ─────────────────────────────────────────────────────────────

  private static VerticalLayout styledPanel(String width) {
    VerticalLayout panel = new VerticalLayout();
    panel.setWidth(width);
    panel.setMinWidth(width);
    panel.setSpacing(false);
    panel.setPadding(true);
    panel.getStyle()
        .set("flex-shrink", "0")
        .set("border", "1px solid var(--lumo-contrast-10pct)")
        .set("border-radius", "var(--lumo-border-radius-l)")
        .set("background", "var(--lumo-base-color)");
    return panel;
  }

  // ── Tab 1: System Config ──────────────────────────────────────────────────

  private static H3 sectionHeading(String text) {
    H3 h = new H3(text);
    h.getStyle()
        .set("font-size", "var(--lumo-font-size-xs)")
        .set("text-transform", "uppercase")
        .set("letter-spacing", "0.05em")
        .set("color", "var(--lumo-secondary-text-color)")
        .set("margin", "0.75rem 0 0.3rem 0");
    return h;
  }

  private static Div configRow(String label, String value) {
    Div row = new Div();
    row.getStyle()
        .set("display", "flex").set("gap", "0.5rem")
        .set("font-size", "var(--lumo-font-size-s)")
        .set("padding", "0.2rem 0")
        .set("border-bottom", "1px solid var(--lumo-contrast-5pct)");

    Span l = new Span(label + ":");
    l.getStyle().set("color", "var(--lumo-secondary-text-color)")
        .set("min-width", "180px").set("flex-shrink", "0");

    Span v = new Span(value != null ? value : "—");
    v.getStyle().set("color", "var(--lumo-body-text-color)")
        .set("font-family", "monospace")
        .set("overflow-x", "auto");

    row.add(l, v);
    return row;
  }

  // ── Tab 2: Prompts ────────────────────────────────────────────────────────

  /**
   * Sanitises an LLM-derived description before substituting it into a user-authored
   * prompt draft. Caps length, strips control characters, neutralises common prompt
   * break-out tokens, and replaces double quotes to keep JSON-style prompts intact.
   * <p>This is a defence-in-depth measure for the "Test draft" flow, where the draft
   * may be executed against real image descriptions during prompt authoring.
   */
  private static String sanitizeForPrompt(String raw) {
    if (raw == null) return "";
    String s = raw.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", " ");
    s = s.replace("```", "'''")
        .replace("</", "< /")
        .replace("\"", "'");
    final int maxLen = 4000;
    if (s.length() > maxLen) s = s.substring(0, maxLen) + "…";
    return s;
  }

  @Override
  public void beforeEnter(BeforeEnterEvent event) {
    refreshPromptSelectors();
  }

  private Component buildHeader() {
    H2 title = new H2(getTranslation("migration.title"));
    Paragraph desc = new Paragraph(getTranslation("migration.description"));
    desc.getStyle().set("color", "var(--lumo-secondary-text-color)");
    VerticalLayout h = new VerticalLayout(title, desc);
    h.setSpacing(false);
    h.setPadding(false);
    h.getStyle().set("margin-bottom", "0.5rem");
    return h;
  }

  // ── Tab 3: Index Rebuild ──────────────────────────────────────────────────

  private Component buildTabSheet() {
    TabSheet tabs = new TabSheet();
    tabs.setWidthFull();

    tabs.add(getTranslation("migration.tab.config"), buildSystemConfigTab());
    tabs.add(getTranslation("migration.tab.prompts"), buildPromptsTab());
    tabs.add(getTranslation("migration.tab.rebuild"), buildRebuildTab());

    return tabs;
  }

  private Component buildSystemConfigTab() {
    VerticalLayout tab = tabLayout();

    tab.add(sectionHeading(getTranslation("migration.config.models")));
    tab.add(configRow(getTranslation("migration.config.vision.model"),
                      ollamaConfig.getVisionModel()));
    tab.add(configRow(getTranslation("migration.config.text.model"),
                      ollamaConfig.getTextModel()));
    tab.add(configRow(getTranslation("migration.config.embedding.model"),
                      ollamaConfig.getEmbeddingModel()));
    tab.add(configRow(getTranslation("migration.config.base.url"),
                      ollamaConfig.baseUrl()));

    tab.add(sectionHeading(getTranslation("migration.config.backend")));
    VectorBackendType backend = registry.getVectorBackendType();
    tab.add(configRow(getTranslation("migration.config.vector.backend"),
                      backend.name()));

    // Prompt versions
    tab.add(sectionHeading(getTranslation("migration.config.active.prompts")));
    for (String key : new String[]{
        VisionAnalysisServiceImpl.PROMPT_KEY,
        SemanticDerivationServiceImpl.PROMPT_KEY}) {
      promptService.getActiveContent(key).ifPresentOrElse(
          content -> tab.add(configRow(key,
                                       getTranslation("migration.config.prompt.override", countLines(content)))),
          () -> tab.add(configRow(key,
                                  getTranslation("migration.config.prompt.builtin")))
      );
    }

    // Image / index statistics
    tab.add(sectionHeading(getTranslation("migration.config.stats")));
    long totalImages = registry.getPersistenceService().findAllImages().size();
    long indexedImages = registry.getPersistenceService().findAllIndexedImageIds().size();
    tab.add(configRow(getTranslation("migration.config.total.images"), String.valueOf(totalImages)));
    tab.add(configRow(getTranslation("migration.config.indexed.images"), String.valueOf(indexedImages)));

    return tab;
  }

  // ── Prompt editor actions ─────────────────────────────────────────────────

  private Component buildPromptsTab() {
    VerticalLayout tab = tabLayout();

    // ── History panel (left) + Editor panel (right) ────────────────────────
    VerticalLayout historyPanel = buildPromptHistoryPanel();
    VerticalLayout editorPanel = buildPromptEditorPanel();

    HorizontalLayout split = new HorizontalLayout(historyPanel, editorPanel);
    split.setWidthFull();
    split.setAlignItems(FlexComponent.Alignment.START);
    split.setSpacing(true);
    split.setPadding(false);
    tab.add(split);
    return tab;
  }

  private VerticalLayout buildPromptHistoryPanel() {
    VerticalLayout panel = styledPanel("340px");

    // Vision prompt section
    panel.add(sectionHeading(getTranslation("migration.prompt.vision")));
    visionSelect.setWidthFull();
    visionSelect.setItemLabelGenerator(PromptTemplateVersion::displayLabel);
    visionSelect.addValueChangeListener(e -> loadVersionIntoEditor(e.getValue()));
    panel.add(visionSelect);

    HorizontalLayout visionBtns = new HorizontalLayout();
    visionBtns.setSpacing(false);
    visionBtns.getStyle().set("gap", "0.3rem");

    Button activateVision = new Button(getTranslation("migration.prompt.activate"),
                                       e -> activateSelected());
    activateVision.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
    Button rollbackVision = new Button(getTranslation("migration.prompt.rollback"),
                                       e -> rollbackSelected());
    rollbackVision.addThemeVariants(ButtonVariant.LUMO_SMALL);
    Button deleteVision = new Button(getTranslation("migration.prompt.delete"),
                                     e -> deleteSelected());
    deleteVision.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY,
                                  ButtonVariant.LUMO_ERROR);
    visionBtns.add(activateVision, rollbackVision, deleteVision);
    panel.add(visionBtns);

    // Semantic prompt section
    panel.add(sectionHeading(getTranslation("migration.prompt.semantic")));
    semanticSelect.setWidthFull();
    semanticSelect.setItemLabelGenerator(PromptTemplateVersion::displayLabel);
    semanticSelect.addValueChangeListener(e -> loadVersionIntoEditor(e.getValue()));
    panel.add(semanticSelect);

    HorizontalLayout semanticBtns = new HorizontalLayout();
    semanticBtns.setSpacing(false);
    semanticBtns.getStyle().set("gap", "0.3rem");

    Button activateSemantic = new Button(getTranslation("migration.prompt.activate"),
                                         e -> activateSelected());
    activateSemantic.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
    Button rollbackSemantic = new Button(getTranslation("migration.prompt.rollback"),
                                         e -> rollbackSelected());
    rollbackSemantic.addThemeVariants(ButtonVariant.LUMO_SMALL);
    Button deleteSemantic = new Button(getTranslation("migration.prompt.delete"),
                                       e -> deleteSelected());
    deleteSemantic.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY,
                                    ButtonVariant.LUMO_ERROR);
    semanticBtns.add(activateSemantic, rollbackSemantic, deleteSemantic);
    panel.add(semanticBtns);

    return panel;
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
    versionLabel.setPlaceholder("e.g. v2-experiment");

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

  private Component buildRebuildTab() {
    VerticalLayout tab = tabLayout();

    tab.add(sectionHeading(getTranslation("migration.rebuild.title")));

    Paragraph info = new Paragraph(getTranslation("migration.rebuild.description"));
    info.getStyle().set("color", "var(--lumo-secondary-text-color)").set("font-size", "var(--lumo-font-size-s)");
    tab.add(info);

    // Progress bar (hidden by default)
    rebuildProgress.setIndeterminate(true);
    rebuildProgress.setVisible(false);
    rebuildProgress.setWidthFull();
    rebuildStatus.getStyle()
        .set("font-size", "var(--lumo-font-size-xs)")
        .set("color", "var(--lumo-secondary-text-color)");

    tab.add(rebuildProgress, rebuildStatus);

    // Action cards
    tab.add(buildRebuildCard(
        getTranslation("migration.rebuild.vector.title"),
        getTranslation("migration.rebuild.vector.description"),
        getTranslation("migration.rebuild.vector.button"),
        () -> {
          long count = registry.getPersistenceService().findAllIndexedImageIds().size();
          return getTranslation("migration.rebuild.vector.confirm")
              + "  " + getTranslation("migration.rebuild.affected.images", count);
        },
        this::triggerVectorRebuild));

    tab.add(buildRebuildCard(
        getTranslation("migration.rebuild.keyword.title"),
        getTranslation("migration.rebuild.keyword.description"),
        getTranslation("migration.rebuild.keyword.button"),
        () -> {
          long count = registry.getPersistenceService().findAllImages().size();
          return getTranslation("migration.rebuild.keyword.confirm")
              + "  " + getTranslation("migration.rebuild.affected.images", count);
        },
        this::triggerKeywordRebuild));

    return tab;
  }

  private Component buildRebuildCard(String title, String description,
                                     String buttonLabel,
                                     java.util.function.Supplier<String> confirmText,
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

  private void refreshPromptSelectors() {
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
    } catch (Exception ex) {
      logger().warn("Failed to save prompt draft: {}", ex.getMessage());
      Notification.show(getTranslation("migration.editor.error.save", ex.getMessage()),
                        3000, Notification.Position.MIDDLE);
    }
  }

  // ── Prompt test / preview ─────────────────────────────────────────────────

  private String resolvePromptKeyFromEditor() {
    if (selectedVersion != null) return selectedVersion.getPromptKey();
    // Fallback: whichever selector has a value
    if (visionSelect.getValue() != null) return VisionAnalysisServiceImpl.PROMPT_KEY;
    if (semanticSelect.getValue() != null) return SemanticDerivationServiceImpl.PROMPT_KEY;
    return null;
  }

  /**
   * Opens a dialog that lets the user test the current draft prompt content against
   * a sample image without activating it.  Results are shown inline so the user can
   * compare the draft output with the existing analysis before deciding to activate.
   */
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

    Dialog dlg = new Dialog();
    dlg.setHeaderTitle(getTranslation("migration.test.title",
                                      promptKey.equals(VisionAnalysisServiceImpl.PROMPT_KEY)
                                          ? getTranslation("migration.prompt.vision")
                                          : getTranslation("migration.prompt.semantic")));
    dlg.setWidth("640px");
    dlg.setMaxHeight("82vh");

    TextField imageIdField = new TextField(getTranslation("migration.test.image.label"));
    imageIdField.setWidthFull();
    imageIdField.setPlaceholder(getTranslation("migration.test.image.placeholder"));

    TextArea resultArea = new TextArea(getTranslation("migration.test.result.label"));
    resultArea.setWidthFull();
    resultArea.setMinHeight("200px");
    resultArea.setReadOnly(true);

    ProgressBar testProgress = new ProgressBar();
    testProgress.setIndeterminate(true);
    testProgress.setVisible(false);
    testProgress.setWidthFull();

    final String finalDraft = draftContent;
    final String finalKey = promptKey;

    Button runBtn = new Button(getTranslation("migration.test.run"), e -> {
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
      final UUID finalId = imageId;
      Thread.ofVirtual().name("prompt-test").start(() -> {
        java.util.concurrent.ExecutorService exec =
            java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
        try {
          java.util.concurrent.Future<String> fut =
              exec.submit(() -> executePromptTest(finalKey, finalId, finalDraft));
          String result;
          try {
            result = fut.get(PROMPT_TEST_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
          } catch (java.util.concurrent.TimeoutException te) {
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
        } catch (Exception ex) {
          logger().warn("Prompt test failed: {}", ex.getMessage());
          ui.access(() -> {
            testProgress.setVisible(false);
            resultArea.setValue(getTranslation("migration.test.error", ex.getMessage()));
          });
        } finally {
          exec.shutdownNow();
        }
      });
    });
    runBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    VerticalLayout content = new VerticalLayout(imageIdField, testProgress, runBtn, resultArea);
    content.setPadding(false);
    content.setSpacing(true);
    dlg.add(content);

    Button closeBtn = new Button(getTranslation("common.close"), ev -> dlg.close());
    dlg.getFooter().add(closeBtn);
    dlg.open();
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
    return registry.getPersistenceService().findAllImages().stream()
        .filter(a -> a.getOriginalFilename() != null
            && a.getOriginalFilename().toLowerCase().contains(lower))
        .map(ImageAsset::getId)
        .findFirst().orElse(null);
  }

  /**
   * Executes the draft prompt against the chosen image and returns the raw model output.
   * <ul>
   *   <li>For the <em>vision</em> key the draft is sent as the system prompt directly to
   *       the vision model together with the image file.</li>
   *   <li>For the <em>semantic</em> key the draft is formatted with the existing vision
   *       description of the image and sent to the text model (no re-vision needed).</li>
   * </ul>
   */
  private String executePromptTest(String promptKey, UUID imageId, String draftContent) {
    OllamaClient ollama = registry.getOllamaClient();

    if (VisionAnalysisServiceImpl.PROMPT_KEY.equals(promptKey)) {
      // Vision test: run draft against the actual stored image file
      var asset = registry.getPersistenceService().findImage(imageId).orElse(null);
      if (asset == null) return getTranslation("migration.test.error.image.not.found");
      var imagePath = registry.getImageStorageService().resolvePath(asset.getId());
      if (!Files.exists(imagePath)) return getTranslation("migration.test.error.file.not.found");
      return ollama.analyzeImageWithVision(imagePath, draftContent)
          .orElse(getTranslation("migration.test.no.result"));
    } else {
      // Semantic test: use the existing vision description as input to the draft prompt
      var analysis = registry.getPersistenceService().findAnalysis(imageId).orElse(null);
      String description = (analysis != null && analysis.getSummary() != null)
          ? analysis.getSummary()
          : getTranslation("migration.test.no.description");
      String formatted = draftContent.formatted(sanitizeForPrompt(description));
      return ollama.generateJson(formatted)
          .orElse(getTranslation("migration.test.no.result"));
    }
  }

  // ── Index rebuild actions ─────────────────────────────────────────────────

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
    } catch (Exception ex) {
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
    } catch (Exception ex) {
      Notification.show(getTranslation("migration.prompt.error.rollback", ex.getMessage()),
                        3000, Notification.Position.MIDDLE);
    }
  }

  // ── Layout helpers ────────────────────────────────────────────────────────

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

  private void triggerVectorRebuild() {
    if (rebuilding) {
      Notification.show(getTranslation("migration.rebuild.already.running"),
                        2000, Notification.Position.MIDDLE);
      return;
    }
    rebuilding = true;
    UI ui = UI.getCurrent();
    ui.access(() -> {
      rebuildProgress.setVisible(true);
      rebuildStatus.setText(getTranslation("migration.rebuild.vector.running"));
    });

    Thread.ofVirtual().name("migration-vector-rebuild").start(() -> {
      try {
        registry.rebuildVectorIndex();
        ui.access(() -> {
          rebuildProgress.setVisible(false);
          rebuildStatus.setText(getTranslation("migration.rebuild.vector.done"));
          Notification n = Notification.show(
              getTranslation("migration.rebuild.vector.done"),
              3000, Notification.Position.BOTTOM_START);
          n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
          rebuilding = false;
        });
      } catch (Exception ex) {
        logger().warn("Vector rebuild failed: {}", ex.getMessage());
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

  private void triggerKeywordRebuild() {
    if (rebuilding) {
      Notification.show(getTranslation("migration.rebuild.already.running"),
                        2000, Notification.Position.MIDDLE);
      return;
    }
    rebuilding = true;
    UI ui = UI.getCurrent();
    ui.access(() -> {
      rebuildProgress.setVisible(true);
      rebuildStatus.setText(getTranslation("migration.rebuild.keyword.running"));
    });

    Thread.ofVirtual().name("migration-keyword-rebuild").start(() -> {
      try {
        registry.rebuildKeywordIndex();
        ui.access(() -> {
          rebuildProgress.setVisible(false);
          rebuildStatus.setText(getTranslation("migration.rebuild.keyword.done"));
          Notification n = Notification.show(
              getTranslation("migration.rebuild.keyword.done"),
              3000, Notification.Position.BOTTOM_START);
          n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
          rebuilding = false;
        });
      } catch (Exception ex) {
        logger().warn("Keyword rebuild failed: {}", ex.getMessage());
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
