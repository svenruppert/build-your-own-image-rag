package com.svenruppert.flow.views.migration;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.MainLayout;
import com.svenruppert.flow.views.shared.ViewServices;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;

/**
 * Model &amp; Index Migration Center — operational maintenance view.
 * <p>Tabs:
 * <ol>
 *   <li><b>System Config</b> ({@link SystemConfigTab}) — read-only current active
 *       configuration (models, backend, prompt overrides, statistics)</li>
 *   <li><b>Prompts</b> ({@link PromptsTab}) — version history per prompt key,
 *       activate/rollback/delete, editable draft editor and draft test</li>
 *   <li><b>Index Rebuild</b> ({@link IndexRebuildTab}) — trigger vector-index and
 *       keyword-index rebuilds</li>
 * </ol>
 */
@Route(value = MigrationCenterView.PATH, layout = MainLayout.class)
public class MigrationCenterView
    extends VerticalLayout
    implements BeforeEnterObserver, HasLogger, HasDynamicTitle {

  public static final String PATH = "migration";

  private final PromptsTab promptsTab;

  public MigrationCenterView() {
    ViewServices services = ViewServices.current();

    setSpacing(false);
    setPadding(true);
    setWidthFull();

    promptsTab = new PromptsTab(services);

    TabSheet tabs = new TabSheet();
    tabs.setWidthFull();
    tabs.add(getTranslation("migration.tab.config"), new SystemConfigTab(services));
    tabs.add(getTranslation("migration.tab.prompts"), promptsTab);
    tabs.add(getTranslation("migration.tab.rebuild"), new IndexRebuildTab(services));

    add(buildHeader(), tabs);
  }

  @Override
  public void beforeEnter(BeforeEnterEvent event) {
    promptsTab.refreshPromptSelectors();
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

  @Override
  public String getPageTitle() {
    return getTranslation("migration.page.title");
  }
}
