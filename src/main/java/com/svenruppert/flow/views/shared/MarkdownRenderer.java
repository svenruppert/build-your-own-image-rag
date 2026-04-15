package com.svenruppert.flow.views.shared;

import com.vaadin.flow.component.html.Div;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

/**
 * Converts CommonMark Markdown text to safe HTML for display inside Vaadin components.
 *
 * <p>Raw HTML blocks inside the Markdown source are escaped (not passed through),
 * preventing XSS if the LLM-generated description contains HTML fragments.
 * Standard Markdown elements — headings, bold, italic, lists, code blocks,
 * blockquotes, paragraphs — are rendered normally.
 *
 * <p>This class is intentionally non-instantiable; use the static factory methods.
 */
public final class MarkdownRenderer {

  private static final Parser PARSER = Parser.builder().build();

  /**
   * Renders Markdown to safe HTML.  Raw HTML in the source is escaped rather than
   * passed through, providing XSS protection for LLM-generated content.
   */
  private static final HtmlRenderer RENDERER = HtmlRenderer.builder()
      .escapeHtml(true)
      .build();

  private MarkdownRenderer() {
  }

  /**
   * Converts Markdown text to a safe HTML string.
   *
   * @param markdown the Markdown-formatted input (may be {@code null} or blank)
   * @return rendered HTML, or an empty string when the input is null/blank
   */
  public static String toSafeHtml(String markdown) {
    if (markdown == null || markdown.isBlank()) return "";
    Node document = PARSER.parse(markdown);
    return RENDERER.render(document);
  }

  /**
   * Converts Markdown text and wraps the result in a styled {@link Div} component.
   *
   * <p>The HTML is injected via {@code innerHTML}, so standard Markdown structure
   * (paragraphs, headings, bullet lists, bold/italic) renders correctly inside
   * the Vaadin component tree.
   *
   * @param markdown the Markdown-formatted input (may be {@code null} or blank)
   * @return a {@code Div} containing the rendered HTML
   */
  public static Div render(String markdown) {
    Div div = new Div();
    div.getElement().setProperty("innerHTML", toSafeHtml(markdown));
    div.getStyle()
        .set("font-size", "var(--lumo-font-size-s)")
        .set("line-height", "1.6")
        .set("max-width", "100%")
        .set("overflow-wrap", "break-word");
    return div;
  }
}
