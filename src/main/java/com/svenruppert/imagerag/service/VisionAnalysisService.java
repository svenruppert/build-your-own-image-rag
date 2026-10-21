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
import com.svenruppert.imagerag.dto.VisionAnalysisResponse;

import java.nio.file.Path;

public interface VisionAnalysisService {

  VisionAnalysisResponse analyzeImage(Path imagePath);

  /**
   * Extracts the readable text visible in the image (OCR) with a dedicated prompt.
   *
   * @return the extracted text verbatim, an empty string if the image contains no
   * readable text, or a failure carrying the reason when the model call failed
   */
  Result<String, String> extractText(Path imagePath);
}
