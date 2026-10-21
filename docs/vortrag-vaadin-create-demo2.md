# Vaadin Create 2026 – Ideen für Demo 2 (Image RAG)

Vortrag **„Two RAG Systems, One Laptop, No Cloud“**, Vaadin Create, Barcelona,
Mittwoch, 28. Oktober 2026, 09:00–09:45 Uhr, Second Track. Dieses Projekt liefert
**Demo 2** (Minute 25–40, etwa 15 Minuten). Aufgaben und Stand: ClickUp-Task
„[VORTRAG] Vaadin Create Barcelona“ mit den Teilaufgaben `[DEMO 1]` bis `[DEMO 6]`.

## Spannungsbogen (Vorschlag, 15 Minuten)

| Minute | Moment | Aussage |
|---|---|---|
| 25 | **1 – Flugmodus** | Ab jetzt läuft alles ohne Netz. |
| 26–29 | **2 – Das Archiv beurteilt deine Fotos** | Datenschutz ist eingebaut, nicht nachgerüstet. |
| 29–32 | **3 – Bannerfoto über den Text im Bild finden** | Das System liest, was auf dem Bild steht. |
| 32–35 | **4 – Suche mit Bedingungen** | Natürliche Sprache wird zu strukturierten Filtern. |
| 35–38 | **5 – Tuning Lab** | Hybride Suche zum Anfassen. |
| 38–40 | **6 – Why not found** | Das System erklärt auch, was es nicht findet. |

## Die Momente im Einzelnen

### 1 – Flugmodus

- **Ablauf:** Vor dem ersten Klick WLAN sichtbar abschalten (Kontrollzentrum auf der
  Leinwand). Ollama, EclipseStore, Lucene und JVector laufen lokal weiter.
- **Wirkung:** „No Cloud“ aus dem Titel wird greifbar. Kostet keine Zeit.
- **Achtung:** Das Reverse Geocoding fragt **Nominatim im Internet** ab. Im Flugmodus
  bleibt es bei reinen Koordinaten. Bilder mit GPS-Daten deshalb vorher verarbeiten
  oder den Fehlschlag bewusst in die Erzählung einbauen.

### 2 – „Das Archiv beurteilt deine Fotos“

- **Ablauf:** Zwei fast gleiche Fotos hochladen, eines mit GPS-Daten im EXIF, eines
  ohne. Das erste landet auf REVIEW (`GPS_DATA_PRESENT`, `ADDRESS_OR_LOCATION_HINT`)
  und bleibt gesperrt, das zweite gilt als SAFE und wird freigegeben.
- **Wirkung:** Setzt den Untertitel „an image archive that judges your photos“ direkt um.
- **Vorbereitung:** Kuratiertes Fotopaar, kein privates Material. Metadaten lassen sich
  durch Neukodieren entfernen. Verhalten im End-to-End-Test vom 21.10.2026 bestätigt.
- **Rückfallebene:** Vorher verarbeitetes Paar in der Übersicht zeigen.

### 3 – Live-Foto vom Konferenzbanner

- **Ablauf:** Banner mit dem Handy fotografieren, per AirDrop auf den Laptop,
  hochladen, nach „Barcelona“ suchen. Treffer über OCR und das BM25-Feld `ocr`.
- **Wirkung:** Material, das erst im Saal entstanden ist.
- **Vorbereitung:** Dauer einplanen (eine Pipeline etwa 1–2 Minuten ohne Denkmodus).
  Während das Bild verarbeitet wird, Moment 4 vorziehen.
- **Rückfallebene:** Am Vortag aufgenommenes Bannerfoto.

### 4 – Suche mit Bedingungen

- **Ablauf:** „Hunde im Schnee, ohne Personen“. Der Inspector zeigt die abgeleiteten
  Filter (Jahreszeit, Person, Kategorie). Danach „Hund im Wald“ auf Deutsch gegen
  englische Bildbeschreibungen.
- **Wirkung:** Query Understanding und mehrsprachige Embeddings ohne Erklärungsaufwand.
- **Rückfallebene:** „Nur transformieren“ zeigt die Filter auch ohne vollständige Suche.

### 5 – Tuning Lab

- **Ablauf:** Eine Anfrage, bei der Schlüsselwort und Bedeutung auseinanderliegen,
  etwa „ABC 123“ gegenüber „Parkschild“. Semantik- und BM25-Gewicht verschieben, die
  Rangfolge ordnet sich neu.
- **Vorbereitung:** Anfragepaar vorab erproben und als Voreinstellung speichern.

