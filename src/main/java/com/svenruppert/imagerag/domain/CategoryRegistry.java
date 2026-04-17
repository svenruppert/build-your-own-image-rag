package com.svenruppert.imagerag.domain;

import com.svenruppert.imagerag.domain.enums.CategoryGroup;
import com.svenruppert.imagerag.domain.enums.SourceCategory;

import java.util.*;

/**
 * Static mapping from fine-grained {@link SourceCategory} values to coarse
 * {@link CategoryGroup} buckets and user-friendly display labels.
 *
 * <p>The registry covers all 52 {@code SourceCategory} values (48 extended + 4 legacy).
 * Any value not explicitly registered falls back to
 * {@link CategoryGroup#UNCATEGORIZED} with the enum name as label.
 *
 * <p>All methods are {@code null}-safe: passing {@code null} returns
 * {@link CategoryGroup#UNCATEGORIZED} or {@code "—"} as appropriate.
 *
 * <p>This class is intentionally non-instantiable — use only the static helpers.
 */
public final class CategoryRegistry {

  private static final Map<SourceCategory, CategoryMeta> REGISTRY =
      new EnumMap<>(SourceCategory.class);

  /**
   * Alias → canonical {@link SourceCategory} mapping.
   * Keys are lower-cased and trimmed for case-insensitive lookup.
   *
   * <p>Aliases cover common German and English synonyms and abbreviations that users or
   * the LLM might use in free-text queries or category assignments.
   */
  private static final Map<String, SourceCategory> ALIASES = new HashMap<>();

