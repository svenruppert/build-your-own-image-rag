package com.svenruppert.imagerag.service.impl;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.imagerag.domain.enums.SimilarityFunction;
import com.svenruppert.imagerag.dto.VectorSearchHit;
import com.svenruppert.imagerag.persistence.PersistenceService;
import com.svenruppert.imagerag.service.VectorIndexService;
import io.github.jbellis.jvector.graph.*;
import io.github.jbellis.jvector.util.Bits;
import io.github.jbellis.jvector.vector.VectorSimilarityFunction;
import io.github.jbellis.jvector.vector.VectorizationProvider;
import io.github.jbellis.jvector.vector.types.VectorFloat;
import io.github.jbellis.jvector.vector.types.VectorTypeSupport;

import java.util.*;

/**
 * Vector-search backend that combines two responsibilities:
 *
 * <ol>
 *   <li><b>Durable persistence (GigaMap role)</b> via EclipseStore — raw
 *       {@code float[]} embeddings are stored in {@code AppDataRoot.rawVectorStore},
 *       which EclipseStore persists as part of its normal data root.  This is the
 *       EclipseStore-managed, UUID-keyed map that survives application restarts
 *       without requiring the embedding model to be called again.</li>
 *   <li><b>Fast approximate-nearest-neighbour search</b> via JVector 4.x — an HNSW
 *       graph index (DiskANN-style) is built in memory from the persisted vectors at
 *       startup.  The index is kept in sync with the EclipseStore data through the
 *       {@link #index} and {@link #remove} operations.</li>
 * </ol>
 *
 * <h3>JVector 4.x notes</h3>
 * <ul>
 *   <li>{@code OnHeapGraphIndex} implements {@code MutableGraphIndex} which extends
 *       {@code ImmutableGraphIndex} — the static {@code GraphSearcher.search()}
 *       convenience method therefore accepts it directly.</li>
 *   <li>All vectors are represented as {@link VectorFloat} instances created via
 *       {@link VectorizationProvider}, which selects the best implementation
 *       (Panama-backed SIMD or Java-array fallback) at runtime.</li>
 *   <li>{@code addGraphNode(int, VectorFloat)} is the preferred 4.x API; the
 *       deprecated {@code addGraphNode(int, RandomAccessVectorValues)} overload is
 *       not used.</li>
 * </ul>
 *
 * <h3>Lifecycle</h3>
 * <ul>
 *   <li>Construction: call {@link #initialize()} to load vectors from EclipseStore
 *       and build the initial JVector HNSW index — no Ollama call required.</li>
 *   <li>{@link #index}: persists the vector to EclipseStore and rebuilds the index.</li>
 *   <li>{@link #remove}: removes from EclipseStore and rebuilds the index.</li>
 *   <li>{@link #search}: queries the in-memory JVector HNSW index.</li>
 * </ul>
 *
 * <h3>Thread safety</h3>
 * The JVector index snapshot is replaced atomically via a volatile reference.
 * Rebuilds are synchronised to prevent overlapping rebuilds while reads against a
 * previous snapshot proceed concurrently.
 */
