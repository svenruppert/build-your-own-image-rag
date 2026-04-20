package com.svenruppert.imagerag.service.impl;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.imagerag.domain.LocationSummary;
import com.svenruppert.imagerag.service.ReverseGeocodingService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Reverse geocoding via Nominatim (OSM) — no API key required, rate-limited to 1 req/s.
 * Swap out this implementation for a commercial geocoder in production.
 */
public class ReverseGeocodingServiceImpl
    implements ReverseGeocodingService, HasLogger {

  private static final String NOMINATIM_URL = "https://nominatim.openstreetmap.org/reverse";

  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  private long lastRequestTime = 0L;

  public ReverseGeocodingServiceImpl() {
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
    this.objectMapper = new ObjectMapper();
  }

  @Override
  public LocationSummary reverseGeocode(double latitude, double longitude) {
    try {
      // Nominatim rate limit: 1 request/second
      long now = System.currentTimeMillis();
      long elapsed = now - lastRequestTime;
      if (elapsed < 1100) {
        Thread.sleep(1100 - elapsed);
      }
      lastRequestTime = System.currentTimeMillis();

      String url = NOMINATIM_URL + "?lat=" + latitude + "&lon=" + longitude
          + "&format=json&accept-language=en";

      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(url))
          .timeout(Duration.ofSeconds(10))
          .header("User-Agent", "ImageRAG-Demo/1.0")
          .GET()
          .build();

      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        logger().warn("Nominatim returned HTTP {}", response.statusCode());
        return fallback(latitude, longitude);
      }

      JsonNode json = objectMapper.readTree(response.body());
      JsonNode address = json.path("address");

      String city = firstNonNull(address, "city", "town", "village", "hamlet");
      String region = firstNonNull(address, "state", "county", "region");
      String country = address.path("country").asText(null);
      String display = json.path("display_name").asText(null);

      return new LocationSummary(latitude, longitude, city, region, country, display);

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return fallback(latitude, longitude);
    } catch (Exception e) {
      logger().error("Reverse geocoding failed for ({}, {}): {}", latitude, longitude, e.getMessage());
      return fallback(latitude, longitude);
    }
  }

  private LocationSummary fallback(double latitude, double longitude) {
    String display = String.format("%.4f, %.4f", latitude, longitude);
    return new LocationSummary(latitude, longitude, null, null, null, display);
  }

  private String firstNonNull(JsonNode node, String... keys) {
    for (String key : keys) {
      JsonNode v = node.path(key);
      if (!v.isMissingNode() && !v.isNull()) return v.asText();
    }
    return null;
  }
}
