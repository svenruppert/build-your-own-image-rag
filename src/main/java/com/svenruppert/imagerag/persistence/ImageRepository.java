package com.svenruppert.imagerag.persistence;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.imagerag.domain.ImageAsset;
import com.svenruppert.imagerag.domain.SensitivityAssessment;
import com.svenruppert.imagerag.domain.enums.RiskLevel;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Image assets, search approval, archive state and the SHA-256 duplicate index.
 */
final class ImageRepository
    implements HasLogger {

  private final PersistenceContext ctx;

  ImageRepository(PersistenceContext ctx) {
    this.ctx = ctx;
  }

  void saveImage(ImageAsset asset) {
    ctx.write(() -> {
      ctx.root().getImages().put(asset.getId(), asset);
      ctx.store(ctx.root().getImages());
    });
  }

  /**
   * Permanently removes all persisted data for the given image across all collections.
   * Caller is responsible for also removing the image file, preview cache,
   * and in-memory index entries (vector + keyword).
   */
  void deleteImage(UUID imageId) {
    ctx.write(() -> {
      AppDataRoot root = ctx.root();
      // Remove hash index entry before removing the asset record
      ImageAsset asset = root.getImages().get(imageId);
      if (asset != null && asset.getSha256() != null) {
        root.getHashIndex().remove(asset.getSha256());
        ctx.store(root.getHashIndex());
      }
      root.getImages().remove(imageId);
      root.getMetadata().remove(imageId);
      root.getLocations().remove(imageId);
      root.getAnalyses().remove(imageId);
      root.getAssessments().remove(imageId);
      root.getVectorEntries().remove(imageId);
      root.getApprovedImageIds().remove(imageId);
      root.getOcrResults().remove(imageId);
      root.getRawVectorStore().remove(imageId);
      ctx.store(root.getImages());
      ctx.store(root.getMetadata());
      ctx.store(root.getLocations());
      ctx.store(root.getAnalyses());
      ctx.store(root.getAssessments());
      ctx.store(root.getVectorEntries());
      ctx.store(root.getApprovedImageIds());
      ctx.store(root.getOcrResults());
      ctx.store(root.getRawVectorStore());
      logger().info("Permanently deleted all persisted data for imageId={}", imageId);
    });
  }

  /**
   * Marks an image as approved so it appears in search results.
   */
  void approveImage(UUID imageId) {
    ctx.write(() -> {
      ctx.root().getApprovedImageIds().add(imageId);
      ctx.store(ctx.root().getApprovedImageIds());
    });
  }

  /**
   * Removes approval — the image no longer appears in search results.
   */
  void unapproveImage(UUID imageId) {
    ctx.write(() -> {
      ctx.root().getApprovedImageIds().remove(imageId);
      ctx.store(ctx.root().getApprovedImageIds());
    });
  }

  boolean isApproved(UUID imageId) {
    return ctx.read(() -> ctx.root().getApprovedImageIds().contains(imageId));
  }

  /**
   * Returns {@code true} if the image exists and has been soft-deleted (archived).
   */
  boolean isDeleted(UUID imageId) {
    return ctx.read(() -> {
      ImageAsset asset = ctx.root().getImages().get(imageId);
      return asset != null && asset.isDeleted();
    });
  }

  Optional<ImageAsset> findImage(UUID imageId) {
    return ctx.read(() -> Optional.ofNullable(ctx.root().getImages().get(imageId)));
  }

  List<ImageAsset> findAllImages() {
    return ctx.read(() -> ctx.root().getImages().values().stream()
        .filter(a -> !a.isDeleted())
        .collect(Collectors.toList()));
  }

  List<ImageAsset> findAllImagesIncludingDeleted() {
    return ctx.read(() -> new ArrayList<>(ctx.root().getImages().values()));
  }

  /**
   * Returns all soft-deleted (archived) images, most recently archived first.
   */
  List<ImageAsset> findArchivedImages() {
    return ctx.read(() -> ctx.root().getImages().values().stream()
        .filter(ImageAsset::isDeleted)
        .sorted(Comparator.comparing(
            a -> a.getDeletedAt() != null ? a.getDeletedAt() : Instant.EPOCH,
            Comparator.reverseOrder()))
        .collect(Collectors.toList()));
  }

  void softDeleteImage(UUID imageId, String reason) {
    ctx.write(() -> {
      findImage(imageId).ifPresent(asset -> {
        asset.setDeleted(true);
        asset.setDeletedAt(Instant.now());
        asset.setDeletedReason(reason);
        ctx.store(asset);
      });
      unapproveImage(imageId);
    });
  }

  /**
   * Restores an archived image. It is re-approved only if its stored sensitivity
   * assessment is SAFE; REVIEW / SENSITIVE images stay locked and require manual approval.
   */
  void restoreImage(UUID imageId) {
    ctx.write(() -> {
      findImage(imageId).ifPresent(asset -> {
        asset.setDeleted(false);
        asset.setDeletedAt(null);
        asset.setDeletedReason(null);
        ctx.store(asset);
      });
      SensitivityAssessment assessment = ctx.root().getAssessments().get(imageId);
      if (assessment == null) {
        return;
      }
      if (assessment.getRiskLevel() == RiskLevel.SAFE) {
        approveImage(imageId);
        logger().info("Auto-approved restored image {} (SAFE risk)", imageId);
      } else {
        logger().info("Restored image {} remains locked ({} risk) — manual approval required",
                      imageId, assessment.getRiskLevel());
      }
    });
  }

  // ── Hash-based duplicate detection ────────────────────────────────────────

  /**
   * Returns the imageId of an already-stored asset whose file content matches
   * the given SHA-256 hash, or empty if no duplicate exists.
   */
  Optional<UUID> findImageIdByHash(String sha256) {
    return ctx.read(() -> {
      if (sha256 == null || sha256.isBlank()) {
        return Optional.empty();
      }
      return Optional.ofNullable(ctx.root().getHashIndex().get(sha256));
    });
  }

  /**
   * Registers a sha256 → imageId mapping after a new image has been stored.
   */
  void registerHash(String sha256, UUID imageId) {
    ctx.write(() -> {
      if (sha256 == null || sha256.isBlank()) {
        return;
      }
      ctx.root().getHashIndex().put(sha256, imageId);
      ctx.store(ctx.root().getHashIndex());
    });
  }

  void removeHash(String sha256) {
    ctx.write(() -> {
      if (sha256 == null || sha256.isBlank()) {
        return;
      }
      ctx.root().getHashIndex().remove(sha256);
      ctx.store(ctx.root().getHashIndex());
    });
  }

  /**
   * Rebuilds the hash index from all stored assets; called once at startup so the
   * index covers images persisted before the hash index existed.
   */
  void rebuildHashIndex() {
    ctx.write(() -> {
      Map<String, UUID> index = ctx.root().getHashIndex();
      index.clear();
      int indexed = 0;
      for (ImageAsset asset : ctx.root().getImages().values()) {
        if (asset.getSha256() != null && !asset.getSha256().isBlank()) {
          index.put(asset.getSha256(), asset.getId());
          indexed++;
        }
      }
      ctx.store(index);
      logger().info("Hash index rebuilt: {} entries", indexed);
    });
  }
}
