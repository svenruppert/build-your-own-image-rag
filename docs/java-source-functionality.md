# Analyse des Java-Quelltexts

Stand: 2026-04-28

## Zweck dieses Dokuments

Dieses Dokument beschreibt die Funktionalitäten des gesamten Java-Quelltexts
unter `src/main/java`. Es fasst die Architektur, die Laufzeitabläufe und die
Rollen der wichtigsten Pakete und Klassen zusammen. Einfache Daten- und
Enum-Typen werden dabei inhaltlich gruppiert, damit die Beschreibung fachlich
lesbar bleibt.

## Analysierter Umfang

- 147 Java-Dateien insgesamt
- 31 Java-Dateien im Vaadin-UI-Bereich `com.svenruppert.flow.views`
- 112 Java-Dateien im Fach- und Infrastruktur-Bereich `com.svenruppert.imagerag`
- 24 `@Route`-Deklarationen für navigierbare Vaadin-Views

Nicht Bestandteil dieses Dokuments sind TypeScript-, CSS-, Bild- oder
Ressourcendateien außer dort, wo sie für das Verständnis des Java-Codes
notwendig sind.

## Systemüberblick

Die Anwendung ist ein lokales, serverseitiges Vaadin-Flow-System für
multimodale Bildsuche. Der Java-Code deckt die komplette Kette ab:

1. Upload und Queueing von Bildern
2. Persistierung von Bilddateien und Metadaten
3. KI-gestützte Bildanalyse über Ollama
4. Erzeugung und Speicherung von Embeddings
5. Aufbau eines Keyword-Index und eines Vektor-Index
6. Hybrid- und Multimodal-Suche
7. Taxonomiepflege und Clustering
8. Diagnose-, Reprocessing- und Migrationswerkzeuge
9. Vaadin-UI für Bedienung, Monitoring und Analyse

Die Anwendung ist kein Spring-Boot-Projekt. Die Initialisierung erfolgt über
Vaadin-Servlet- und Service-Hooks sowie einen manuellen `ServiceRegistry`.

## Fachliche Hauptfunktionen

### 1. Bildingestion

Der Code nimmt Bilddateien über die Upload-View entgegen und legt daraus
`IngestionJob`s an. Die Pipeline verarbeitet die Bilder asynchron und führt
folgende Schritte aus:

- Speichern der Originaldatei
- SHA-256-basierte Duplikaterkennung
- Extraktion von EXIF- und Bildmetadaten
- Reverse Geocoding vorhandener GPS-Daten
- Vision-Analyse über Ollama
- Semantische Ableitung von Kategorie, Beschreibung und Tags
- Sensitivitäts- und Privacy-Bewertung
- Embedding-Erzeugung
- Indizierung im Vektor- und Keyword-Index

### 2. Bildverwaltung

Das System verwaltet Bilder nicht nur als Dateien, sondern als persistierte
Fachobjekte mit Analyse-, Risiko-, OCR-, Vektor- und Standortdaten. Bilder
können:

- genehmigt oder gesperrt werden
- archiviert (Soft Delete) werden
- wiederhergestellt werden
- endgültig gelöscht werden
- erneut durch die KI-Pipeline verarbeitet werden

### 3. Suche

Es gibt mehrere Suchmodi:

- Workbench für natürliche Sprache mit Query-Verständnis
- Hybrid Search aus Vektor- und BM25-Retrieval
- Search Tuning Lab für experimentelles Ranking und Relevanzfeedback
- Multimodal Search für Kombination aus Text, Bildbeispiel, OCR-Terms und Kategorien
- "Why not found?"-Diagnose für nicht gefundene Bilder

### 4. Taxonomie und Qualitätspflege

Ein eigener Wartungsbereich analysiert die bestehende Bildmenge und erzeugt
Taxonomie-Vorschläge. Darunter fallen:

- Vorschläge für einzelne Bilder
- Vorschläge auf Taxonomieebene
- Confidence-basierte Nachprüfungen
- Cluster-Erkennung auf Basis persistierter Vektoren

### 5. Operative Wartung

Für den Betrieb stellt der Code folgende Funktionen bereit:

