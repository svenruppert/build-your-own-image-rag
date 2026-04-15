package com.svenruppert.imagerag.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.imagerag.domain.SemanticAnalysis;
import com.svenruppert.imagerag.domain.enums.SeasonHint;
import com.svenruppert.imagerag.domain.enums.SourceCategory;
import com.svenruppert.imagerag.dto.VisionAnalysisResponse;
import com.svenruppert.imagerag.ollama.OllamaClient;
import com.svenruppert.imagerag.ollama.OllamaConfig;
import com.svenruppert.imagerag.service.SemanticDerivationService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SemanticDerivationServiceImpl
    implements SemanticDerivationService, HasLogger {

  private static final String DERIVATION_PROMPT_TEMPLATE = """
      You are a structured data extractor. Given the following image description, extract structured fields as JSON.
      Image description:
      "%s"
      Return ONLY valid JSON with these exact fields (no markdown, no explanation):
      {
        "shortSummary": "<max 20 words summary>",
        "tags": ["tag1", "tag2", "tag3"],
        "sourceCategory": "<see allowed values below>",
        "sceneType": "<indoor|outdoor|urban|rural|natural|mixed>",
        "seasonHint": "<WINTER|SPRING|SUMMER|AUTUMN|UNKNOWN>",
        "containsPerson": <true|false>,
        "personCountHint": <integer or null>,
        "containsVehicle": <true|false>,
        "containsReadableText": <true|false>,
        "containsLicensePlateHint": <true|false>
      }
      Allowed sourceCategory values (choose the single best match):
      Nature:     LANDSCAPE, MOUNTAIN, FOREST, BEACH_COASTAL, DESERT, RIVER_WATER,
                  LAKE_POND, SKY_CLOUDS, FIELD_MEADOW, PLANT_BOTANICAL, SNOW_ICE,
                  ROCK_GEOLOGY, FLOWER
      Animals:    BIRD, MAMMAL_DOMESTIC, MAMMAL_WILD, REPTILE, INSECT, MARINE_LIFE
      People:     PORTRAIT, GROUP_PEOPLE, CROWD, WORK_PROFESSIONAL, SPORT_ACTIVITY,
                  FAMILY_CHILD
      Urban:      ARCHITECTURE_EXTERIOR, ARCHITECTURE_INTERIOR, BRIDGE_INFRASTRUCTURE,
                  MONUMENT_HISTORIC, PARK_GARDEN, MARKET_COMMERCIAL, NIGHT_SCENE, CITY
      Vehicles:   CAR, TRUCK_HEAVY, MOTORCYCLE, BICYCLE, AIRCRAFT, WATERCRAFT,
                  PUBLIC_TRANSPORT
      Technology: ELECTRONICS, INDUSTRIAL_MACHINERY, MEDICAL_EQUIPMENT
      Objects:    DOCUMENT_TEXT, SIGN_SIGNAGE, FOOD_DRINK, ARTWORK_GRAPHIC
      Activities: SPORT_EVENT, OUTDOOR_ACTIVITY, CEREMONY_RITUAL
      Other:      MIXED, UNKNOWN
      """;

  private final OllamaClient ollamaClient;
  private final OllamaConfig config;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public SemanticDerivationServiceImpl(OllamaClient ollamaClient, OllamaConfig config) {
    this.ollamaClient = ollamaClient;
    this.config = config;
  }

  @Override
  public SemanticAnalysis derive(UUID imageId, VisionAnalysisResponse response) {
    SemanticAnalysis analysis = new SemanticAnalysis();
    analysis.setImageId(imageId);
    analysis.setSummary(response.rawDescription());

    String prompt = DERIVATION_PROMPT_TEMPLATE.formatted(
        response.rawDescription().replace("\"", "'"));

    Optional<String> jsonResponse = ollamaClient.generateJson(prompt);

    if (jsonResponse.isPresent()) {
      parseStructuredFields(analysis, jsonResponse.get());
    } else {
      applyFallbackHeuristics(analysis, response.rawDescription());
    }

    if (analysis.getShortSummary() == null || analysis.getShortSummary().isBlank()) {
      String desc = response.rawDescription();
      analysis.setShortSummary(desc.length() > 100 ? desc.substring(0, 97) + "..." : desc);
    }
    if (analysis.getSourceCategory() == null) analysis.setSourceCategory(SourceCategory.UNKNOWN);
    if (analysis.getSeasonHint() == null) analysis.setSeasonHint(SeasonHint.UNKNOWN);

    // Populate provenance fields so the detail view can show which models produced this analysis
    analysis.setVisionModel(response.model() != null ? response.model() : config.getVisionModel());
    analysis.setSemanticModel(config.getTextModel());
    analysis.setAnalysisTimestamp(Instant.now());

    return analysis;
  }

  private void parseStructuredFields(SemanticAnalysis analysis, String json) {
    try {
      // Strip potential markdown code fences
      String cleaned = json.trim();
      if (cleaned.startsWith("```")) {
        cleaned = cleaned.replaceFirst("```[a-zA-Z]*\\n?", "").replaceAll("```$", "").trim();
      }

      JsonNode node = objectMapper.readTree(cleaned);

      analysis.setShortSummary(node.path("shortSummary").asText(null));
      analysis.setSceneType(node.path("sceneType").asText(null));

      List<String> tags = new ArrayList<>();
      node.path("tags").forEach(t -> tags.add(t.asText()));
      analysis.setTags(tags);

      String cat = node.path("sourceCategory").asText("UNKNOWN").toUpperCase();
      analysis.setSourceCategory(safeEnum(SourceCategory.class, cat, SourceCategory.UNKNOWN));

      String season = node.path("seasonHint").asText("UNKNOWN").toUpperCase();
      analysis.setSeasonHint(safeEnum(SeasonHint.class, season, SeasonHint.UNKNOWN));

      analysis.setContainsPerson(node.path("containsPerson").asBoolean(false));
      if (!node.path("personCountHint").isNull()) {
        analysis.setPersonCountHint(node.path("personCountHint").asInt(0));
      }
      analysis.setContainsVehicle(node.path("containsVehicle").asBoolean(false));
      analysis.setContainsReadableText(node.path("containsReadableText").asBoolean(false));
      analysis.setContainsLicensePlateHint(node.path("containsLicensePlateHint").asBoolean(false));

      logger().debug("Structured fields derived for image via LLM");

    } catch (Exception e) {
      logger().warn("Could not parse structured fields from LLM response: {}. Falling back to heuristics.", e.getMessage());
      applyFallbackHeuristics(analysis, analysis.getSummary());
    }
  }

  private void applyFallbackHeuristics(SemanticAnalysis analysis, String description) {
    String lower = description != null ? description.toLowerCase() : "";

    analysis.setContainsPerson(lower.contains("person") || lower.contains("people")
                                   || lower.contains("man") || lower.contains("woman") || lower.contains("child"));
    analysis.setContainsVehicle(lower.contains("car") || lower.contains("vehicle")
                                    || lower.contains("truck") || lower.contains("bus"));
    analysis.setContainsReadableText(lower.contains("sign") || lower.contains("text")
                                         || lower.contains("inscription") || lower.contains("label"));
    analysis.setContainsLicensePlateHint(lower.contains("license plate") || lower.contains("number plate"));

    if (lower.contains("flower") || lower.contains("blossom") || lower.contains("petal")) {
      analysis.setSourceCategory(SourceCategory.FLOWER);
    } else if (lower.contains("city") || lower.contains("urban") || lower.contains("street")
        || lower.contains("building")) {
      analysis.setSourceCategory(SourceCategory.CITY);
    } else {
      analysis.setSourceCategory(SourceCategory.UNKNOWN);
    }

    if (lower.contains("snow") || lower.contains("frost") || lower.contains("winter")) {
      analysis.setSeasonHint(SeasonHint.WINTER);
    } else if (lower.contains("spring") || lower.contains("blossom")) {
      analysis.setSeasonHint(SeasonHint.SPRING);
    } else if (lower.contains("summer") || lower.contains("hot") || lower.contains("sunny")) {
      analysis.setSeasonHint(SeasonHint.SUMMER);
    } else if (lower.contains("autumn") || lower.contains("fall") || lower.contains("leaves")) {
      analysis.setSeasonHint(SeasonHint.AUTUMN);
    } else {
      analysis.setSeasonHint(SeasonHint.UNKNOWN);
    }

    String shortDesc = description != null && description.length() > 100
        ? description.substring(0, 97) + "..." : description;
    analysis.setShortSummary(shortDesc);
  }

  private <T extends Enum<T>> T safeEnum(Class<T> enumClass, String value, T defaultValue) {
    try {
      return Enum.valueOf(enumClass, value);
    } catch (IllegalArgumentException e) {
      return defaultValue;
    }
  }
}
