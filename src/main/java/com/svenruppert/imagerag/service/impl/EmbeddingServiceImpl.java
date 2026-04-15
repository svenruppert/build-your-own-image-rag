package com.svenruppert.imagerag.service.impl;

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
  public float[] embed(String text) {
    if (text == null || text.isBlank()) {
      logger().warn("Empty text passed to embed()");
      return new float[0];
    }

    float[] vector = ollamaClient.embed(text);

    if (vector == null || vector.length == 0) {
      logger().warn("Ollama returned null/empty embedding — using zero-vector fallback");
      return new float[0]; // return empty so callers can detect and skip indexing
    }

    logger().debug("Embedded text ({} chars) -> {} dimensions", text.length(), vector.length);
    return vector;
  }
}
