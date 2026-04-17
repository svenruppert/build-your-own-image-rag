package com.svenruppert.imagerag.domain.enums;

/**
 * Fine-grained internal image category used by the AI classification pipeline.
 * <p>Each value maps to a coarse {@link CategoryGroup} via {@code CategoryRegistry}.
 * The four legacy values ({@code FLOWER}, {@code CITY}, {@code MIXED}, {@code UNKNOWN})
 * are retained for backward compatibility with images already stored in the archive.
 * <p>New images are classified into the extended set of ~52 values; the LLM prompt
 * in {@code SemanticDerivationServiceImpl} lists all values grouped by theme.
 */
public enum SourceCategory {

  // ── Nature ───────────────────────────────────────────────────────────────────
  LANDSCAPE,
  MOUNTAIN,
  FOREST,
  BEACH_COASTAL,
  DESERT,
  RIVER_WATER,
  LAKE_POND,
  SKY_CLOUDS,
  FIELD_MEADOW,
  PLANT_BOTANICAL,
  SNOW_ICE,
  ROCK_GEOLOGY,

  // ── Animals ──────────────────────────────────────────────────────────────────
  BIRD,
  MAMMAL_DOMESTIC,
  MAMMAL_WILD,
  REPTILE,
  INSECT,
  MARINE_LIFE,

  // ── People ───────────────────────────────────────────────────────────────────
  PORTRAIT,
  GROUP_PEOPLE,
  CROWD,
  WORK_PROFESSIONAL,
  SPORT_ACTIVITY,
  FAMILY_CHILD,

  // ── Urban & Architecture ─────────────────────────────────────────────────────
  ARCHITECTURE_EXTERIOR,
  ARCHITECTURE_INTERIOR,
  BRIDGE_INFRASTRUCTURE,
  MONUMENT_HISTORIC,
  PARK_GARDEN,
  MARKET_COMMERCIAL,
  NIGHT_SCENE,

  // ── Vehicles & Transport ─────────────────────────────────────────────────────
  CAR,
  TRUCK_HEAVY,
  MOTORCYCLE,
  BICYCLE,
  AIRCRAFT,
  WATERCRAFT,
  PUBLIC_TRANSPORT,

  // ── Technology & Industry ────────────────────────────────────────────────────
  ELECTRONICS,
  INDUSTRIAL_MACHINERY,
  MEDICAL_EQUIPMENT,

  // ── Objects & Media ──────────────────────────────────────────────────────────
  DOCUMENT_TEXT,
  SIGN_SIGNAGE,
  FOOD_DRINK,
  ARTWORK_GRAPHIC,

  // ── Activities & Events ──────────────────────────────────────────────────────
  SPORT_EVENT,
  OUTDOOR_ACTIVITY,
  CEREMONY_RITUAL,

  // ── Legacy / Backward-compatible values ──────────────────────────────────────
  /**
   * @deprecated Kept for backward compatibility; new images use PLANT_BOTANICAL.
   */
  FLOWER,
  /**
   * @deprecated Kept for backward compatibility; new images use ARCHITECTURE_EXTERIOR.
   */
  CITY,
  /**
   * Catch-all for images spanning multiple categories.
   */
  MIXED,
  /**
   * Fallback when no category can be determined.
   */
  UNKNOWN
}
