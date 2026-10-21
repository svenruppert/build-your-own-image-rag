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
package com.svenruppert.flow.views.tuning;

import com.svenruppert.flow.views.shared.ImagePreviewFactory;
import com.svenruppert.imagerag.domain.CategoryRegistry;
import com.svenruppert.imagerag.domain.ImageAsset;
import com.svenruppert.imagerag.domain.SearchResultItem;
import com.svenruppert.imagerag.domain.enums.FeedbackType;
import com.svenruppert.imagerag.domain.enums.SeasonHint;
import com.svenruppert.imagerag.dto.FeedbackSession;
import com.svenruppert.imagerag.dto.ScoreBreakdown;
import com.svenruppert.imagerag.dto.TuningSearchResult;
import com.svenruppert.imagerag.service.ImageCatalogService;
import com.svenruppert.imagerag.service.PreviewService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static com.svenruppert.flow.views.tuning.TuningStyles.tag;

/**
 * Builds the result cards of the Search Tuning Lab: thumbnail, rank and rank delta,
 * tags, score breakdown, summary and the per-result actions (feedback, query by
 * example, why-not-found).
 */
class TuningResultCards {

  private final Component owner;
  private final ImageCatalogService catalog;
  private final ImagePreviewFactory previewFactory;
  private final FeedbackSession feedbackSession;
  private final BiConsumer<SearchResultItem, FeedbackType> onFeedback;
  private final Consumer<SearchResultItem> onUseAsExample;
  private final Consumer<SearchResultItem> onWhyNotFound;

  /**
   * @param owner           component used to resolve translations
   * @param feedbackSession live feedback session, used for the feedback tag of a card
   * @param onFeedback      marks a result with a feedback type
   * @param onUseAsExample  sets a result as query-by-example anchor
   * @param onWhyNotFound   opens the why-not-found diagnosis for a result
   */
  TuningResultCards(Component owner,
                    ImageCatalogService catalog,
                    ImagePreviewFactory previewFactory,
                    FeedbackSession feedbackSession,
                    BiConsumer<SearchResultItem, FeedbackType> onFeedback,
                    Consumer<SearchResultItem> onUseAsExample,
                    Consumer<SearchResultItem> onWhyNotFound) {
    this.owner = owner;
    this.catalog = catalog;
    this.previewFactory = previewFactory;
    this.feedbackSession = feedbackSession;
    this.onFeedback = onFeedback;
    this.onUseAsExample = onUseAsExample;
    this.onWhyNotFound = onWhyNotFound;
  }

  static String displayTitle(SearchResultItem item) {
    return item.getTitle() != null ? item.getTitle() : item.getImageId().toString();
  }

  Component card(int rank, TuningSearchResult tsr, int delta) {
    SearchResultItem item = tsr.item();

    Div thumbWrap = new Div(buildThumbnail(item));
    thumbWrap.getStyle()
        .set("width", "160px").set("min-width", "160px").set("height", "120px")
        .set("flex-shrink", "0").set("overflow", "hidden")
        .set("background", "var(--lumo-contrast-5pct)")
        .set("display", "flex").set("align-items", "center").set("justify-content", "center");

    Div info = new Div();
    info.getStyle()
        .set("flex", "1").set("padding", "0.6rem 0.75rem")
        .set("min-width", "0").set("overflow", "hidden")
        .set("display", "flex").set("flex-direction", "column").set("gap", "0.3rem");

    Span rankBadge = new Span("#" + rank);
    rankBadge.getStyle().set("font-weight", "700")
        .set("color", "var(--lumo-primary-color)").set("flex-shrink", "0");

    Span filename = new Span(displayTitle(item));
    filename.getStyle()
        .set("font-weight", "600").set("font-size", "var(--lumo-font-size-s)")
        .set("flex", "1").set("overflow", "hidden")
        .set("text-overflow", "ellipsis").set("white-space", "nowrap");

    HorizontalLayout header = new HorizontalLayout(rankBadge, filename, buildDeltaBadge(delta));
    header.setWidthFull();
    header.setAlignItems(FlexComponent.Alignment.CENTER);
    header.setSpacing(false);
    header.getStyle().set("gap", "0.4rem");

    info.add(header, buildTags(tsr), buildScoreBreakdown(tsr.breakdown(), tsr));

    if (item.getSummary() != null && !item.getSummary().isBlank()) {
      String txt = item.getSummary();
      if (txt.length() > 140) txt = txt.substring(0, 137) + "…";
      Div summary = new Div();
      summary.setText(txt);
      summary.getStyle()
          .set("font-size", "var(--lumo-font-size-xs)")
          .set("color", "var(--lumo-secondary-text-color)");
      info.add(summary);
    }

    info.add(buildActionRow(item));

    Div card = new Div();
    card.getStyle()
        .set("display", "flex").set("align-items", "stretch")
        .set("border", "1px solid var(--lumo-contrast-10pct)")
        .set("border-radius", "var(--lumo-border-radius-m)")
        .set("margin-bottom", "0.5rem")
        .set("background", "var(--lumo-base-color)")
        .set("width", "100%").set("overflow", "hidden");
    card.add(thumbWrap, info);
    return card;
  }