  static {
    // ── Nature ────────────────────────────────────────────────────────────────
    reg(SourceCategory.LANDSCAPE, CategoryGroup.NATURE, "Landscape");
    reg(SourceCategory.MOUNTAIN, CategoryGroup.NATURE, "Mountain");
    reg(SourceCategory.FOREST, CategoryGroup.NATURE, "Forest");
    reg(SourceCategory.BEACH_COASTAL, CategoryGroup.NATURE, "Beach / Coast");
    reg(SourceCategory.DESERT, CategoryGroup.NATURE, "Desert");
    reg(SourceCategory.RIVER_WATER, CategoryGroup.NATURE, "River / Water");
    reg(SourceCategory.LAKE_POND, CategoryGroup.NATURE, "Lake / Pond");
    reg(SourceCategory.SKY_CLOUDS, CategoryGroup.NATURE, "Sky / Clouds");
    reg(SourceCategory.FIELD_MEADOW, CategoryGroup.NATURE, "Field / Meadow");
    reg(SourceCategory.PLANT_BOTANICAL, CategoryGroup.NATURE, "Plant / Botanical");
    reg(SourceCategory.SNOW_ICE, CategoryGroup.NATURE, "Snow / Ice");
    reg(SourceCategory.ROCK_GEOLOGY, CategoryGroup.NATURE, "Rock / Geology");
    // Legacy value — maps to Nature
    reg(SourceCategory.FLOWER, CategoryGroup.NATURE, "Flower");

    // ── Animals ───────────────────────────────────────────────────────────────
    reg(SourceCategory.BIRD, CategoryGroup.ANIMALS, "Bird");
    reg(SourceCategory.MAMMAL_DOMESTIC, CategoryGroup.ANIMALS, "Domestic Animal");
    reg(SourceCategory.MAMMAL_WILD, CategoryGroup.ANIMALS, "Wild Mammal");
    reg(SourceCategory.REPTILE, CategoryGroup.ANIMALS, "Reptile");
    reg(SourceCategory.INSECT, CategoryGroup.ANIMALS, "Insect");
    reg(SourceCategory.MARINE_LIFE, CategoryGroup.ANIMALS, "Marine Life");

    // ── People ────────────────────────────────────────────────────────────────
    reg(SourceCategory.PORTRAIT, CategoryGroup.PEOPLE, "Portrait");
    reg(SourceCategory.GROUP_PEOPLE, CategoryGroup.PEOPLE, "Group of People");
    reg(SourceCategory.CROWD, CategoryGroup.PEOPLE, "Crowd");
    reg(SourceCategory.WORK_PROFESSIONAL, CategoryGroup.PEOPLE, "Work / Professional");
    reg(SourceCategory.SPORT_ACTIVITY, CategoryGroup.PEOPLE, "Sport / Activity");
    reg(SourceCategory.FAMILY_CHILD, CategoryGroup.PEOPLE, "Family / Children");

    // ── Urban & Architecture ──────────────────────────────────────────────────
    reg(SourceCategory.ARCHITECTURE_EXTERIOR, CategoryGroup.URBAN, "Architecture");
    reg(SourceCategory.ARCHITECTURE_INTERIOR, CategoryGroup.URBAN, "Interior");
    reg(SourceCategory.BRIDGE_INFRASTRUCTURE, CategoryGroup.URBAN, "Bridge / Infrastructure");
    reg(SourceCategory.MONUMENT_HISTORIC, CategoryGroup.URBAN, "Monument / Historic");
    reg(SourceCategory.PARK_GARDEN, CategoryGroup.URBAN, "Park / Garden");
    reg(SourceCategory.MARKET_COMMERCIAL, CategoryGroup.URBAN, "Market / Commercial");
    reg(SourceCategory.NIGHT_SCENE, CategoryGroup.URBAN, "Night Scene");
    // Legacy value — maps to Urban
    reg(SourceCategory.CITY, CategoryGroup.URBAN, "City");

    // ── Vehicles & Transport ──────────────────────────────────────────────────
    reg(SourceCategory.CAR, CategoryGroup.VEHICLES, "Car");
    reg(SourceCategory.TRUCK_HEAVY, CategoryGroup.VEHICLES, "Truck / Heavy Vehicle");
    reg(SourceCategory.MOTORCYCLE, CategoryGroup.VEHICLES, "Motorcycle");
    reg(SourceCategory.BICYCLE, CategoryGroup.VEHICLES, "Bicycle");
    reg(SourceCategory.AIRCRAFT, CategoryGroup.VEHICLES, "Aircraft");
    reg(SourceCategory.WATERCRAFT, CategoryGroup.VEHICLES, "Watercraft");
    reg(SourceCategory.PUBLIC_TRANSPORT, CategoryGroup.VEHICLES, "Public Transport");

    // ── Technology & Industry ─────────────────────────────────────────────────
    reg(SourceCategory.ELECTRONICS, CategoryGroup.TECHNOLOGY, "Electronics");
    reg(SourceCategory.INDUSTRIAL_MACHINERY, CategoryGroup.TECHNOLOGY, "Industrial Machinery");
    reg(SourceCategory.MEDICAL_EQUIPMENT, CategoryGroup.TECHNOLOGY, "Medical Equipment");

    // ── Objects & Media ───────────────────────────────────────────────────────
    reg(SourceCategory.DOCUMENT_TEXT, CategoryGroup.OBJECTS_MEDIA, "Document / Text");
    reg(SourceCategory.SIGN_SIGNAGE, CategoryGroup.OBJECTS_MEDIA, "Sign / Signage");
    reg(SourceCategory.FOOD_DRINK, CategoryGroup.OBJECTS_MEDIA, "Food & Drink");
    reg(SourceCategory.ARTWORK_GRAPHIC, CategoryGroup.OBJECTS_MEDIA, "Artwork / Graphic");

    // ── Activities & Events ───────────────────────────────────────────────────
    reg(SourceCategory.SPORT_EVENT, CategoryGroup.ACTIVITIES, "Sport Event");
    reg(SourceCategory.OUTDOOR_ACTIVITY, CategoryGroup.ACTIVITIES, "Outdoor Activity");
    reg(SourceCategory.CEREMONY_RITUAL, CategoryGroup.ACTIVITIES, "Ceremony / Ritual");

    // ── Uncategorized / legacy catch-alls ─────────────────────────────────────
    reg(SourceCategory.MIXED, CategoryGroup.UNCATEGORIZED, "Mixed");
    reg(SourceCategory.UNKNOWN, CategoryGroup.UNCATEGORIZED, "Unknown");

    // ── Aliases / synonyms ────────────────────────────────────────────────────
    // Vehicles
    alias("auto", SourceCategory.CAR);
    alias("pkw", SourceCategory.CAR);
    alias("car", SourceCategory.CAR);
    alias("kfz", SourceCategory.CAR);
    alias("lkw", SourceCategory.TRUCK_HEAVY);
    alias("truck", SourceCategory.TRUCK_HEAVY);
    alias("motorrad", SourceCategory.MOTORCYCLE);
    alias("bike", SourceCategory.BICYCLE);
    alias("fahrrad", SourceCategory.BICYCLE);
    alias("flugzeug", SourceCategory.AIRCRAFT);
    alias("plane", SourceCategory.AIRCRAFT);
    alias("ship", SourceCategory.WATERCRAFT);
    alias("schiff", SourceCategory.WATERCRAFT);
    alias("bus", SourceCategory.PUBLIC_TRANSPORT);
    alias("zug", SourceCategory.PUBLIC_TRANSPORT);
    alias("train", SourceCategory.PUBLIC_TRANSPORT);
    // Nature
    alias("wald", SourceCategory.FOREST);
    alias("forest", SourceCategory.FOREST);
    alias("berg", SourceCategory.MOUNTAIN);
    alias("mountain", SourceCategory.MOUNTAIN);
    alias("strand", SourceCategory.BEACH_COASTAL);
    alias("sea", SourceCategory.BEACH_COASTAL);
    alias("meer", SourceCategory.BEACH_COASTAL);
    alias("beach", SourceCategory.BEACH_COASTAL);
    alias("see", SourceCategory.LAKE_POND);
    alias("lake", SourceCategory.LAKE_POND);
    alias("teich", SourceCategory.LAKE_POND);
    alias("pond", SourceCategory.LAKE_POND);
    alias("fluss", SourceCategory.RIVER_WATER);
    alias("river", SourceCategory.RIVER_WATER);
    alias("wüste", SourceCategory.DESERT);
    alias("schnee", SourceCategory.SNOW_ICE);
    alias("snow", SourceCategory.SNOW_ICE);
    alias("blume", SourceCategory.FLOWER);
    alias("flower", SourceCategory.FLOWER);
    alias("pflanze", SourceCategory.PLANT_BOTANICAL);
    alias("plant", SourceCategory.PLANT_BOTANICAL);
    alias("feld", SourceCategory.FIELD_MEADOW);
    alias("wiese", SourceCategory.FIELD_MEADOW);
    alias("field", SourceCategory.FIELD_MEADOW);
    alias("himmel", SourceCategory.SKY_CLOUDS);
    alias("sky", SourceCategory.SKY_CLOUDS);
    // Animals
    alias("vogel", SourceCategory.BIRD);
    alias("bird", SourceCategory.BIRD);
    alias("hund", SourceCategory.MAMMAL_DOMESTIC);
    alias("katze", SourceCategory.MAMMAL_DOMESTIC);
    alias("dog", SourceCategory.MAMMAL_DOMESTIC);
    alias("cat", SourceCategory.MAMMAL_DOMESTIC);
    alias("tier", SourceCategory.MAMMAL_WILD);
    alias("animal", SourceCategory.MAMMAL_WILD);
    alias("fisch", SourceCategory.MARINE_LIFE);
    alias("fish", SourceCategory.MARINE_LIFE);
    // People
    alias("person", SourceCategory.PORTRAIT);
    alias("mensch", SourceCategory.PORTRAIT);
    alias("portrait", SourceCategory.PORTRAIT);
    alias("group", SourceCategory.GROUP_PEOPLE);
    alias("gruppe", SourceCategory.GROUP_PEOPLE);
    alias("family", SourceCategory.FAMILY_CHILD);
    alias("familie", SourceCategory.FAMILY_CHILD);
    alias("sport", SourceCategory.SPORT_ACTIVITY);
    alias("work", SourceCategory.WORK_PROFESSIONAL);
    alias("arbeit", SourceCategory.WORK_PROFESSIONAL);
    // Urban
    alias("gebäude", SourceCategory.ARCHITECTURE_EXTERIOR);
    alias("building", SourceCategory.ARCHITECTURE_EXTERIOR);
    alias("haus", SourceCategory.ARCHITECTURE_EXTERIOR);
    alias("house", SourceCategory.ARCHITECTURE_EXTERIOR);
    alias("innenraum", SourceCategory.ARCHITECTURE_INTERIOR);
    alias("interior", SourceCategory.ARCHITECTURE_INTERIOR);
    alias("brücke", SourceCategory.BRIDGE_INFRASTRUCTURE);
    alias("bridge", SourceCategory.BRIDGE_INFRASTRUCTURE);
    alias("park", SourceCategory.PARK_GARDEN);
    alias("garten", SourceCategory.PARK_GARDEN);
    alias("garden", SourceCategory.PARK_GARDEN);
    alias("markt", SourceCategory.MARKET_COMMERCIAL);
    alias("market", SourceCategory.MARKET_COMMERCIAL);
    alias("nacht", SourceCategory.NIGHT_SCENE);
    alias("night", SourceCategory.NIGHT_SCENE);
    alias("stadt", SourceCategory.CITY);
    alias("city", SourceCategory.CITY);
    // Objects
    alias("essen", SourceCategory.FOOD_DRINK);
    alias("food", SourceCategory.FOOD_DRINK);
    alias("dokument", SourceCategory.DOCUMENT_TEXT);
    alias("document", SourceCategory.DOCUMENT_TEXT);
    alias("schild", SourceCategory.SIGN_SIGNAGE);
    alias("sign", SourceCategory.SIGN_SIGNAGE);
    alias("kunst", SourceCategory.ARTWORK_GRAPHIC);
    alias("art", SourceCategory.ARTWORK_GRAPHIC);
    // Technology
    alias("technik", SourceCategory.ELECTRONICS);
    alias("electronics", SourceCategory.ELECTRONICS);
    alias("maschine", SourceCategory.INDUSTRIAL_MACHINERY);
    alias("machine", SourceCategory.INDUSTRIAL_MACHINERY);
  }

