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
package com.svenruppert.imagerag.service;

import com.svenruppert.imagerag.dto.KeywordIndexDocument;
import com.svenruppert.imagerag.dto.KeywordSearchHit;

import java.util.List;
import java.util.UUID;

/**
 * BM25/Lucene keyword retrieval service.
 * Complements the vector index for hybrid retrieval.
 */
public interface KeywordIndexService {

  /**
   * Index or re-index a document for BM25 retrieval.
   */
  void index(UUID imageId, String filename, String summary, List<String> tags,
             String categoryLabel, String locationText, String ocrText);

  default void index(KeywordIndexDocument document) {
    if (document == null) {
      return;
    }
    index(document.imageId(), document.filename(), document.summary(), document.tags(),
          document.categoryLabel(), document.locationText(), document.ocrText());
  }

  /**
   * Remove an image from the keyword index.
   */
  void remove(UUID imageId);

  /**
   * Search the keyword index using BM25 scoring.
   *
   * @param query user query or embedding text
   * @param topK  max hits to return
   * @return ranked list of hits with normalized scores
   */
  List<KeywordSearchHit> search(String query, int topK);

  /**
   * Close/flush the index — call at shutdown.
   */
  void close();

  /**
   * Rebuild the entire index from scratch.
   */
  void rebuildAll(java.util.function.Supplier<List<com.svenruppert.imagerag.domain.ImageAsset>> allImages,
                  java.util.function.Function<UUID, java.util.Optional<com.svenruppert.imagerag.domain.SemanticAnalysis>> findAnalysis,
                  java.util.function.Function<UUID, java.util.Optional<com.svenruppert.imagerag.domain.LocationSummary>> findLocation,
                  java.util.function.Function<UUID, java.util.Optional<com.svenruppert.imagerag.domain.OcrResult>> findOcr);
}