- Monitoring der Ingestion-Queue
- Änderung der Parallelität
- Rebuild von Keyword- und Vektor-Index
- Prompt-Versionierung für KI-Prompts
- Reprocessing und Modellmigration

## Architekturebene

### UI-Schicht: `com.svenruppert.flow`

Die UI ist eine serverseitige Vaadin-Flow-Oberfläche. Der Server hält die
Komponenteninstanzen, verarbeitet Benutzerinteraktionen und aktualisiert die
Ansicht über Push.

### Fach- und Infrastrukturschicht: `com.svenruppert.imagerag`

Hier liegen:

- Konfiguration und Bootstrap
- Fachobjekte und DTOs
- Persistenz
- Pipeline
- Service-Abstraktionen
- Service-Implementierungen
- Ollama-Integration

### Übergreifende Muster

- Persistenz über EclipseStore
- Suchindexe über Lucene und JVector
- KI-Anbindung über die Ollama-HTTP-API
- Manuelle Dependency-Verkabelung über `ServiceRegistry`
- UI-nahe Service-Fassade über `ViewServices`

## Zentrale Laufzeitabläufe

### Startup

Beim Start initialisiert `AppInitializer` den `ServiceRegistry`, registriert
Shutdown-Hooks und stellt die vom Benutzer gewählte Sprache pro Session wieder
her. `ServiceRegistry` baut anschließend die komplette Infrastruktur auf:

- Persistenz
- Bildspeicher
- Preview-Cache
- Ollama-Client
- Suchdienste
- Ingestion-Pipeline
- Audit
- Taxonomie-Services
- Prompt-Management

### Upload bis Suchbarkeit

1. `UploadView` übergibt eine Datei als `IngestionJob` an die Pipeline.
2. `IngestionPipeline` speichert das Bild und prüft auf Duplikate.
3. `MetadataExtractionServiceImpl` liest Dateiinformationen und EXIF.
4. `ReverseGeocodingServiceImpl` leitet Standorttexte aus GPS ab.
5. `VisionAnalysisServiceImpl` fragt die Vision-Komponente von Ollama.
6. `SemanticDerivationServiceImpl` erzeugt semantische Struktur.
7. `SensitivityAssessmentServiceImpl` bewertet Datenschutz- und Risikoaspekte.
8. `EmbeddingServiceImpl` erzeugt Embeddings.
9. `VectorIndexService` und `KeywordIndexService` indizieren das Bild.

### Hybridsuche

1. `QueryUnderstandingServiceImpl` übersetzt natürliche Sprache in einen
   `SearchPlan`.
2. `SearchServiceImpl` erzeugt einen Query-Vektor.
3. Vektor- und Keyword-Index liefern Kandidaten.
4. `SearchServiceImpl` fusioniert beide Kanäle über RRF.
5. Strukturfilter und Privacy-Gates werden angewandt.
6. Die UI stellt Ergebnisse, Erklärungen und Diagnosezahlen dar.

### Taxonomieanalyse

1. Die UI baut einen `TaxonomyAnalysisScope`.
2. `TaxonomyAnalysisServiceImpl` läuft über die Bildmenge.
3. Confidence, Kategorien und Heuristiken werden ausgewertet.
4. Vorschläge werden als `TaxonomySuggestion` oder
   `CategoryClusterSuggestion` erzeugt.
5. Die UI zeigt diese Vorschläge inkl. Fortschritt und manueller Freigabe an.

## Paket- und Klassenbeschreibung

## `com.svenruppert.flow`

| Klasse | Funktion |
| --- | --- |
| `AppServlet` | Servlet-Bootstrap der Vaadin-Anwendung. Registriert Session-Initialisierung und Fehlerbehandlung auf Session-Ebene. |
| `AppShell` | Definiert globale Vaadin-Shell-Aspekte wie `@PWA`, Theme, Viewport-Metadaten und serverseitigen Push. |
| `MainLayout` | Zentrale App-Navigation mit Drawer, Sprachumschaltung und SideNav-Einträgen für alle Hauptviews. |

## `com.svenruppert.flow.i18n`

| Klasse | Funktion |
| --- | --- |
| `TranslationProvider` | Stellt EN/DE-Translationsdateien für Vaadin bereit und dient als I18NProvider für die gesamte UI. |

