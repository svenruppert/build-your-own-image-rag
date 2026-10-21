/*
 * Copyright © 2026 Sven Ruppert (sven.ruppert@gmail.com)
 *
 * Licensed under the EUPL, Version 1.2 (the "Licence");
 * you may not use this file except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *     https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */
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
