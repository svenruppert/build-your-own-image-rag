package com.svenruppert.imagerag.bootstrap;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;

/**
 * Vaadin hook that initializes the ServiceRegistry when the application starts.
 * Registered via META-INF/services/com.vaadin.flow.server.VaadinServiceInitListener.
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
  }
}
