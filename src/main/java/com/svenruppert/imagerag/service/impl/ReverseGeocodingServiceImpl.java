package com.svenruppert.imagerag.service.impl;

import com.svenruppert.imagerag.settings.SettingsService;
import com.svenruppert.imagerag.settings.Settings;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.dependencies.core.net.HttpStatus;
import com.svenruppert.imagerag.domain.LocationSummary;
import com.svenruppert.imagerag.service.ReverseGeocodingService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Reverse geocoding via Nominatim (OSM) — no API key required, rate-limited to 1 req/s.
 * Swap out this implementation for a commercial geocoder in production.
 */
public class ReverseGeocodingServiceImpl
    implements ReverseGeocodingService, HasLogger {

  private static final String NOMINATIM_URL = "https://nominatim.openstreetmap.org/reverse";
  /**
   * Rebuilt when the timeout setting changes.
   */
  private volatile HttpClient httpClient;
  private final ObjectMapper objectMapper;
  /**
   * Serializes requests across ingestion workers so the rate limit holds globally.
   */
  private final ReentrantLock rateLimitLock = new ReentrantLock(true);
  /**
   * Monotonic timestamp of the last request; guarded by {@link #rateLimitLock}.
   */
  private long lastRequestNanos = System.nanoTime() - TimeUnit.MILLISECONDS.toNanos(minRequestIntervalMillis());

  public ReverseGeocodingServiceImpl() {
    this.httpClient = buildHttpClient();
    this.objectMapper = new ObjectMapper();
    SettingsService.getInstance().addListener(change -> {
      if (change.setting() == Settings.GEOCODING_TIMEOUT_SECONDS) {
        httpClient = buildHttpClient();
      }
    });
  }

  private static HttpClient buildHttpClient() {
    return HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(requestTimeoutSeconds()))
        .build();
  }

  /**
   * Minimum gap between two Nominatim requests (usage policy: at most one request per
   * second); see {@link Settings#GEOCODING_MIN_INTERVAL_MILLIS}.
   */
  private static long minRequestIntervalMillis() {
    return SettingsService.getInstance().get(Settings.GEOCODING_MIN_INTERVAL_MILLIS);
  }

  /**
   * Connect and request timeout for Nominatim calls ({@link Settings#GEOCODING_TIMEOUT_SECONDS}).
   */
  private static int requestTimeoutSeconds() {
    return SettingsService.getInstance().get(Settings.GEOCODING_TIMEOUT_SECONDS);
  }

  @Override
  public LocationSummary reverseGeocode(double latitude, double longitude) {
    try {
      awaitRequestSlot();

      String url = NOMINATIM_URL + "?lat=" + latitude + "&lon=" + longitude
          + "&format=json&accept-language=en";

      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(url))
          .timeout(Duration.ofSeconds(requestTimeoutSeconds()))
          .header("User-Agent", "ImageRAG-Demo/1.0")
          .GET()
          .build();

      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != HttpStatus.OK.code()) {
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

  /**
   * Blocks until the next request is allowed by the Nominatim rate limit.
   */
  private void awaitRequestSlot()
      throws InterruptedException {
    rateLimitLock.lockInterruptibly();
    try {
      long minGap = TimeUnit.MILLISECONDS.toNanos(minRequestIntervalMillis());
      long wait = lastRequestNanos + minGap - System.nanoTime();
      if (wait > 0) {
        TimeUnit.NANOSECONDS.sleep(wait);
      }
      lastRequestNanos = System.nanoTime();
    } finally {
      rateLimitLock.unlock();
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
