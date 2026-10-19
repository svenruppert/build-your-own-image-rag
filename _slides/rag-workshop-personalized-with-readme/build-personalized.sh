#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
CSV_FILE="$ROOT_DIR/participants.csv"
TMP_DIR="$ROOT_DIR/.tmp/participant-meta"
DIST_DIR="$ROOT_DIR/dist/personalized"

if [[ ! -f "$CSV_FILE" ]]; then
  echo "ERROR: participants.csv not found in project root."
  exit 1
fi

mkdir -p "$TMP_DIR" "$DIST_DIR"

sanitize() {
  echo "$1" | tr '[:upper:]' '[:lower:]' | sed -E 's/[^a-z0-9]+/-/g; s/^-+//; s/-+$//'
}

tail -n +2 "$CSV_FILE" | while IFS=, read -r firstName lastName email company; do
  firstName="${firstName:-}"
  lastName="${lastName:-}"
  email="${email:-}"
  company="${company:-}"

  if [[ -z "$firstName" || -z "$lastName" || -z "$email" ]]; then
    echo "Skipping invalid row: missing required fields."
    continue
  fi

  fullName="$firstName $lastName"

  if [[ -n "$company" ]]; then
    companyLine="Company: $company"
  else
    companyLine=""
  fi

  disclaimer="This personalized workshop copy was generated exclusively for $fullName <$email> and may not be redistributed."
  slug="$(sanitize "${firstName}-${lastName}")"
  metaFile="$TMP_DIR/${slug}.yml"

  cat > "$metaFile" <<EOF
participant:
  firstName: "$firstName"
  lastName: "$lastName"
  fullName: "$fullName"
  email: "$email"
  company: "$company"
  companyLine: "$companyLine"
  disclaimer: "$disclaimer"
EOF

  quarto render "$ROOT_DIR/slides.qmd" --to beamer --metadata-file "$metaFile"

  target="$DIST_DIR/build-your-own-rag-in-java_${slug}.pdf"
  mv "$ROOT_DIR/slides.pdf" "$target"
  echo "Created: $target"
done
