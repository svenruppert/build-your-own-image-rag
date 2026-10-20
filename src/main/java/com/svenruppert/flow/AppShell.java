package com.svenruppert.flow;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Meta;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.component.page.Viewport;
import com.vaadin.flow.server.AppShellSettings;
import com.vaadin.flow.server.PWA;
import com.vaadin.flow.shared.communication.PushMode;
import com.vaadin.flow.theme.Theme;

/**
 * Application shell: page metadata, viewport, PWA manifest, theme and server push
 * for the whole application.
 */
@Meta(name = "author", content = "Sven Ruppert")
@Viewport("width=device-width, initial-scale=1.0")
@PWA(name = "Image RAG", shortName = "Image RAG")
@Theme("my-theme")
@Push(PushMode.AUTOMATIC)
public class AppShell
    implements AppShellConfigurator {

  @Override
  public void configurePage(AppShellSettings settings) {
    // No additional page settings; the annotations above configure the shell.
  }
}