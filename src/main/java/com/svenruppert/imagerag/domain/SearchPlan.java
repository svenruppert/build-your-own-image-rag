package com.svenruppert.imagerag.domain;

import com.svenruppert.imagerag.domain.enums.RiskLevel;
import com.svenruppert.imagerag.domain.enums.SeasonHint;
import com.svenruppert.imagerag.domain.enums.SourceCategory;

public class SearchPlan {

  private String originalQuery;
  private String embeddingText;
  private Boolean containsPerson;
  private Boolean containsVehicle;
  private Boolean containsLicensePlate;
  private SeasonHint seasonHint;
  private SourceCategory sourceCategory;
  private RiskLevel privacyLevel;
  private String explanation;

  public SearchPlan() {
  }

  public String getOriginalQuery() {
    return originalQuery;
  }

  public void setOriginalQuery(String v) {
    this.originalQuery = v;
  }

  public String getEmbeddingText() {
    return embeddingText;
  }

  public void setEmbeddingText(String v) {
    this.embeddingText = v;
  }

  public Boolean getContainsPerson() {
    return containsPerson;
  }

  public void setContainsPerson(Boolean v) {
    this.containsPerson = v;
  }

  public Boolean getContainsVehicle() {
    return containsVehicle;
  }

  public void setContainsVehicle(Boolean v) {
    this.containsVehicle = v;
  }

  public Boolean getContainsLicensePlate() {
    return containsLicensePlate;
  }

  public void setContainsLicensePlate(Boolean v) {
    this.containsLicensePlate = v;
  }

  public SeasonHint getSeasonHint() {
    return seasonHint;
  }

  public void setSeasonHint(SeasonHint v) {
    this.seasonHint = v;
  }

  public SourceCategory getSourceCategory() {
    return sourceCategory;
  }

  public void setSourceCategory(SourceCategory v) {
    this.sourceCategory = v;
  }

  public RiskLevel getPrivacyLevel() {
    return privacyLevel;
  }

  public void setPrivacyLevel(RiskLevel v) {
    this.privacyLevel = v;
  }

  public String getExplanation() {
    return explanation;
  }

  public void setExplanation(String v) {
    this.explanation = v;
  }
}
