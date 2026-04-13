package com.svenruppert.flow.views;

import com.svenruppert.flow.MainLayout;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;

import static com.vaadin.flow.component.html.AnchorTarget.BLANK;

@Route(value = AboutView.PATH, layout = MainLayout.class)
public class AboutView
    extends VerticalLayout {

  public static final String PATH = "about";

  public AboutView() {
    H1 title = new H1("About");
    H2 subtitle = new H2("Vaadin Flow Demo Application");

    Paragraph description = new Paragraph("This is a demo application built with Vaadin Flow " +
                                          "framework to showcase various UI components and features.");

    Paragraph version = new Paragraph("Version: 1.0.0");
    Paragraph author = new Paragraph("Created by: Sven Ruppert");
    Paragraph bio = new Paragraph("""
                                      Sven Ruppert has been involved in software development for more than 20 years. \
                                      As developer advocate he is constantly looking for innovations in software development. \
                                      He is speaking internationally at conferences and has authored numerous technical articles and books.""");
    Paragraph homepage = new Paragraph(
        new Paragraph("Visit my website: "),
        new Anchor("https://www.svenruppert.com", "www.svenruppert.com", BLANK));

    Image vaadinLogo = new Image("images/vaadin-logo.png", "Vaadin Logo");
    vaadinLogo.setWidth("200px");

    setSpacing(true);
    setPadding(true);
    setAlignItems(Alignment.CENTER);

    add(title, subtitle, description, version, author, bio, homepage, vaadinLogo);
  }

}