### 6 – Why not found

- **Ablauf:** Ein erwartetes Bild fehlt in den Treffern. Die Diagnose nennt den Grund:
  Score unter der Schwelle, gesperrt oder archiviert.
- **Wirkung:** Glaubwürdige Antwort auf „Und wenn es nicht funktioniert?“.
- **Vorbereitung:** Das gesperrte REVIEW-Bild aus Moment 2 eignet sich als Fall.

## Weitere Ideen (bei Zeit oder für die Alternativfassung)

| Idee | Kern | Risiko |
|---|---|---|
| Query-by-Example | Ein Bild als Beispiel wählen, ähnliche finden. | gering |
| Prompt live ändern | Im Migration Center den Bild-Prompt auf deutsche Beschreibungen umstellen, testen, aktivieren, ein Bild neu verarbeiten. | gering |
| Der Preis des Denkens | Denkmodus in den Einstellungen einschalten und eine Suche messen (etwa 15 s ohne, 70–130 s mit). Passt zu „Was es gekostet hat“. | gering, kostet Zeit |
| Ollama beenden | Suche fällt auf die Rohanfrage zurück, neue Uploads werden gesperrt statt freigegeben: Die App scheitert sicher. | hoch, nur mit Aufzeichnung |
| 20 Bilder auf einmal | Parallelität live verstellen, Fortschritt per Push. Bedient die Erzählung über virtuelle Threads. | mittel, braucht Zeit |
| Taxonomie und Cluster | Selbst vorgeschlagene Kategorien und entdeckte Cluster. | mittel |

## Technische Vorbereitung

- Den kuratierten Bildbestand **vorher vollständig verarbeiten**; live nur 2–3 Bilder.
- Kurz vor dem Vortrag eine Suche absetzen, damit das Modell geladen ist.
- Denkmodus aus (Standard seit Commit `17c7a79`).
- Alle Klickpfade einmal komplett im Browser durchgehen.
- Jeden Moment als Bildschirmaufzeichnung sichern.
- Datenverzeichnisse des Demo-Bestands sichern, damit sich nach der Generalprobe der
  Ausgangszustand wiederherstellen lässt.

## Modellvergleich gemma4:31b und qwen3.8:27b-mlx

Gemessen am 21.10.2026 auf dem Vortragsrechner (Apple M2 Max) mit den Original-Prompts
der Anwendung, Denkmodus aus. Testbilder: Schild „PARKPLATZ ABC 123“, Hund im Wald,
Hund in der Wohnung (Handyfoto), Hamburger Speicherstadt. Vier Suchanfragen aus der
Demo-Planung.

**Stabilität.** Mit Bildern in Originalgröße ist `qwen3.8:27b-mlx` beim Handyfoto
(3024 × 4032 Pixel) abgestürzt: Die Bildbeschreibung brauchte 289 s, danach meldete
der MLX-Runner von Ollama `Command buffer execution failed: Impacting Interactivity`
(macOS bricht den GPU-Auftrag ab). `gemma4:31b` (GGUF) verarbeitete dasselbe Bild in
34 s. Mit Bildern, die auf höchstens 1536 Pixel verkleinert sind, liefen beide Modelle
stabil.

**Laufzeiten mit verkleinerten Bildern (Sekunden):**

| Aufgabe | gemma4:31b | qwen3.8:27b-mlx |
|---|---|---|
| Laden (Kaltstart) | 13 | 6 |
| Bildbeschreibung (4 Bilder) | 27–48 | 33–44 |
| OCR (4 Bilder) | 7–12 | 10–25 |
| Anfrageverständnis, JSON (4 Anfragen) | 25–30 | 19–24 |
| Semantische Ableitung, JSON (2 Bilder) | 40–46 | 22–29 |
| **Pipeline je Bild (Beschreibung + OCR + Ableitung), grob** | **≈ 90** | **≈ 85** |

**Qualität:**

