package com.svenruppert.imagerag.service.impl;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.imagerag.domain.ImageAsset;
import com.svenruppert.imagerag.domain.LocationSummary;
import com.svenruppert.imagerag.domain.OcrResult;
import com.svenruppert.imagerag.domain.SemanticAnalysis;
import com.svenruppert.imagerag.dto.KeywordSearchHit;
import com.svenruppert.imagerag.service.KeywordIndexService;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * BM25 keyword retrieval using Apache Lucene.
 * Index is stored on disk at the path provided to the constructor.
 * Thread-safe: IndexWriter is kept open and shared; IndexSearcher is
 * re-opened (near-real-time) per search call.
 */
public class KeywordIndexServiceImpl
    implements KeywordIndexService, HasLogger {

  private static final String FIELD_ID = "id";
  private static final String FIELD_FILENAME = "filename";
  private static final String FIELD_SUMMARY = "summary";
  private static final String FIELD_TAGS = "tags";
  private static final String FIELD_CATEGORY = "category";
  private static final String FIELD_LOCATION = "location";
  private static final String FIELD_OCR = "ocr";

  private static final String[] SEARCH_FIELDS = {
      FIELD_SUMMARY, FIELD_TAGS, FIELD_FILENAME, FIELD_CATEGORY, FIELD_LOCATION, FIELD_OCR
  };
  private static final float[] FIELD_BOOSTS = {3.0f, 2.0f, 1.5f, 1.0f, 1.0f, 2.0f};

  private final StandardAnalyzer analyzer;
  private final FSDirectory directory;
  private final IndexWriter writer;

  public KeywordIndexServiceImpl(Path indexPath)
      throws IOException {
    analyzer = new StandardAnalyzer();
    directory = FSDirectory.open(indexPath);
    IndexWriterConfig config = new IndexWriterConfig(analyzer);
    config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
    writer = new IndexWriter(directory, config);
    // commit so an empty reader is always openable
    writer.commit();
    logger().info("Lucene keyword index opened at {}", indexPath);
  }

  private static void addTextField(Document doc, String field, String value) {
    if (value != null && !value.isBlank()) {
      doc.add(new TextField(field, value, Field.Store.NO));
    }
  }

  @Override
  public void index(UUID imageId, String filename, String summary, List<String> tags,
                    String categoryLabel, String locationText, String ocrText) {
    try {
      Document doc = buildDocument(imageId, filename, summary, tags,
                                   categoryLabel, locationText, ocrText);
      writer.updateDocument(new Term(FIELD_ID, imageId.toString()), doc);
      writer.commit();
    } catch (IOException e) {
      logger().error("Failed to index document for imageId={}: {}", imageId, e.getMessage(), e);
    }
  }

  @Override
  public void remove(UUID imageId) {
    try {
      writer.deleteDocuments(new Term(FIELD_ID, imageId.toString()));
      writer.commit();
    } catch (IOException e) {
      logger().error("Failed to remove document for imageId={}: {}", imageId, e.getMessage(), e);
    }
  }

  @Override
  public List<KeywordSearchHit> search(String query, int topK) {
    if (query == null || query.isBlank()) {
      return List.of();
    }
    try {
      String escaped = QueryParser.escape(query.trim());
      Query luceneQuery = new MultiFieldQueryParser(SEARCH_FIELDS, analyzer,
                                                    buildBoostMap()).parse(escaped);

      try (DirectoryReader reader = DirectoryReader.open(writer)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        TopDocs topDocs = searcher.search(luceneQuery, topK);

        if (topDocs.totalHits.value() == 0) {
          return List.of();
        }

        float maxScore = topDocs.scoreDocs[0].score;
        List<KeywordSearchHit> hits = new ArrayList<>();
        for (ScoreDoc sd : topDocs.scoreDocs) {
          Document doc = searcher.storedFields().document(sd.doc);
          String idStr = doc.get(FIELD_ID);
          if (idStr == null) continue;
          float normalized = maxScore > 0 ? sd.score / maxScore : 0f;
          hits.add(new KeywordSearchHit(UUID.fromString(idStr), normalized));
        }
        return hits;
      }
    } catch (ParseException | IOException e) {
      logger().warn("Lucene search failed for query '{}': {}", query, e.getMessage());
      return List.of();
    }
  }

  @Override
  public void close() {
    try {
      writer.close();
      directory.close();
      analyzer.close();
      logger().info("Lucene keyword index closed");
    } catch (IOException e) {
      logger().warn("Error closing Lucene index: {}", e.getMessage());
    }
  }

  @Override
  public void rebuildAll(Supplier<List<ImageAsset>> allImages,
                         Function<UUID, Optional<SemanticAnalysis>> findAnalysis,
                         Function<UUID, Optional<LocationSummary>> findLocation,
                         Function<UUID, Optional<OcrResult>> findOcr) {
    try {
      writer.deleteAll();
      writer.commit();
      int count = 0;
      for (ImageAsset asset : allImages.get()) {
        SemanticAnalysis analysis = findAnalysis.apply(asset.getId()).orElse(null);
        LocationSummary location = findLocation.apply(asset.getId()).orElse(null);
        OcrResult ocr = findOcr.apply(asset.getId()).orElse(null);

        String summary = analysis != null ? analysis.getSummary() : null;
        List<String> tags = analysis != null && analysis.getTags() != null
            ? analysis.getTags() : List.of();
        String catLabel = analysis != null && analysis.getSourceCategory() != null
            ? analysis.getSourceCategory().name() : null;
        String locText = location != null ? location.toHumanReadable() : null;
        String ocrText = ocr != null ? ocr.getExtractedText() : null;

        index(asset.getId(), asset.getOriginalFilename(), summary, tags,
              catLabel, locText, ocrText);
        count++;
      }
      logger().info("Lucene index rebuilt: {} documents", count);
    } catch (IOException e) {
      logger().error("Failed to rebuild Lucene index: {}", e.getMessage(), e);
    }
  }

  private Document buildDocument(UUID imageId, String filename, String summary,
                                 List<String> tags, String categoryLabel,
                                 String locationText, String ocrText) {
    Document doc = new Document();
    doc.add(new StringField(FIELD_ID, imageId.toString(), Field.Store.YES));
    addTextField(doc, FIELD_FILENAME, filename);
    addTextField(doc, FIELD_SUMMARY, summary);
    if (tags != null && !tags.isEmpty()) {
      addTextField(doc, FIELD_TAGS, String.join(" ", tags));
    }
    addTextField(doc, FIELD_CATEGORY, categoryLabel);
    addTextField(doc, FIELD_LOCATION, locationText);
    addTextField(doc, FIELD_OCR, ocrText);
    return doc;
  }

  private Map<String, Float> buildBoostMap() {
    Map<String, Float> boosts = new HashMap<>();
    for (int i = 0; i < SEARCH_FIELDS.length; i++) {
      boosts.put(SEARCH_FIELDS[i], FIELD_BOOSTS[i]);
    }
    return boosts;
  }
}
