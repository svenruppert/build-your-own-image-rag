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

public class LocationSummary {

  private Double latitude;
  private Double longitude;
  private String city;
  private String region;
  private String country;
  private String displayName;

  public LocationSummary() {
  }

  public LocationSummary(Double latitude, Double longitude, String city,
                         String region, String country, String displayName) {
    this.latitude = latitude;
    this.longitude = longitude;
    this.city = city;
    this.region = region;
    this.country = country;
    this.displayName = displayName;
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

  public String getCity() {
    return city;
  }

  public void setCity(String city) {
    this.city = city;
  }

  public String getRegion() {
    return region;
  }

  public void setRegion(String region) {
    this.region = region;
  }

  public String getCountry() {
    return country;
  }

  public void setCountry(String country) {
    this.country = country;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public String toHumanReadable() {
    if (city != null && country != null) return city + ", " + country;
    if (displayName != null) return displayName;
    if (latitude != null && longitude != null) return latitude + ", " + longitude;
    return "Unknown location";
  }
}
