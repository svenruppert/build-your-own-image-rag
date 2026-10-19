# Build Your Own Image RAG aus Anwendersicht

## Worum es in der Anwendung geht

Diese Anwendung hilft dabei, eine lokale Bildsammlung intelligent zu erschliessen
und wiederzufinden. Bilder werden nicht nur gespeichert, sondern inhaltlich
analysiert, verschlagwortet, bewertet und durchsuchbar gemacht.

Aus Anwendersicht ist das System eine Arbeitsoberflaeche fuer drei Hauptaufgaben:

- Bilder aufnehmen und verarbeiten
- Bilder durchsuchen und bewerten
- die inhaltliche Qualitaet und Kategorisierung der Sammlung pflegen

Das System richtet sich an Benutzer, die mit Bildbestaenden arbeiten und mehr
brauchen als eine reine Dateiverwaltung. Es kombiniert Upload, Inhaltsanalyse,
Suche, Qualitaetssicherung und Wartung in einer gemeinsamen Oberflaeche.

## Welchen Nutzen die Anwendung bietet

Mit der Anwendung koennen Benutzer:

- grosse Bildmengen geordnet aufnehmen
- Inhalte semantisch durchsuchen statt nur nach Dateinamen
- sensible oder problematische Bilder schneller erkennen
- Kategorien nachpflegen und verbessern
- Suchstrategien vergleichen und optimieren
- nachvollziehen, warum ein Bild gefunden oder nicht gefunden wurde

Kurz gesagt: Die Anwendung macht aus einer unsortierten Bildsammlung eine
durchsuchbare, erklaerbare und pflegbare Wissensbasis.

## Typische Benutzersicht auf den Gesamtprozess

Ein normaler Arbeitsablauf sieht so aus:

1. Bilder werden hochgeladen.
2. Das System verarbeitet die Bilder im Hintergrund.
3. Die Bilder erscheinen in der Uebersicht mit Beschreibung, Kategorie und Risiko.
4. Ueber die Suche oder Multimodalsuche werden passende Bilder gefunden.
5. Falls noetig werden Bilder freigegeben, gesperrt, archiviert oder neu verarbeitet.
6. Die Taxonomie wird laufend verbessert, wenn das System Unsicherheiten oder
   Muster erkennt.

## Die wichtigsten Bereiche der Anwendung

## Dashboard

Das Dashboard ist der Einstiegspunkt. Es dient nicht nur als Startseite, sondern
als Orientierungs- und Arbeitsflaeche.

Aus Sicht des Benutzers bietet das Dashboard:

- einen schnellen Ueberblick ueber den Zustand des Systems
- direkte Einstiege in die wichtigsten Arbeitsbereiche
- Hinweise auf den aktuellen Bearbeitungsstand
- eine didaktische Struktur fuer Schulung, Workshop oder Exploration

Das Dashboard ist besonders hilfreich fuer neue Benutzer, weil es die
Anwendung nicht als lose Sammlung einzelner Screens zeigt, sondern als
zusammenhaengenden Workflow.

## Upload

Im Upload-Bereich koennen Bilder direkt in das System eingestellt werden.

Wichtig aus Anwendersicht:

- mehrere Dateien koennen in einem Schritt hochgeladen werden
- die Dateien werden nicht sofort vollstaendig verarbeitet, sondern in eine
  Verarbeitungswarteschlange gestellt
- der Benutzer muss nicht warten, bis die Analyse beendet ist
- nach dem Upload kann direkt zur Pipeline gewechselt werden, um den Fortschritt
  zu verfolgen

Der Upload-Bereich ist damit der Eingangskanal fuer neue Inhalte.

## Pipeline

Die Pipeline zeigt, was mit hochgeladenen Bildern gerade passiert. Sie ist die
Arbeitsansicht fuer laufende oder fehlgeschlagene Verarbeitungen.

Benutzer koennen dort:

- sehen, welche Jobs in der Warteschlange sind
- laufende Verarbeitungen beobachten
- fehlgeschlagene Jobs neu starten
- Jobs abbrechen oder priorisieren
- die Parallelitaet der Verarbeitung anpassen
- den Keyword- oder Vektorindex neu aufbauen

Die Pipeline ist besonders fuer Operatoren und Administratoren wichtig. Sie
macht sichtbar, dass die Anwendung nicht nur Bilder speichert, sondern sie in
mehreren Verarbeitungsschritten anreichert.

## Uebersicht

Die Uebersicht ist die zentrale Arbeitsansicht fuer bereits aktive Bilder.

Hier koennen Benutzer:

- Bilder als Tabelle oder Kacheln ansehen
- nach Kategorie, Risiko und Sichtbarkeit filtern
- Bilddetails oeffnen
- Kategorien und Risikoangaben direkt bearbeiten
- Bilder freigeben oder sperren
- Bilder archivieren
- mehrere Bilder gleichzeitig bearbeiten
- zwei Bilder miteinander vergleichen