## `com.svenruppert.flow.views`

| Klasse | Funktion |
| --- | --- |
| `AboutView` | Statische Informationsseite über den Autor und die Image-RAG-Anwendung mit Kartenlayout und externen Links. |
| `YoutubeView` | Einbettung eines YouTube-Videos über ein `IFrame`. |

## `com.svenruppert.flow.views.upload`

| Klasse | Funktion |
| --- | --- |
| `UploadView` | Entgegennahme mehrerer Bilddateien über Vaadin Upload, Umwandlung in Queue-Einträge und Navigation zur Pipeline. |

## `com.svenruppert.flow.views.pipeline`

| Klasse | Funktion |
| --- | --- |
| `PipelineView` | Live-Monitor für die Ingestion- und Reprocessing-Jobs. Bietet Pausieren, Priorisieren, Abbrechen, Retry, Rebuild-Aktionen und Parallelitaetssteuerung. |

## `com.svenruppert.flow.views.overview`

| Klasse | Funktion |
| --- | --- |
| `OverviewView` | Hauptansicht für aktive Bilder. Stellt Tabelle und Kachelansicht, Filter, Batch-Aktionen, Freigabe, Archivierung und Detailöffnung bereit. |

## `com.svenruppert.flow.views.archive`

| Klasse | Funktion |
| --- | --- |
| `ArchiveView` | Verwaltung archivierter Bilder. Ermöglicht Wiederherstellung, permanente Löschung, Batch-Aktionen und Einsicht in archivierte Inhalte. |

## `com.svenruppert.flow.views.search`

| Klasse | Funktion |
| --- | --- |
| `SearchView` | Search Workbench mit Query-Verständnis, Advanced Search Plan, Ergebniskacheln/-tabellen, Facetten und Suchhistorie. |
| `SearchInspectorComponent` | Visualisiert die internen Suchschritte und dient der Transparenz für Query-Verarbeitung und Resultatbildung. |
| `SearchHistoryDialog` | Vollansicht der gespeicherten Suchhistorie inklusive Löschen und Wiederverwenden von Suchläufen. |

## `com.svenruppert.flow.views.multimodal`

| Klasse | Funktion |
| --- | --- |
| `MultimodalSearchView` | Kombiniert mehrere Suchsignale wie Text, Bildbeispiel, OCR und Kategorie in einem gemeinsamen Retrieval-Lauf. |

## `com.svenruppert.flow.views.tuning`

| Klasse | Funktion |
| --- | --- |
| `SearchTuningView` | Experimentierumgebung für Retrieval-Konfigurationen, Gewichtungen, Similarity-Funktionen, Relevanzfeedback und Presets. |
| `TuningInspectorComponent` | Zeigt die Pipeline- und Diagnoseschritte eines Tuning-Laufs. |
| `FeedbackSessionPanel` | Visualisiert das aktuell gesammelte Relevanzfeedback innerhalb einer Tuning-Session. |

## `com.svenruppert.flow.views.taxonomy`

| Klasse | Funktion |
| --- | --- |
| `TaxonomyMaintenanceView` | Zentrale Arbeitsoberfläche für Taxonomieanalyse, Vorschläge, Cluster Discovery und Bulk-Aktionen. |
| `TaxonomyAnalysisInspectorComponent` | Fortschritts- und Schrittvisualisierung für Taxonomieanalysen. |
| `ManualImageSelectionDialog` | Bildauswahl für Analysen im manuellen Scope. |

## `com.svenruppert.flow.views.migration`

| Klasse | Funktion |
| --- | --- |
| `MigrationCenterView` | Wartungs- und Migrationszentrale für Modellkonfiguration, Prompt-Versionen und Index-Rebuilds. |

## `com.svenruppert.flow.views.dashboard`

| Klasse | Funktion |
| --- | --- |
| `DashboardView` | Didaktisches Dashboard mit Systemstatus, Navigation, Lernpfaden und Schnellzugriffen auf die Anwendungsbereiche. |

## `com.svenruppert.flow.views.detail`