  private CategoryRegistry() {
  }

  private static void reg(SourceCategory cat, CategoryGroup group, String label) {
    REGISTRY.put(cat, new CategoryMeta(group, label));
  }

  private static void alias(String key, SourceCategory cat) {
    ALIASES.put(key.toLowerCase(), cat);
  }

  /**
   * Returns the coarse {@link CategoryGroup} for the given fine-grained category.
   *
   * @param cat the source category (may be {@code null})
   * @return the matching group, or {@link CategoryGroup#UNCATEGORIZED} when unmapped
   */
  public static CategoryGroup getGroup(SourceCategory cat) {
    if (cat == null) return CategoryGroup.UNCATEGORIZED;
    CategoryMeta meta = REGISTRY.get(cat);
    return meta != null ? meta.group() : CategoryGroup.UNCATEGORIZED;
  }

  // ── Public API ────────────────────────────────────────────────────────────

  /**
   * Returns the user-friendly display label for the given source category.
   *
   * @param cat the source category (may be {@code null})
   * @return the label, or {@code "\u2014"} for {@code null}, or the enum name if unmapped
   */
  public static String getUserLabel(SourceCategory cat) {
    if (cat == null) return "\u2014";
    CategoryMeta meta = REGISTRY.get(cat);
    return meta != null ? meta.userLabel() : cat.name();
  }

