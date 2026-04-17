package com.svenruppert.imagerag.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.imagerag.domain.*;
import com.svenruppert.imagerag.domain.enums.CategoryLifecycleState;
import com.svenruppert.imagerag.domain.enums.SourceCategory;
import com.svenruppert.imagerag.ollama.OllamaClient;
import com.svenruppert.imagerag.ollama.OllamaConfig;
import com.svenruppert.imagerag.persistence.PersistenceService;
import com.svenruppert.imagerag.service.TaxonomyAnalysisProgress;
import com.svenruppert.imagerag.service.TaxonomyAnalysisService;

import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Heuristic + optional-LLM taxonomy analysis.
 *
 * <h3>Analysis strategy</h3>
 * <ol>
 *   <li>Resolve the scope to the matching image set.</li>
 *   <li>For each image check:
 *     <ul>
 *       <li>Primary category is {@code UNKNOWN} or {@code MIXED} → ask LLM for better category.</li>
 *       <li>Stored {@link CategoryConfidence} has a better alternative (higher score than primary)
 *           → suggest reclassification with LLM rationale.</li>
 *       <li>Primary category has a {@link com.svenruppert.imagerag.domain.enums.CategoryLifecycleState#DEPRECATED}
 *           metadata record → suggest reassignment to the registered replacement.</li>
 *     </ul>
 *   </li>
 *   <li>Taxonomy-level checks: all images with deprecated primary categories generate
 *       {@link com.svenruppert.imagerag.domain.enums.TaxonomySuggestionType#REASSIGN_DEPRECATED_CATEGORY}
 *       suggestions even when they are outside the image-level scope.</li>
 * </ol>
 */
public class TaxonomyAnalysisServiceImpl
    implements TaxonomyAnalysisService, HasLogger {

  private static final String MODEL_NAME = "TaxonomyAnalysisService";

  /**
   * LLM prompt to re-evaluate a category assignment.
   */
  private static final String REEVAL_PROMPT_TEMPLATE = """
      You are a taxonomy expert for a photo archive.
      Image description: "%s"
      Current category: %s
      Evaluate the current category assignment and suggest a better one if needed.
      Return ONLY valid JSON (no markdown):
      {
        "primaryConfidence": <float 0.0-1.0>,
        "suggestedCategory": "<CATEGORY or same as current if correct>",
        "rationale": "<one sentence explanation>",
        "alternatives": [
          {"category": "<CATEGORY>", "confidence": <float>}
        ]
      }
      Allowed categories: LANDSCAPE, MOUNTAIN, FOREST, BEACH_COASTAL, DESERT, RIVER_WATER,
      LAKE_POND, SKY_CLOUDS, FIELD_MEADOW, PLANT_BOTANICAL, SNOW_ICE, ROCK_GEOLOGY, FLOWER,
      BIRD, MAMMAL_DOMESTIC, MAMMAL_WILD, REPTILE, INSECT, MARINE_LIFE,
      PORTRAIT, GROUP_PEOPLE, CROWD, WORK_PROFESSIONAL, SPORT_ACTIVITY, FAMILY_CHILD,
      ARCHITECTURE_EXTERIOR, ARCHITECTURE_INTERIOR, BRIDGE_INFRASTRUCTURE, MONUMENT_HISTORIC,
      PARK_GARDEN, MARKET_COMMERCIAL, NIGHT_SCENE, CITY,
      CAR, TRUCK_HEAVY, MOTORCYCLE, BICYCLE, AIRCRAFT, WATERCRAFT, PUBLIC_TRANSPORT,
      ELECTRONICS, INDUSTRIAL_MACHINERY, MEDICAL_EQUIPMENT,
      DOCUMENT_TEXT, SIGN_SIGNAGE, FOOD_DRINK, ARTWORK_GRAPHIC,
      SPORT_EVENT, OUTDOOR_ACTIVITY, CEREMONY_RITUAL,
      MIXED, UNKNOWN
      """;

  private final PersistenceService persistenceService;
  private final OllamaClient ollamaClient;
  private final OllamaConfig ollamaConfig;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public TaxonomyAnalysisServiceImpl(PersistenceService persistenceService,
                                     OllamaClient ollamaClient,
                                     OllamaConfig ollamaConfig) {
    this.persistenceService = persistenceService;
    this.ollamaClient = ollamaClient;
    this.ollamaConfig = ollamaConfig;
  }

  // ── Public API ────────────────────────────────────────────────────────────

  @Override
  public List<TaxonomySuggestion> analyze(TaxonomyAnalysisScope scope,
                                          Consumer<TaxonomyAnalysisProgress> progressCallback) {
    TaxonomyAnalysisProgress progress = new TaxonomyAnalysisProgress(false);
    try {
      persistenceService.clearTaxonomySuggestions();
      List<TaxonomySuggestion> suggestions = generateSuggestions(scope, progress, progressCallback);
      suggestions.forEach(persistenceService::saveTaxonomySuggestion);
      logger().info("Taxonomy analysis complete: {} suggestion(s) generated and persisted",
                    suggestions.size());

      progress.setSuggestionsGenerated(suggestions.size());
      progress.markCompleted(TaxonomyAnalysisProgress.STEP_SUGGESTIONS,
                             suggestions.size() + " suggestion(s) generated and saved");
      progress.markCompleted(TaxonomyAnalysisProgress.STEP_DONE,
                             "Analysis complete — " + suggestions.size() + " suggestion(s) ready for review");
      progress.setCompleted();
      notify(progress, progressCallback);

      return Collections.unmodifiableList(suggestions);
    } catch (Exception e) {
      progress.setFailed(e.getMessage());
      notify(progress, progressCallback);
      throw e;
    }
  }

  @Override
  public List<TaxonomySuggestion> analyzeDryRun(TaxonomyAnalysisScope scope,
                                                Consumer<TaxonomyAnalysisProgress> progressCallback) {
    TaxonomyAnalysisProgress progress = new TaxonomyAnalysisProgress(true);
    try {
      List<TaxonomySuggestion> suggestions = generateSuggestions(scope, progress, progressCallback);
      logger().info("Taxonomy dry-run complete: {} suggestion(s) (not persisted)", suggestions.size());

      progress.setSuggestionsGenerated(suggestions.size());
      progress.markCompleted(TaxonomyAnalysisProgress.STEP_SUGGESTIONS,
                             suggestions.size() + " suggestion(s) identified (dry-run — not saved)");
      progress.markCompleted(TaxonomyAnalysisProgress.STEP_PREVIEW,
                             "Dry-run preview ready — review before running live analysis");
      progress.markCompleted(TaxonomyAnalysisProgress.STEP_DONE,
                             "Dry run complete — " + suggestions.size() + " suggestion(s) previewed");
      progress.setCompleted();
      notify(progress, progressCallback);

      return Collections.unmodifiableList(suggestions);
    } catch (Exception e) {
      progress.setFailed(e.getMessage());
      notify(progress, progressCallback);
      throw e;
    }
  }

  // ── Core logic ────────────────────────────────────────────────────────────

  private List<TaxonomySuggestion> generateSuggestions(TaxonomyAnalysisScope scope,
                                                       TaxonomyAnalysisProgress progress,
                                                       Consumer<TaxonomyAnalysisProgress> cb) {
    List<TaxonomySuggestion> results = new ArrayList<>();

    // Step 1 — Scope definition
    progress.markActive(TaxonomyAnalysisProgress.STEP_SCOPE, "Resolving scope…");
    notify(progress, cb);

    // Step 2 — Image loading
    progress.markCompleted(TaxonomyAnalysisProgress.STEP_SCOPE,
                           "Scope mode: " + (scope != null ? scope.getMode() : "ALL"));
    progress.markActive(TaxonomyAnalysisProgress.STEP_IMAGES, "Loading images…");
    notify(progress, cb);

    List<ImageAsset> images = resolveScope(scope);
    progress.setTotalImages(images.size());
    progress.markCompleted(TaxonomyAnalysisProgress.STEP_IMAGES,
                           images.size() + " image(s) loaded");
    logger().info("Taxonomy analysis: processing {} image(s) for scope={}", images.size(), scope);

    // Step 3 — Taxonomy loading (deprecated categories)
    progress.markActive(TaxonomyAnalysisProgress.STEP_TAXONOMY, "Loading category metadata…");
    notify(progress, cb);

    Map<SourceCategory, CategoryMetadata> deprecatedCategories = buildDeprecatedLookup();
    progress.markCompleted(TaxonomyAnalysisProgress.STEP_TAXONOMY,
                           deprecatedCategories.size() + " deprecated category record(s) found");

    // Step 4 — Per-image analysis
    progress.markActive(TaxonomyAnalysisProgress.STEP_ANALYSIS,
                        "Analysing 0 / " + images.size() + " images…");
    notify(progress, cb);

    for (ImageAsset asset : images) {
      UUID imageId = asset.getId();
      Optional<SemanticAnalysis> analysisOpt = persistenceService.findAnalysis(imageId);
      if (analysisOpt.isEmpty()) {
        progress.incrementAnalyzed();
        notify(progress, cb);
        continue;
      }

      SemanticAnalysis analysis = analysisOpt.get();
      SourceCategory primary = analysis.getSourceCategory();

      // 1. Deprecated primary category → reassign suggestion
      if (primary != null && deprecatedCategories.containsKey(primary)) {
        CategoryMetadata meta = deprecatedCategories.get(primary);
        SourceCategory replacement = meta.getReplacementCategory();
        if (replacement != null && replacement != primary) {
          results.add(TaxonomySuggestion.reassignDeprecated(
              imageId, asset.getOriginalFilename(), primary, replacement, MODEL_NAME));
        }
        progress.incrementAnalyzed();
        notify(progress, cb);
        continue;
      }

      // 2. UNKNOWN / MIXED primary → try LLM reclassification
      if (primary == SourceCategory.UNKNOWN || primary == SourceCategory.MIXED) {
        TaxonomySuggestion s = tryLlmReclassify(imageId, asset.getOriginalFilename(), analysis);
        if (s != null) results.add(s);
        progress.incrementAnalyzed();
        notify(progress, cb);
        continue;
      }

      // 3. Stored confidence has better alternative → suggest reclassify
      CategoryConfidence confidence = analysis.getCategoryConfidence();
      if (confidence != null) {
        Optional<CategoryCandidate> better = confidence.getBetterAlternative();
        if (better.isPresent()) {
          CategoryCandidate alt = better.get();
          String rationale = String.format(
              "Confidence for '%s' (%.2f) is lower than alternative '%s' (%.2f)",
              primary, confidence.getPrimaryScore(), alt.getCategory(), alt.getScore());
          results.add(TaxonomySuggestion.reclassify(
              imageId, asset.getOriginalFilename(),
              primary, alt.getCategory(),
              alt.getScore(), rationale, MODEL_NAME));
        }
      }

      progress.incrementAnalyzed();
      notify(progress, cb);
    }

    progress.markCompleted(TaxonomyAnalysisProgress.STEP_ANALYSIS,
                           "Analysed " + images.size() + " image(s)");

    // Step 5 — Generating suggestions (live mode skips STEP_PREVIEW, dry-run skips nothing)
    progress.markActive(TaxonomyAnalysisProgress.STEP_SUGGESTIONS, "Consolidating suggestions…");
    notify(progress, cb);

    // If this is a live run, STEP_PREVIEW is skipped (handled by caller after return)
    if (!progress.isDryRun()) {
      progress.markSkipped(TaxonomyAnalysisProgress.STEP_PREVIEW, "Not applicable for live runs");
      notify(progress, cb);
    }

    return results;
  }

  // ── Helper ────────────────────────────────────────────────────────────────

  private void notify(TaxonomyAnalysisProgress progress,
                      Consumer<TaxonomyAnalysisProgress> cb) {
    if (cb != null) {
      try {
        cb.accept(progress);
      } catch (Exception e) {
        logger().warn("Progress callback threw: {}", e.getMessage());
      }
    }
  }

  /**
   * Resolves a scope to the matching list of (non-archived) {@link ImageAsset}s.
   */
  private List<ImageAsset> resolveScope(TaxonomyAnalysisScope scope) {
    if (scope == null) return persistenceService.findAllImages();

    return switch (scope.getMode()) {
      case ALL -> persistenceService.findAllImages();

      case MANUAL_SELECTION -> {
        List<UUID> ids = scope.getManualImageIds();
        yield ids.stream()
            .map(id -> persistenceService.findImage(id).orElse(null))
            .filter(a -> a != null && !a.isDeleted())
            .collect(Collectors.toList());
      }

      case CATEGORY_GROUP -> {
        com.svenruppert.imagerag.domain.enums.CategoryGroup group = scope.getTargetGroup();
        yield persistenceService.findAllImages().stream()
            .filter(asset -> {
              Optional<SemanticAnalysis> aOpt = persistenceService.findAnalysis(asset.getId());
              if (aOpt.isEmpty()) return false;
              SemanticAnalysis a = aOpt.get();
              boolean primaryMatch =
                  CategoryRegistry.getGroup(a.getSourceCategory()) == group;
              boolean secondaryMatch = scope.isIncludeSubgroups() &&
                  a.getSecondaryCategories().stream()
                      .anyMatch(sc -> CategoryRegistry.getGroup(sc) == group);
              return primaryMatch || secondaryMatch;
            })
            .collect(Collectors.toList());
      }

      case MISSING_CATEGORY -> persistenceService.findAllImages().stream()
          .filter(asset -> {
            Optional<SemanticAnalysis> aOpt = persistenceService.findAnalysis(asset.getId());
            if (aOpt.isEmpty()) return true;
            SourceCategory cat = aOpt.get().getSourceCategory();
            return cat == null || cat == SourceCategory.UNKNOWN || cat == SourceCategory.MIXED;
          })
          .collect(Collectors.toList());

      case LOW_CONFIDENCE -> {
        double threshold = scope.getConfidenceThreshold();
        yield persistenceService.findAllImages().stream()
            .filter(asset -> {
              Optional<SemanticAnalysis> aOpt = persistenceService.findAnalysis(asset.getId());
              if (aOpt.isEmpty()) return false;
              CategoryConfidence cc = aOpt.get().getCategoryConfidence();
              return cc == null || cc.isAmbiguous(threshold);
            })
            .collect(Collectors.toList());
      }
    };
  }

  /**
   * Builds a map of all deprecated categories based on persisted {@link CategoryMetadata}.
   */
  private Map<SourceCategory, CategoryMetadata> buildDeprecatedLookup() {
    Map<SourceCategory, CategoryMetadata> map = new EnumMap<>(SourceCategory.class);
    for (CategoryMetadata meta : persistenceService.findAllCategoryMetadata()) {
      if (meta.getLifecycleState() == CategoryLifecycleState.DEPRECATED
          && meta.getCategory() != null) {
        map.put(meta.getCategory(), meta);
      }
    }
    return map;
  }

  /**
   * Calls the LLM to re-evaluate a category assignment for an image.
   * Returns a {@link TaxonomySuggestion} if the LLM suggests a different category,
   * or null if the call fails or the suggestion is the same as the current category.
   */
  private TaxonomySuggestion tryLlmReclassify(UUID imageId, String filename,
                                              SemanticAnalysis analysis) {
    if (!ollamaClient.isAvailable() || analysis.getSummary() == null) {
      return null;
    }
    try {
      String prompt = REEVAL_PROMPT_TEMPLATE.formatted(
          analysis.getSummary().replace("\"", "'"),
          analysis.getSourceCategory());

      Optional<String> jsonResponse = ollamaClient.generateJson(prompt);
      if (jsonResponse.isEmpty()) return null;

      String cleaned = jsonResponse.get().trim();
      if (cleaned.startsWith("```")) {
        cleaned = cleaned.replaceFirst("```[a-zA-Z]*\\n?", "").replaceAll("```$", "").trim();
      }

      JsonNode node = objectMapper.readTree(cleaned);
      double primaryConf = node.path("primaryConfidence").asDouble(0.5);
      String suggestedCatStr = node.path("suggestedCategory").asText("").toUpperCase();
      String rationale = node.path("rationale").asText("LLM re-evaluation");

      // Parse alternative candidates and update CategoryConfidence on the analysis
      List<CategoryCandidate> alternatives = new ArrayList<>();
      node.path("alternatives").forEach(alt -> {
        String catStr = alt.path("category").asText("").toUpperCase();
        double conf = alt.path("confidence").asDouble(0.0);
        try {
          SourceCategory altCat = SourceCategory.valueOf(catStr);
          alternatives.add(new CategoryCandidate(altCat, conf));
        } catch (IllegalArgumentException ignored) { /* skip unknown */ }
      });

      // Persist updated confidence
      CategoryConfidence cc = new CategoryConfidence(
          imageId, analysis.getSourceCategory(), primaryConf,
          alternatives, Instant.now(), ollamaConfig.getTextModel());
      analysis.setCategoryConfidence(cc);
      persistenceService.saveAnalysis(imageId, analysis);

      // If suggested category differs meaningfully, create a suggestion
      SourceCategory suggestedCat = null;
      try {
        suggestedCat = SourceCategory.valueOf(suggestedCatStr);
      } catch (IllegalArgumentException ignored) { /* fallback below */ }

      if (suggestedCat != null && suggestedCat != analysis.getSourceCategory()
          && suggestedCat != SourceCategory.UNKNOWN) {
        return TaxonomySuggestion.reclassify(imageId, filename,
                                             analysis.getSourceCategory(), suggestedCat,
                                             1.0 - primaryConf, rationale, MODEL_NAME);
      }

    } catch (Exception e) {
      logger().warn("LLM re-evaluation failed for image {}: {}", imageId, e.getMessage());
    }
    return null;
  }
}
