# Build Your Own Image RAG

A compact, self-hosted multimodal image RAG application built with Core Java and
Vaadin Flow. The application ingests images, enriches them with local AI analysis,
stores searchable metadata, and offers semantic, keyword, multimodal, and tuning
workflows for exploring the image collection.

The project is designed as a hands-on training and experimentation app. It keeps
the UI, ingestion pipeline, persistence layer, vector search, keyword search, and
LLM integration visible enough to study and modify.

## Features

- Upload and queue image ingestion jobs.
- Extract EXIF and image metadata.
- Run local vision and semantic analysis through Ollama.
- Derive privacy and sensitivity assessments.
- Store images and metadata locally with EclipseStore.
- Build semantic vectors with an Ollama embedding model.
- Search with vector similarity, Lucene BM25 keyword retrieval, and hybrid ranking.
- Use a JVector-backed persisted vector backend through EclipseStore.
- Inspect image details, analysis data, OCR results, location summaries, and provenance.
- Archive, restore, permanently delete, approve, lock, and reprocess images.
- Tune retrieval settings interactively and save presets.
- Run multimodal searches with text, image, OCR, and category signals.
- Maintain taxonomy suggestions, discover clusters, and review low-confidence images.
- Rebuild keyword and vector indexes from the UI.
- Switch the UI between English and German.

## Technology Stack

- Java 26, configured through `pom.xml`.
- Maven 3.9.9 or newer.
- Vaadin Flow 25.1.1.
- Jetty 12.1.x Maven plugin for local execution.
- EclipseStore 4.x for local object persistence.
- Apache Lucene 10.x for BM25 keyword search.
- JVector 4.x for approximate nearest-neighbor search.
- Ollama for local vision, text, and embedding models.
- SLF4J Simple for logging.

This is a Vaadin Flow servlet application packaged as a WAR. It does not use
Spring Boot.

## Requirements

- JDK 26 available on `PATH`.
- Maven 3.9.9 or newer.
- Ollama running locally for AI-powered ingestion and search.
- The configured Ollama models available locally.

The default Ollama settings are defined in code and can be overridden by
`src/main/resources/imagerag.properties` or JVM system properties:

- `ollama.host`, default `localhost`
- `ollama.port`, default `11434`
- `ollama.vision.model`, default `gemma4:31b`
- `ollama.text.model`, default `gemma4:31b`
- `embedding.model`, default `bge-m3`

## Quick Start

Start Ollama and make sure the configured models are available:

```bash
ollama serve
ollama pull bge-m3
ollama pull gemma4:31b
```

Run the application:

```bash
mvn jetty:run
```

Open the UI:

```text
http://localhost:8080
```

The Maven default goal is also `jetty:run`, so this is equivalent:

```bash
mvn
```

## Build and Test

Compile and run tests:

```bash
mvn test
```

Build the WAR:

```bash
mvn package
```

Build with the production profile:

```bash
mvn -Pproduction package
```

The build output is written under `target/`. The configured final artifact name
uses the `application` WAR name prefix.

## Configuration

Application configuration is loaded from:

```text
src/main/resources/imagerag.properties
```

Every key can be overridden with a JVM system property. System properties have
the highest precedence, followed by `imagerag.properties`, followed by hard-coded
defaults.

Important settings:

| Key | Purpose | Default in this project |
| --- | --- | --- |
| `vector.backend` | Select vector backend: `in-memory` or `gigamap-jvector` | `gigamap-jvector` |
| `embedding.model` | Ollama embedding model for semantic vectors | `bge-m3` |
| `search.min.score` | Default search result score cutoff | `0.65` |
| `taxonomy.confidence.threshold` | Low-confidence taxonomy threshold | `0.60` |
| `ollama.host` | Ollama host | `localhost` |
| `ollama.port` | Ollama port | `11434` |

Example override:

```bash
mvn jetty:run -Dembedding.model=bge-m3 -Dvector.backend=in-memory
```

Changing the embedding model requires a vector index rebuild. Use the Pipeline or
Migration Center view after changing `embedding.model`.

## Local Data

The application stores runtime data in local project directories:

| Directory | Content |
| --- | --- |
| `_data` | EclipseStore object graph |
| `_data_images` | Stored original image files |
| `_data_images_previews` | Generated preview and thumbnail images |
| `_data_keyword_index` | Lucene keyword index |

These directories are runtime state. Deleting them resets the local dataset.

## Main Views

- Dashboard: orientation, status, and guided entry points.
- Upload: upload images into the ingestion queue.
- Pipeline: monitor ingestion and reprocessing jobs, tune parallelism, rebuild indexes.
- Overview: browse active images, edit categories and risk levels, approve or lock images.
- Archive: restore or permanently delete archived images.
- Search: semantic and hybrid image search with query transformation and facets.
- Multimodal Search: combine text, image, OCR, and category signals.
- Taxonomy Maintenance: review taxonomy suggestions and discover clusters.
- Search Tuning Lab: experiment with retrieval modes, weights, feedback, and presets.
- Migration Center: inspect configuration, manage prompt versions, and rebuild indexes.

## Architecture Overview

The code is organized into two main areas:

```text
src/main/java/com/svenruppert/flow
```

Vaadin Flow UI, layouts, routes, dialogs, shared UI helpers, and i18n integration.

```text
src/main/java/com/svenruppert/imagerag
```

Domain model, DTOs, local persistence, ingestion pipeline, service interfaces,
service implementations, Ollama integration, search, taxonomy, and bootstrap code.

Important infrastructure classes:

- `ServiceRegistry`: manual dependency container and application service bootstrap.
- `IngestionPipeline`: asynchronous upload and reprocessing workflow.
- `PersistenceService`: EclipseStore-backed persistence facade.
- `SearchServiceImpl`: semantic, hybrid, tuning, and multimodal retrieval logic.
- `KeywordIndexServiceImpl`: Lucene BM25 indexing and search.
- `EclipseStoreGigaMapJVectorBackend`: persisted raw vectors with JVector search.
- `ViewServices`: view-facing access point for application services.

## Ingestion Flow

1. The user uploads one or more image files.
2. The pipeline stores the image file locally and checks for duplicates.
3. Metadata and optional location information are extracted.
4. Vision analysis is requested from Ollama.
5. Semantic categories, descriptions, OCR, and tags are derived.
6. Sensitivity and privacy risk are assessed.
7. Embeddings are generated and indexed.
8. Keyword index documents are written for BM25 retrieval.
9. The image becomes visible in Overview and Search.

## Search Modes

The project supports several retrieval workflows:

- Natural language search with query understanding.
- Hybrid vector plus BM25 retrieval.
- RRF-based score fusion.
- Query-by-example in the tuning workflow.
- Multimodal signal search.
- Why-not-found diagnostics for missed results.

## Troubleshooting

If AI analysis does not work, verify that Ollama is reachable:

```bash
curl http://localhost:11434/api/tags
```

If search results look inconsistent after changing `embedding.model`, rebuild the
vector index from the Pipeline or Migration Center view.

If the keyword index has stale or missing results, rebuild the keyword index from
the Pipeline or Migration Center view.

If local data should be reset completely, stop the application and remove the
runtime data directories listed above.

## Development Notes

- The project currently uses a manual service registry rather than a DI framework.
- The UI is implemented with server-side Vaadin Flow Java views.
- Background work uses virtual threads and bounded executors.
- Vaadin push is enabled for live UI updates.
- Runtime configuration can be supplied through JVM properties for CI or Docker use.

## License

This project is licensed under the Apache License, Version 2.0. See
`LICENSE` for details.
