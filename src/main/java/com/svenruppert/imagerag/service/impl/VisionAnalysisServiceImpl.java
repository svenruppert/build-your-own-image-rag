package com.svenruppert.imagerag.service.impl;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.imagerag.dto.VisionAnalysisResponse;
import com.svenruppert.imagerag.ollama.OllamaClient;
import com.svenruppert.imagerag.ollama.OllamaConfig;
import com.svenruppert.imagerag.service.PromptTemplateService;
import com.svenruppert.imagerag.service.VisionAnalysisService;

import java.nio.file.Path;
import java.util.Optional;

public class VisionAnalysisServiceImpl
    implements VisionAnalysisService, HasLogger {

  /**
   * The prompt key used for {@link PromptTemplateService} lookups.
   */
  public static final String PROMPT_KEY = "vision";
  /**
   * Built-in version tag — registered in the migration centre on startup.
   */
  public static final String PROMPT_VERSION = "v1";

  public static final String VISION_PROMPT = """
      Analyze this image in detail for a semantic image archive.
      Describe:
      - Subject type: what is the primary subject (flower, street, building, person, vehicle, landscape, etc.)
      - Scene type: indoor, outdoor, urban, rural, natural
      - Season hints: visible season from lighting, vegetation, clothing, weather
      - Atmosphere: mood, time of day, lighting conditions
      - Notable objects: list the most prominent visible objects
      - People: are people visible? Approximately how many?
      - Vehicles: are vehicles visible?
      - Text: is readable text or signage visible?
      - Special details: GPS clues, architectural style, botanical details if relevant
      Write a coherent paragraph description suitable for semantic retrieval.
      Do not mention that this is an AI analysis. Be specific and objective.
      """;

  private final OllamaClient ollamaClient;
  private final OllamaConfig config;
  /**
   * Optional — null before the migration centre is wired in older service graphs.
   */
  private PromptTemplateService promptTemplateService;

  public VisionAnalysisServiceImpl(OllamaClient ollamaClient, OllamaConfig config) {
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
  public VisionAnalysisResponse analyzeImage(Path imagePath) {
    logger().info("Starting vision analysis for: {}", imagePath.getFileName());

    String activePrompt = resolvePrompt();
    Optional<String> description = ollamaClient.analyzeImageWithVision(imagePath, activePrompt);

    if (description.isPresent() && !description.get().isBlank()) {
      logger().info("Vision analysis complete for: {}", imagePath.getFileName());
      return new VisionAnalysisResponse(description.get(), config.getVisionModel(), true);
    }

    logger().warn("Vision analysis unavailable for: {} — using stub description", imagePath.getFileName());
    return new VisionAnalysisResponse(
        "An image was uploaded but could not be analyzed. "
            + "File: " + imagePath.getFileName().toString(),
        config.getVisionModel(),
        false
    );
  }

  // ── Helpers ──────────────────────────────────────────────────────────────

  private String resolvePrompt() {
    if (promptTemplateService != null) {
      Optional<String> override = promptTemplateService.getActiveContent(PROMPT_KEY);
      if (override.isPresent()) {
        logger().debug("Using managed vision prompt override from PromptTemplateService");
        return override.get();
      }
    }
    return VISION_PROMPT;
  }
}
