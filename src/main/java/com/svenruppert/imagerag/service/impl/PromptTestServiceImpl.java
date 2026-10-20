package com.svenruppert.imagerag.service.impl;

import com.svenruppert.functional.result.Result;
import com.svenruppert.imagerag.domain.SemanticAnalysis;
import com.svenruppert.imagerag.ollama.OllamaClient;
import com.svenruppert.imagerag.ollama.PromptTemplates;
import com.svenruppert.imagerag.persistence.PersistenceService;
import com.svenruppert.imagerag.service.ImageStorageService;
import com.svenruppert.imagerag.service.PromptTestService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * {@link PromptTestService} that calls Ollama directly with the draft prompt.
 */
public class PromptTestServiceImpl
    implements PromptTestService {

  /**
   * Upper bound for the description inserted into a text prompt.
   */
  private static final int MAX_DESCRIPTION_LENGTH = 4000;

  private final OllamaClient ollamaClient;
  private final PersistenceService persistenceService;
  private final ImageStorageService imageStorageService;

  public PromptTestServiceImpl(OllamaClient ollamaClient,
                               PersistenceService persistenceService,
                               ImageStorageService imageStorageService) {
    this.ollamaClient = ollamaClient;
    this.persistenceService = persistenceService;
    this.imageStorageService = imageStorageService;
  }

  @Override
  public Result<String, Failure> run(String promptKey, UUID imageId, String draftContent,
                                     String descriptionFallback) {
    if (isImagePrompt(promptKey)) {
      if (persistenceService.findImage(imageId).isEmpty()) {
        return Result.failure(Failure.IMAGE_NOT_FOUND);
      }
      Path imagePath = imageStorageService.resolvePath(imageId);
      if (!Files.exists(imagePath)) {
        return Result.failure(Failure.FILE_NOT_FOUND);
      }
      return ollamaClient.analyzeImageWithVision(imagePath, draftContent)
          .mapError(reason -> Failure.NO_RESULT);
    }
    String description = persistenceService.findAnalysis(imageId)
        .map(SemanticAnalysis::getSummary)
        .orElse(descriptionFallback);
    String prompt = PromptTemplates.fill(draftContent, sanitizeForPrompt(description));
    return ollamaClient.generateJson(prompt)
        .mapError(reason -> Failure.NO_RESULT);
  }

  private static boolean isImagePrompt(String promptKey) {
    return VisionAnalysisServiceImpl.PROMPT_KEY.equals(promptKey)
        || VisionAnalysisServiceImpl.OCR_PROMPT_KEY.equals(promptKey);
  }

  /**
   * Neutralizes control characters and sequences that could break out of the prompt
   * structure, and caps the length.
   */
  static String sanitizeForPrompt(String raw) {
    if (raw == null) return "";
    String s = raw.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", " ");
    s = s.replace("```", "'''")
        .replace("</", "< /")
        .replace("\"", "'");
    if (s.length() > MAX_DESCRIPTION_LENGTH) s = s.substring(0, MAX_DESCRIPTION_LENGTH) + "…";
    return s;
  }
}
