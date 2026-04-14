package com.svenruppert.flow;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.vaadin.flow.server.VaadinServlet;
import com.vaadin.flow.server.VaadinServletService;
import jakarta.servlet.ServletException;

/**
 * Vaadin servlet bootstrap for the application.
 * Responsibilities:
 * - Use the browser locale as default (Accept-Language -> UI locale).
 * - Constrain to supported locales (DE/EN/FI).
 * - Allow session-scoped language selection (overrides browser locale).
 */
public class AppServlet
    extends VaadinServlet
    implements HasLogger {

  @Override
  protected void servletInitialized()
      throws ServletException {
    super.servletInitialized();
    logger().info("servletInitialized .. started");
    VaadinServletService service = getService();
    service.addSessionInitListener(e -> {
      e.getSession().setErrorHandler(err -> err.getThrowable().printStackTrace());
    });
    //service.addSessionInitListener();
  }


}
