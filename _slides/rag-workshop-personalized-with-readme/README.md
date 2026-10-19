# RAG Workshop Slides – Personalized PDF Build

This project generates a Quarto/Beamer PDF slide deck for a workshop about building a secure, self-hosted RAG system in Java.

In addition to the normal PDF build, the project supports **participant-specific PDFs**.  
Each participant receives an individual copy that can contain personalized text directly in the Markdown slide files.

## Features

- Quarto → PDF/Beamer slide deck
- Mermaid diagrams rendered before the PDF build
- One folder per slide
- Participant-based PDF personalization via CSV
- Placeholders directly usable inside Markdown content
- Personalized output filenames
- Personalized footer/disclaimer support

---

## Project structure

```text
rag-workshop-personalized/
├─ _quarto.yml
├─ custom.tex
├─ Makefile
├─ participants.csv
├─ build-personalized.sh
├─ slides.qmd
├─ scripts/
│  └─ render-mermaid.sh
├─ slides/
│  ├─ 01-title/
│  │  ├─ page.md
│  │  ├─ diagrams/
│  │  ├─ rendered/
│  │  └─ images/
│  └─ ...
└─ dist/
   └─ personalized/
```

---

## Participant file

Participant data is read from:

```text
participants.csv
```

### Supported columns

The CSV file must contain one header row.

Supported attributes:

| Column | Required | Description |
|---|---:|---|
| `firstName` | yes | Participant first name |
| `lastName` | yes | Participant last name |
| `email` | yes | Participant email address |
| `company` | no | Participant company |

### Example

```csv
firstName,lastName,email,company
Max,Mustermann,max@example.com,Acme GmbH
Julia,Schmidt,julia@example.com,
```

---

## Generated personalization attributes

For each CSV row, the personalization build creates metadata that can be used directly in Markdown.

### Available attributes in Markdown

| Attribute | Description |
|---|---|
| `participant.firstName` | First name |
| `participant.lastName` | Last name |
| `participant.fullName` | First name + last name |
| `participant.email` | Email address |
| `participant.company` | Raw company value |
| `participant.companyLine` | Preformatted company line, empty if no company exists |
| `participant.disclaimer` | Personalized non-redistribution message |

### Example values

```yaml
participant:
  firstName: Max
  lastName: Mustermann
  fullName: Max Mustermann
  email: max@example.com
  company: Acme GmbH
  companyLine: Company: Acme GmbH
  disclaimer: This personalized workshop copy is intended only for Max Mustermann and may not be redistributed.
```

---

## Using placeholders in Markdown

You can insert participant-specific content directly in any slide Markdown file.

### Examples

```markdown
Prepared for **{{< meta participant.fullName >}}**
```

```markdown
Email: {{< meta participant.email >}}
```

```markdown
{{< meta participant.companyLine >}}
```

```markdown
**{{< meta participant.disclaimer >}}**
```

This makes it possible to add visible ownership and non-redistribution hints anywhere in the deck.

---

## Build commands

### Standard PDF

Builds the normal, non-personalized workshop deck:

```bash
make pdf
```

### Personalized PDFs

Builds one PDF per participant from `participants.csv`:

```bash
make pdf-personalized
```

---

## Build stages

### `make pdf`

1. Render Mermaid diagrams
2. Build the Quarto/Beamer slide deck
3. Create `slides.pdf`

### `make pdf-personalized`

1. Read `participants.csv`
2. Generate participant metadata for each row
3. Render Mermaid diagrams
4. Render the deck once per participant
5. Write PDFs to:

```text
dist/personalized/
```

---

## Output naming

Personalized files are written with participant-specific names.

Example:

```text
dist/personalized/Build-Your-Own-RAG-in-Java_Max_Mustermann.pdf
dist/personalized/Build-Your-Own-RAG-in-Java_Julia_Schmidt.pdf
```

---

## Mermaid rendering

Mermaid source files are stored per slide in:

```text
slides/<slide-folder>/diagrams/
```

Rendered diagram images are written to:

```text
slides/<slide-folder>/rendered/
```

This happens automatically during the build.

---

## Requirements

To build the project locally, you typically need:

- Quarto
- XeLaTeX / TinyTeX / TeX Live
- `mermaid-cli`
- Google Chrome, if your Mermaid rendering setup uses Puppeteer with a Chrome executable
- GNU `make`

---

## Typical workflow

### Normal workshop deck

```bash
make pdf
```

### Personalized delivery

1. Edit `participants.csv`
2. Add or update placeholders in slide Markdown files
3. Run:

```bash
make pdf-personalized
```

4. Collect the generated PDFs from:

```text
dist/personalized/
```

---

## Notes

- Personalization is intended to make ownership visible inside the PDF.
- This is useful when each participant receives an individual workshop copy.
- Email addresses and company names are personal data. Distribution should be handled carefully.
- The company field is optional. The build will still work if it is empty.

---

## Recommended personalization usage

Good places for placeholders are:

- title slide
- introduction slide
- footer
- final slide
- appendix
- warning / disclaimer box

Example:

```markdown
This workshop copy was generated exclusively for **{{< meta participant.fullName >}}**  
and may not be shared with third parties.

{{< meta participant.email >}}
{{< meta participant.companyLine >}}
```

---

## Summary

Use:

```bash
make pdf
```

for the normal deck, and:

```bash
make pdf-personalized
```

to generate one customized PDF per participant from `participants.csv`.
