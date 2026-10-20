package com.svenruppert.imagerag.ollama;

import com.svenruppert.imagerag.settings.SettingsService;
import com.svenruppert.imagerag.settings.Settings;
import com.svenruppert.imagerag.bootstrap.AppConfig;

public class OllamaConfig {

  /**
   * Default model for both vision analysis and text generation.
   */
  private static final String DEFAULT_GENERATION_MODEL = "gemma4:31b";

  private String host;
  private int port;
  private String visionModel;
  private String textModel;
  private String embeddingModel;

  public OllamaConfig() {
    AppConfig cfg = AppConfig.getInstance();
    this.host = cfg.get("ollama.host", "localhost");
    this.port = Integer.parseInt(cfg.get("ollama.port", "11434"));
    this.visionModel = cfg.get("ollama.vision.model", DEFAULT_GENERATION_MODEL);
    this.textModel = cfg.get("ollama.text.model", DEFAULT_GENERATION_MODEL);
    // embedding.model: read via AppConfig so -Dembedding.model=... or imagerag.properties override works.
    this.embeddingModel = cfg.getEmbeddingModel();
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

  /**
   * Request timeout for generation and embedding calls ({@link Settings#OLLAMA_TIMEOUT_SECONDS});
   * large models need generous limits.
   */
  public int getTimeoutSeconds() {
    return SettingsService.getInstance().get(Settings.OLLAMA_TIMEOUT_SECONDS);
  }
}
