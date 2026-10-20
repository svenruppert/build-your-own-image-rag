package com.svenruppert.imagerag.service.impl;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Whole-word keyword heuristics over free-text image descriptions.
 * <p>Plain {@code contains} checks produce false positives ("man" in "many" or "German");
 * these patterns only match complete words or phrases, case-insensitively.
 */
final class DescriptionKeywords {

  static final Pattern PERSON = words(
      "person", "persons", "people", "man", "men", "woman", "women", "boy", "girl",
      "child", "children", "kid", "kids", "crowd", "pedestrian", "pedestrians");
  static final Pattern CHILD = words(
      "child", "children", "kid", "kids", "baby", "babies", "toddler", "toddlers",
      "infant", "infants", "boy", "boys", "girl", "girls", "schoolchild", "schoolchildren",
      "kleinkind", "säugling");
  static final Pattern PRIVATE_PROPERTY = words(
      "license plate", "licence plate", "number plate", "house number", "doorbell",
      "door bell", "mailbox", "letterbox", "letter box", "nameplate", "name plate",
      "front door", "private property", "garden gate");
  static final Pattern VEHICLE = words("car", "cars", "vehicle", "vehicles", "truck", "trucks",
                                       "bus", "buses", "van", "motorcycle", "bicycle");
  static final Pattern READABLE_TEXT = words("sign", "signs", "signage", "text", "inscription",
                                             "label", "labels", "lettering", "caption");
  static final Pattern LICENSE_PLATE = words("license plate", "licence plate", "number plate");

  private DescriptionKeywords() {
  }

  static boolean matches(Pattern pattern, String text) {
    return text != null && pattern.matcher(text).find();
  }

  private static Pattern words(String... words) {
    String alternatives = String.join("|", List.of(words).stream()
        .map(w -> Pattern.quote(w.toLowerCase(Locale.ROOT)))
        .toList());
    return Pattern.compile("(?<![\\p{L}\\p{N}])(?:" + alternatives + ")(?![\\p{L}\\p{N}])",
                           Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  }
}
