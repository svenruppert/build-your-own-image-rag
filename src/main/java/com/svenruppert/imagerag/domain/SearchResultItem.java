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

import com.svenruppert.imagerag.domain.enums.RiskLevel;
import com.svenruppert.imagerag.domain.enums.SeasonHint;
import com.svenruppert.imagerag.domain.enums.SourceCategory;

import java.util.UUID;

public class SearchResultItem {

  private UUID imageId;
  private String title;
  private String summary;
  private double score;
  private SourceCategory sourceCategory;
  private SeasonHint seasonHint;
  private Boolean containsPerson;
  private RiskLevel riskLevel;

  public SearchResultItem() {
  }

  public SearchResultItem(UUID imageId, String title, String summary, double score,
                          SourceCategory sourceCategory, SeasonHint seasonHint,
                          Boolean containsPerson, RiskLevel riskLevel) {
    this.imageId = imageId;
    this.title = title;
    this.summary = summary;
    this.score = score;
    this.sourceCategory = sourceCategory;
    this.seasonHint = seasonHint;
    this.containsPerson = containsPerson;
    this.riskLevel = riskLevel;
  }

  public UUID getImageId() {
    return imageId;
  }

  public void setImageId(UUID imageId) {
    this.imageId = imageId;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getSummary() {
    return summary;
  }

  public void setSummary(String summary) {
    this.summary = summary;
  }

  public double getScore() {
    return score;
  }

  public void setScore(double score) {
    this.score = score;
  }

  public SourceCategory getSourceCategory() {
    return sourceCategory;
  }

  public void setSourceCategory(SourceCategory sourceCategory) {
    this.sourceCategory = sourceCategory;
  }

  public SeasonHint getSeasonHint() {
    return seasonHint;
  }

  public void setSeasonHint(SeasonHint seasonHint) {
    this.seasonHint = seasonHint;
  }

  public Boolean getContainsPerson() {
    return containsPerson;
  }

  public void setContainsPerson(Boolean containsPerson) {
    this.containsPerson = containsPerson;
  }

  public RiskLevel getRiskLevel() {
    return riskLevel;
  }

  public void setRiskLevel(RiskLevel riskLevel) {
    this.riskLevel = riskLevel;
  }
}
