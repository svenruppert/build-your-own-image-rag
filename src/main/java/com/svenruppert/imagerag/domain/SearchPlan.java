package com.svenruppert.imagerag.domain;

import com.svenruppert.imagerag.domain.enums.CategoryGroup;
import com.svenruppert.imagerag.domain.enums.RiskLevel;
import com.svenruppert.imagerag.domain.enums.SeasonHint;

public class SearchPlan {

  private String originalQuery;
  private String embeddingText;
  private Boolean containsPerson;
  private Boolean containsVehicle;
  private Boolean containsLicensePlate;
  private SeasonHint seasonHint;
  /**
   * Coarse category group filter chosen by the LLM or the user.
   * Null means no category filter is applied.
   */
  private CategoryGroup categoryGroup;
  private RiskLevel privacyLevel;
  private String explanation;
  /**
   * Minimum cosine-similarity score for a candidate to be kept in search results.
   * When {@code null} the service uses its built-in default (currently 0.45).
   * Must be in [0.0, 1.0]; the service clamps the value if it is out of range.
   */
  private Double minScore;

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

  public CategoryGroup getCategoryGroup() {
    return categoryGroup;
  }

  public void setCategoryGroup(CategoryGroup v) {
    this.categoryGroup = v;
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

  public Double getMinScore() {
    return minScore;
  }

  public void setMinScore(Double v) {
    this.minScore = v;
  }
}
