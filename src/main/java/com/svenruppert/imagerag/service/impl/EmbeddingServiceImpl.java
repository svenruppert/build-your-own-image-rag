package com.svenruppert.imagerag.service.impl;

import com.svenruppert.functional.result.Result;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.imagerag.ollama.OllamaClient;
import com.svenruppert.imagerag.service.EmbeddingService;

public class EmbeddingServiceImpl
    implements EmbeddingService, HasLogger {

  private final OllamaClient ollamaClient;

  public EmbeddingServiceImpl(OllamaClient ollamaClient) {
    this.ollamaClient = ollamaClient;
  }

  @Override
  public Result<float[], String> embed(String text) {
    if (text == null || text.isBlank()) {
      return Result.failure("empty text");
    }
    return ollamaClient.embed(text)
        .flatMap(vector -> vector.length > 0
            ? Result.<float[], String>success(vector)
            : Result.<float[], String>failure("empty embedding returned"))
        .peek(vector -> logger().debug("Embedded text ({} chars) -> {} dimensions",
                                       text.length(), vector.length));
  }
}