| Klasse | Funktion |
| --- | --- |
| `DetailDialog` | Mehrtab-Darstellung eines Bildes mit Übersicht, Analyse, Privacy, Standort und Provenance; erlaubt auch Reprocessing und Kategoriekorrektur. |
| `ComparisonDialog` | Gegenüberstellung zweier Bilder inklusive Analyse- und Risiko-Metadaten. |
| `CategoryTreeChooserDialog` | Taxonomieorientierte Kategorienauswahl für Bearbeitungsdialoge. |
| `ReprocessingDiffDialog` | Vergleich vor/nach Reprocessing auf abgeleiteten Feldern. |

## `com.svenruppert.flow.views.shared`

| Klasse | Funktion |
| --- | --- |
| `ViewServices` | UI-nahe Fassade auf die fachlichen Services des `ServiceRegistry`. |
| `ImagePreviewFactory` | Einheitliche Erzeugung von Bildvorschauen für Tabelle, Kachel und Detailansicht mit Fallback auf Originaldaten. |
| `CategoryChooserComponent` | Zweistufiger Kategorie-Filter für Gruppen- und Detailkategorien. |
| `ViewModeToggle` | Umschalter zwischen Tabellen- und Kachelansichten. |
| `MarkdownRenderer` | Wandelt Markdown aus Analysen oder Modelltexten in sichere HTML-Ausgabe um. |
| `ResultExplainabilityPanel` | Erläuterungskomponente für Suchergebnis-Scoring und Kanalbeiträge. |
| `WhyNotFoundDialog` | Diagnose-Dialog, der erklärt, warum ein Bild zu einer Anfrage nicht gefunden wurde. |

## `com.svenruppert.imagerag.bootstrap`

| Klasse | Funktion |
| --- | --- |
| `AppConfig` | Liest `imagerag.properties` plus JVM-Overrides und stellt typisierte Konfigurationszugriffe bereit. |
| `AppInitializer` | Vaadin-Service-Hook für Initialisierung, Shutdown-Verkabelung und Session-Lokalisierung. |
| `ServiceRegistry` | Manueller DI-Container. Baut Infrastruktur, Dienste, Suchindexe und Pipeline auf und hält sie als Singletons. |

## `com.svenruppert.imagerag.ollama`

| Klasse | Funktion |
| --- | --- |
| `OllamaConfig` | Hält Host, Port, Modellnamen und Timeouts für die lokale Ollama-Instanz. |
| `OllamaClient` | HTTP-Client für Vision-Prompts, JSON-Generierung, Embeddings und Verfügbarkeitsprüfung. |

## `com.svenruppert.imagerag.persistence`

| Klasse | Funktion |
| --- | --- |
| `AppDataRoot` | Persistiertes EclipseStore-Wurzelobjekt mit Maps und Listen für alle Fachobjekte. |
| `PersistenceService` | Fachliche Persistenz-Fassade über EclipseStore für Bilder, Analysen, Vektoren, Suchhistorie, Audits, Taxonomie, Presets und Prompt-Versionen. |

## `com.svenruppert.imagerag.pipeline`

| Klasse | Funktion |
| --- | --- |
| `IngestionPipeline` | Kernprozess für Uploads und Reprocessing. Verwaltet Queue, Jobs, Parallelität, Duplikaterkennung und die komplette Analysekette. |
| `IngestionJob` | Laufzeitmodell eines Pipeline-Jobs mit Status, Zeitpunkten, Ergebnis-ID und Diff-Daten. |
| `JobStep` | Enum der Pipeline-Statusschritte. |
| `JobType` | Enum für Upload- versus Reprocessing-Herkunft eines Jobs. |

## `com.svenruppert.imagerag.service`

Diese Schicht beschreibt die öffentlichen fachlichen Abstraktionen:

