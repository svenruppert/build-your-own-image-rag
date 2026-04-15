package com.svenruppert.imagerag.domain;

import com.svenruppert.imagerag.domain.enums.CategoryGroup;
import com.svenruppert.imagerag.domain.enums.SourceCategory;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

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
  }

  private CategoryRegistry() {
  }

  private static void reg(SourceCategory cat, CategoryGroup group, String label) {
    REGISTRY.put(cat, new CategoryMeta(group, label));
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
