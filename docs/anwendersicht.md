# Build Your Own Image RAG aus Anwendersicht

## Worum es in der Anwendung geht

Diese Anwendung hilft dabei, eine lokale Bildsammlung intelligent zu erschließen
und wiederzufinden. Bilder werden nicht nur gespeichert, sondern inhaltlich
analysiert, verschlagwortet, bewertet und durchsuchbar gemacht.

Aus Anwendersicht ist das System eine Arbeitsoberfläche für drei Hauptaufgaben:

- Bilder aufnehmen und verarbeiten
- Bilder durchsuchen und bewerten
- die inhaltliche Qualität und Kategorisierung der Sammlung pflegen

Das System richtet sich an Benutzer, die mit Bildbeständen arbeiten und mehr
brauchen als eine reine Dateiverwaltung. Es kombiniert Upload, Inhaltsanalyse,
Suche, Qualitätssicherung und Wartung in einer gemeinsamen Oberfläche.

## Welchen Nutzen die Anwendung bietet

Mit der Anwendung können Benutzer:

- große Bildmengen geordnet aufnehmen
- Inhalte semantisch durchsuchen statt nur nach Dateinamen
- sensible oder problematische Bilder schneller erkennen
- Kategorien nachpflegen und verbessern
- Suchstrategien vergleichen und optimieren
- nachvollziehen, warum ein Bild gefunden oder nicht gefunden wurde

Kurz gesagt: Die Anwendung macht aus einer unsortierten Bildsammlung eine
durchsuchbare, erklärbare und pflegbare Wissensbasis.

## Typische Benutzersicht auf den Gesamtprozess

Ein normaler Arbeitsablauf sieht so aus:

1. Bilder werden hochgeladen.
2. Das System verarbeitet die Bilder im Hintergrund.
3. Die Bilder erscheinen in der Übersicht mit Beschreibung, Kategorie und Risiko.
4. Über die Suche oder Multimodalsuche werden passende Bilder gefunden.
5. Falls nötig werden Bilder freigegeben, gesperrt, archiviert oder neu verarbeitet.
6. Die Taxonomie wird laufend verbessert, wenn das System Unsicherheiten oder
   Muster erkennt.

## Die wichtigsten Bereiche der Anwendung

## Dashboard

Das Dashboard ist der Einstiegspunkt. Es dient nicht nur als Startseite, sondern
als Orientierungs- und Arbeitsfläche.

Aus Sicht des Benutzers bietet das Dashboard:

- einen schnellen Überblick über den Zustand des Systems
- direkte Einstiege in die wichtigsten Arbeitsbereiche
- Hinweise auf den aktuellen Bearbeitungsstand
- eine didaktische Struktur für Schulung, Workshop oder Exploration

Das Dashboard ist besonders hilfreich für neue Benutzer, weil es die
Anwendung nicht als lose Sammlung einzelner Screens zeigt, sondern als
zusammenhängenden Workflow.

## Upload

Im Upload-Bereich können Bilder direkt in das System eingestellt werden.

Wichtig aus Anwendersicht:

- mehrere Dateien können in einem Schritt hochgeladen werden
- die Dateien werden nicht sofort vollständig verarbeitet, sondern in eine
  Verarbeitungswarteschlange gestellt
- der Benutzer muss nicht warten, bis die Analyse beendet ist
- nach dem Upload kann direkt zur Pipeline gewechselt werden, um den Fortschritt
  zu verfolgen

Der Upload-Bereich ist damit der Eingangskanal für neue Inhalte.

## Pipeline

Die Pipeline zeigt, was mit hochgeladenen Bildern gerade passiert. Sie ist die
Arbeitsansicht für laufende oder fehlgeschlagene Verarbeitungen.

Benutzer können dort:

- sehen, welche Jobs in der Warteschlange sind
- laufende Verarbeitungen beobachten
- fehlgeschlagene Jobs neu starten
- Jobs abbrechen oder priorisieren
- die Parallelität der Verarbeitung anpassen
- den Keyword- oder Vektorindex neu aufbauen

Die Pipeline ist besonders für Operatoren und Administratoren wichtig. Sie
macht sichtbar, dass die Anwendung nicht nur Bilder speichert, sondern sie in
mehreren Verarbeitungsschritten anreichert.

## Übersicht

Die Übersicht ist die zentrale Arbeitsansicht für bereits aktive Bilder.

Hier können Benutzer:

- Bilder als Tabelle oder Kacheln ansehen
- nach Kategorie, Risiko und Sichtbarkeit filtern
- Bilddetails öffnen
- Kategorien und Risikoangaben direkt bearbeiten
- Bilder freigeben oder sperren
- Bilder archivieren
- mehrere Bilder gleichzeitig bearbeiten
- zwei Bilder miteinander vergleichen

