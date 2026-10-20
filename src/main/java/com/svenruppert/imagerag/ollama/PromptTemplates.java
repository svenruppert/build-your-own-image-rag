package com.svenruppert.imagerag.ollama;

/**
 * Safe placeholder substitution for prompt templates.
 * <p>Prompt templates can be edited by users in the Migration Center. Formatting them with
 * {@link String#formatted} would throw on any literal {@code %} (for example "50% of the
 * image") or on a missing argument. This helper only replaces the {@value #PLACEHOLDER}
 * placeholder, treats {@code %%} as an escaped percent sign (compatible with templates
 * written for {@code String.format}) and leaves every other character untouched.
 */
public final class PromptTemplates {

  public static final String PLACEHOLDER = "%s";

  private PromptTemplates() {
  }

  /**
   * Replaces the placeholders in order of appearance with {@code values}.
   * Placeholders without a matching value are replaced with an empty string;
   * surplus values are ignored.
   */
  public static String fill(String template, String... values) {
    StringBuilder out = new StringBuilder(template.length() + 64);
    int next = 0;
    for (int i = 0; i < template.length(); i++) {
      char c = template.charAt(i);
      if (c == '%' && i + 1 < template.length()) {
        char following = template.charAt(i + 1);
        if (following == 's') {
          out.append(next < values.length && values[next] != null ? values[next] : "");
          next++;
          i++;
          continue;
        }
        if (following == '%') {
          out.append('%');
          i++;
          continue;
        }
      }
      out.append(c);
    }
    return out.toString();
  }

  /**
   * Counts the {@value #PLACEHOLDER} placeholders, ignoring escaped {@code %%}.
   */
  public static int countPlaceholders(String template) {
    int count = 0;
    for (int i = 0; i + 1 < template.length(); i++) {
      if (template.charAt(i) == '%') {
        char following = template.charAt(i + 1);
        if (following == 's') {
          count++;
        }
        if (following == 's' || following == '%') {
          i++;
        }
      }
    }
    return count;
  }
}
