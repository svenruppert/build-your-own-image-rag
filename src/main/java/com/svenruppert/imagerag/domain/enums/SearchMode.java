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