  private HorizontalLayout buildTags(TuningSearchResult tsr) {
    SearchResultItem item = tsr.item();
    HorizontalLayout tags = new HorizontalLayout();
    tags.setSpacing(false);
    tags.getStyle().set("gap", "0.3rem").set("flex-wrap", "wrap");

    if (item.getSourceCategory() != null) {
      tags.add(tag(CategoryRegistry.getUserLabel(item.getSourceCategory()),
                   "var(--lumo-primary-color-10pct)", "var(--lumo-primary-color)"));
    }
    if (item.getSeasonHint() != null && item.getSeasonHint() != SeasonHint.UNKNOWN) {
      tags.add(tag(item.getSeasonHint().name(),
                   "var(--lumo-contrast-5pct)", "var(--lumo-contrast-60pct)"));
    }
    if (Boolean.TRUE.equals(item.getContainsPerson())) {
      tags.add(tag(t("tuning.tag.person"),
                   "var(--lumo-contrast-5pct)", "var(--lumo-contrast-60pct)"));
    }
    if (tsr.isInBothChannels()) {
      tags.add(tag(t("tuning.tag.hybrid"),
                   "var(--lumo-success-color-10pct)", "var(--lumo-success-color)"));
    } else if (tsr.isInVectorResults()) {
      tags.add(tag(t("tuning.tag.semantic"),
                   "var(--lumo-primary-color-10pct)", "var(--lumo-primary-color)"));
    } else if (tsr.isInBm25Results()) {
      tags.add(tag(t("tuning.tag.bm25"),
                   "var(--lumo-contrast-5pct)", "var(--lumo-contrast-70pct)"));
    }
    feedbackSession.getType(item.getImageId()).ifPresent(ft ->
        tags.add(tag(ft.getIcon() + " " + feedbackLabel(ft), ft.getBackground(), ft.getColor())));
    return tags;
  }

  /**
   * Per-result action row: feedback buttons, query-by-example and why-not-found.
   */
  private Component buildActionRow(SearchResultItem item) {
    HorizontalLayout row = new HorizontalLayout();
    row.setSpacing(false);
    row.getStyle().set("gap", "0.3rem").set("flex-wrap", "wrap").set("margin-top", "0.1rem");

    for (FeedbackType ft : FeedbackType.values()) {
      Button btn = new Button(ft.getIcon() + " " + feedbackLabel(ft));
      btn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
      btn.getStyle().set("font-size", "var(--lumo-font-size-xs)");
      btn.addClickListener(e -> onFeedback.accept(item, ft));
      row.add(btn);
    }

    Button qbeBtn = new Button(t("tuning.qbe.use"));
    qbeBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
    qbeBtn.getStyle().set("font-size", "var(--lumo-font-size-xs)");
    qbeBtn.addClickListener(e -> onUseAsExample.accept(item));

    Button wnfBtn = new Button(t("tuning.wnf.button"));
    wnfBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
    wnfBtn.getStyle().set("font-size", "var(--lumo-font-size-xs)");
    wnfBtn.addClickListener(e -> onWhyNotFound.accept(item));

    row.add(divider(), qbeBtn, divider(), wnfBtn);
    return row;
  }

  private static Span divider() {
    Span divider = new Span("|");
    divider.getStyle().set("color", "var(--lumo-contrast-30pct)").set("padding", "0 0.2rem");
    return divider;
  }

  private Component buildThumbnail(SearchResultItem item) {
    ImageAsset asset = catalog.findImage(item.getImageId()).orElse(null);
    if (asset == null) {
      Span ph = new Span("—");
      ph.getStyle().set("font-size", "1.4rem").set("color", "var(--lumo-contrast-30pct)");
      return ph;
    }
    return previewFactory.image(
        asset,
        PreviewService.PreviewSize.TILE,
        "100%",
        "100%",
        "cover",
        item.getTitle() != null ? item.getTitle() : t("tuning.image.alt"));
  }

