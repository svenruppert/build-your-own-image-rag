package com.svenruppert.imagerag.domain;

import java.time.Instant;
import java.util.UUID;

public class ImageAsset {

  private UUID id;
  private String originalFilename;
  private String storedFilename;
  private String mimeType;
  private long fileSize;
  private int width;
  private int height;
  private String sha256;
  private Instant uploadedAt;
  private boolean originalStored;
  private boolean exifPresent;
  private boolean gpsPresent;

  /**
   * True when this image has been soft-deleted (archived).
   * Soft-deleted images are hidden from normal search and overview flows
   * but not immediately removed from storage.
   * Not declared final — EclipseStore Unsafe reconstruction compatibility.
   */
  private boolean deleted;
  /**
   * Timestamp of the soft-delete action. Null when not deleted.
   */
  private java.time.Instant deletedAt;
  /**
   * Optional reason text for the deletion.
   */
  private String deletedReason;

  public ImageAsset() {
  }

  public ImageAsset(UUID id, String originalFilename, String storedFilename,
                    String mimeType, long fileSize, int width, int height,
                    String sha256, Instant uploadedAt, boolean originalStored,
                    boolean exifPresent, boolean gpsPresent) {
    this.id = id;
    this.originalFilename = originalFilename;
    this.storedFilename = storedFilename;
    this.mimeType = mimeType;
    this.fileSize = fileSize;
    this.width = width;
    this.height = height;
    this.sha256 = sha256;
    this.uploadedAt = uploadedAt;
    this.originalStored = originalStored;
    this.exifPresent = exifPresent;
    this.gpsPresent = gpsPresent;
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getOriginalFilename() {
    return originalFilename;
  }

  public void setOriginalFilename(String originalFilename) {
    this.originalFilename = originalFilename;
  }

  public String getStoredFilename() {
    return storedFilename;
  }

  public void setStoredFilename(String storedFilename) {
    this.storedFilename = storedFilename;
  }

  public String getMimeType() {
    return mimeType;
  }

  public void setMimeType(String mimeType) {
    this.mimeType = mimeType;
  }

  public long getFileSize() {
    return fileSize;
  }

  public void setFileSize(long fileSize) {
    this.fileSize = fileSize;
  }

  public int getWidth() {
    return width;
  }

  public void setWidth(int width) {
    this.width = width;
  }

  public int getHeight() {
    return height;
  }

  public void setHeight(int height) {
    this.height = height;
  }

  public String getSha256() {
    return sha256;
  }

  public void setSha256(String sha256) {
    this.sha256 = sha256;
  }

  public Instant getUploadedAt() {
    return uploadedAt;
  }

  public void setUploadedAt(Instant uploadedAt) {
    this.uploadedAt = uploadedAt;
  }

  public boolean isOriginalStored() {
    return originalStored;
  }

  public void setOriginalStored(boolean originalStored) {
    this.originalStored = originalStored;
  }

  public boolean isExifPresent() {
    return exifPresent;
  }

  public void setExifPresent(boolean exifPresent) {
    this.exifPresent = exifPresent;
  }

  public boolean isGpsPresent() {
    return gpsPresent;
  }

  public void setGpsPresent(boolean gpsPresent) {
    this.gpsPresent = gpsPresent;
  }

  public boolean isDeleted() {
    return deleted;
  }

  public void setDeleted(boolean deleted) {
    this.deleted = deleted;
  }

  public java.time.Instant getDeletedAt() {
    return deletedAt;
  }

  public void setDeletedAt(java.time.Instant deletedAt) {
    this.deletedAt = deletedAt;
  }

  public String getDeletedReason() {
    return deletedReason;
  }

  public void setDeletedReason(String deletedReason) {
    this.deletedReason = deletedReason;
  }
}
