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
