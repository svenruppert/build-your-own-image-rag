package com.svenruppert.imagerag.service;

import com.svenruppert.imagerag.dto.VectorSearchHit;

import java.util.List;
import java.util.UUID;

public interface VectorIndexService {

  void index(UUID imageId, float[] vector);

  List<VectorSearchHit> search(float[] queryVector, int limit);

  void remove(UUID imageId);
}
