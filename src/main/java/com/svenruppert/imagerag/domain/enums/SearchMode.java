package com.svenruppert.imagerag.domain.enums;

/**
 * The two explicit operating modes of the Search Workbench.
 * <p>{@link #TRANSFORM_ONLY} runs LLM query understanding and stops.  The user can
 * inspect the transformation result, optionally edit parameters in the Advanced panel,
 * and decide whether to proceed to actual execution.
 * <p>{@link #TRANSFORM_AND_EXECUTE} performs the full pipeline: LLM query
 * understanding → vector search → filtering → result display.
 */
public enum SearchMode {
  TRANSFORM_ONLY,
  TRANSFORM_AND_EXECUTE
}
