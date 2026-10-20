package com.svenruppert.flow.views.taxonomy;

import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;

/**
 * Success/error notification shared by the components of the taxonomy maintenance view.
 */
final class TaxonomyNotifications {

  private TaxonomyNotifications() {
  }

  static void show(String message, boolean error) {
    Notification n = Notification.show(message, 4000, Notification.Position.BOTTOM_START);
    n.addThemeVariants(error ? NotificationVariant.LUMO_ERROR : NotificationVariant.LUMO_SUCCESS);
  }
}
