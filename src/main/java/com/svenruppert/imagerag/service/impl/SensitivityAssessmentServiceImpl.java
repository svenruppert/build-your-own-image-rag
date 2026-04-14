package com.svenruppert.imagerag.service.impl;

import com.svenruppert.imagerag.domain.*;
import com.svenruppert.imagerag.domain.enums.RecommendedStorageMode;
import com.svenruppert.imagerag.domain.enums.RiskLevel;
import com.svenruppert.imagerag.domain.enums.SensitivityFlag;
import com.svenruppert.imagerag.service.SensitivityAssessmentService;

import java.util.HashSet;
import java.util.Set;
import java.util.StringJoiner;

public class SensitivityAssessmentServiceImpl
    implements SensitivityAssessmentService {

  @Override
  public SensitivityAssessment assess(ImageAsset asset,
                                      ImageMetadataInfo metadataInfo,
                                      LocationSummary locationSummary,
                                      SemanticAnalysis semanticAnalysis) {
    Set<SensitivityFlag> flags = new HashSet<>();
    StringJoiner notes = new StringJoiner("; ");

    if (asset.isExifPresent()) {
      flags.add(SensitivityFlag.EXIF_METADATA_PRESENT);
      notes.add("EXIF metadata embedded in file");
    }

    if (asset.isGpsPresent()) {
      flags.add(SensitivityFlag.GPS_DATA_PRESENT);
      notes.add("GPS coordinates found in EXIF");
    }

    if (locationSummary != null && locationSummary.getCity() != null) {
      flags.add(SensitivityFlag.ADDRESS_OR_LOCATION_HINT);
      notes.add("Location resolved: " + locationSummary.toHumanReadable());
    }

    if (semanticAnalysis != null) {
      if (Boolean.TRUE.equals(semanticAnalysis.getContainsPerson())) {
        flags.add(SensitivityFlag.PERSON_VISIBLE);
        Integer count = semanticAnalysis.getPersonCountHint();
        if (count != null && count > 0) {
          notes.add("Approx. " + count + " person(s) visible");
        }
      }

      if (Boolean.TRUE.equals(semanticAnalysis.getContainsLicensePlateHint())) {
        flags.add(SensitivityFlag.LICENSE_PLATE_VISIBLE);
        notes.add("Possible license plate visible");
      }

      if (Boolean.TRUE.equals(semanticAnalysis.getContainsReadableText())) {
        flags.add(SensitivityFlag.READABLE_SIGN_OR_TEXT);
        notes.add("Readable text or signage visible");
      }
    }

    if (flags.isEmpty()) {
      flags.add(SensitivityFlag.NO_OBVIOUS_SENSITIVE_CONTENT);
    }

    RiskLevel riskLevel = computeRiskLevel(flags);
    boolean reviewRequired = riskLevel == RiskLevel.SENSITIVE
        || flags.contains(SensitivityFlag.CHILD_VISIBLE)
        || flags.contains(SensitivityFlag.LICENSE_PLATE_VISIBLE);

    RecommendedStorageMode storageMode = computeStorageMode(riskLevel);

    SensitivityAssessment assessment = new SensitivityAssessment();
    assessment.setImageId(asset.getId());
    assessment.setRiskLevel(riskLevel);
    assessment.setFlags(flags);
    assessment.setReviewRequired(reviewRequired);
    assessment.setRecommendedStorageMode(storageMode);
    assessment.setNotes(notes.length() > 0 ? notes.toString() : "No notable sensitivity factors");

    return assessment;
  }

  private RiskLevel computeRiskLevel(Set<SensitivityFlag> flags) {
    if (flags.contains(SensitivityFlag.CHILD_VISIBLE)
        || flags.contains(SensitivityFlag.LICENSE_PLATE_VISIBLE)
        || (flags.contains(SensitivityFlag.PERSON_VISIBLE)
        && flags.contains(SensitivityFlag.GPS_DATA_PRESENT))) {
      return RiskLevel.SENSITIVE;
    }
    if (flags.contains(SensitivityFlag.PERSON_VISIBLE)
        || flags.contains(SensitivityFlag.GPS_DATA_PRESENT)
        || flags.contains(SensitivityFlag.READABLE_SIGN_OR_TEXT)) {
      return RiskLevel.REVIEW;
    }
    return RiskLevel.SAFE;
  }

  private RecommendedStorageMode computeStorageMode(RiskLevel riskLevel) {
    return switch (riskLevel) {
      case SAFE -> RecommendedStorageMode.STORE_FULL;
      case REVIEW -> RecommendedStorageMode.STORE_REDUCED;
      case SENSITIVE -> RecommendedStorageMode.DISCARD_OR_REVIEW;
    };
  }
}
