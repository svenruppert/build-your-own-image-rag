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
package com.svenruppert.imagerag.dto;

import java.time.Instant;

public record ExtractedMetadata(
    int width,
    int height,
    boolean exifPresent,
    boolean gpsPresent,
    Double latitude,
    Double longitude,
    Instant captureTimestamp,
    String cameraModel
) {
  public static ExtractedMetadata minimal(int width, int height) {
    return new ExtractedMetadata(width, height, false, false, null, null, null, null);
  }
}
