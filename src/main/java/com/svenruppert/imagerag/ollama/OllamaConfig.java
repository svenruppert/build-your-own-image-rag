package com.svenruppert.imagerag.ollama;

import com.svenruppert.imagerag.bootstrap.AppConfig;

public class OllamaConfig {

  /**
   * Default model for both vision analysis and text generation.
   */
  private static final String DEFAULT_GENERATION_MODEL = "gemma4:31b";
  /**
   * Default request timeout in seconds; large models need generous limits.
   */
  public static final int DEFAULT_TIMEOUT_SECONDS = 300;

  private String host;
  private int port;
  private String visionModel;
  private String textModel;
  private String embeddingModel;
  private int timeoutSeconds;

  public OllamaConfig() {
    AppConfig cfg = AppConfig.getInstance();
    this.host = cfg.get("ollama.host", "localhost");
    this.port = Integer.parseInt(cfg.get("ollama.port", "11434"));
    this.visionModel = cfg.get("ollama.vision.model", DEFAULT_GENERATION_MODEL);
    this.textModel = cfg.get("ollama.text.model", DEFAULT_GENERATION_MODEL);
    // embedding.model: read via AppConfig so -Dembedding.model=... or imagerag.properties override works.
    this.embeddingModel = cfg.getEmbeddingModel();
    this.timeoutSeconds = parseTimeout(cfg.get("ollama.timeout.seconds",
                                                String.valueOf(DEFAULT_TIMEOUT_SECONDS)));
  }

  private static int parseTimeout(String value) {
    try {
      int seconds = Integer.parseInt(value.trim());
      return seconds > 0 ? seconds : DEFAULT_TIMEOUT_SECONDS;
    } catch (NumberFormatException e) {
      return DEFAULT_TIMEOUT_SECONDS;
    }
  }

  public String baseUrl() {
    return "http://" + host + ":" + port;
  }

  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public String getVisionModel() {
    return visionModel;
  }

  public void setVisionModel(String visionModel) {
    this.visionModel = visionModel;
  }

  public String getTextModel() {
    return textModel;
  }

  public void setTextModel(String textModel) {
    this.textModel = textModel;
  }

  public String getEmbeddingModel() {
    return embeddingModel;
  }

  public void setEmbeddingModel(String embeddingModel) {
    this.embeddingModel = embeddingModel;
  }

  public int getTimeoutSeconds() {
    return timeoutSeconds;
  }

  public void setTimeoutSeconds(int timeoutSeconds) {
    this.timeoutSeconds = timeoutSeconds;
  }
}
