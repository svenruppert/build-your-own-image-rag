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

import com.svenruppert.functional.result.Result;

import java.util.UUID;

/**
 * Runs a draft prompt against one stored image without changing any data, so a prompt
 * version can be evaluated before it is activated.
 */
public interface PromptTestService {

  /**
   * Why a prompt test produced no model output.
   */
  enum Failure {
    IMAGE_NOT_FOUND,
    FILE_NOT_FOUND,
    NO_RESULT
  }

  /**
   * Executes the draft and returns the raw model output.
   * <ul>
   *   <li>Image prompts (vision, OCR) are sent to the vision model together with the
   *       stored image file.</li>
   *   <li>Text prompts (semantic derivation) are filled with the image's existing vision
   *       description, or {@code descriptionFallback} when there is none, and sent to the
   *       text model.</li>
   * </ul>
   */
  Result<String, Failure> run(String promptKey, UUID imageId, String draftContent,
                              String descriptionFallback);
}
