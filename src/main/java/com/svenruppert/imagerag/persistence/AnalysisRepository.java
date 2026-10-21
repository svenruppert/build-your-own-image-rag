/*
 * Copyright © 2026 Sven Ruppert (sven.ruppert@gmail.com)
 *
 * Licensed under the EUPL, Version 1.2 (the "Licence");
 * you may not use this file except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *     https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */
package com.svenruppert.imagerag.persistence;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.imagerag.domain.*;
import com.svenruppert.imagerag.domain.enums.RiskLevel;
import com.svenruppert.imagerag.domain.enums.SensitivityFlag;
import com.svenruppert.imagerag.domain.enums.SourceCategory;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Per-image analysis results: EXIF metadata, location, semantic analysis, sensitivity
 * assessment and OCR, including the in-place user corrections.
 */
final class AnalysisRepository
    implements HasLogger {

  private final PersistenceContext ctx;

  AnalysisRepository(PersistenceContext ctx) {
    this.ctx = ctx;
  }

  void saveMetadata(UUID imageId, ImageMetadataInfo metadata) {
    ctx.write(() -> {
      ctx.root().getMetadata().put(imageId, metadata);
      ctx.store(ctx.root().getMetadata());
    });
  }

  void saveLocation(UUID imageId, LocationSummary location) {
    ctx.write(() -> {
      ctx.root().getLocations().put(imageId, location);
      ctx.store(ctx.root().getLocations());
    });
  }

  void saveAnalysis(UUID imageId, SemanticAnalysis analysis) {
    ctx.write(() -> {
      ctx.root().getAnalyses().put(imageId, analysis);
      ctx.store(ctx.root().getAnalyses());
    });
  }

  void saveAssessment(UUID imageId, SensitivityAssessment assessment) {
    ctx.write(() -> {
      ctx.root().getAssessments().put(imageId, assessment);
      ctx.store(ctx.root().getAssessments());
    });
  }

  void saveOcrResult(UUID imageId, OcrResult result) {
    ctx.write(() -> {
      ctx.root().getOcrResults().put(imageId, result);
      ctx.store(ctx.root().getOcrResults());
    });
  }

  Optional<SemanticAnalysis> findAnalysis(UUID imageId) {
    return ctx.read(() -> Optional.ofNullable(ctx.root().getAnalyses().get(imageId)));
  }

  Optional<SensitivityAssessment> findAssessment(UUID imageId) {
    return ctx.read(() -> Optional.ofNullable(ctx.root().getAssessments().get(imageId)));
  }

  Optional<LocationSummary> findLocation(UUID imageId) {
    return ctx.read(() -> Optional.ofNullable(ctx.root().getLocations().get(imageId)));
  }

  Optional<ImageMetadataInfo> findMetadata(UUID imageId) {
    return ctx.read(() -> Optional.ofNullable(ctx.root().getMetadata().get(imageId)));
  }

  Optional<OcrResult> findOcrResult(UUID imageId) {
    return ctx.read(() -> Optional.ofNullable(ctx.root().getOcrResults().get(imageId)));
  }

  // ── In-place user corrections ─────────────────────────────────────────────

  /**
   * Updates the risk level of an existing assessment in place and persists the change.
   */
  void updateRiskLevel(UUID imageId, RiskLevel newLevel) {
    ctx.write(() -> findAssessment(imageId).ifPresent(assessment -> {
      assessment.setRiskLevel(newLevel);
      ctx.store(assessment);
      logger().info("Updated riskLevel to {} for imageId={}", newLevel, imageId);
    }));
  }

  /**
   * Updates the source category of an existing analysis in place and persists the change.
   */
  void updateSourceCategory(UUID imageId, SourceCategory newCategory) {
    ctx.write(() -> findAnalysis(imageId).ifPresent(analysis -> {
      analysis.setSourceCategory(newCategory);
      ctx.store(analysis);
      logger().info("Updated sourceCategory to {} for imageId={}", newCategory, imageId);
    }));
  }

  /**
   * Replaces the secondary categories of an existing analysis; an empty or
   * {@code null} list clears them.
   */
  void updateSecondaryCategories(UUID imageId, List<SourceCategory> categories) {
    ctx.write(() -> findAnalysis(imageId).ifPresent(analysis -> {
      analysis.setSecondaryCategories(categories != null ? categories : List.of());
      ctx.store(analysis);
      logger().info("Updated secondaryCategories for imageId={}: {}", imageId, categories);
    }));
  }

  // ── Startup maintenance ───────────────────────────────────────────────────

  /**
   * Normalizes multi-category assignments across all stored analyses (duplicates removed,
   * primary never repeated as secondary). Idempotent.
   *
   * @return number of analyses that were changed and re-persisted
   */
  int normalizeAllCategoryAssignments() {
    return ctx.writeAndGet(() -> {
      int changed = 0;
      for (SemanticAnalysis analysis : ctx.root().getAnalyses().values()) {
        if (analysis == null) continue;
        if (CategoryAssignmentNormalizer.normalize(analysis)) {
          ctx.store(analysis);
          changed++;
        }
      }
      if (changed > 0) {
        logger().info("Normalized category assignments for {} analysis record(s)", changed);
      }
      return changed;
    });
  }

  /**
   * EclipseStore reconstructs objects via {@code Unsafe.allocateInstance()}, bypassing
   * constructors. {@code EnumSet} has {@code final} internal fields ({@code elementType},
   * {@code universe}) that are set in the constructor — after Unsafe reconstruction they
   * are {@code null}, causing {@link NullPointerException} on iteration.
   * <p>Replaces any broken {@code EnumSet}-backed flags field with a safe {@link HashSet}
   * and re-persists the affected assessments.
   */
  void migrateBrokenFlagSets() {
    ctx.write(() -> {
      int migrated = 0;
      for (SensitivityAssessment assessment : ctx.root().getAssessments().values()) {
        Set<SensitivityFlag> flags = assessment.getFlags();
        if (flags == null) {
          assessment.setFlags(new HashSet<>());
          ctx.store(assessment);
          migrated++;
          continue;
        }
        // Detect a broken EnumSet by probing it; replace on failure
        try {
          flags.size();
          for (@SuppressWarnings("unused") var ignored : flags) break;
        } catch (NullPointerException e) {
          // 'universe' is null — rebuild as HashSet (the values cannot be recovered)
          logger().warn("Rebuilding broken EnumSet flags for assessment imageId={}",
                        assessment.getImageId());
          assessment.setFlags(new HashSet<>());
          ctx.store(assessment);
          migrated++;
        }
      }
      if (migrated > 0) {
        logger().info("Migrated {} assessments from broken EnumSet to HashSet", migrated);
      }
    });
  }
}