| Typ | Funktion |
| --- | --- |
| `AuditService` | Audit-Logging fachlich relevanter Zustandsänderungen |
| `ClusterDiscoveryService` | Erzeugung von Cluster-basierten Taxonomie-Vorschlägen |
| `EmbeddingService` | Erzeugung numerischer Embeddings |
| `ImageStorageService` | Ablage, Auflösung und Löschen von Bilddateien |
| `KeywordIndexService` | BM25-Indexierung und Keyword-Suche |
| `MetadataExtractionService` | EXIF- und Bildmetadatenextraktion |
| `PreviewService` | Erzeugung und Cache-Verwaltung von Bildpreviews |
| `PromptTemplateService` | Versionierung, Aktivierung und Lookup von KI-Prompts |
| `QueryUnderstandingService` | Übersetzung natürlicher Sprache in `SearchPlan`s |
| `ReprocessingService` | Neue Ausführung der KI-Pipeline für bestehende Bilder |
| `ReverseGeocodingService` | Standortauflosung aus Koordinaten |
| `SearchService` | Reguläre Suche, Tuning-Suche und Multimodal-Suche |
| `SearchStrategyAutopilot` | Heuristische Empfehlung für Retrieval-Konfigurationen |
| `QueryIntentResolver` | Leichte Intent-Erkennung für das Tuning-Lab |
| `SemanticDerivationService` | Ableitung semantischer Felder aus Vision-Antworten |
| `SensitivityAssessmentService` | Risiko- und Privacy-Bewertung |
| `TaxonomyAnalysisProgress` | Fortschrittsobjekt für Taxonomieanalyse-Läufe |
| `TaxonomyAnalysisService` | Erzeugt Taxonomie-Vorschläge aus Bildmengen |
| `TaxonomySuggestionService` | Lifecycle-Management für Vorschläge |
| `VectorIndexService` | Abstraktion über Vektorindexe |
| `VisionAnalysisService` | Vision-Analyse einzelner Bilder |
| `WhyNotFoundService` | Diagnostik für fehlende Suchtreffer |

## `com.svenruppert.imagerag.service.impl`

| Klasse | Funktion |
| --- | --- |
| `AuditServiceImpl` | Persistiert Audit-Einträge über `PersistenceService`. |
| `ClusterDiscoveryServiceImpl` | Berechnet Cluster auf Basis gespeicherter Vektoren und erzeugt `CategoryClusterSuggestion`s. |
| `EclipseStoreGigaMapJVectorBackend` | Persistierter JVector-Backend für ANN-Suche mit EclipseStore als Rohvektorspeicher. |
| `EmbeddingServiceImpl` | Delegiert Embedding-Erzeugung an `OllamaClient`. |
| `ImageStorageServiceImpl` | Speichert, löscht und lokalisiert Originalbilddateien. |
| `KeywordIndexServiceImpl` | Verwaltet Lucene-Indexaufbau, Dokumentfelder, Rebuild und BM25-Suche. |
| `MetadataExtractionServiceImpl` | Liest Metadaten aus Bilddateien und leitet DTOs für Persistenz ab. |
| `PreviewServiceImpl` | Erzeugt und cached Thumbnails und Detail-Previews auf Dateisystemebene. |
| `PromptTemplateServiceImpl` | Pflegt versionierte Prompt-Vorlagen und importiert Built-ins beim ersten Start. |
| `QueryUnderstandingServiceImpl` | Fragt Ollama nach strukturierten Query-Daten und baut daraus einen `SearchPlan`. |
| `ReprocessingServiceImpl` | Reicht Reprocessing-Aufträge an die gemeinsame Pipeline weiter. |
| `ReverseGeocodingServiceImpl` | Fragt Nominatim für menschenlesbare Standorttexte an. |
| `SearchServiceImpl` | Realisiert Hybrid Search, RRF-Fusion, Tuning-Lab-Ranking und Multimodal-Retrieval. |
| `SemanticDerivationServiceImpl` | Verarbeitet Vision-Ergebnisse zu Kategorien, Beschreibungen, Tags und Confidence-Daten. |
| `SensitivityAssessmentServiceImpl` | Bewertet Bilder anhand von Flags, Risiko-Level und empfohlener Speicherart. |
| `TaxonomyAnalysisServiceImpl` | Analysiert vorhandene Bilddaten und erzeugt reviewbare Taxonomie-Vorschläge. |
| `TaxonomySuggestionServiceImpl` | Akzeptiert, verwirft und wendet Taxonomie-Vorschläge an. |
| `VectorIndexServiceImpl` | Einfacher In-Memory-Vektorindex für Demo- oder Entwicklungsbetrieb. |
| `VisionAnalysisServiceImpl` | Fragt die Vision-Komponente von Ollama für eine initiale Bildbeschreibung an. |
| `WhyNotFoundServiceImpl` | Rekonstruiert kanalweise, warum ein konkretes Bild bei einer Suche nicht in den Resultaten auftaucht. |