Die Übersicht ist damit der Bereich für das operative Tagesgeschäft: sichten,
prüfen, freigeben, korrigieren.

## Archiv

Das Archiv zeigt Bilder, die aus dem aktiven Bestand entfernt wurden, aber noch
nicht endgültig gelöscht sind.

Benutzer können dort:

- archivierte Bilder ansehen
- sie wiederherstellen
- sie dauerhaft löschen
- Archivaktionen gesammelt auf mehrere Bilder anwenden

Das Archiv trennt den aktiven Bestand vom Rückhaltebereich. Dadurch können
Benutzer konservativ arbeiten: erst ausblenden, später endgültig entfernen.

## Suche

Die Suche ist die klassische Arbeitsoberfläche für semantische Bildsuche.

Benutzer geben nicht nur Schlagwörter ein, sondern können natürliche
Formulierungen verwenden, zum Beispiel:

- "Sommerbilder mit Personen im Park"
- "Fahrzeuge mit lesbarem Kennzeichen"
- "ruhige Landschaften ohne Menschen"

Aus Sicht des Benutzers bietet die Suche:

- Umwandlung freier Sprache in eine strukturierte Suchabsicht
- Ergebnisdarstellung als Tabelle oder Kachelansicht
- Suchhistorie
- absichtlich getrennte Modi für "nur Interpretation" und "direkte Suche"
- Facetten zur Eingrenzung der Resultate
- Einsicht in die Suchlogik

Die Suchoberfläche ist nicht als Black Box gedacht. Benutzer können
nachvollziehen, wie eine Anfrage interpretiert wurde und wie Ergebnisse
zustande kommen.

## Multimodale Suche

Die multimodale Suche erweitert die klassische Suchmaske. Statt nur Text zu
verwenden, können mehrere Signale kombiniert werden.

Das bedeutet aus Anwendersicht:

- Text kann mit einem Bildbeispiel kombiniert werden
- OCR-Begriffe können die Suche verfeinern
- Kategorien können direkt als Signal eingebracht werden
- mehrere Suchaspekte werden in einem gemeinsamen Lauf berücksichtigt

Dieser Bereich ist besonders nützlich, wenn ein Bild nicht allein über
Beschreibung oder Schlagwort gut zu finden ist, sondern erst durch die
Kombination mehrerer Hinweise.

## Search Tuning Lab

Das Tuning Lab richtet sich an Benutzer, die Suchqualität bewusst vergleichen
und optimieren wollen.

Hier können Benutzer:

- verschiedene Retrieval-Modi ausprobieren
- Gewichtungen anpassen
- Score-Grenzen verändern
- Relevanzfeedback geben
- Query-by-Example verwenden
- Presets speichern und erneut laden
- Rankingveränderungen zwischen Suchläufen vergleichen

Dieser Bereich ist weniger für den Alltag gedacht als für Analyse,
Qualitätssicherung, Demonstration und Feinabstimmung.

## Taxonomy Maintenance

In diesem Bereich wird die inhaltliche Ordnung der Bildsammlung gepflegt.

Das System unterstützt Benutzer dabei, Unsicherheiten und Inkonsistenzen zu
finden. Es erzeugt Vorschläge, die von Menschen geprüft und übernommen
werden können.

Benutzer können dort:

- Vorschläge für Bildkategorien prüfen
- unsichere Zuordnungen gezielt untersuchen
- Cluster mit ähnlichen Bildern entdecken
- Vorschläge gesammelt akzeptieren, ablehnen oder anwenden
- einzelne Bildmengen für eine Analyse auswählen

Dieser Bereich ist wichtig, wenn aus einer funktionierenden Suche langfristig
eine stabile und hochwertige Bildtaxonomie entstehen soll.

## Migration Center

Das Migration Center ist die Wartungsansicht für technische und fachliche
Weiterentwicklung des Systems.

Aus Anwendersicht, genauer gesagt aus Sicht eines fortgeschrittenen Operators,
bietet dieser Bereich:

- Einblick in die aktuelle Modell- und Systemkonfiguration
- Verwaltung versionierter KI-Prompts
- Testen und Aktivieren neuer Promptvarianten
- Rebuild von Suchindexen

Dieser Bereich ist nicht für normale Endanwender gedacht, sondern für
Benutzer, die das Verhalten des Systems aktiv weiterentwickeln oder
umbauen wollen.

## Detailansichten und Vergleiche

Neben den großen Arbeitsbereichen gibt es mehrere Dialoge, die einzelne
Bilder oder Suchergebnisse genauer erklären.

Wichtige Nutzerfunktionen sind:

