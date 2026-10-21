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

import com.svenruppert.imagerag.dto.ExtractedMetadata;

import java.nio.file.Path;

public interface MetadataExtractionService {

  /**
   * Extracts dimensions, EXIF and GPS data on a best-effort basis. Never fails: values
   * that cannot be read stay empty (zero dimensions, {@code null} fields).
   */
  ExtractedMetadata extract(Path imagePath);
}
