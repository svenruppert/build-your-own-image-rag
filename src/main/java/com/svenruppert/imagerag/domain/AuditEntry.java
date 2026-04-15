package com.svenruppert.imagerag.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * An immutable record of a critical state-changing action.
 * Stored in EclipseStore for traceability.
 */
public class AuditEntry {

  private UUID entryId;
  private String action;
  private UUID imageId;
  private String imageFilename;
  private String detail;
  private Instant timestamp;

  public AuditEntry() {
  }

  public AuditEntry(String action, UUID imageId, String imageFilename, String detail) {
    this.entryId = UUID.randomUUID();
    this.action = action;
    this.imageId = imageId;
    this.imageFilename = imageFilename;
    this.detail = detail;
    this.timestamp = Instant.now();
  }

  public UUID getEntryId() {
    return entryId;
  }

  public void setEntryId(UUID entryId) {
    this.entryId = entryId;
  }

  public String getAction() {
    return action;
  }

  public void setAction(String action) {
    this.action = action;
  }

  public UUID getImageId() {
    return imageId;
  }

  public void setImageId(UUID imageId) {
    this.imageId = imageId;
  }

  public String getImageFilename() {
    return imageFilename;
  }

  public void setImageFilename(String imageFilename) {
    this.imageFilename = imageFilename;
  }

  public String getDetail() {
    return detail;
  }

  public void setDetail(String detail) {
    this.detail = detail;
  }

  public Instant getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(Instant timestamp) {
    this.timestamp = timestamp;
  }
}
