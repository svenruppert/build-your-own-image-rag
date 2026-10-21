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
package com.svenruppert.flow.views.taxonomy;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.views.shared.Formats;
import com.svenruppert.flow.views.shared.ImagePreviewFactory;
import com.svenruppert.imagerag.domain.CategoryClusterSuggestion;
import com.svenruppert.imagerag.service.ClusterDiscoveryService;
import com.svenruppert.imagerag.service.ImageCatalogService;
import com.svenruppert.imagerag.service.PreviewService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.textfield.NumberField;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * "Cluster discovery" tab of the taxonomy maintenance view: configuration, a background
 * discovery run, the suggestion grid and the image/accept preview dialogs.
 */
class ClusterDiscoveryPanel
    extends VerticalLayout
    implements HasLogger {

  private static final int MAX_THUMBNAILS = 40;

  private final ClusterDiscoveryService clusterDiscoveryService;
  private final ImageCatalogService catalog;
  private final ImagePreviewFactory previewFactory;

  private final Grid<CategoryClusterSuggestion> clusterGrid = new Grid<>();
  private final ProgressBar clusterProgress = new ProgressBar();
  private final Span clusterStatus = new Span();
  private final NumberField maxClustersField = new NumberField();
  private final NumberField minSizeField = new NumberField();
  private final NumberField similarityThresholdField = new NumberField();

  private volatile boolean clusterRunning = false;

  ClusterDiscoveryPanel(ClusterDiscoveryService clusterDiscoveryService,
                        ImageCatalogService catalog,
                        ImagePreviewFactory previewFactory) {
    this.clusterDiscoveryService = clusterDiscoveryService;
    this.catalog = catalog;
    this.previewFactory = previewFactory;

    setSpacing(true);
    setPadding(false);
    setWidthFull();

    Paragraph desc = new Paragraph(getTranslation("cluster.description"));
    desc.getStyle().set("color", "var(--lumo-secondary-text-color)")
        .set("font-size", "var(--lumo-font-size-s)");
    add(desc);

    add(buildConfigRow());

    // Progress
    clusterProgress.setIndeterminate(true);
    clusterProgress.setVisible(false);
    clusterProgress.setWidthFull();
    clusterStatus.getStyle()
        .set("font-size", "var(--lumo-font-size-xs)")
        .set("color", "var(--lumo-secondary-text-color)");
    add(clusterProgress, clusterStatus);

    // Grid
    buildClusterGrid();
    clusterGrid.setWidthFull();
    clusterGrid.setHeight("380px");
    add(clusterGrid);

    // Populate from persisted suggestions on load
    refreshClusterGrid();
  }

  /**
   * Helper: adds a label → value row to the two-column stats grid.
   */
  private static void addClusterStatRow(Div grid, String label, String value) {
    Span l = new Span(label + ":");
    l.getStyle().set("color", "var(--lumo-secondary-text-color)").set("white-space", "nowrap");
    Span v = new Span(value);
    v.getStyle().set("color", "var(--lumo-body-text-color)");
    grid.add(l, v);
  }

  private HorizontalLayout buildConfigRow() {
    maxClustersField.setLabel(getTranslation("cluster.max.clusters"));
    maxClustersField.setValue(10.0);
    maxClustersField.setMin(2);
    maxClustersField.setMax(50);
    maxClustersField.setStep(1);
    maxClustersField.setStepButtonsVisible(true);
    maxClustersField.setWidth("140px");

    minSizeField.setLabel(getTranslation("cluster.min.size"));
    minSizeField.setValue(3.0);
    minSizeField.setMin(1);
    minSizeField.setMax(50);
    minSizeField.setStep(1);
    minSizeField.setStepButtonsVisible(true);
    minSizeField.setWidth("140px");

    similarityThresholdField.setLabel(getTranslation("cluster.threshold"));
    similarityThresholdField.setValue(0.6);
    similarityThresholdField.setMin(0.1);
    similarityThresholdField.setMax(1.0);
    similarityThresholdField.setStep(0.05);
    similarityThresholdField.setStepButtonsVisible(true);
    similarityThresholdField.setWidth("160px");

    Button runBtn = new Button(getTranslation("cluster.run.button"), e -> runClusterDiscovery());
    runBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    HorizontalLayout configRow = new HorizontalLayout(
        maxClustersField, minSizeField, similarityThresholdField, runBtn);
    configRow.setAlignItems(FlexComponent.Alignment.END);
    configRow.setSpacing(true);
    return configRow;
  }

  private void buildClusterGrid() {
    clusterGrid.addColumn(s -> s.getStatus() != null
            ? getTranslation("cluster.status." + s.getStatus().name().toLowerCase()) : "—")
        .setHeader(getTranslation("cluster.suggestion.status")).setAutoWidth(true);

    clusterGrid.addColumn(s -> s.getSuggestionType() != null
            ? s.getSuggestionType().getLabel() : "—")
        .setHeader(getTranslation("cluster.suggestion.type")).setAutoWidth(true);

    clusterGrid.addColumn(s -> s.getClusterLabel() != null
            ? s.getClusterLabel() : "—")
        .setHeader(getTranslation("cluster.suggestion.label")).setAutoWidth(true).setFlexGrow(1);

    clusterGrid.addColumn(s -> s.getCategoryDistribution() != null
            ? s.getCategoryDistribution().keySet().stream()
              .sorted()
              .collect(Collectors.joining(", "))
            : "—")
        .setHeader(getTranslation("cluster.suggestion.categories")).setAutoWidth(true);

    clusterGrid.addColumn(s -> String.valueOf(s.clusterSize()))
        .setHeader(getTranslation("cluster.suggestion.size")).setAutoWidth(true);

    clusterGrid.addColumn(s -> Formats.decimal(s.getIntraClusterSimilarity()))
        .setHeader(getTranslation("cluster.suggestion.similarity")).setAutoWidth(true);

    clusterGrid.addComponentColumn(s -> {
      HorizontalLayout actions = new HorizontalLayout();
      actions.setSpacing(false);
      actions.getStyle().set("gap", "0.3rem");

      // "View images" — always visible so the user can inspect cluster members
      Button viewBtn = new Button(getTranslation("cluster.view.images"),
                                  e -> openClusterImagesDialog(s));
      viewBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
      actions.add(viewBtn);

      if (s.getStatus() == CategoryClusterSuggestion.ClusterSuggestionStatus.OPEN) {
        // "Accept" now shows an impact preview before committing
        Button acceptBtn = new Button(getTranslation("cluster.accept"),
                                      e -> openClusterAcceptPreview(s));
        acceptBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_SUCCESS);

        Button dismissBtn = new Button(getTranslation("cluster.dismiss"), e -> {
          s.dismiss();
          clusterDiscoveryService.saveSuggestion(s);
          refreshClusterGrid();
          Notification.show(getTranslation("cluster.dismissed", s.getClusterLabel()),
                            2000, Notification.Position.BOTTOM_START);
        });
        dismissBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

        actions.add(acceptBtn, dismissBtn);
      }
      return actions;
    }).setHeader(getTranslation("taxonomy.col.actions")).setAutoWidth(true).setFlexGrow(0);
  }

  private void runClusterDiscovery() {
    if (clusterRunning) {
      Notification.show(getTranslation("cluster.running"), 2000, Notification.Position.MIDDLE);
      return;
    }
    clusterRunning = true;
    int maxClusters = (int) (maxClustersField.getValue() != null
        ? maxClustersField.getValue() : 10.0);
    int minSize = (int) (minSizeField.getValue() != null
        ? minSizeField.getValue() : 3.0);
    double threshold = similarityThresholdField.getValue() != null
        ? similarityThresholdField.getValue() : 0.6;

    UI ui = UI.getCurrent();
    ui.access(() -> {
      clusterProgress.setVisible(true);
      clusterStatus.setText(getTranslation("cluster.running"));
    });

    Thread.ofVirtual().name("cluster-discovery").start(() -> {
      try {
        List<CategoryClusterSuggestion> suggestions = clusterDiscoveryService.discover(
            maxClusters, minSize, threshold,
            msg -> ui.access(() -> clusterStatus.setText(msg)));

        // Persist newly discovered suggestions
        suggestions.forEach(clusterDiscoveryService::saveSuggestion);

        ui.access(() -> {
          clusterProgress.setVisible(false);
          clusterStatus.setText(getTranslation("cluster.done", suggestions.size()));
          Notification n = Notification.show(
              getTranslation("cluster.done", suggestions.size()),
              3000, Notification.Position.BOTTOM_START);
          n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
          refreshClusterGrid();
          clusterRunning = false;
        });
      } catch (RuntimeException ex) {
        // Worker-thread boundary: no Vaadin error handler runs here, so every failure is
        // logged here and shown to the user.
        logger().error("Cluster discovery failed: {}", ex.getMessage(), ex);
        ui.access(() -> {
          clusterProgress.setVisible(false);
          clusterStatus.setText(getTranslation("cluster.error", ex.getMessage()));
          TaxonomyNotifications.show(getTranslation("cluster.error", ex.getMessage()), true);
          clusterRunning = false;
        });
      }
    });
  }

  private void refreshClusterGrid() {
    List<CategoryClusterSuggestion> suggestions =
        clusterDiscoveryService.findAllSuggestions();
    clusterGrid.setItems(suggestions);
  }

  /**
   * Opens a dialog showing thumbnail previews of the images that belong to the cluster.
   * This lets reviewers judge whether the cluster is semantically coherent before
   * accepting or dismissing the suggestion.
   */
  private void openClusterImagesDialog(CategoryClusterSuggestion s) {
    Dialog dlg = new Dialog();
    String label = s.getClusterLabel() != null ? s.getClusterLabel() : "—";
    dlg.setHeaderTitle(getTranslation("cluster.images.title", label));
    dlg.setWidth("760px");
    dlg.setMaxHeight("82vh");

    VerticalLayout content = new VerticalLayout();
    content.setPadding(false);
    content.setSpacing(true);
    content.setWidthFull();

    // Cluster stats summary
    Paragraph info = new Paragraph(getTranslation(
        "cluster.images.info", s.clusterSize(),
        Formats.decimal(s.getIntraClusterSimilarity())));
    info.getStyle()
        .set("font-size", "var(--lumo-font-size-xs)")
        .set("color", "var(--lumo-secondary-text-color)")
        .set("margin", "0 0 0.5rem 0");
    content.add(info);

    // Thumbnail grid
    Div thumbGrid = new Div();
    thumbGrid.getStyle()
        .set("display", "grid")
        .set("grid-template-columns", "repeat(auto-fill, minmax(110px, 1fr))")
        .set("gap", "0.5rem");

    List<UUID> ids = s.getImageIds();
    // Cap the thumbnails to keep the dialog manageable
    List<UUID> shown = ids.size() > MAX_THUMBNAILS ? ids.subList(0, MAX_THUMBNAILS) : ids;
    for (UUID imgId : shown) {
      catalog.findImage(imgId).ifPresent(asset -> {
        Div cell = new Div();
        cell.getStyle()
            .set("border", "1px solid var(--lumo-contrast-10pct)")
            .set("border-radius", "var(--lumo-border-radius-s)")
            .set("overflow", "hidden")
            .set("aspect-ratio", "1")
            .set("background", "var(--lumo-contrast-5pct)")
            .set("display", "flex")
            .set("align-items", "center")
            .set("justify-content", "center");

        String altText = asset.getOriginalFilename() != null
            ? asset.getOriginalFilename() : imgId.toString();
        cell.add(previewFactory.image(
            asset,
            PreviewService.PreviewSize.TILE,
            "100%",
            "100%",
            "cover",
            altText));
        thumbGrid.add(cell);
      });
    }

    if (ids.size() > MAX_THUMBNAILS) {
      Paragraph overflow = new Paragraph(
          getTranslation("cluster.images.overflow", ids.size() - MAX_THUMBNAILS));
      overflow.getStyle()
          .set("font-size", "var(--lumo-font-size-xs)")
          .set("color", "var(--lumo-secondary-text-color)")
          .set("margin-top", "0.5rem");
      content.add(thumbGrid, overflow);
    } else {
      content.add(thumbGrid);
    }

    dlg.add(content);
    Button closeBtn = new Button(getTranslation("common.close"), e -> dlg.close());
    dlg.getFooter().add(closeBtn);
    dlg.open();
  }

  /**
   * Shows a dry-run / impact preview dialog before the user confirms acceptance of a
   * cluster suggestion.  Displays the cluster size, suggestion type, category distribution,
   * and rationale so the user can make an informed decision.
   */
  private void openClusterAcceptPreview(CategoryClusterSuggestion s) {
    Dialog dlg = new Dialog();
    dlg.setHeaderTitle(getTranslation("cluster.accept.preview.title"));
    dlg.setWidth("460px");

    VerticalLayout content = new VerticalLayout();
    content.setPadding(false);
    content.setSpacing(false);
    content.setWidthFull();

    // Impact stats grid
    Div stats = new Div();
    stats.getStyle()
        .set("display", "grid")
        .set("grid-template-columns", "auto 1fr")
        .set("gap", "0.25rem 0.9rem")
        .set("font-size", "var(--lumo-font-size-s)")
        .set("background", "var(--lumo-contrast-5pct)")
        .set("border-radius", "var(--lumo-border-radius-s)")
        .set("padding", "0.5rem 0.75rem")
        .set("margin-bottom", "0.5rem")
        .set("width", "100%");

    addClusterStatRow(stats, getTranslation("cluster.accept.preview.images"),
                      String.valueOf(s.clusterSize()));
    addClusterStatRow(stats, getTranslation("cluster.accept.preview.type"),
                      s.getSuggestionType() != null ? s.getSuggestionType().getLabel() : "—");
    addClusterStatRow(stats, getTranslation("cluster.accept.preview.dominant"),
                      s.getDominantCategory() != null ? s.getDominantCategory().name() : "—");
    addClusterStatRow(stats, getTranslation("cluster.accept.preview.similarity"),
                      Formats.decimal(s.getIntraClusterSimilarity()));

    if (!s.getCategoryDistribution().isEmpty()) {
      StringBuilder catStr = new StringBuilder();
      s.getCategoryDistribution().entrySet().stream()
          .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
          .forEach(entry -> {
            if (catStr.length() > 0) catStr.append(", ");
            catStr.append(entry.getKey()).append("×").append(entry.getValue());
          });
      addClusterStatRow(stats, getTranslation("cluster.accept.preview.categories"), catStr.toString());
    }
    content.add(stats);

    if (s.getRationale() != null && !s.getRationale().isBlank()) {
      Paragraph rationale = new Paragraph(s.getRationale());
      rationale.getStyle()
          .set("font-size", "var(--lumo-font-size-xs)")
          .set("color", "var(--lumo-secondary-text-color)")
          .set("margin", "0");
      content.add(rationale);
    }

    dlg.add(content);

    Button cancelBtn = new Button(getTranslation("common.cancel"), e -> dlg.close());
    Button confirmBtn = new Button(getTranslation("cluster.accept.confirm"));
    confirmBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
    confirmBtn.addClickListener(e -> {
      s.accept();
      clusterDiscoveryService.saveSuggestion(s);
      dlg.close();
      refreshClusterGrid();
      Notification n = Notification.show(
          getTranslation("cluster.accepted", s.getClusterLabel()),
          2000, Notification.Position.BOTTOM_START);
      n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    });
    dlg.getFooter().add(cancelBtn, confirmBtn);
    dlg.open();
  }
}
