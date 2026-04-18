package com.svenruppert.imagerag.service.impl;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.imagerag.domain.SearchPlan;
import com.svenruppert.imagerag.domain.enums.CategoryGroup;
import com.svenruppert.imagerag.domain.enums.RiskLevel;
import com.svenruppert.imagerag.domain.enums.SeasonHint;
import com.svenruppert.imagerag.ollama.OllamaClient;
import com.svenruppert.imagerag.service.QueryUnderstandingService;

import java.util.Optional;

public class QueryUnderstandingServiceImpl
    implements QueryUnderstandingService, HasLogger {

  private static final String QUERY_PROMPT_TEMPLATE = """
      You are a search query analyzer for an image archive system.
      Analyze the following search query and extract structured search parameters.
      Query: "%s"
      Return ONLY valid JSON with these exact fields (no markdown, no explanation, no comments):
      {
        "embeddingText": "<rich semantic description for vector search, max 100 words>",
        "containsPerson": <true|false|null>,
        "containsVehicle": <true|false|null>,
        "containsLicensePlate": <true|false|null>,
        "seasonHint": "<WINTER|SPRING|SUMMER|AUTUMN|UNKNOWN|null>",
        "categoryGroup": "<NATURE|ANIMALS|PEOPLE|URBAN|VEHICLES|TECHNOLOGY|OBJECTS_MEDIA|ACTIVITIES|UNCATEGORIZED|null>",
        "privacyLevel": "<SAFE|REVIEW|SENSITIVE|null>",
        "explanation": "<brief explanation of query interpretation>"
      }
      Rules:
      - embeddingText must always be filled with a rich semantic description relevant to the query
      - Use null (not quoted) when a filter does not apply to the query
      - containsPerson: true only when the query explicitly asks for images with people
      - containsVehicle: true when the query mentions cars, vehicles, trucks, motorcycles, etc.
      - containsLicensePlate: true when the query mentions license plates, number plates, Nummernschild, etc.
      - If containsLicensePlate is true, containsVehicle should also be true
      - categoryGroup: NATURE for landscape/plant/weather, ANIMALS for wildlife/pets,
        PEOPLE for portraits/crowds, URBAN for buildings/cities, VEHICLES for cars/aircraft,
        TECHNOLOGY for electronics/machinery, OBJECTS_MEDIA for documents/food/art,
        ACTIVITIES for sports/events; null if the query does not imply a specific category
      - seasonHint, categoryGroup, privacyLevel must be null if not explicitly implied by the query
      """;

  private final OllamaClient ollamaClient;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public QueryUnderstandingServiceImpl(OllamaClient ollamaClient) {
    this.ollamaClient = ollamaClient;
  }

  @Override
  public SearchPlan understand(String query) {
    logger().info("Analyzing query: {}", query);

    SearchPlan plan = new SearchPlan();
    plan.setOriginalQuery(query);
    plan.setEmbeddingText(query); // sensible default

    String prompt = QUERY_PROMPT_TEMPLATE.formatted(query.replace("\"", "'"));
    Optional<String> jsonResponse = ollamaClient.generateJson(prompt);

    if (jsonResponse.isPresent()) {
      parseSearchPlan(plan, jsonResponse.get());
    } else {
      logger().warn("Query understanding unavailable — using raw query for embedding");
    }

    return plan;
  }

  private void parseSearchPlan(SearchPlan plan, String json) {
    try {
      String cleaned = json.trim();
      if (cleaned.startsWith("```")) {
        cleaned = cleaned.replaceFirst("```[a-zA-Z]*\\n?", "").replaceAll("```$", "").trim();
      }

      JsonNode node = objectMapper.readTree(cleaned);

      String embeddingText = node.path("embeddingText").asText(null);
      if (embeddingText != null && !embeddingText.isBlank()) {
        plan.setEmbeddingText(embeddingText);
      }

      plan.setContainsPerson(nullableBool(node, "containsPerson"));
      plan.setContainsVehicle(nullableBool(node, "containsVehicle"));
      plan.setContainsLicensePlate(nullableBool(node, "containsLicensePlate"));

      plan.setSeasonHint(nullableEnum(node, "seasonHint", SeasonHint.class));
      plan.setCategoryGroup(nullableEnum(node, "categoryGroup", CategoryGroup.class));
      plan.setPrivacyLevel(nullableEnum(node, "privacyLevel", RiskLevel.class));
      plan.setExplanation(node.path("explanation").asText(null));

      logger().info(
          "SearchPlan: embedding='{}', person={}, vehicle={}, licensePlate={}, season={}, categoryGroup={}",
          plan.getEmbeddingText(), plan.getContainsPerson(), plan.getContainsVehicle(),
          plan.getContainsLicensePlate(), plan.getSeasonHint(), plan.getCategoryGroup());

    } catch (Exception e) {
      logger().warn("Failed to parse SearchPlan JSON: {}. Using raw query as fallback.",
                    e.getMessage());
    }
  }

  private Boolean nullableBool(JsonNode node, String field) {
    JsonNode n = node.path(field);
    if (n.isNull() || n.isMissingNode()) return null;
    return n.asBoolean();
  }

  private <T extends Enum<T>> T nullableEnum(JsonNode node, String field, Class<T> cls) {
    String val = node.path(field).asText(null);
    if (val == null || val.equals("null")) return null;
    try {
      return Enum.valueOf(cls, val.toUpperCase());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