Die Uebersicht ist damit der Bereich fuer das operative Tagesgeschaeft: sichten,
pruefen, freigeben, korrigieren.

## Archiv

Das Archiv zeigt Bilder, die aus dem aktiven Bestand entfernt wurden, aber noch
nicht endgueltig geloescht sind.

Benutzer koennen dort:

- archivierte Bilder ansehen
- sie wiederherstellen
- sie dauerhaft loeschen
- Archivaktionen gesammelt auf mehrere Bilder anwenden

Das Archiv trennt den aktiven Bestand vom Rueckhaltebereich. Dadurch koennen
Benutzer konservativ arbeiten: erst ausblenden, spaeter endgueltig entfernen.

## Suche

Die Suche ist die klassische Arbeitsoberflaeche fuer semantische Bildsuche.

Benutzer geben nicht nur Schlagwoerter ein, sondern koennen natuerliche
Formulierungen verwenden, zum Beispiel:

- "Sommerbilder mit Personen im Park"
- "Fahrzeuge mit lesbarem Kennzeichen"
- "ruhige Landschaften ohne Menschen"

Aus Sicht des Benutzers bietet die Suche:

- Umwandlung freier Sprache in eine strukturierte Suchabsicht
- Ergebnisdarstellung als Tabelle oder Kachelansicht
- Suchhistorie
- absichtlich getrennte Modi fuer "nur Interpretation" und "direkte Suche"
- Facetten zur Eingrenzung der Resultate
- Einsicht in die Suchlogik

Die Suchoberflaeche ist nicht als Black Box gedacht. Benutzer koennen
nachvollziehen, wie eine Anfrage interpretiert wurde und wie Ergebnisse
zustande kommen.

## Multimodale Suche

Die multimodale Suche erweitert die klassische Suchmaske. Statt nur Text zu
verwenden, koennen mehrere Signale kombiniert werden.

Das bedeutet aus Anwendersicht:

- Text kann mit einem Bildbeispiel kombiniert werden
- OCR-Begriffe koennen die Suche verfeinern
- Kategorien koennen direkt als Signal eingebracht werden
- mehrere Suchaspekte werden in einem gemeinsamen Lauf beruecksichtigt

Dieser Bereich ist besonders nuetzlich, wenn ein Bild nicht allein ueber
Beschreibung oder Schlagwort gut zu finden ist, sondern erst durch die
Kombination mehrerer Hinweise.

## Search Tuning Lab

Das Tuning Lab richtet sich an Benutzer, die Suchqualitaet bewusst vergleichen
und optimieren wollen.

Hier koennen Benutzer:

- verschiedene Retrieval-Modi ausprobieren
- Gewichtungen anpassen
- Score-Grenzen veraendern
- Relevanzfeedback geben
- Query-by-Example verwenden
- Presets speichern und erneut laden
- Rankingveraenderungen zwischen Suchlaeufen vergleichen

Dieser Bereich ist weniger fuer den Alltag gedacht als fuer Analyse,
Qualitaetssicherung, Demonstration und Feinabstimmung.

## Taxonomy Maintenance

In diesem Bereich wird die inhaltliche Ordnung der Bildsammlung gepflegt.

Das System unterstuetzt Benutzer dabei, Unsicherheiten und Inkonsistenzen zu
finden. Es erzeugt Vorschlaege, die von Menschen geprueft und uebernommen
werden koennen.

Benutzer koennen dort:

- Vorschlaege fuer Bildkategorien pruefen
- unsichere Zuordnungen gezielt untersuchen
- Cluster mit aehnlichen Bildern entdecken
- Vorschlaege gesammelt akzeptieren, ablehnen oder anwenden
- einzelne Bildmengen fuer eine Analyse auswaehlen

Dieser Bereich ist wichtig, wenn aus einer funktionierenden Suche langfristig
eine stabile und hochwertige Bildtaxonomie entstehen soll.

## Migration Center

Das Migration Center ist die Wartungsansicht fuer technische und fachliche
Weiterentwicklung des Systems.

Aus Anwendersicht, genauer gesagt aus Sicht eines fortgeschrittenen Operators,
bietet dieser Bereich:

- Einblick in die aktuelle Modell- und Systemkonfiguration
- Verwaltung versionierter KI-Prompts
- Testen und Aktivieren neuer Promptvarianten
- Rebuild von Suchindexen

Dieser Bereich ist nicht fuer normale Endanwender gedacht, sondern fuer
Benutzer, die das Verhalten des Systems aktiv weiterentwickeln oder
umbauen wollen.

## Detailansichten und Vergleiche

Neben den grossen Arbeitsbereichen gibt es mehrere Dialoge, die einzelne
Bilder oder Suchergebnisse genauer erklaeren.

Wichtige Nutzerfunktionen sind:

