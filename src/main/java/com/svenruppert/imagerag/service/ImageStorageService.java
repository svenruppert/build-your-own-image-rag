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
import com.svenruppert.functional.result.Unit;
import com.svenruppert.imagerag.dto.StoredImage;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.UUID;

public interface ImageStorageService {

  /**
   * Stores the image content under a new id. The stored extension is derived from the
   * detected content format.
   *
   * @return the stored image, or a failure carrying the reason (unsupported content,
   * I/O error)
   */
  Result<StoredImage, String> store(InputStream inputStream, String originalFilename, String mimeType);

  /**
   * Opens the stored image file.
   *
   * @return an open stream the caller must close, or a failure carrying the reason
   */
  Result<InputStream, String> load(UUID imageId);

  Path resolvePath(UUID imageId);

  /**
   * Deletes the stored image file for the given id. Succeeds as a no-op if the file does
   * not exist.
   */
  Result<Unit, String> delete(UUID imageId);
}
