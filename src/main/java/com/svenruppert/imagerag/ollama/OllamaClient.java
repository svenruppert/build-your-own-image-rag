package com.svenruppert.imagerag.ollama;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.dependencies.core.net.HttpStatus;
import com.svenruppert.dependencies.core.net.MediaType;
import com.svenruppert.functional.result.Result;
import com.svenruppert.functional.result.functions.CheckedSupplier;
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
   * Returns the raw text description, or a failure carrying the reason.
   */
  public Result<String, String> analyzeImageWithVision(Path imagePath, String prompt) {
    CheckedSupplier<Long> size = () -> Files.size(imagePath);
    return size.get()
        .mapError(OllamaClient::describe)
        .flatMap(bytes -> bytes <= MAX_VISION_IMAGE_BYTES
            ? Result.<Long, String>success(bytes)
            : Result.<Long, String>failure(
                "size " + bytes + " exceeds limit " + MAX_VISION_IMAGE_BYTES))
        .flatMap(ignored -> generate(() -> {
          ObjectNode body = objectMapper.createObjectNode();
          body.put("model", config.getVisionModel());
          body.put("prompt", prompt);
          body.put("stream", false);
          ArrayNode images = body.putArray("images");
          images.add(Base64.getEncoder().encodeToString(Files.readAllBytes(imagePath)));
          return body;
        }))
        .peekFailure(reason -> logger().error("Vision analysis failed for {}: {}",
                                              imagePath.getFileName(), reason));
  }

  /**
   * Sends a text prompt to Ollama and expects a JSON response.
   * Returns the raw JSON string, or a failure carrying the reason.
   */
  public Result<String, String> generateJson(String prompt) {
    return generate(() -> {
      ObjectNode body = objectMapper.createObjectNode();
      body.put("model", config.getTextModel());
      body.put("prompt", prompt);
      body.put("stream", false);
      ObjectNode format = objectMapper.createObjectNode();
      format.put("type", "object");
      body.set("format", format);
      return body;
    }).peekFailure(reason -> logger().error("JSON generation failed: {}", reason));
  }

  /**
   * Computes an embedding vector for the given text using Ollama's embed endpoint.
   * Returns the vector, or a failure carrying the reason.
   */
  public Result<float[], String> embed(String text) {
    CheckedSupplier<JsonNode> call = () -> {
      ObjectNode body = objectMapper.createObjectNode();
      body.put("model", config.getEmbeddingModel());
      body.put("input", text);
      return objectMapper.readTree(postJson(config.baseUrl() + "/api/embed", body.toString()));
    };
    return call.get()
        .mapError(OllamaClient::describe)
        .flatMap(response -> {
          JsonNode embeddings = response.path("embeddings");
          if (!embeddings.isArray() || embeddings.isEmpty()) {
            return Result.<float[], String>failure("no embeddings returned for text: "
                                                       + text.substring(0, Math.min(50, text.length())));
          }
          JsonNode first = embeddings.get(0);
          float[] vector = new float[first.size()];
          for (int i = 0; i < first.size(); i++) {
            vector[i] = (float) first.get(i).asDouble();
          }
          return Result.<float[], String>success(vector);
        })
        .peekFailure(reason -> logger().error("Embedding failed: {}", reason));
  }

  /**
   * Strips a Markdown code fence (for example {@code ```json ... ```}) that
   * models sometimes wrap around a JSON answer.
   */
  public static String stripCodeFence(String json) {
    String cleaned = json.trim();
    if (cleaned.startsWith("```")) {
      cleaned = cleaned.replaceFirst("```[a-zA-Z]*\\n?", "").replaceAll("```$", "").trim();
    }
    return cleaned;
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
      return response.statusCode() == HttpStatus.OK.code();
    } catch (Exception e) {
      logger().warn("Ollama not reachable at {}: {}", config.baseUrl(), e.getMessage());
      return false;
    }
  }

  /**
   * Posts a request body to {@code /api/generate} and extracts the non-blank
   * {@code response} text.
   */
  private Result<String, String> generate(CheckedSupplier<ObjectNode> body) {
    CheckedSupplier<JsonNode> call = () -> objectMapper.readTree(
        postJson(config.baseUrl() + "/api/generate", body.getWithException().toString()));
    return call.get()
        .mapError(OllamaClient::describe)
        .map(response -> response.path("response").asText("").trim())
        .flatMap(text -> text.isEmpty()
            ? Result.<String, String>failure("empty response from Ollama")
            : Result.<String, String>success(text));
  }

  /**
   * Turns a captured throwable into a log-friendly reason and restores the
   * interrupt flag when the call was interrupted.
   */
  private static String describe(Throwable throwable) {
    if (throwable instanceof InterruptedException) {
      Thread.currentThread().interrupt();
    }
    return throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
  }

  private String postJson(String url, String jsonBody)
      throws IOException, InterruptedException {
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
        .header("Content-Type", MediaType.APPLICATION_JSON.mime())
        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
        .build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() != HttpStatus.OK.code()) {
      throw new IOException("Ollama returned HTTP "
          + HttpStatus.fromCode(response.statusCode()) + ": " + response.body());
    }
    return response.body();
  }
}
