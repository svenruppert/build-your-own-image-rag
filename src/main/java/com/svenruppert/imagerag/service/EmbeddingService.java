package com.svenruppert.imagerag.service;

public interface EmbeddingService {

  float[] embed(String text);
}
