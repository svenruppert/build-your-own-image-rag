package com.svenruppert.imagerag.ollama;

public class OllamaConfig {

  private String host;
  private int port;
  private String visionModel;
  private String textModel;
  private String embeddingModel;
  private int timeoutSeconds;

  public OllamaConfig() {
    this.host = "localhost";
    this.port = 11434;
    this.visionModel = "gemma4:31b";
    this.textModel = "gemma4:31b";
    this.embeddingModel = "nomic-embed-text-v2-moe";
    this.timeoutSeconds = 300; // gemma4:31b is large — needs more time
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
