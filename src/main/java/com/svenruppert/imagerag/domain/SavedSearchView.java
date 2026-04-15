package com.svenruppert.imagerag.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * A user-saved search configuration that can be reloaded later.
 * Distinct from {@link RecentSearchEntry} (automatic history) — saved views
 * are created intentionally by the user and have a user-chosen name.
 */
public class SavedSearchView {

  private UUID id;
  private String name;
  private String query;
  private String embeddingText;
  private String categoryGroupName;
  private String privacyLevel;
  private Double minScore;
  private Boolean filterPerson;
  private Boolean filterVehicle;
  private String viewMode;
  private Instant savedAt;

  public SavedSearchView() {
  }

  public SavedSearchView(String name, String query) {
    this.id = UUID.randomUUID();
    this.name = name;
    this.query = query;
    this.savedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getQuery() {
    return query;
  }

  public void setQuery(String query) {
    this.query = query;
  }

  public String getEmbeddingText() {
    return embeddingText;
  }

  public void setEmbeddingText(String embeddingText) {
    this.embeddingText = embeddingText;
  }

  public String getCategoryGroupName() {
    return categoryGroupName;
  }

  public void setCategoryGroupName(String categoryGroupName) {
    this.categoryGroupName = categoryGroupName;
  }

  public String getPrivacyLevel() {
    return privacyLevel;
  }

  public void setPrivacyLevel(String privacyLevel) {
    this.privacyLevel = privacyLevel;
  }

  public Double getMinScore() {
    return minScore;
  }

  public void setMinScore(Double minScore) {
    this.minScore = minScore;
  }

  public Boolean getFilterPerson() {
    return filterPerson;
  }

  public void setFilterPerson(Boolean filterPerson) {
    this.filterPerson = filterPerson;
  }

  public Boolean getFilterVehicle() {
    return filterVehicle;
  }

  public void setFilterVehicle(Boolean filterVehicle) {
    this.filterVehicle = filterVehicle;
  }

  public String getViewMode() {
    return viewMode;
  }

  public void setViewMode(String viewMode) {
    this.viewMode = viewMode;
  }

  public Instant getSavedAt() {
    return savedAt;
  }

  public void setSavedAt(Instant savedAt) {
    this.savedAt = savedAt;
  }
}
