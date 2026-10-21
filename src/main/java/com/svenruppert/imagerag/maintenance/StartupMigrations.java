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
package com.svenruppert.imagerag.maintenance;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.imagerag.domain.enums.RiskLevel;
import com.svenruppert.imagerag.persistence.PersistenceService;

/**
 * Idempotent data migrations that run once per application start.
 */
public class StartupMigrations
    implements HasLogger {

  private final PersistenceService persistenceService;

  public StartupMigrations(PersistenceService persistenceService) {
    this.persistenceService = persistenceService;
  }

  public void run() {
    migrateApprovals();
    // Covers images ingested before duplicate detection existed.
    persistenceService.rebuildHashIndex();
    // Removes primary-in-secondary duplicates in records persisted before that check existed.
    persistenceService.normalizeAllCategoryAssignments();
  }

  /**
   * Approves all active images whose stored sensitivity assessment is SAFE (data ingested
   * before the approval feature existed). Images without an assessment, or with REVIEW /
   * SENSITIVE risk, stay locked.
   */
  private void migrateApprovals() {
    int approved = 0;
    int locked = 0;
    int pending = 0;
    for (var image : persistenceService.findAllImages()) {
      var assessment = persistenceService.findAssessment(image.getId());
      if (assessment.isEmpty()) {
        pending++;
      } else if (assessment.get().getRiskLevel() == RiskLevel.SAFE) {
        persistenceService.approveImage(image.getId());
        approved++;
      } else {
        locked++;
      }
    }
    logger().info("Approval migration complete — auto-approved: {}, locked (REVIEW/SENSITIVE): {}, "
                      + "pending assessment: {}", approved, locked, pending);
  }
}
