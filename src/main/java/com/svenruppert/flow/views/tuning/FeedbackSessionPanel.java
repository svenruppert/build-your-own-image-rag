package com.svenruppert.flow.views.tuning;

import com.svenruppert.imagerag.domain.enums.FeedbackType;
import com.svenruppert.imagerag.dto.FeedbackEntry;
import com.svenruppert.imagerag.dto.FeedbackSession;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Compact panel that displays the active feedback entries in the current
 * Search Tuning Lab session, grouped by feedback type.
 * <p>Instances live inside {@link SearchTuningView} and are refreshed via
 * {@link #refresh(FeedbackSession)} after every feedback mark/remove action.
 * <p>The panel is intended to be placed inside a collapsible {@code Details}
 * component in the right workbench panel.
 */
public class FeedbackSessionPanel
    extends VerticalLayout {

  private final Consumer<UUID> onRemove;

  private final Div content = new Div();

  /**
   * @param onRemove callback invoked (on the UI thread) when the user clicks
   *                 the remove button for a feedback entry
   */
  public FeedbackSessionPanel(Consumer<UUID> onRemove) {
    this.onRemove = onRemove;

    setPadding(false);
    setSpacing(false);
    setWidthFull();

    content.setWidthFull();
    add(content);
  }

  // ── Public API ────────────────────────────────────────────────────────────

  /**
   * Refreshes the panel to reflect the current state of {@code session}.
   * Must be called on the Vaadin UI thread.
   */
  public void refresh(FeedbackSession session) {
    content.removeAll();

    if (session == null || session.isEmpty()) {
      Span empty = new Span(getTranslation("tuning.feedback.empty"));
      empty.getStyle()
          .set("font-size", "var(--lumo-font-size-xs)")
          .set("color", "var(--lumo-disabled-text-color)")
          .set("display", "block")
          .set("padding", "0.25rem 0");
      content.add(empty);
      return;
    }

    // Render each feedback type group in order
    for (FeedbackType type : FeedbackType.values()) {
      List<FeedbackEntry> group = session.getByType(type);
      if (group.isEmpty()) continue;

      // Group heading
      Span heading = new Span(type.getIcon() + " " + type.getLabel());
      heading.getStyle()
          .set("font-size", "var(--lumo-font-size-xs)")
          .set("font-weight", "700")
          .set("color", type.getColor())
          .set("display", "block")
          .set("margin-top", "0.4rem")
          .set("margin-bottom", "0.1rem");
      content.add(heading);

      for (FeedbackEntry entry : group) {
        content.add(buildEntryRow(entry, type));
      }
    }
  }

  // ── Private helpers ───────────────────────────────────────────────────────

  private HorizontalLayout buildEntryRow(FeedbackEntry entry, FeedbackType type) {
    // Colour badge on the left
    Span badge = new Span(type.getIcon());
    badge.getStyle()
        .set("background", type.getBackground())
        .set("color", type.getColor())
        .set("font-size", "10px")
        .set("font-weight", "700")
        .set("padding", "1px 4px")
        .set("border-radius", "var(--lumo-border-radius-s)")
        .set("flex-shrink", "0");

    // Truncated label
    Span label = new Span(entry.displayLabel());
    label.getStyle()
        .set("font-size", "var(--lumo-font-size-xs)")
        .set("flex", "1")
        .set("overflow", "hidden")
        .set("text-overflow", "ellipsis")
        .set("white-space", "nowrap")
        .set("color", "var(--lumo-body-text-color)");
    label.getElement().setAttribute("title", entry.label());  // full name on hover

    // Remove button
    Button removeBtn = new Button(VaadinIcon.CLOSE_SMALL.create());
    removeBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_SMALL);
    removeBtn.getStyle().set("flex-shrink", "0").set("color", "var(--lumo-error-color)");
    removeBtn.addClickListener(e -> onRemove.accept(entry.imageId()));

    HorizontalLayout row = new HorizontalLayout(badge, label, removeBtn);
    row.setWidthFull();
    row.setAlignItems(Alignment.CENTER);
    row.setSpacing(false);
    row.getStyle()
        .set("gap", "0.35rem")
        .set("padding", "0.15rem 0");
    return row;
  }
}