| Prüfpunkt | gemma4:31b | qwen3.8:27b-mlx |
|---|---|---|
| OCR Schild | „PARKPLATZ ABC 123“ ✔ | „PARKPLATZ ABC 123“ ✔ |
| OCR kleiner Schriftzug im Stadtbild | falsch | falsch |
| Bilder ohne Text | „NO_TEXT“ ✔ | „NO_TEXT“ ✔ |
| Filter „Hunde im Schnee ohne Personen“ | Winter, keine Person, Tiere ✔ | Winter, keine Person, Tiere ✔ |
| Filter „Auto mit lesbarem Kennzeichen“ | Fahrzeug, Kennzeichen; setzt zusätzlich „keine Person“ | Fahrzeug, Kennzeichen; Person offen ✔ |
| Datenschutzstufe für Personen- und Kennzeichen-Anfragen | SAFE / SENSITIVE | REVIEW (vorsichtiger) |
| Jahreszeit Stadtbild (Frühling) | Frühling ✔ | Herbst ✗ |
| Ort Stadtbild | Kanal, Europa | Hamburg, Speicherstadt, HafenCity ✔ |
| Gültiges JSON | 6 von 6 | 6 von 6 |

**Empfehlung für den Vortrag:** bei `gemma4:31b` bleiben. Das Modell ist mit
Originalbildern stabil, der gesamte Ablauf ist damit end-to-end getestet, und die
Ergebnisse sind gleichwertig. `qwen3.8:27b-mlx` lohnt sich als Textmodell (Anfrage-
verständnis und Ableitung etwa ein Drittel schneller), setzt aber das Verkleinern der
Bilder voraus und müsste vor dem Vortrag ebenso vollständig erprobt werden. Beide
Modelle gleichzeitig zu nutzen, kostet Arbeitsspeicher und Ladezeit beim Wechsel.

## Implementierungen, die die Demo verbessern würden

Sortiert nach Nutzen für den 28.10. Nichts davon ist umgesetzt; jeder Punkt wird erst
nach Freigabe als Issue erfasst und implementiert.

| # | Implementierung | Nutzen für die Demo | Aufwand |
|---|---|---|---|
| 1 | **Bilder vor der Analyse verkleinern** (z. B. längste Kante 1536 px). Die App sendet heute das Original, bis 50 MB. | Verhindert den Absturz von MLX-Modellen bei Handyfotos, spart Übertragung und teils Zeit (Hund im Wald: 63 s → 36 s). | gering |
| 2 | **Modell warm halten:** beim Start der App ein kurzer Aufruf, dazu `keep_alive` über eine Einstellung (z. B. 60 min). Ollama entlädt ein Modell standardmäßig nach 5 Minuten Leerlauf. | Kein Kaltstart (13 s) mitten im Vortrag, wenn zwischen zwei Demo-Momenten Zeit vergeht. | gering |
| 3 | **Anfrageverständnis nicht überfiltern:** Filter nur setzen, wenn die Anfrage sie ausdrücklich nennt; sonst `null`. | „Hund im Wald“ schließt heute Bilder mit Personen aus. Moment 4 wird dadurch verlässlicher. | gering (Prompt) |
| 4 | **Text- und Bildmodell in den Einstellungen umschaltbar** (das Embedding-Modell bleibt gesperrt, weil ein Wechsel eine Neuindizierung erfordert). | Modellwechsel live zeigen; Rückfallebene, falls ein Modell auf der Bühne Probleme macht. | mittel |
| 5 | **Reverse Geocoding offline:** ohne Netz sofort auf Koordinaten zurückfallen statt auf den Timeout zu warten, optional eine lokale Ortsdatenbank. | Moment 1 (Flugmodus) ohne Verzögerung bei Bildern mit GPS-Daten. | gering bis mittel |
| 6 | **Laufzeit je Pipeline-Schritt anzeigen** (Sekunden für OCR, Bildanalyse, Ableitung, Embedding). | Stützt den Abschnitt „Was es gekostet hat“ mit echten Zahlen auf der Leinwand. | mittel |
| 7 | **Demo-Bestand sichern und wiederherstellen** (Skript, das die Datenverzeichnisse zurückspielt). | Nach jeder Probe derselbe Ausgangszustand. | gering |
| 8 | **Bildbeschreibung als Stream in die Oberfläche** (Token für Token, wie die Antworten in Demo 1). | Starker Effekt, verbindet beide Demos; die Wartezeit wird sichtbar statt leer. | mittel bis hoch |
| 9 | **Vollständiges JSON-Schema für die strukturierten Antworten** (bestehendes Feature). | Robustere Felder, weniger Fallback-Fälle. | mittel |
| 10 | **Zustand „Analyse unvollständig“ mit Wiedervorlage** (bestehendes Feature). | Macht den riskanten Moment „Ollama beenden“ rund: Die Bilder werden nachverarbeitet, sobald Ollama zurück ist. | mittel |

Empfehlung bis zum Vortrag: Punkte 1, 2, 3 und 7 (jeweils klein, direkt wirksam),
bei Zeit 5 und 6.