  private Div buildScoreBreakdown(ScoreBreakdown bd, TuningSearchResult tsr) {
    Div container = new Div();
    container.getStyle()
        .set("background", "var(--lumo-contrast-5pct)")
        .set("border-radius", "var(--lumo-border-radius-s)")
        .set("padding", "0.35rem 0.5rem");

    Div barWrap = new Div();
    barWrap.getStyle()
        .set("display", "flex").set("height", "6px")
        .set("border-radius", "3px").set("overflow", "hidden")
        .set("background", "var(--lumo-contrast-10pct)")
        .set("margin-bottom", "0.3rem").set("width", "100%");

    addBarSegment(barWrap, Math.round(bd.semanticFraction() * 80), "var(--lumo-primary-color)");
    addBarSegment(barWrap, Math.round(bd.bm25Fraction() * 80), "var(--lumo-success-color)");
    if (bd.hasFeedback()) {
      double base = Math.max(bd.scoreBeforeBoost(), 0.001);
      double fbWidth = Math.round(Math.min(Math.abs(bd.feedbackContrib()) / base, 0.15) * 80);
      addBarSegment(barWrap, fbWidth,
                    bd.feedbackContrib() > 0 ? "#f59e0b" : "var(--lumo-error-color)");
    }

    // Numeric breakdown — intentionally kept as compact technical notation
    StringBuilder nums = new StringBuilder();
    nums.append(String.format("Score %.3f", bd.finalScore()));
    if (bd.semanticContrib() > 0) nums.append(String.format("  │  Sem %.3f", bd.semanticContrib()));
    if (bd.bm25Contrib() > 0) nums.append(String.format("  │  BM25 %.3f", bd.bm25Contrib()));
    if (bd.confidenceBoost() > 0.001)
      nums.append(String.format("  │  Conf +%.0f%%", bd.confidenceBoost() * 100));
    if (bd.hasFeedback()) nums.append(String.format("  │  Fb %+.3f", bd.feedbackContrib()));
    if (tsr.vectorRank() >= 0) nums.append(String.format("  │  vRk %d", tsr.vectorRank() + 1));
    if (tsr.bm25Rank() >= 0) nums.append(String.format("  │  kRk %d", tsr.bm25Rank() + 1));

    Span numsSpan = new Span(nums.toString());
    numsSpan.getStyle()
        .set("font-size", "var(--lumo-font-size-xs)")
        .set("color", "var(--lumo-secondary-text-color)")
        .set("font-family", "monospace");

    container.add(barWrap, numsSpan);
    return container;
  }

  private static void addBarSegment(Div barWrap, double widthPercent, String color) {
    if (widthPercent > 0) {
      Div seg = new Div();
      seg.getStyle().set("width", widthPercent + "%")
          .set("background", color).set("flex-shrink", "0");
      barWrap.add(seg);
    }
  }

  private Span buildDeltaBadge(int delta) {
    Span badge = new Span();
    badge.getStyle()
        .set("font-size", "var(--lumo-font-size-xs)").set("font-weight", "700")
        .set("padding", "1px 5px")
        .set("border-radius", "var(--lumo-border-radius-s)").set("flex-shrink", "0");

    if (delta == Integer.MAX_VALUE) {
      badge.setText(t("tuning.rank.new"));
      badge.getStyle().set("background", "var(--lumo-primary-color-10pct)")
          .set("color", "var(--lumo-primary-color)");
    } else if (delta == Integer.MIN_VALUE || delta == 0) {
      badge.setText(delta == 0 ? "=" : "—");
      badge.getStyle().set("background", "var(--lumo-contrast-5pct)")
          .set("color", "var(--lumo-contrast-60pct)");
    } else if (delta > 0) {
      badge.setText("▲" + delta);
      badge.getStyle().set("background", "var(--lumo-success-color-10pct)")
          .set("color", "var(--lumo-success-color)");
    } else {
      badge.setText("▼" + Math.abs(delta));
      badge.getStyle().set("background", "var(--lumo-error-color-10pct)")
          .set("color", "var(--lumo-error-color)");
    }
    return badge;
  }

  String feedbackLabel(FeedbackType type) {
    return t("tuning.feedback.type." + type.name().toLowerCase());
  }

  private String t(String key, Object... params) {
    return owner.getTranslation(key, params);
  }
}
