package com.svenruppert.flow;

import com.vaadin.flow.component.page.*;
import com.vaadin.flow.server.AppShellSettings;
import com.vaadin.flow.server.PWA;
import com.vaadin.flow.theme.Theme;

/**
 * Typical use cases of AppShell
 * ✅ Viewport & mobile optimization
 * ✅ Setting metadata (SEO, security)
 * ✅ Favicons, touch icons
 * ✅ Global JavaScript snippets (analytics, monitoring)
 * ✅ Global CSS (e.g., corporate branding)
 * ✅ Selecting a theme for the entire app
 */
@Meta(name = "author", content = "Sven Ruppert")
@Viewport("width=device-width, initial-scale=1.0")
@PWA(name = "Project Base for Vaadin", shortName = "Project Base")
@Theme("my-theme")
@Push
public class AppShell
    implements AppShellConfigurator {

  @Override
  public void configurePage(AppShellSettings settings) {

//    settings.addFavIcon("icon",
//                        "icons/my-favicon.png",
//                        "32x32");
//
//    // Externes CSS
//    settings.addLink("stylesheet",
//                     "https://cdn.example.com/styles/global.css");
//
//    // Externes Script
//    settings.addInlineWithContents(
//        "console.log('Hello from AppShell!');",
//        Inline.Wrapping.AUTOMATIC);
  }
}