public class EclipseStoreGigaMapJVectorBackend
    implements VectorIndexService, HasLogger {

  // ── JVector 4.x HNSW tuning parameters ───────────────────────────────────
  /**
   * Max graph connections per node (DiskANN M). Higher = better recall, more RAM.
   */
  private static final int HNSW_M = 16;
  /**
   * Construction beam width (efConstruction). Higher = better quality, slower build.
   */
  private static final int HNSW_EF_CONSTRUCTION = 100;
  /**
   * DiskANN neighborOverflow — candidate pool multiplier during build (1.2 = typical).
   */
  private static final float HNSW_NEIGHBOR_OVERFLOW = 1.2f;
  /**
   * DiskANN alpha — long-range pruning parameter (1.2 = typical production value).
   */
  private static final float HNSW_ALPHA = 1.2f;

  /**
   * JVector {@link VectorTypeSupport} singleton.
   * Selects the best float-vector implementation at JVM startup (Panama SIMD or plain array).
   */
  private static final VectorTypeSupport VT = VectorizationProvider.getInstance()
      .getVectorTypeSupport();

  private final PersistenceService persistenceService;
  /**
   * Guards concurrent rebuilds so only one runs at a time.
   */
  private final Object rebuildLock = new Object();
  /**
   * Live JVector index snapshot; replaced atomically after each rebuild.
   * {@code null} before the first successful build (empty store).
   */
  private volatile JVectorIndexSnapshot currentIndex = null;

  public EclipseStoreGigaMapJVectorBackend(PersistenceService persistenceService) {
    this.persistenceService = persistenceService;
  }

  // ── VectorIndexService ────────────────────────────────────────────────────

  /**
   * Persists {@code vector} to the EclipseStore GigaMap (raw-vector store) and
   * rebuilds the JVector HNSW index so the image is immediately searchable.
   */
  @Override
  public void index(UUID imageId, float[] vector) {
    if (vector == null || vector.length == 0) {
      logger().warn("Skipping indexing for {} — empty vector", imageId);
      return;
    }
    persistenceService.saveRawVector(imageId, vector);
    rebuildIndex();
    logger().debug("Indexed vector for {} (dim={}) in EclipseStore + JVector 4.x",
                   imageId, vector.length);
  }

  /**
   * Removes the vector from the EclipseStore GigaMap and rebuilds the JVector HNSW
   * index so the image no longer appears in search results.
   */
  @Override
  public void remove(UUID imageId) {
    persistenceService.removeRawVector(imageId);
    rebuildIndex();
    logger().debug("Removed vector for {} from EclipseStore + JVector 4.x", imageId);
  }

  /**
   * Queries the current JVector HNSW index for the {@code limit} nearest neighbours
   * of {@code queryVector}.  Returns an empty list if the index has not been built.
   */
  @Override
  public List<VectorSearchHit> search(float[] queryVector, int limit) {
    if (queryVector == null || queryVector.length == 0) {
      return List.of();
    }
    JVectorIndexSnapshot idx = currentIndex;
    if (idx == null || idx.isEmpty()) {
      logger().debug("JVector index is empty — returning no results");
      return List.of();
    }
    return idx.search(queryVector, limit);
  }

  /**
   * Search with an explicit similarity function.
   *
   * <p>COSINE delegates to the fast JVector HNSW graph index.  DOT_PRODUCT and
   * EUCLIDEAN perform a brute-force scan over the raw vectors stored in EclipseStore
   * via {@link PersistenceService#findAllRawVectors()}.  The brute-force path is
   * accurate but slower — it is intended only for the Search Tuning Lab.
   */
  @Override
  public List<VectorSearchHit> search(float[] queryVector, int limit, SimilarityFunction fn) {
    if (queryVector == null || queryVector.length == 0) return List.of();
    if (fn == SimilarityFunction.COSINE) {
      return search(queryVector, limit); // fast HNSW path
    }
    // Brute-force for non-cosine functions (tuning lab use only)
    Map<UUID, float[]> all = persistenceService.findAllRawVectors();
    if (all.isEmpty()) return List.of();

    List<VectorSearchHit> results = new ArrayList<>(all.size());
    for (Map.Entry<UUID, float[]> entry : all.entrySet()) {
      double score = switch (fn) {
        case DOT_PRODUCT -> dotProduct(queryVector, entry.getValue());
        case EUCLIDEAN   -> euclideanSimilarity(queryVector, entry.getValue());
        default          -> 0.0;
      };
      results.add(new VectorSearchHit(entry.getKey(), score));
    }
    results.sort(Comparator.naturalOrder()); // VectorSearchHit.compareTo() = descending
    return new ArrayList<>(results.subList(0, Math.min(limit, results.size())));
  }

  private double dotProduct(float[] a, float[] b) {
    int len = Math.min(a.length, b.length);
    double dot = 0.0;
    for (int i = 0; i < len; i++) dot += (double) a[i] * b[i];
    return dot;
  }

  private double euclideanSimilarity(float[] a, float[] b) {
    int len = Math.min(a.length, b.length);
    double sumSq = 0.0;
    for (int i = 0; i < len; i++) {
      double d = (double) a[i] - b[i];
      sumSq += d * d;
    }
    return 1.0 / (1.0 + Math.sqrt(sumSq));
  }

  // ── Startup initialisation ────────────────────────────────────────────────

  /**
   * Loads all raw vectors from EclipseStore and builds the initial JVector HNSW index.
   * Must be called once after construction, before the first {@link #search}.
   * Unlike the in-memory backend's {@code restoreVectorIndex()}, this does <em>not</em>
   * require Ollama — vectors are read directly from the EclipseStore GigaMap.
   */
  public void initialize() {
    Map<UUID, float[]> stored = persistenceService.findAllRawVectors();
    if (stored.isEmpty()) {
      logger().info("EclipseStore GigaMap raw-vector store is empty — JVector index not built. "
                        + "Vectors will be indexed as images are processed.");
      return;
    }
    logger().info("Building JVector 4.x HNSW index from {} vectors in EclipseStore...",
                  stored.size());
    currentIndex = JVectorIndexSnapshot.build(stored);
    logger().info("JVector 4.x HNSW index ready: {} vectors indexed", stored.size());
  }

  /**
   * Returns {@code true} if no raw vectors have been persisted in the EclipseStore
   * GigaMap yet. Used by {@code ServiceRegistry} to detect when a one-time migration
   * from the in-memory backend is needed (re-embed and persist all existing analyses).
   */
  public boolean isEmpty() {
    return persistenceService.getRawVectorCount() == 0;
  }

  // ── Internal rebuild ──────────────────────────────────────────────────────

  /**
   * Rebuilds the JVector HNSW index from the current EclipseStore GigaMap contents.
   * Synchronised to prevent concurrent rebuilds; publishes the new index atomically.
   */
  private void rebuildIndex() {
    synchronized (rebuildLock) {
      Map<UUID, float[]> all = persistenceService.findAllRawVectors();
      if (all.isEmpty()) {
        currentIndex = null;
        return;
      }
      currentIndex = JVectorIndexSnapshot.build(all);
      logger().debug("JVector 4.x HNSW index rebuilt: {} vectors", all.size());
    }
  }

  // ── JVector index snapshot ────────────────────────────────────────────────

  /**
   * Immutable snapshot of the JVector HNSW graph together with the data structures
   * needed to map integer JVector node-IDs back to application UUIDs.
   *
   * <p>JVector identifies nodes by sequential {@code int} IDs.  This class maintains
   * the mapping: {@code nodeId → UUID} as a {@code List<UUID>} where the list index
   * equals the JVector node ID assigned during construction.
   *
   * <p>In JVector 4.x, {@code GraphIndexBuilder.getGraph()} returns an
   * {@code ImmutableGraphIndex} (the declared return type); {@code OnHeapGraphIndex}
   * implements {@code MutableGraphIndex} which extends {@code ImmutableGraphIndex},
   * so the built graph satisfies the static {@code GraphSearcher.search()} parameter.
   */
  private static class JVectorIndexSnapshot {

    /**
     * JVector 4.x: getGraph() return type is ImmutableGraphIndex.
     */
    private final ImmutableGraphIndex graph;
    /**
     * Maps JVector node-int-id → application UUID. Index = node ID.
     */
    private final List<UUID> nodeToUuid;
    /**
     * Pre-converted JVector float vectors in node-ID order, reused for search scoring.
     */
    private final FloatVectorValues ravv;

    private JVectorIndexSnapshot(ImmutableGraphIndex graph,
                                 List<UUID> nodeToUuid,
                                 FloatVectorValues ravv) {
      this.graph = graph;
      this.nodeToUuid = nodeToUuid;
      this.ravv = ravv;
    }

    /**
     * Builds a JVector 4.x HNSW index from the supplied {@code UUID → float[]} map.
     *
     * <p>Node IDs are assigned by insertion order of the map's entry set.
     * Each {@code float[]} is wrapped in a JVector {@link VectorFloat} via
     * {@link VectorizationProvider} so JVector can apply SIMD optimisations.
     */
    static JVectorIndexSnapshot build(Map<UUID, float[]> vectors) {
      List<UUID> uuids = new ArrayList<>(vectors.size());
      List<VectorFloat<?>> vfList = new ArrayList<>(vectors.size());

      for (Map.Entry<UUID, float[]> entry : vectors.entrySet()) {
        uuids.add(entry.getKey());
        vfList.add(VT.createFloatVector(entry.getValue()));
      }

      int size = vfList.size();
      int dim = vfList.get(0).length();
      FloatVectorValues ravv = new FloatVectorValues(vfList, dim);

      // JVector 4.x constructor: boolean addHierarchy enables the HNSW multi-layer structure
      // on top of the Vamana base graph, improving recall especially for larger collections.
      GraphIndexBuilder builder = new GraphIndexBuilder(
          ravv, VectorSimilarityFunction.COSINE,
          HNSW_M, HNSW_EF_CONSTRUCTION, HNSW_NEIGHBOR_OVERFLOW, HNSW_ALPHA,
          true /* addHierarchy */);

      for (int nodeId = 0; nodeId < size; nodeId++) {
        // JVector 4.x: addGraphNode(int, VectorFloat<?>) is the primary API
        builder.addGraphNode(nodeId, vfList.get(nodeId));
      }

      // getGraph() return type in JVector 4.x is ImmutableGraphIndex
      ImmutableGraphIndex graph = builder.getGraph();
      return new JVectorIndexSnapshot(graph, uuids, ravv);
    }

    boolean isEmpty() {
      return nodeToUuid.isEmpty();
    }

    /**
     * Queries the JVector HNSW index for the {@code limit} nearest neighbours of
     * {@code rawQuery}.
     *
     * <p>In JVector 4.x the static {@code GraphSearcher.search()} convenience method
     * accepts an {@code ImmutableGraphIndex}. Because {@code OnHeapGraphIndex} implements
     * {@code MutableGraphIndex} which extends {@code ImmutableGraphIndex}, the in-memory
     * graph is passed directly without any conversion.
     *
     * @return ranked {@link VectorSearchHit} list, highest cosine similarity first
     */
    List<VectorSearchHit> search(float[] rawQuery, int limit) {
      VectorFloat<?> queryVF = VT.createFloatVector(rawQuery);

      // GraphSearcher.search() static convenience method — JVector 4.x signature:
      // search(VectorFloat<?>, int topK, RandomAccessVectorValues, VectorSimilarityFunction,
      //        ImmutableGraphIndex, Bits)
      //
      // OnHeapGraphIndex satisfies ImmutableGraphIndex because in JVector 4.x:
      //   MutableGraphIndex extends ImmutableGraphIndex
      //   OnHeapGraphIndex implements MutableGraphIndex
      SearchResult results = GraphSearcher.search(
          queryVF, limit, ravv,
          VectorSimilarityFunction.COSINE, graph, Bits.ALL);

      List<VectorSearchHit> hits = new ArrayList<>();
      for (SearchResult.NodeScore ns : results.getNodes()) {
        if (ns.node >= 0 && ns.node < nodeToUuid.size()) {
          hits.add(new VectorSearchHit(nodeToUuid.get(ns.node), ns.score));
        }
      }

      Collections.sort(hits);  // VectorSearchHit.compareTo() is descending by score
      return hits.subList(0, Math.min(limit, hits.size()));
    }
  }

  // ── RandomAccessVectorValues implementation ───────────────────────────────

  /**
   * Wraps a {@code List<VectorFloat<?>>} as JVector's {@link RandomAccessVectorValues}
   * interface, required by both {@link GraphIndexBuilder} (for build-time scoring) and
   * {@link GraphSearcher} (for search-time reranking).
   *
   * <p>In JVector 4.x, {@code RandomAccessVectorValues} is not generic; {@link #getVector}
   * returns {@code VectorFloat<?>} directly.  Each element is an independent
   * {@link VectorFloat} object created via {@link VectorizationProvider}, so
   * {@link #isValueShared()} returns {@code false}.
   */
  private static class FloatVectorValues
      implements RandomAccessVectorValues {

    private final List<VectorFloat<?>> vectors;
    private final int dimension;

    FloatVectorValues(List<VectorFloat<?>> vectors, int dimension) {
      this.vectors = vectors;
      this.dimension = dimension;
    }

    @Override
    public int size() {
      return vectors.size();
    }

    @Override
    public int dimension() {
      return dimension;
    }

    /**
     * Returns the pre-converted {@link VectorFloat} for {@code nodeId}.
     */
    @Override
    public VectorFloat<?> getVector(int nodeId) {
      return vectors.get(nodeId);
    }

    /**
     * Returns {@code false}: each {@link #getVector} call returns a distinct, stable
     * {@link VectorFloat} object — JVector does not need to copy it defensively.
     */
    @Override
    public boolean isValueShared() {
      return false;
    }

    @Override
    public FloatVectorValues copy() {
      return new FloatVectorValues(new ArrayList<>(vectors), dimension);
    }
  }
}
