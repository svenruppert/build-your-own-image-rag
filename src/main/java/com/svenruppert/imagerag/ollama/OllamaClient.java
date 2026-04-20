package com.svenruppert.imagerag.ollama;

import com.svenruppert.dependencies.core.logger.HasLogger;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

public class OllamaClient
    implements HasLogger {

  /**
   * Safety cap on image bytes sent to the vision model (50 MiB).
   */
  private static final long MAX_VISION_IMAGE_BYTES = 50L * 1024 * 1024;

  private final OllamaConfig config;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  public OllamaClient(OllamaConfig config) {
    this.config = config;
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build();
    this.objectMapper = new ObjectMapper();
  }

  /**
   * Sends an image to Ollama for vision-based semantic description.
   * Returns the raw text description, or empty on failure.
   */
  public Optional<String> analyzeImageWithVision(Path imagePath, String prompt) {
    try {
      long size = Files.size(imagePath);
      if (size > MAX_VISION_IMAGE_BYTES) {
        logger().warn("Refusing vision analysis for {}: size {} exceeds limit {}",
                      imagePath.getFileName(), size, MAX_VISION_IMAGE_BYTES);
        return Optional.empty();
      }
      byte[] imageBytes = Files.readAllBytes(imagePath);
      String base64Image = Base64.getEncoder().encodeToString(imageBytes);

      ObjectNode body = objectMapper.createObjectNode();
      body.put("model", config.getVisionModel());
      body.put("prompt", prompt);
      body.put("stream", false);

      ArrayNode images = body.putArray("images");
      images.add(base64Image);

      String responseJson = postJson(config.baseUrl() + "/api/generate", body.toString());
      JsonNode response = objectMapper.readTree(responseJson);
      String text = response.path("response").asText("").trim();

      if (text.isEmpty()) {
        logger().warn("Empty vision response from Ollama for {}", imagePath.getFileName());
        return Optional.empty();
      }
      return Optional.of(text);

    } catch (Exception e) {
      logger().error("Vision analysis failed for {}: {}", imagePath.getFileName(), e.getMessage());
      return Optional.empty();
    }
  }

  /**
   * Sends a text prompt to Ollama and expects a JSON response.
   * Returns the raw JSON string, or empty on failure.
   */
  public Optional<String> generateJson(String prompt) {
    try {
      ObjectNode body = objectMapper.createObjectNode();
      body.put("model", config.getTextModel());
      body.put("prompt", prompt);
      body.put("stream", false);

      ObjectNode format = objectMapper.createObjectNode();
      format.put("type", "object");
      body.set("format", format);

      String responseJson = postJson(config.baseUrl() + "/api/generate", body.toString());
      JsonNode response = objectMapper.readTree(responseJson);
      String text = response.path("response").asText("").trim();

      if (text.isEmpty()) {
        logger().warn("Empty JSON response from Ollama for query understanding");
        return Optional.empty();
      }
      return Optional.of(text);

    } catch (Exception e) {
      logger().error("JSON generation failed: {}", e.getMessage());
      return Optional.empty();
    }
  }

  /**
   * Computes an embedding vector for the given text using Ollama's embed endpoint.
   * Returns null on failure.
   */
  public float[] embed(String text) {
    try {
      ObjectNode body = objectMapper.createObjectNode();
      body.put("model", config.getEmbeddingModel());
      body.put("input", text);

      String responseJson = postJson(config.baseUrl() + "/api/embed", body.toString());
      JsonNode response = objectMapper.readTree(responseJson);
      JsonNode embeddingsNode = response.path("embeddings");

      if (embeddingsNode.isMissingNode() || !embeddingsNode.isArray() || embeddingsNode.isEmpty()) {
        logger().warn("No embeddings returned from Ollama for text: {}", text.substring(0, Math.min(50, text.length())));
        return null;
      }

      JsonNode firstEmbedding = embeddingsNode.get(0);
      float[] vector = new float[firstEmbedding.size()];
      for (int i = 0; i < firstEmbedding.size(); i++) {
        vector[i] = (float) firstEmbedding.get(i).asDouble();
      }
      return vector;

    } catch (Exception e) {
      logger().error("Embedding failed: {}", e.getMessage());
      return null;
    }
  }

  /**
   * Checks if Ollama is reachable.
   */
  public boolean isAvailable() {
    try {
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(config.baseUrl() + "/api/tags"))
          .timeout(Duration.ofSeconds(5))
          .GET()
          .build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      return response.statusCode() == 200;
    } catch (Exception e) {
      logger().warn("Ollama not reachable at {}: {}", config.baseUrl(), e.getMessage());
      return false;
    }
  }

  private String postJson(String url, String jsonBody)
      throws IOException, InterruptedException {
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
        .build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() != 200) {
      throw new IOException("Ollama returned HTTP " + response.statusCode() + ": " + response.body());
    }
    return response.body();
  }
}
