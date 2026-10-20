package com.svenruppert.flow.views.overview;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.views.detail.DetailDialog;
import com.svenruppert.flow.views.shared.ViewServices;
import com.svenruppert.functional.result.Result;
import com.svenruppert.functional.result.Unit;
import com.svenruppert.imagerag.domain.ImageAsset;
import com.svenruppert.imagerag.domain.LocationSummary;
import com.svenruppert.imagerag.domain.SemanticAnalysis;
import com.svenruppert.imagerag.domain.SensitivityAssessment;
import com.svenruppert.imagerag.domain.enums.RiskLevel;
import com.svenruppert.imagerag.service.ImageCatalogService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;

/**
 * Per-image actions and widgets shared by the table and tile views of the overview:
 * visibility toggle, risk selector, visibility icon, detail dialog, archive / permanent
 * delete and reprocessing. State-changing actions trigger the reload callback.
 * <p>Translations are resolved through the owning view component.
 */
class OverviewImageActions
    implements HasLogger {

  // Audit and archive reasons are stored data: fixed English text, never UI-language dependent.
  private static final String REASON_SINGLE_ARCHIVE = "User archived from overview";
  private static final String REASON_MANUAL = "Manual change from overview";
  private static final String REASON_PERMANENT_DELETE = "Permanently deleted from overview";

  private final Component owner;
  private final ViewServices services;
  private final Runnable reload;

  /**
   * @param owner    component used to resolve translations
   * @param services view-facing services
   * @param reload   reloads the overview data after a state change
   */
  OverviewImageActions(Component owner, ViewServices services, Runnable reload) {
    this.owner = owner;
    this.services = services;
    this.reload = reload;
  }

  static void showNotification(String message, boolean success) {
    Notification n = Notification.show(message, 3000, Notification.Position.BOTTOM_END);
    n.addThemeVariants(success ? NotificationVariant.LUMO_SUCCESS : NotificationVariant.LUMO_CONTRAST);
  }

  private String tr(String key, Object... params) {
    return owner.getTranslation(key, params);
  }

  /**
   * Returns an eye/eye-slash icon indicating whether the image is approved for
   * search results, with a tooltip.
   */
  Component visibilityIcon(ImageAsset asset) {
    boolean approved = services.catalog().isApproved(asset.getId());
    Icon icon = approved ? VaadinIcon.EYE.create() : VaadinIcon.EYE_SLASH.create();
    icon.setSize("18px");
    icon.setColor(approved ? "var(--lumo-primary-color)" : "var(--lumo-contrast-50pct)");
    icon.getElement().setAttribute("title",
                                   tr(approved ? "overview.visibility.tooltip.approved"
                                          : "overview.visibility.tooltip.locked"));
    return icon;
  }

  Select<RiskLevel> riskSelect(ImageAsset asset) {
    ImageCatalogService ps = services.catalog();
    RiskLevel current = ps.findAssessment(asset.getId())
        .map(SensitivityAssessment::getRiskLevel).orElse(null);
    Select<RiskLevel> sel = new Select<>();
    sel.setItems(RiskLevel.values());
    sel.setPlaceholder("—");
    if (current != null) sel.setValue(current);
    sel.getStyle().set("font-size", "var(--lumo-font-size-s)");
    sel.addValueChangeListener(e -> {
      if (e.getValue() != null && e.getValue() != e.getOldValue()) {
        services.moderation().changeRiskLevel(asset, e.getOldValue(), e.getValue());
        showNotification(tr("overview.risk.updated", e.getValue().name()), true);
        reload.run();
      }
    });
    return sel;
  }

  HorizontalLayout approveButton(ImageAsset asset) {
    ImageCatalogService ps = services.catalog();
    boolean hasAssessment = ps.findAssessment(asset.getId()).isPresent();
    if (!hasAssessment) {
      Span pending = new Span(tr("overview.processing"));
      pending.getStyle().set("font-size", "var(--lumo-font-size-s)")
          .set("color", "var(--lumo-secondary-text-color)");
      return new HorizontalLayout(pending);
    }
    boolean approved = ps.isApproved(asset.getId());
    Button btn;
    if (approved) {
      btn = new Button(tr("overview.lock"), VaadinIcon.EYE_SLASH.create());
      btn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_CONTRAST, ButtonVariant.LUMO_TERTIARY);
    } else {
      btn = new Button(tr("overview.approve"), VaadinIcon.EYE.create());
      btn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_SUCCESS, ButtonVariant.LUMO_TERTIARY);
    }
    btn.addClickListener(e -> {
      if (approved) {
        services.moderation().lock(asset, REASON_MANUAL);
        showNotification(tr("overview.locked", asset.getOriginalFilename()), false);
      } else {
        services.moderation().approve(asset, REASON_MANUAL);
        showNotification(tr("overview.approved", asset.getOriginalFilename()), true);
      }
      reload.run();
    });
    return new HorizontalLayout(btn);
  }

  void openDetailDialog(ImageAsset asset) {
    ImageCatalogService ps = services.catalog();
    SemanticAnalysis analysis = ps.findAnalysis(asset.getId()).orElse(null);
    SensitivityAssessment assess = ps.findAssessment(asset.getId()).orElse(null);
    LocationSummary location = ps.findLocation(asset.getId()).orElse(null);
    new DetailDialog(asset, analysis, assess, location).open();
  }

  void confirmDelete(ImageAsset asset) {
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle(tr("overview.delete.title"));

    VerticalLayout body = new VerticalLayout();
    body.add(new Paragraph(tr("overview.delete.text", asset.getOriginalFilename())));

    Button archiveBtn = new Button(tr("overview.archive"), e -> {
      softDeleteImage(asset);
      dialog.close();
    });
    archiveBtn.addThemeVariants(ButtonVariant.LUMO_CONTRAST, ButtonVariant.LUMO_PRIMARY);

    Button hardDeleteBtn = new Button(tr("overview.delete.hard"), e -> {
      hardDeleteImage(asset);
      dialog.close();
    });
    hardDeleteBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_PRIMARY);

    Button cancelBtn = new Button(tr("overview.delete.cancel"), e -> dialog.close());
    cancelBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

    dialog.getFooter().add(cancelBtn, archiveBtn, hardDeleteBtn);
    dialog.add(body);
    dialog.open();
  }

  private void softDeleteImage(ImageAsset asset) {
    services.moderation().archive(asset, REASON_SINGLE_ARCHIVE);
    showNotification(tr("overview.archived", asset.getOriginalFilename()), false);
    reload.run();
  }

  private void hardDeleteImage(ImageAsset asset) {
    switch (services.deleteImage(asset.getId(), REASON_PERMANENT_DELETE)) {
      case Result.Success<Unit, String> done -> {
        showNotification(tr("overview.deleted", asset.getOriginalFilename()), false);
        reload.run();
      }
      case Result.Failure<Unit, String>(String error) -> {
        Notification n = Notification.show(
            tr("overview.delete.error", error), 5000, Notification.Position.MIDDLE);
        n.addThemeVariants(NotificationVariant.LUMO_ERROR);
      }
    }
  }

  void startReprocess(ImageAsset asset) {
    switch (services.reprocessing().reprocess(asset.getId())) {
      case Result.Success<Unit, String> done ->
          showNotification(tr("overview.reprocess.started", asset.getOriginalFilename()),
                           true);
      case Result.Failure<Unit, String>(String error) -> {
        logger().error("Failed to queue reprocessing for {}: {}", asset.getId(), error);
        Notification n = Notification.show(
            tr("overview.reprocess.error", error),
            5000, Notification.Position.MIDDLE);
        n.addThemeVariants(NotificationVariant.LUMO_ERROR);
      }
    }
  }
}
