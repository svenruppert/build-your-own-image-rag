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