- Detailansicht eines Bildes
- Vergleich von zwei Bildern
- Anzeige des Unterschieds vor und nach Reprocessing
- Erklaerung von Suchergebnissen
- Diagnose, warum ein Bild nicht gefunden wurde

Diese Funktionen helfen vor allem dann, wenn Entscheidungen nicht allein aus
dem Vorschaubild getroffen werden koennen.

## Was das System fuer den Benutzer im Hintergrund erledigt

Ohne dass Benutzer jeden Schritt manuell anstossen muessen, uebernimmt die
Anwendung im Hintergrund mehrere Aufgaben:

- technische Metadaten lesen
- Bildinhalte durch KI beschreiben lassen
- Kategorien vorschlagen
- Risiken bewerten
- Embeddings erzeugen
- Suchindizes aufbauen
- Bildvorschauen erzeugen
- Duplikate erkennen

Dadurch wird die Arbeit fuer Benutzer von der reinen Dateiverwaltung auf
fachliche Bewertung und inhaltliche Suche verschoben.

## Wichtige Begriffe aus Anwendersicht

### Freigeben

Ein Bild ist fuer die Suche und den aktiven Bestand zulaessig.

### Sperren

Ein Bild bleibt gespeichert, soll aber nicht normal sichtbar oder suchbar sein.

### Archivieren

Ein Bild wird aus dem aktiven Bestand entfernt, aber noch nicht endgueltig
geloescht.

### Reprocessing

Ein Bild wird erneut durch die Analysekette geschickt, damit geaenderte Modelle,
Prompts oder Logiken auf bestehende Daten angewendet werden.

### Taxonomie

Die geordnete Kategorienstruktur, mit der Bilder fachlich eingeordnet werden.

### Multimodal

Mehrere Arten von Suchsignalen werden gleichzeitig genutzt, etwa Text, OCR und
ein Bildbeispiel.

## Fuer welche Benutzergruppen die Anwendung sinnvoll ist

Die Anwendung eignet sich vor allem fuer:

- Operatoren, die Bildbestaende kuratieren
- Analysten, die Bilder inhaltlich durchsuchen
- Datenschutz- oder Review-Verantwortliche, die sensible Inhalte pruefen
- Entwickler und Fachverantwortliche, die Such- und Analysequalitaet verbessern
- Schulungs- und Workshop-Umgebungen, in denen semantische Suche erklaert wird

## Typische Nutzungsszenarien

### Szenario 1: Neue Bildsammlung aufnehmen

Ein Benutzer laedt eine neue Sammlung hoch, verfolgt die Verarbeitung in der
Pipeline und prueft anschliessend in der Uebersicht, ob Kategorien, Risiko und
Freigabestatus plausibel sind.

### Szenario 2: Inhaltlich nach Bildern suchen

Ein Benutzer kennt den Dateinamen nicht, weiss aber, was auf dem Bild zu sehen
ist. Er formuliert eine Beschreibung in der Suche und bekommt passende Bilder
ueber semantische und keywordbasierte Verfahren.

### Szenario 3: Ein Suchproblem untersuchen

Ein Bild wird nicht gefunden, obwohl es fachlich passen sollte. Der Benutzer
nutzt "Why not found?" oder das Tuning Lab, um zu verstehen, ob Filter,
Gewichtungen, Freigabestatus oder Query-Signale die Ursache sind.

### Szenario 4: Kategorien verbessern

Die Taxonomieanalyse schlaegt neue oder korrigierte Kategorien vor. Ein
Verantwortlicher prueft diese Vorschlaege und uebernimmt nur die fachlich
sinnvollen.

### Szenario 5: Modell- oder Promptwechsel

Nach einer Aenderung an Prompt oder Embedding-Modell werden bestehende Bilder
neu verarbeitet oder Indexe neu aufgebaut, damit die Suche wieder konsistent
arbeitet.

## Erwartungen an die Anwendung

Die Anwendung ist stark darauf ausgelegt, Ergebnisse nachvollziehbar zu machen.
Benutzer sollten sie daher nicht nur als Suchmaschine betrachten, sondern als
interaktive Arbeitsumgebung fuer:

- Sichtung
- Erklaerung
- Korrektur
- Qualitaetssicherung
- Systempflege

## Zusammenfassung

Aus Benutzersicht ist Build Your Own Image RAG eine kombinierte Plattform fuer
Bildaufnahme, Inhaltsanalyse, semantische Suche und Datenpflege.

Sie unterstuetzt nicht nur dabei, Bilder zu finden, sondern auch dabei:

- den Bildbestand strukturiert zu verwalten
- sensible Inhalte kontrolliert zu behandeln
- Suchergebnisse besser zu verstehen
- Kategorien und Suchqualitaet laufend zu verbessern

Damit ist die Anwendung nicht nur ein Viewer oder Suchfrontend, sondern ein
kompletter Arbeitsraum fuer semantisch erschlossene Bildsammlungen.
