package com.svenruppert.flow;

import com.svenruppert.flow.views.AboutView;
import com.svenruppert.flow.views.YoutubeView;
import com.svenruppert.flow.views.main.MainView;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.theme.lumo.LumoUtility;

import static com.vaadin.flow.component.icon.VaadinIcon.*;

public class MainLayout
    extends AppLayout {

  public MainLayout() {
    createHeader();
  }

  private void createHeader() {
    H1 appTitle = new H1("Vaadin Flow Demo");

    SideNav views = getPrimaryNavigation();
    Scroller scroller = new Scroller(views);
    scroller.setClassName(LumoUtility.Padding.SMALL);

    DrawerToggle toggle = new DrawerToggle();
    H2 viewTitle = new H2("Headline");


    HorizontalLayout wrapper = new HorizontalLayout(toggle, viewTitle);
    wrapper.setAlignItems(FlexComponent.Alignment.CENTER);
    wrapper.setSpacing(false);

//    VerticalLayout viewHeader = new VerticalLayout(wrapper, getSecondaryNavigation());
    VerticalLayout viewHeader = new VerticalLayout(wrapper);
    viewHeader.setPadding(false);
    viewHeader.setSpacing(false);

    addToDrawer(appTitle, scroller);
    addToNavbar(viewHeader);

    setPrimarySection(Section.DRAWER);
  }

  private SideNav getPrimaryNavigation() {
    SideNav sideNav = new SideNav();
    sideNav.addItem(new SideNavItem("Dashboard",
                                    "/" + MainView.PATH,
                                    DASHBOARD.create()),
                    new SideNavItem("Youtube",
                                    "/" + YoutubeView.PATH,
                                    CART.create()),
                    new SideNavItem("About",
                                    "/" + AboutView.PATH,
                                    USER_HEART.create())
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