## Datenmodell: `com.svenruppert.imagerag.domain`

### Kernentitäten

| Typ | Fachliche Rolle |
| --- | --- |
| `ImageAsset` | Persistierte Stammdaten eines Bildes inklusive Dateiinformationen, SHA-256 und Archivierungsstatus |
| `ImageMetadataInfo` | EXIF- und technische Metadaten des Bildes |
| `LocationSummary` | Zusammenfassung eines Standorts in Koordinaten- und Textform |
| `SemanticAnalysis` | Ergebnis der semantischen Bildanalyse mit Beschreibung, Kategorie, Tags, Prompt-Versionen und Confidence |
| `SensitivityAssessment` | Datenschutz- und Risikobewertung inklusive Flags und Review-Bedarf |
| `OcrResult` | Separat persistiertes OCR-Ergebnis |
| `VectorEntry` | Persistierte Metadaten über ein gespeichertes Embedding |

### Suchbezogene Modelle

| Typ | Fachliche Rolle |
| --- | --- |
| `SearchPlan` | Strukturiertes Suchvorhaben mit semantischem Query-Text und Filtern |
| `SearchResultItem` | Kompakte Ergebnisdarstellung für regulare Suchviews |
| `RecentSearchEntry` | Automatisch gespeicherter Suchverlauf |
| `SavedSearchView` | Vom Benutzer gespeicherte Suchkonfiguration |
| `SearchTuningConfig` | Laufkonfiguration des Tuning-Labs |
| `SearchTuningPreset` | Persistiertes Tuning-Preset |

### Taxonomie- und Verwaltungsmodelle

| Typ | Fachliche Rolle |
| --- | --- |
| `TaxonomySuggestion` | Reviewbarer Vorschlag für Bild- oder Taxonomieänderungen |
| `TaxonomyAnalysisScope` | Definiert die Bildmenge einer Taxonomieanalyse |
| `TaxonomyAnalysisProgress` | Laufender Fortschritt einer Analyse |
| `CategoryMetadata` | Lifecycle-Informationen zu Kategorien |
| `CategoryConfidence` | Primarkategorie, Alternativen und Scores |
| `CategoryCandidate` | Einzelner Alternativkandidat in einer Confidence-Auswertung |
| `CategoryRegistry` | Statisches Nachschlagewerk für Labels, Gruppen und Metadaten der internen Kategorien |
| `CategoryClusterSuggestion` | Cluster-basierter Vorschlag aus der Vektoranalyse |
| `CategoryAssignmentNormalizer` | Hilfslogik zur Bereinigung doppelter oder inkonsistenter Kategoriezuweisungen |
| `ImageOverviewFilter` | Wiederverwendbarer Filterzustand für Übersicht und Kachelansichten |
| `ProcessingSettings` | Zur Laufzeit veränderbare Pipeline- und Suchparallelität |
| `PromptTemplateVersion` | Versionierte KI-Prompt-Definition |
| `AuditEntry` | Audit-Trail-Eintrag |
| `ReprocessingDiff` | Vorher/Nachher-Diff eines Reprocessing-Laufs |

### Kategorie- und Risiko-Enms

Die Enums in `com.svenruppert.imagerag.domain.enums` definieren:

- fachliche Kategorien (`SourceCategory`, `CategoryGroup`)
- Status und Lebenszyklen (`SuggestionStatus`, `CategoryLifecycleState`)
- Datenschutz und Risiko (`RiskLevel`, `SensitivityFlag`, `RecommendedStorageMode`)
- Such- und Retrievalmodi (`SearchMode`, `RetrievalMode`, `SimilarityFunction`)
- Hilfssignale (`SeasonHint`, `FeedbackType`, `QueryIntentType`)
- Infrastrukturmodi (`VectorBackendType`, `TaxonomySuggestionType`)

