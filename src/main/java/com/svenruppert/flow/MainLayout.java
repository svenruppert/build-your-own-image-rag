package com.svenruppert.flow;

import com.svenruppert.flow.views.AboutView;
import com.svenruppert.flow.views.YoutubeView;
import com.svenruppert.flow.views.archive.ArchiveView;
import com.svenruppert.flow.views.main.MainView;
import com.svenruppert.flow.views.migration.MigrationCenterView;
import com.svenruppert.flow.views.multimodal.MultimodalSearchView;
import com.svenruppert.flow.views.overview.OverviewView;
import com.svenruppert.flow.views.pipeline.PipelineView;
import com.svenruppert.flow.views.search.SearchView;
import com.svenruppert.flow.views.taxonomy.TaxonomyMaintenanceView;
import com.svenruppert.flow.views.tuning.SearchTuningView;
import com.svenruppert.flow.views.upload.UploadView;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.theme.lumo.LumoUtility;

import java.util.Locale;

import static com.vaadin.flow.component.icon.VaadinIcon.*;

public class MainLayout
    extends AppLayout {

  public MainLayout() {
    createHeader();
  }

  private void createHeader() {
    H1 appTitle = new H1(getTranslation("app.title"));

    SideNav views = getPrimaryNavigation();
    Scroller scroller = new Scroller(views);
    scroller.setClassName(LumoUtility.Padding.SMALL);

    DrawerToggle toggle = new DrawerToggle();
    H2 viewTitle = new H2(getTranslation("app.subtitle"));

    HorizontalLayout left = new HorizontalLayout(toggle, viewTitle);
    left.setAlignItems(FlexComponent.Alignment.CENTER);
    left.setSpacing(false);

    // Language toggle — EN / DE.
    // The currently active locale is highlighted with a PRIMARY variant so the user
    // always knows which language is in effect.
    String activeLang = UI.getCurrent().getLocale().getLanguage();

    Button enBtn = new Button("EN", e -> switchLanguage("en"));
    Button deBtn = new Button("DE", e -> switchLanguage("de"));
    enBtn.addThemeVariants(ButtonVariant.LUMO_SMALL,
                           "en".equals(activeLang) ? ButtonVariant.LUMO_PRIMARY : ButtonVariant.LUMO_TERTIARY);
    deBtn.addThemeVariants(ButtonVariant.LUMO_SMALL,
                           "de".equals(activeLang) ? ButtonVariant.LUMO_PRIMARY : ButtonVariant.LUMO_TERTIARY);
    enBtn.getElement().setAttribute("title", "Switch to English");
    deBtn.getElement().setAttribute("title", "Auf Deutsch wechseln");

    HorizontalLayout langButtons = new HorizontalLayout(enBtn, deBtn);
    langButtons.setSpacing(false);
    langButtons.getStyle().set("margin-left", "auto");

    HorizontalLayout navbar = new HorizontalLayout(left, langButtons);
    navbar.setWidthFull();
    navbar.setAlignItems(FlexComponent.Alignment.CENTER);
    navbar.setSpacing(false);
    navbar.getStyle().set("padding-right", "var(--lumo-space-m)");

    addToDrawer(appTitle, scroller);
    addToNavbar(navbar);

    setPrimarySection(Section.DRAWER);
  }

  /**
   * Stores the chosen locale in the {@link VaadinSession} and reloads the page so
   * that all translated strings are re-evaluated with the new locale.
   * The locale is restored on reload by {@code AppInitializer}'s UIInitListener.
   */
  private void switchLanguage(String lang) {
    Locale locale = Locale.forLanguageTag(lang);
    VaadinSession.getCurrent().setAttribute("app.locale", locale);
    UI.getCurrent().getPage().reload();
  }

  private SideNav getPrimaryNavigation() {
    SideNav sideNav = new SideNav();
    sideNav.addItem(
        new SideNavItem(getTranslation("nav.upload"), "/" + UploadView.PATH, UPLOAD.create()),
        new SideNavItem(getTranslation("nav.pipeline"), "/" + PipelineView.PATH, LIST.create()),
        new SideNavItem(getTranslation("nav.overview"), "/" + OverviewView.PATH, TABLE.create()),
        new SideNavItem(getTranslation("nav.archive"), "/" + ArchiveView.PATH, ARCHIVE.create()),
        new SideNavItem(getTranslation("nav.search"), "/" + SearchView.PATH, SEARCH.create()),
        new SideNavItem(getTranslation("nav.multimodal"), "/" + MultimodalSearchView.PATH, SPLIT.create()),
        new SideNavItem(getTranslation("nav.taxonomy"), "/" + TaxonomyMaintenanceView.PATH, TAG.create()),
        new SideNavItem(getTranslation("nav.tuning"), "/" + SearchTuningView.PATH, CHART_LINE.create()),
        new SideNavItem(getTranslation("nav.migration"), "/" + MigrationCenterView.PATH, COG.create()),
        new SideNavItem(getTranslation("nav.dashboard"), "/" + MainView.PATH, DASHBOARD.create()),
        new SideNavItem(getTranslation("nav.youtube"), "/" + YoutubeView.PATH, CART.create()),
        new SideNavItem(getTranslation("nav.about"), "/" + AboutView.PATH, USER_HEART.create())
    );
    return sideNav;
  }

  //  private HorizontalLayout getSecondaryNavigation() {
  //    HorizontalLayout navigation = new HorizontalLayout();
  //    navigation.addClassNames(LumoUtility.JustifyContent.CENTER,
  //                             LumoUtility.Gap.SMALL, LumoUtility.Height.MEDIUM);
  //    //TODO i18n
  //    RouterLink all = createLink("All");
  //    RouterLink open = createLink("Open");
  //    RouterLink completed = createLink("Completed");
  //    RouterLink cancelled = createLink("Cancelled");
  //
  //    navigation.add(all, open, completed, cancelled);
  //    return navigation;
  //  }
  //
  //  private RouterLink createLink(String viewName) {
  //    RouterLink link = new RouterLink();
  //    link.add(viewName);
  //    // Demo has no routes
  //     //link.setRoute(YoutubeView.class);
  //
  //    link.addClassNames(LumoUtility.Display.FLEX,
  //                       LumoUtility.AlignItems.CENTER,
  //                       LumoUtility.Padding.Horizontal.MEDIUM,
  //                       LumoUtility.TextColor.SECONDARY,
  //                       LumoUtility.FontWeight.MEDIUM);
  //    link.getStyle().set("text-decoration", "none");
  //
  //    return link;
  //  }
}