package com.svenruppert.imagerag.service;

import com.svenruppert.imagerag.domain.ImageAsset;
import com.svenruppert.imagerag.domain.enums.RiskLevel;
import com.svenruppert.imagerag.domain.enums.SourceCategory;

import java.util.List;
import java.util.UUID;

/**
 * Manual moderation of images: visibility, risk level, category and archiving.
 * <p>Every state change is written to the audit log by the service itself, so the UI
 * cannot change moderation state without leaving a trace.
 */
public interface ImageModerationService {

  /**
   * Makes the image visible in search.
   *
   * @param reason language-independent reason stored in the audit log
   */
  void approve(ImageAsset asset, String reason);

  /**
   * Hides the image from search.
   *
   * @param reason language-independent reason stored in the audit log
   */
  void lock(ImageAsset asset, String reason);

  /**
   * Sets a new risk level and re-evaluates visibility: {@link RiskLevel#SAFE} approves
   * the image, every other level locks it.
   */
  void changeRiskLevel(ImageAsset asset, RiskLevel oldLevel, RiskLevel newLevel);

  void changeSourceCategory(UUID imageId, SourceCategory newCategory);

  void changeSecondaryCategories(UUID imageId, List<SourceCategory> categories);

  /**
   * Moves the image to the archive (soft delete).
   *
   * @param reason language-independent reason stored with the image and in the audit log
   */
  void archive(ImageAsset asset, String reason);
}
