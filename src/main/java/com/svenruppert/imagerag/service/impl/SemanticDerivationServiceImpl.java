package com.svenruppert.imagerag.service.impl;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.imagerag.domain.CategoryCandidate;
import com.svenruppert.imagerag.domain.CategoryConfidence;
import com.svenruppert.imagerag.domain.SemanticAnalysis;
import com.svenruppert.imagerag.domain.enums.SeasonHint;
import com.svenruppert.imagerag.domain.enums.SourceCategory;
import com.svenruppert.imagerag.dto.VisionAnalysisResponse;
import com.svenruppert.imagerag.ollama.OllamaClient;
import com.svenruppert.imagerag.ollama.OllamaConfig;
import com.svenruppert.imagerag.service.PromptTemplateService;
import com.svenruppert.imagerag.service.SemanticDerivationService;

import java.time.Instant;
import java.util.*;

public class SemanticDerivationServiceImpl
    implements SemanticDerivationService, HasLogger {

  /**
   * Increment this version string whenever the prompt template below is changed.
   * The value is persisted in {@link com.svenruppert.imagerag.domain.SemanticAnalysis#setSemanticPromptVersion}
   * so that provenance tracking can show which version produced a given result.
   * v3: Added primaryCategoryConfidence and alternativeCategories fields for taxonomy maintenance.
   */
  public static final String DERIVATION_PROMPT_VERSION = "v3";

  /**
   * Vision-side prompt version identifier.  The vision step is handled by
   * {@link com.svenruppert.imagerag.service.impl.VisionAnalysisServiceImpl}; this constant
   * is a placeholder so the provenance tab always has something to display.
   */
  public static final String VISION_PROMPT_VERSION = "v1";

  public static final String DERIVATION_PROMPT_TEMPLATE = """
      You are a structured data extractor. Given the following image description, extract structured fields as JSON.
      Image description:
      "%s"
      Return ONLY valid JSON with these exact fields (no markdown, no explanation):
      {
        "shortSummary": "<max 20 words summary>",
        "tags": ["tag1", "tag2", "tag3"],
        "sourceCategory": "<see allowed values below — choose the single best match>",
        "primaryCategoryConfidence": <float 0.0-1.0, your confidence in sourceCategory>,
        "secondaryCategories": ["<optional additional categories — empty array if none apply>"],
        "alternativeCategories": [
          {"category": "<CATEGORY>", "confidence": <float 0.0-1.0>}
        ],
        "sceneType": "<indoor|outdoor|urban|rural|natural|mixed>",
        "seasonHint": "<WINTER|SPRING|SUMMER|AUTUMN|UNKNOWN>",
        "containsPerson": <true|false>,
        "personCountHint": <integer or null>,
        "containsVehicle": <true|false>,
        "containsReadableText": <true|false>,
        "containsLicensePlateHint": <true|false>
      }
      Allowed sourceCategory / secondaryCategories / alternativeCategories values:
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

  /**
   * The prompt key used for {@link PromptTemplateService} lookups.
   */
  public static final String PROMPT_KEY = "semantic";

  private final OllamaClient ollamaClient;
  private final OllamaConfig config;
  private final ObjectMapper objectMapper = new ObjectMapper();
  /**
   * Optional — null before the migration centre is wired in older service graphs.
   */
  private PromptTemplateService promptTemplateService;

  public SemanticDerivationServiceImpl(OllamaClient ollamaClient, OllamaConfig config) {
    this.ollamaClient = ollamaClient;
    this.config = config;
  }

  /**
   * Called by {@link com.svenruppert.imagerag.bootstrap.ServiceRegistry} after construction.
   */
  public void setPromptTemplateService(PromptTemplateService promptTemplateService) {
    this.promptTemplateService = promptTemplateService;
  }

  @Override
  public SemanticAnalysis derive(UUID imageId, VisionAnalysisResponse response) {
    SemanticAnalysis analysis = new SemanticAnalysis();
    analysis.setImageId(imageId);
    analysis.setSummary(response.rawDescription());

    // Resolve active prompt — managed override takes precedence over built-in
    String promptTemplate = (promptTemplateService != null)
        ? promptTemplateService.getActiveContent(PROMPT_KEY).orElse(DERIVATION_PROMPT_TEMPLATE)
        : DERIVATION_PROMPT_TEMPLATE;

    String prompt = promptTemplate.formatted(
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

    // Populate provenance fields so the detail view can show which models and prompt versions
    // produced this analysis.
    analysis.setVisionModel(response.model() != null ? response.model() : config.getVisionModel());
    analysis.setSemanticModel(config.getTextModel());
    analysis.setAnalysisTimestamp(Instant.now());
    analysis.setVisionPromptVersion(VISION_PROMPT_VERSION);
    analysis.setSemanticPromptVersion(DERIVATION_PROMPT_VERSION);

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

      // Parse optional secondary categories (empty array = no secondary categories)
      List<SourceCategory> secondaryCats = new ArrayList<>();
      node.path("secondaryCategories").forEach(t -> {
        String secCat = t.asText("").toUpperCase();
        if (!secCat.isBlank()) {
          SourceCategory sc = safeEnum(SourceCategory.class, secCat, null);
          if (sc != null && sc != analysis.getSourceCategory()) {
            secondaryCats.add(sc);
          }
        }
      });
      analysis.setSecondaryCategories(secondaryCats);

      // Parse confidence data (v3 prompt fields)
      double primaryConf = node.path("primaryCategoryConfidence").asDouble(-1.0);
      if (primaryConf >= 0.0) {
        List<CategoryCandidate> alternatives = new ArrayList<>();
        node.path("alternativeCategories").forEach(alt -> {
          String altCatStr = alt.path("category").asText("").toUpperCase();
          double altConf = alt.path("confidence").asDouble(0.0);
          SourceCategory altCat = safeEnum(SourceCategory.class, altCatStr, null);
          if (altCat != null && altCat != analysis.getSourceCategory()) {
            alternatives.add(new CategoryCandidate(altCat, altConf));
          }
        });
        // Keep top 5 alternatives sorted descending.
        // Use new ArrayList<> — subList() returns ArrayList$SubList which EclipseStore cannot persist.
        alternatives.sort(Comparator.comparingDouble(CategoryCandidate::getScore).reversed());
        List<CategoryCandidate> top5 = new ArrayList<>(alternatives.subList(0, Math.min(5, alternatives.size())));
        CategoryConfidence confidence = new CategoryConfidence(
            analysis.getImageId(), analysis.getSourceCategory(),
            primaryConf, top5, java.time.Instant.now(), config.getTextModel());
        analysis.setCategoryConfidence(confidence);
      }

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
    if (value == null || value.isBlank()) return defaultValue;
    try {
      return Enum.valueOf(enumClass, value);
    } catch (IllegalArgumentException e) {
      return defaultValue;
    }
  }
}
