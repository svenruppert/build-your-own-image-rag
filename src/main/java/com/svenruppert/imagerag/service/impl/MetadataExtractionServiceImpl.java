package com.svenruppert.imagerag.service.impl;

import com.drew.imaging.ImageMetadataReader;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.imagerag.dto.ExtractedMetadata;
import com.svenruppert.imagerag.service.MetadataExtractionService;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;
import java.util.TimeZone;

public class MetadataExtractionServiceImpl
    implements MetadataExtractionService, HasLogger {

  @Override
  public ExtractedMetadata extract(Path imagePath)
      throws IOException {
    int width = 0;
    int height = 0;
    boolean exifPresent = false;
    boolean gpsPresent = false;
    Double latitude = null;
    Double longitude = null;
    Instant captureTimestamp = null;
    String cameraModel = null;

    // Read image dimensions via ImageIO (reliable fallback)
    try {
      BufferedImage img = ImageIO.read(imagePath.toFile());
      if (img != null) {
        width = img.getWidth();
        height = img.getHeight();
      }
    } catch (Exception e) {
      logger().warn("ImageIO could not read dimensions for {}: {}", imagePath.getFileName(), e.getMessage());
    }

    // Read EXIF metadata via metadata-extractor
    try {
      Metadata metadata = ImageMetadataReader.readMetadata(imagePath.toFile());

      ExifIFD0Directory exifDir = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
      if (exifDir != null) {
        exifPresent = true;
        cameraModel = exifDir.getString(ExifIFD0Directory.TAG_MODEL);
      }

      ExifSubIFDDirectory subDir = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
      if (subDir != null) {
        exifPresent = true;

        // Prefer EXIF dimensions if ImageIO failed
        if (width == 0 && subDir.containsTag(ExifSubIFDDirectory.TAG_EXIF_IMAGE_WIDTH)) {
          width = subDir.getInt(ExifSubIFDDirectory.TAG_EXIF_IMAGE_WIDTH);
        }
        if (height == 0 && subDir.containsTag(ExifSubIFDDirectory.TAG_EXIF_IMAGE_HEIGHT)) {
          height = subDir.getInt(ExifSubIFDDirectory.TAG_EXIF_IMAGE_HEIGHT);
        }

        Date dateTime = subDir.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL,
                                       TimeZone.getDefault());
        if (dateTime != null) {
          captureTimestamp = dateTime.toInstant();
        }
      }

      GpsDirectory gpsDir = metadata.getFirstDirectoryOfType(GpsDirectory.class);
      if (gpsDir != null) {
        GeoLocation geoLocation = gpsDir.getGeoLocation();
        if (geoLocation != null && !geoLocation.isZero()) {
          gpsPresent = true;
          latitude = geoLocation.getLatitude();
          longitude = geoLocation.getLongitude();
        }
      }

    } catch (Exception e) {
      logger().debug("EXIF extraction skipped for {}: {}", imagePath.getFileName(), e.getMessage());
    }

    return new ExtractedMetadata(width, height, exifPresent, gpsPresent,
                                 latitude, longitude, captureTimestamp, cameraModel);
  }
}
