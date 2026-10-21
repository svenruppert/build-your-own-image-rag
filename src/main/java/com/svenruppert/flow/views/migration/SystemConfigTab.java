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
package com.svenruppert.flow.views.migration;

import com.svenruppert.flow.views.settings.SettingsView;
import com.svenruppert.flow.views.shared.ViewServices;
import com.svenruppert.imagerag.domain.enums.VectorBackendType;
import com.svenruppert.imagerag.ollama.OllamaConfig;
import com.svenruppert.imagerag.service.PromptTemplateService;
import com.svenruppert.imagerag.service.impl.SemanticDerivationServiceImpl;
import com.svenruppert.imagerag.service.impl.VisionAnalysisServiceImpl;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.RouterLink;

import static com.svenruppert.flow.views.migration.MigrationUi.configRow;
import static com.svenruppert.flow.views.migration.MigrationUi.countLines;
import static com.svenruppert.flow.views.migration.MigrationUi.sectionHeading;

/**
 * "System config" tab of the migration center: read-only view of the active models,
 * vector backend, prompt overrides and image/index statistics.
 */
class SystemConfigTab
    extends VerticalLayout {

  SystemConfigTab(ViewServices services) {
    MigrationUi.styleTab(this);
    OllamaConfig ollamaConfig = services.ollamaConfig();
    PromptTemplateService promptService = services.promptTemplates();

    Span settingsHint = new Span(getTranslation("migration.config.settings.hint") + " ");
    settingsHint.add(new RouterLink(getTranslation("nav.settings"), SettingsView.class));
    settingsHint.getStyle().set("font-size", "var(--lumo-font-size-s)");
    add(settingsHint);

    add(sectionHeading(getTranslation("migration.config.models")));
    add(configRow(getTranslation("migration.config.vision.model"),
                  ollamaConfig.getVisionModel()));
    add(configRow(getTranslation("migration.config.text.model"),
                  ollamaConfig.getTextModel()));
    add(configRow(getTranslation("migration.config.embedding.model"),
                  ollamaConfig.getEmbeddingModel()));
    add(configRow(getTranslation("migration.config.base.url"),
                  ollamaConfig.baseUrl()));

    add(sectionHeading(getTranslation("migration.config.backend")));
    VectorBackendType backend = services.vectorBackendType();
    add(configRow(getTranslation("migration.config.vector.backend"),
                  backend.name()));

    // Prompt versions
    add(sectionHeading(getTranslation("migration.config.active.prompts")));
    for (String key : new String[]{
        VisionAnalysisServiceImpl.PROMPT_KEY,
        SemanticDerivationServiceImpl.PROMPT_KEY}) {
      promptService.getActiveContent(key).ifPresentOrElse(
          content -> add(configRow(key,
                                   getTranslation("migration.config.prompt.override", countLines(content)))),
          () -> add(configRow(key,
                              getTranslation("migration.config.prompt.builtin")))
      );
    }

    // Image / index statistics
    add(sectionHeading(getTranslation("migration.config.stats")));
    long totalImages = services.catalog().findAllImages().size();
    long indexedImages = services.catalog().findAllIndexedImageIds().size();
    add(configRow(getTranslation("migration.config.total.images"), String.valueOf(totalImages)));
    add(configRow(getTranslation("migration.config.indexed.images"), String.valueOf(indexedImages)));
  }
}
