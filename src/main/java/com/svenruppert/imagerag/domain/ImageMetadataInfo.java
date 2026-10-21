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
package com.svenruppert.imagerag.domain;

import java.time.Instant;

public class ImageMetadataInfo {

  private Instant captureTimestamp;
  private String cameraModel;
  private Double latitude;
  private Double longitude;

  public ImageMetadataInfo() {
  }

  public ImageMetadataInfo(Instant captureTimestamp, String cameraModel,
                           Double latitude, Double longitude) {
    this.captureTimestamp = captureTimestamp;
    this.cameraModel = cameraModel;
    this.latitude = latitude;
    this.longitude = longitude;
  }

  public Instant getCaptureTimestamp() {
    return captureTimestamp;
  }

  public void setCaptureTimestamp(Instant captureTimestamp) {
    this.captureTimestamp = captureTimestamp;
  }

  public String getCameraModel() {
    return cameraModel;
  }

  public void setCameraModel(String cameraModel) {
    this.cameraModel = cameraModel;
  }

  public Double getLatitude() {
    return latitude;
  }

  public void setLatitude(Double latitude) {
    this.latitude = latitude;
  }

  public Double getLongitude() {
    return longitude;
  }

  public void setLongitude(Double longitude) {
    this.longitude = longitude;
  }

  public boolean hasGpsData() {
    return latitude != null && longitude != null;
  }
}
