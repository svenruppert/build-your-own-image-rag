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
package com.svenruppert.flow.views;

import com.svenruppert.flow.MainLayout;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import org.jspecify.annotations.NonNull;

@Route(value = AboutView.PATH, layout = MainLayout.class)
@CssImport("./styles/about-view.css")
public class AboutView
    extends Composite<Div>
    implements HasDynamicTitle {

  public static final String PATH = "about";

  private static final String C_ROOT = "about-root";
  private static final String C_CONTAINER = "about-container";
  private static final String C_HERO = "about-hero";
  private static final String C_CARD = "about-card";
  private static final String C_GRID = "about-grid";
  private static final String C_LINK = "about-link";
  private static final String C_IMG_ICON = "about-link__imgicon";
  private static final String C_ICONROW = "about-iconrow";

  @Override
  public String getPageTitle() {
    return getTranslation("about.title");
  }

  public AboutView() {
    Div root = getContent();
    root.addClassName(C_ROOT);

    VerticalLayout container = new VerticalLayout();
    container.addClassName(C_CONTAINER);
    container.setWidthFull();
    container.setSpacing(false);
    container.setPadding(false);

    // --- HERO ---
    VerticalLayout hero = new VerticalLayout();
    hero.addClassName(C_HERO);
    hero.setPadding(true);
    hero.setSpacing(false);
    hero.setAlignItems(Alignment.START);

    H1 title = new H1(getTranslation("about.title"));
    Paragraph subtitle = new Paragraph(getTranslation("about.subtitle"));
    Paragraph description = new Paragraph(
        getTranslation("about.description")
    );

    HorizontalLayout heroHeader = new HorizontalLayout(title);
    // The version is only known when running from a packaged WAR whose manifest
    // carries Implementation-Version; during development no badge is shown.
    String version = AboutView.class.getPackage().getImplementationVersion();
    if (version != null && !version.isBlank()) {
      Span versionBadge = new Span(getTranslation("about.version", version));
      versionBadge.getElement().setAttribute("theme", "badge primary pill");
      heroHeader.add(versionBadge);
    }
    heroHeader.setAlignItems(Alignment.CENTER);
    heroHeader.setSpacing(true);

    hero.add(heroHeader, subtitle, description);

    // --- PROFILE CARD ---
    VerticalLayout profileCard = card();

    var header = getHorizontalLayout();

    Paragraph bio = new Paragraph(
        getTranslation("about.bio")
    );

    HorizontalLayout badges = new HorizontalLayout();
    badges.setWrap(true);
    badges.setSpacing(true);
    badges.add(
        badge("Vaadin Flow"),
        badge("Java 8-25"),
        badge("Security"),
        badge("RAG/AI"),
        badge("EclipseStore"),
        badge("DevSecOps")
    );

    HorizontalLayout links = new HorizontalLayout();
    links.setSpacing(true);
    links.setWrap(true);

    links.add(
        themedLink("https://www.svenruppert.com", getTranslation("about.link.website"), new Icon(VaadinIcon.GLOBE_WIRE)),
        themedLink("https://github.com/svenruppert", "GitHub", imgIcon("icons/github-mark.svg", "GitHub")),
        themedLink("https://www.linkedin.com/in/sven-ruppert", "LinkedIn", imgIcon("icons/linkedin-mark.png", "LinkedIn"))
    );

    profileCard.add(header, bio, badges, new Hr(), links);

    // --- PROJECT CARD ---
    VerticalLayout aboutCard = card();

    H3 projectTitle = new H3(getTranslation("about.project.title"));
    Paragraph projectText = new Paragraph(
        getTranslation("about.project.text")
    );

    UnorderedList featureList = new UnorderedList(
        new ListItem(withIcon(VaadinIcon.CHECK, getTranslation("about.project.feature.ingestion"))),
        new ListItem(withIcon(VaadinIcon.CHECK, getTranslation("about.project.feature.search"))),
        new ListItem(withIcon(VaadinIcon.CHECK, getTranslation("about.project.feature.privacy"))),
        new ListItem(withIcon(VaadinIcon.CHECK, getTranslation("about.project.feature.local")))
    );

    HorizontalLayout techBadges = new HorizontalLayout();
    techBadges.setWrap(true);
    techBadges.setSpacing(true);
    techBadges.add(
        badge("Java 26"),
        badge("Vaadin Flow"),
        badge("Ollama"),
        badge("EclipseStore"),
        badge("Lucene BM25"),
        badge("JVector")
    );

    aboutCard.add(projectTitle, projectText, featureList, techBadges);

    // --- GRID ---
    FlexLayout grid = new FlexLayout(profileCard, aboutCard);
    grid.addClassName(C_GRID);
    grid.setWidthFull();
    grid.setFlexWrap(FlexLayout.FlexWrap.WRAP);

    // --- FOOTER ---
    Div footer = new Div();
    footer.addClassName("about-footer");
    footer.add(new Icon(VaadinIcon.INFO_CIRCLE_O),
               new Text(getTranslation("about.footer")));

    container.add(hero, grid, footer);
    root.add(container);
  }

  private static @NonNull HorizontalLayout getHorizontalLayout() {
    HorizontalLayout header = new HorizontalLayout();
    header.setAlignItems(Alignment.CENTER);
    header.setSpacing(true);

    Image portrait = new Image("images/portrait-sven.jpg", "Sven Ruppert");
    portrait.addClassName("about-avatar");

    VerticalLayout headerText = new VerticalLayout();
    headerText.setSpacing(false);
    headerText.setPadding(false);

    H2 authorName = new H2("Sven Ruppert");
    Span role = new Span("Developer Advocate • Java • Secure Coding");

    headerText.add(authorName, role);
    header.add(portrait, headerText);
    return header;
  }

  private VerticalLayout card() {
    VerticalLayout card = new VerticalLayout();
    card.addClassName(C_CARD);
    card.setPadding(true);
    card.setSpacing(true);
    card.setAlignItems(Alignment.START);
    return card;
  }

  private Span badge(String label) {
    Span s = new Span(label);
    s.getElement().setAttribute("theme", "badge contrast pill");
    return s;
  }

  private Component withIcon(VaadinIcon icon, String text) {
    HorizontalLayout row = new HorizontalLayout();
    row.addClassName(C_ICONROW);
    row.setSpacing(true);
    row.setAlignItems(Alignment.CENTER);
    row.add(new Icon(icon), new Text(text));
    return row;
  }

  private Anchor themedLink(String href, String label, Component icon) {
    Anchor a = new Anchor(href, "");
    a.addClassName(C_LINK);
    a.setTarget("_blank");
    a.getElement().setAttribute("aria-label", label);
    a.add(icon, new Span(label));
    return a;
  }

  private Image imgIcon(String src, String alt) {
    Image img = new Image(src, alt);
    img.addClassName(C_IMG_ICON);
    return img;
  }
}