## DTO-Schicht: `com.svenruppert.imagerag.dto`

Die DTOs trennen transiente Such- und Analyseergebnisse von den persistierten
Domain-Objekten.

### Wichtige DTOs

| Typ | Funktion |
| --- | --- |
| `SearchResult` | Rückgabeobjekt der regulären Suche inklusive Diagnosezählern |
| `VectorSearchHit` | Treffer aus dem Vektorindex |
| `KeywordSearchHit` | Treffer aus dem Lucene-BM25-Index |
| `KeywordIndexDocument` | Normalisierte Datenstruktur für ein Lucene-Dokument |
| `MultimodalSearchConfig` | Konfiguration eines multimodalen Suchlaufs |
| `MultimodalSignal` | Ein einzelnes Suchsignal, z. B. Text oder Bildbeispiel |
| `MultimodalSearchResult` | Multimodales Suchergebnis mit Signalattribution |
| `ScoreBreakdown` | Detaillierte Score-Zerlegung für Tuning-Lab-Ergebnisse |
| `TuningSearchResult` | Ergebnisdatensatz eines Tuning-Laufs |
| `TuningSearchResponse` | Gesamtrückgabe eines Tuning-Laufs |
| `TuningRun` | Snapshot eines abgeschlossenen Tuning-Laufs für Vorher/Nachher-Vergleiche |
| `FeedbackSession` | Session-Zustand für Relevanzfeedback |
| `FeedbackEntry` | Einzelner Feedback-Eintrag |
| `WhyNotFoundAnalysis` | Diagnostikobjekt für fehlende Treffer |
| `SearchStrategyPlan` | Empfehlung des Search Autopilot |
| `VisionAnalysisResponse` | Strukturiertes Ergebnis der Vision-Stufe |
| `ExtractedMetadata` | Übergabeobjekt für extrahierte Dateimetadaten |
| `StoredImage` | Einfaches Transportobjekt für gespeicherte Bildinformationen |

## Besondere funktionale Beobachtungen

### Privacy Gate

Suchergebnisse werden nicht rein technisch, sondern auch fachlich gefiltert.
Nicht genehmigte Bilder werden in `SearchServiceImpl` explizit aus der Ausgabe
entfernt.

### Prompt-Versionierung

Die Vision- und Semantic-Prompts werden versioniert gespeichert. Analyseobjekte
merken sich die jeweils verwendeten Prompt-Versionen, sodass spätere Vergleiche
und Rebuilds nachvollziehbar bleiben.

### Persistierte und nicht persistierte Vektoren

Der Code unterstützt zwei Vektorbackends:

- `VectorIndexServiceImpl` als flüchtigen In-Memory-Index
- `EclipseStoreGigaMapJVectorBackend` als persistierten Rohvektorspeicher plus
  JVector-Suchindex

### Reprocessing als First-Class-Workflow

Reprocessing ist keine Sonderroute außerhalb der Pipeline, sondern läuft über
dieselbe Job-Infrastruktur wie neue Uploads. Dadurch bleiben Monitoring,
Abbruchlogik und Statusanzeige konsistent.

### Erklärbarkeit

Die UI-Komponenten für Suchinspektion, Score Breakdown und
"Why not found?" zeigen, dass der Code nicht nur Suchergebnisse liefern will,
sondern explizit erklärbare Retrieval-Schritte bereitstellt.

## Beobachtete Randfunktionen und Legacy-Reste

- `AboutView` und `YoutubeView` gehören eher zur Demo- bzw. Präsentationsschicht
  als zum Kernworkflow.

## Schlussbild

Der Java-Code implementiert eine vollständige lokale Bild-RAG-Anwendung mit
stark sichtbarer Fachlogik. Die wichtigsten funktionalen Säulen sind:

- lokale Bildingestion
- KI-gestützte semantische Anreicherung
- hybride und multimodale Suche
- Datenschutz- und Freigabelogik
- Taxonomiepflege
- operative Wartung über Vaadin-Tools

Die Anwendung ist damit nicht nur ein Suchfrontend, sondern ein komplettes
Trainings-, Analyse- und Wartungssystem für semantisch erschlossene Bilddaten.
