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
package com.svenruppert.flow.views.overview;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.views.detail.ComparisonDialog;
import com.svenruppert.flow.views.shared.ViewServices;
import com.svenruppert.imagerag.domain.ImageAsset;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static com.svenruppert.flow.views.overview.OverviewImageActions.showNotification;

/**
 * Batch action toolbar of the overview, visible while grid rows are selected:
 * approve, lock, archive, reprocess and — for exactly two images — compare.
 */
class OverviewBatchToolbar
    extends HorizontalLayout
    implements HasLogger {

  // Audit and archive reasons are stored data: fixed English text, never UI-language dependent.
  private static final String REASON_BATCH = "Batch action from overview";
  private static final String REASON_BATCH_ARCHIVE = "Batch archive from overview";

  private final ViewServices services;
  private final Supplier<Set<ImageAsset>> selection;
  private final Runnable reload;
  private final Span selectedCountLabel = new Span();
  private final Button compareBtn = new Button();

  /**
   * @param services  view-facing services
   * @param selection supplies the currently selected images
   * @param reload    reloads the overview data after a state change
   */
  OverviewBatchToolbar(ViewServices services, Supplier<Set<ImageAsset>> selection,
                       Runnable reload) {
    this.services = services;
    this.selection = selection;
    this.reload = reload;

    selectedCountLabel.getStyle().set("font-size", "var(--lumo-font-size-s)")
        .set("align-self", "flex-end");

    Button approveAllBtn = new Button(getTranslation("overview.batch.approve"), e -> batchApprove());
    approveAllBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_SUCCESS,
                                   ButtonVariant.LUMO_TERTIARY);

    Button lockAllBtn = new Button(getTranslation("overview.batch.lock"), e -> batchLock());
    lockAllBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_CONTRAST,
                                ButtonVariant.LUMO_TERTIARY);

    Button archiveAllBtn = new Button(getTranslation("overview.batch.delete"), e -> batchArchive());
    archiveAllBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR,
                                   ButtonVariant.LUMO_TERTIARY);

    Button reprocessAllBtn = new Button(getTranslation("overview.batch.reprocess"), e -> batchReprocess());
    reprocessAllBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

    // Compare button — only enabled when exactly 2 images are selected
    compareBtn.setText(getTranslation("overview.compare"));
    compareBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
    compareBtn.setVisible(false); // shown only with exactly-2 selection
    compareBtn.addClickListener(e -> openComparisonDialog());

    add(selectedCountLabel, approveAllBtn, lockAllBtn, archiveAllBtn,
        reprocessAllBtn, compareBtn);
    setAlignItems(Alignment.CENTER);
    setSpacing(true);
    setVisible(false);
  }

  /**
   * Reflects a new selection size: toolbar visibility, count label and compare button.
   */
  void onSelectionChanged(int count) {
    setVisible(count > 0);
    selectedCountLabel.setText(getTranslation("overview.selected.count", count));
    // Compare button is only useful when exactly 2 images are selected
    compareBtn.setVisible(count == 2);
  }

  private void batchApprove() {
    Set<ImageAsset> selected = selection.get();
    selected.forEach(a -> services.moderation().approve(a, REASON_BATCH));
    showNotification(getTranslation("overview.batch.approved", selected.size()), true);
    reload.run();
  }

  private void batchLock() {
    Set<ImageAsset> selected = selection.get();
    selected.forEach(a -> services.moderation().lock(a, REASON_BATCH));
    showNotification(getTranslation("overview.batch.locked", selected.size()), false);
    reload.run();
  }

  private void batchArchive() {
    Set<ImageAsset> selected = selection.get();
    selected.forEach(a -> services.moderation().archive(a, REASON_BATCH_ARCHIVE));
    showNotification(getTranslation("overview.batch.archived", selected.size()), false);
    reload.run();
  }

  private void batchReprocess() {
    Set<ImageAsset> selected = selection.get();
    int count = 0;
    for (ImageAsset asset : selected) {
      if (services.reprocessing().reprocess(asset.getId())
          .peekFailure(error -> logger().warn("Failed to reprocess {}: {}", asset.getId(), error))
          .isSuccess()) {
        count++;
      }
    }
    showNotification(getTranslation("overview.batch.reprocess.started", count), true);
  }

  /**
   * Opens a side-by-side {@link ComparisonDialog} for the exactly two currently selected images.
   */
  private void openComparisonDialog() {
    Set<ImageAsset> selected = selection.get();
    if (selected.size() != 2) return; // guard — button should only appear with exactly 2
    List<ImageAsset> pair = new ArrayList<>(selected);
    new ComparisonDialog(pair.get(0), pair.get(1)).open();
  }
}
