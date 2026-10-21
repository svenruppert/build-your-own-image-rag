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
package com.svenruppert.imagerag.domain.enums;

/**
 * Controls which retrieval channels are active during a search-tuning run.
 * <ul>
 *   <li>{@link #SEMANTIC_ONLY} — only the vector/embedding channel is used;
 *       BM25 results are ignored.</li>
 *   <li>{@link #BM25_ONLY} — only the keyword/BM25 channel is used;
 *       vector search (and embedding) are skipped entirely.</li>
 *   <li>{@link #HYBRID} — both channels run and their scores are fused via
 *       Reciprocal Rank Fusion (default).</li>
 * </ul>
 */
public enum RetrievalMode {

  SEMANTIC_ONLY,
  BM25_ONLY,
  HYBRID;

  public String getLabel() {
    return switch (this) {
      case SEMANTIC_ONLY -> "Semantic Only";
      case BM25_ONLY -> "BM25 / Keyword Only";
      case HYBRID -> "Hybrid (Semantic + BM25)";
    };
  }

  public String getDescription() {
    return switch (this) {
      case SEMANTIC_ONLY -> "Vector embedding search only — no keyword matching";
      case BM25_ONLY -> "Keyword BM25 search only — no vector embedding";
      case HYBRID -> "Both channels fused via Reciprocal Rank Fusion";
    };
  }
}