- Detailansicht eines Bildes
- Vergleich von zwei Bildern
- Anzeige des Unterschieds vor und nach Reprocessing
- Erklärung von Suchergebnissen
- Diagnose, warum ein Bild nicht gefunden wurde

Diese Funktionen helfen vor allem dann, wenn Entscheidungen nicht allein aus
dem Vorschaubild getroffen werden können.

## Was das System für den Benutzer im Hintergrund erledigt

Ohne dass Benutzer jeden Schritt manuell anstoßen müssen, übernimmt die
Anwendung im Hintergrund mehrere Aufgaben:

- technische Metadaten lesen
- Bildinhalte durch KI beschreiben lassen
- Kategorien vorschlagen
- Risiken bewerten
- Embeddings erzeugen
- Suchindizes aufbauen
- Bildvorschauen erzeugen
- Duplikate erkennen

Dadurch wird die Arbeit für Benutzer von der reinen Dateiverwaltung auf
fachliche Bewertung und inhaltliche Suche verschoben.

## Wichtige Begriffe aus Anwendersicht

### Freigeben

Ein Bild ist für die Suche und den aktiven Bestand zulässig.

### Sperren

Ein Bild bleibt gespeichert, soll aber nicht normal sichtbar oder suchbar sein.

### Archivieren

Ein Bild wird aus dem aktiven Bestand entfernt, aber noch nicht endgültig
gelöscht.

### Reprocessing

Ein Bild wird erneut durch die Analysekette geschickt, damit geänderte Modelle,
Prompts oder Logiken auf bestehende Daten angewendet werden.

### Taxonomie

Die geordnete Kategorienstruktur, mit der Bilder fachlich eingeordnet werden.

### Multimodal

Mehrere Arten von Suchsignalen werden gleichzeitig genutzt, etwa Text, OCR und
ein Bildbeispiel.

## Für welche Benutzergruppen die Anwendung sinnvoll ist

Die Anwendung eignet sich vor allem für:

- Operatoren, die Bildbestände kuratieren
- Analysten, die Bilder inhaltlich durchsuchen
- Datenschutz- oder Review-Verantwortliche, die sensible Inhalte prüfen
- Entwickler und Fachverantwortliche, die Such- und Analysequalität verbessern
- Schulungs- und Workshop-Umgebungen, in denen semantische Suche erklärt wird

## Typische Nutzungsszenarien

### Szenario 1: Neue Bildsammlung aufnehmen

Ein Benutzer lädt eine neue Sammlung hoch, verfolgt die Verarbeitung in der
Pipeline und prüft anschließend in der Übersicht, ob Kategorien, Risiko und
Freigabestatus plausibel sind.

### Szenario 2: Inhaltlich nach Bildern suchen

Ein Benutzer kennt den Dateinamen nicht, weiß aber, was auf dem Bild zu sehen
ist. Er formuliert eine Beschreibung in der Suche und bekommt passende Bilder
über semantische und keywordbasierte Verfahren.

### Szenario 3: Ein Suchproblem untersuchen

Ein Bild wird nicht gefunden, obwohl es fachlich passen sollte. Der Benutzer
nutzt "Why not found?" oder das Tuning Lab, um zu verstehen, ob Filter,
Gewichtungen, Freigabestatus oder Query-Signale die Ursache sind.

### Szenario 4: Kategorien verbessern

Die Taxonomieanalyse schlägt neue oder korrigierte Kategorien vor. Ein
Verantwortlicher prüft diese Vorschläge und übernimmt nur die fachlich
sinnvollen.

### Szenario 5: Modell- oder Promptwechsel

Nach einer Änderung an Prompt oder Embedding-Modell werden bestehende Bilder
neu verarbeitet oder Indexe neu aufgebaut, damit die Suche wieder konsistent
arbeitet.

## Erwartungen an die Anwendung

Die Anwendung ist stark darauf ausgelegt, Ergebnisse nachvollziehbar zu machen.
Benutzer sollten sie daher nicht nur als Suchmaschine betrachten, sondern als
interaktive Arbeitsumgebung für:

- Sichtung
- Erklärung
- Korrektur
- Qualitätssicherung
- Systempflege

## Zusammenfassung

Aus Benutzersicht ist Build Your Own Image RAG eine kombinierte Plattform für
Bildaufnahme, Inhaltsanalyse, semantische Suche und Datenpflege.

Sie unterstützt nicht nur dabei, Bilder zu finden, sondern auch dabei:

- den Bildbestand strukturiert zu verwalten
- sensible Inhalte kontrolliert zu behandeln
- Suchergebnisse besser zu verstehen
- Kategorien und Suchqualität laufend zu verbessern

Damit ist die Anwendung nicht nur ein Viewer oder Suchfrontend, sondern ein
kompletter Arbeitsraum für semantisch erschlossene Bildsammlungen.
