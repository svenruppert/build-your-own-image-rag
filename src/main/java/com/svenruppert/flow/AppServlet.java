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
package com.svenruppert.flow;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.vaadin.flow.server.VaadinServlet;
import com.vaadin.flow.server.VaadinServletService;
import jakarta.servlet.ServletException;

/**
 * Vaadin servlet of the application.
 * <p>On initialization it registers a session error handler that logs every
 * unhandled UI error with its stack trace instead of printing it to stderr.
 * Locale selection is handled by the UI layer ({@code MainLayout}) and the
 * {@code TranslationProvider}, not by this servlet.
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
      e.getSession().setErrorHandler(
          err -> logger().error("unhandled session error", err.getThrowable()));
    });
  }
}
