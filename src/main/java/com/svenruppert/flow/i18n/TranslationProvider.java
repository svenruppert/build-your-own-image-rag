package com.svenruppert.flow.i18n;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.vaadin.flow.i18n.I18NProvider;

import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Vaadin {@link I18NProvider} backed by {@code translations.properties} files
 * on the classpath. English is the default/fallback locale.
 * <p>Files:
 * <ul>
 *   <li>{@code translations.properties} — English (default)</li>
 *   <li>{@code translations_de.properties} — German</li>
 * </ul>
 * <p>Registered through the servlet init parameter {@code i18n.provider} in
 * {@code WEB-INF/web.xml}; Vaadin 25 ignores a {@code META-INF/services} entry for
 * {@code I18NProvider}.
 */
public class TranslationProvider
    implements I18NProvider, HasLogger {

  private static final String BUNDLE = "vaadin-i18n/translations";

  /**
   * Without this control, {@code getBundle(BUNDLE, Locale.ENGLISH)} falls back to the JVM
   * default locale before the base bundle — on a German JVM, English then shows German texts.
   */
  private static final ResourceBundle.Control NO_FALLBACK =
      ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);

  @Override
  public List<Locale> getProvidedLocales() {
    return List.of(Locale.ENGLISH, Locale.GERMAN);
  }

  @Override
  public String getTranslation(String key, Locale locale, Object... params) {
    if (key == null) return "";
    Locale effective = (locale != null) ? locale : Locale.ENGLISH;
    try {
      ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE, effective, NO_FALLBACK);
      return format(bundle.getString(key), effective, params);
    } catch (MissingResourceException primary) {
      // If the key is missing for a non-English locale, fall back to English.
      // This prevents !key! strings from leaking into the UI when a new translation
      // key exists in English but has not yet been added to the DE bundle.
      if (!Locale.ENGLISH.getLanguage().equals(effective.getLanguage())) {
        try {
          ResourceBundle englishBundle = ResourceBundle.getBundle(BUNDLE, Locale.ENGLISH, NO_FALLBACK);
          String value = englishBundle.getString(key);
          logger().debug("Missing i18n key '{}' for locale {} — using English fallback",
                         key, effective);
          return format(value, effective, params);
        } catch (MissingResourceException ignored) {
          // Not found in English either — fall through to the warning below.
        }
      }
      logger().warn("Missing i18n key '{}' for locale {} (no English fallback found)",
                    key, effective);
      return "!" + key + "!";
    }
  }

  /**
   * Applies MessageFormat parameters with the requested locale, so numbers use the
   * decimal separator of the UI language rather than that of the JVM default locale.
   */
  private static String format(String pattern, Locale locale, Object... params) {
    if (params == null || params.length == 0) {
      return pattern;
    }
    return new MessageFormat(pattern, locale).format(params);
  }
}