  /**
   * Returns the group label for the given source category.
   * Convenience shortcut for {@code getGroup(cat).getLabel()}.
   */
  public static String getGroupLabel(SourceCategory cat) {
    return getGroup(cat).getLabel();
  }

  /**
   * Looks up a {@link SourceCategory} by an alias or synonym (case-insensitive).
   *
   * <p>Examples: {@code "auto"} → {@code CAR}, {@code "wald"} → {@code FOREST}.
   * Returns {@link Optional#empty()} when the alias is not registered.
   *
   * @param alias the alias or synonym to resolve (may be {@code null})
   * @return the matching category, or empty if unknown
   */
  public static Optional<SourceCategory> findByAlias(String alias) {
    if (alias == null || alias.isBlank()) return Optional.empty();
    return Optional.ofNullable(ALIASES.get(alias.toLowerCase().trim()));
  }

  /**
   * Returns all registered alias keys (lower-cased) for the given category.
   * Useful for search expansion and display.
   */
  public static List<String> getAliasesFor(SourceCategory cat) {
    return ALIASES.entrySet().stream()
        .filter(e -> e.getValue() == cat)
        .map(Map.Entry::getKey)
        .sorted()
        .toList();
  }

  /**
   * Returns all {@link SourceCategory} values that belong to the given group,
   * in enum declaration order.
   */
  public static List<SourceCategory> getCategoriesInGroup(CategoryGroup group) {
    return REGISTRY.entrySet().stream()
        .filter(e -> e.getValue().group() == group)
        .map(Map.Entry::getKey)
        .sorted()
        .toList();
  }

  /**
   * Metadata record for a single category mapping.
   */
  public record CategoryMeta(CategoryGroup group, String userLabel) { }
}
