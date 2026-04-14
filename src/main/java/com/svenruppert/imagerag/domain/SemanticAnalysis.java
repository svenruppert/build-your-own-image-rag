package com.svenruppert.imagerag.domain;

import com.svenruppert.imagerag.domain.enums.SeasonHint;
import com.svenruppert.imagerag.domain.enums.SourceCategory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SemanticAnalysis {

  private UUID imageId;
  private String summary;
  private String shortSummary;
  private List<String> tags = new ArrayList<>();
  private SourceCategory sourceCategory;
  private String sceneType;
  private SeasonHint seasonHint;
  private Boolean containsPerson;
  private Integer personCountHint;
  private Boolean containsVehicle;
  private Boolean containsReadableText;
  private Boolean containsLicensePlateHint;

  public SemanticAnalysis() {
  }

  public UUID getImageId() {
    return imageId;
  }

  public void setImageId(UUID imageId) {
    this.imageId = imageId;
  }

  public String getSummary() {
    return summary;
  }

  public void setSummary(String summary) {
    this.summary = summary;
  }

  public String getShortSummary() {
    return shortSummary;
  }

  public void setShortSummary(String shortSummary) {
    this.shortSummary = shortSummary;
  }

  public List<String> getTags() {
    return tags;
  }

  public void setTags(List<String> tags) {
    this.tags = tags;
  }

  public SourceCategory getSourceCategory() {
    return sourceCategory;
  }

  public void setSourceCategory(SourceCategory sourceCategory) {
    this.sourceCategory = sourceCategory;
  }

  public String getSceneType() {
    return sceneType;
  }

  public void setSceneType(String sceneType) {
    this.sceneType = sceneType;
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

  public Integer getPersonCountHint() {
    return personCountHint;
  }

  public void setPersonCountHint(Integer personCountHint) {
    this.personCountHint = personCountHint;
  }

  public Boolean getContainsVehicle() {
    return containsVehicle;
  }

  public void setContainsVehicle(Boolean containsVehicle) {
    this.containsVehicle = containsVehicle;
  }

  public Boolean getContainsReadableText() {
    return containsReadableText;
  }

  public void setContainsReadableText(Boolean containsReadableText) {
    this.containsReadableText = containsReadableText;
  }

  public Boolean getContainsLicensePlateHint() {
    return containsLicensePlateHint;
  }

  public void setContainsLicensePlateHint(Boolean containsLicensePlateHint) {
    this.containsLicensePlateHint = containsLicensePlateHint;
  }
}
