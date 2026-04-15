package com.svenruppert.imagerag.bootstrap;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import com.vaadin.flow.server.VaadinSession;

import java.util.Locale;

/**
 * Vaadin hook that initializes the ServiceRegistry when the application starts.
 * Registered via META-INF/services/com.vaadin.flow.server.VaadinServiceInitListener.
 *
 * <p>Also registers a {@code UIInitListener} that restores the user's preferred locale
 * from the {@link VaadinSession} on every page (re-)load.  The locale is stored under
 * the key {@code "app.locale"} by the language-toggle buttons in {@code MainLayout}.
 */
public class AppInitializer
    implements VaadinServiceInitListener, HasLogger {

  @Override
  public void serviceInit(ServiceInitEvent event) {
    try {
      ServiceRegistry.initialize();
      logger().info("Application initialized successfully.");
    } catch (Exception e) {
      logger().error("Failed to initialize ServiceRegistry: {}", e.getMessage(), e);
      throw new RuntimeException("Application startup failed", e);
    }

    // Release all resources (Lucene lock, EclipseStore channels, thread pools) when the
    // Vaadin service is destroyed — covers both hot-reload and normal server shutdown.
    // Without this, a Jetty hot-reload attempt creates a second ServiceRegistry while
    // the first one still holds the Lucene NativeFSLock, causing OverlappingFileLockException.
    event.getSource().addServiceDestroyListener(e -> ServiceRegistry.shutdown());

    // Restore the user's chosen locale on every new UI creation (includes page reloads).
    event.getSource().addUIInitListener(uiEvent -> {
      VaadinSession session = uiEvent.getUI().getSession();
      Object stored = session.getAttribute("app.locale");
      if (stored instanceof Locale locale) {
        uiEvent.getUI().setLocale(locale);
      }
    });
  }
}
