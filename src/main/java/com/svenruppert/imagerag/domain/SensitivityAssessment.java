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
package com.svenruppert.imagerag.domain;

import com.svenruppert.imagerag.domain.enums.RecommendedStorageMode;
import com.svenruppert.imagerag.domain.enums.RiskLevel;
import com.svenruppert.imagerag.domain.enums.SensitivityFlag;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class SensitivityAssessment {

  private UUID imageId;
  private RiskLevel riskLevel;
  // HashSet instead of EnumSet: EclipseStore reconstructs objects via Unsafe,
  // bypassing constructors. EnumSet has final internal fields (elementType, universe)
  // that stay null after Unsafe reconstruction → NPE on iteration.
  private Set<SensitivityFlag> flags = new HashSet<>();
  private boolean reviewRequired;
  private RecommendedStorageMode recommendedStorageMode;
  private String notes;

  public SensitivityAssessment() {
  }

  public UUID getImageId() {
    return imageId;
  }

  public void setImageId(UUID imageId) {
    this.imageId = imageId;
  }

  public RiskLevel getRiskLevel() {
    return riskLevel;
  }

  public void setRiskLevel(RiskLevel riskLevel) {
    this.riskLevel = riskLevel;
  }

  public Set<SensitivityFlag> getFlags() {
    return flags;
  }

  public void setFlags(Set<SensitivityFlag> flags) {
    this.flags = flags;
  }

  public boolean isReviewRequired() {
    return reviewRequired;
  }

  public void setReviewRequired(boolean reviewRequired) {
    this.reviewRequired = reviewRequired;
  }

  public RecommendedStorageMode getRecommendedStorageMode() {
    return recommendedStorageMode;
  }

  public void setRecommendedStorageMode(RecommendedStorageMode recommendedStorageMode) {
    this.recommendedStorageMode = recommendedStorageMode;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
  }
}
