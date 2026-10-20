package com.svenruppert.imagerag.service;

import com.svenruppert.functional.result.Result;
public interface EmbeddingService {

  /**
   * Computes the embedding vector for the given text.
   * A failure carries the reason (blank input, unreachable model, empty answer).
   */
  Result<float[], String> embed(String text);
}
