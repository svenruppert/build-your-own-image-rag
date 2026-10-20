package com.svenruppert.imagerag.service.impl;

import com.svenruppert.imagerag.domain.ImageAsset;
import com.svenruppert.imagerag.domain.enums.AuditAction;
import com.svenruppert.imagerag.domain.enums.RiskLevel;
import com.svenruppert.imagerag.domain.enums.SourceCategory;
import com.svenruppert.imagerag.persistence.PersistenceService;
import com.svenruppert.imagerag.service.AuditService;
import com.svenruppert.imagerag.service.ImageModerationService;

import java.util.List;
import java.util.UUID;

/**
 * {@link ImageModerationService} that applies changes through the
 * {@link PersistenceService} and records each of them with the {@link AuditService}.
 */
public class ImageModerationServiceImpl
    implements ImageModerationService {

  static final String REASON_RISK_SAFE = "Risk level set to SAFE";
  static final String REASON_RISK_NOT_SAFE = "Risk level set above SAFE";

  private final PersistenceService persistenceService;
  private final AuditService auditService;

  public ImageModerationServiceImpl(PersistenceService persistenceService,
                                    AuditService auditService) {
    this.persistenceService = persistenceService;
    this.auditService = auditService;
  }

  @Override
  public void approve(ImageAsset asset, String reason) {
    persistenceService.approveImage(asset.getId());
    auditService.log(AuditAction.APPROVE, asset.getId(), asset.getOriginalFilename(), reason);
  }

  @Override
  public void lock(ImageAsset asset, String reason) {
    persistenceService.unapproveImage(asset.getId());
    auditService.log(AuditAction.UNAPPROVE, asset.getId(), asset.getOriginalFilename(), reason);
  }

  @Override
  public void changeRiskLevel(ImageAsset asset, RiskLevel oldLevel, RiskLevel newLevel) {
    persistenceService.updateRiskLevel(asset.getId(), newLevel);
    auditService.log(AuditAction.RISK_CHANGE, asset.getId(), asset.getOriginalFilename(),
                     oldLevel + " -> " + newLevel);
    if (newLevel == RiskLevel.SAFE) {
      approve(asset, REASON_RISK_SAFE);
    } else {
      lock(asset, REASON_RISK_NOT_SAFE);
    }
  }

  @Override
  public void changeSourceCategory(UUID imageId, SourceCategory newCategory) {
    persistenceService.updateSourceCategory(imageId, newCategory);
  }

  @Override
  public void changeSecondaryCategories(UUID imageId, List<SourceCategory> categories) {
    persistenceService.updateSecondaryCategories(imageId, categories);
  }

  @Override
  public void archive(ImageAsset asset, String reason) {
    persistenceService.softDeleteImage(asset.getId(), reason);
    auditService.log(AuditAction.SOFT_DELETE, asset.getId(), asset.getOriginalFilename(), reason);
  }
}
