#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SLIDES_DIR="$ROOT_DIR/slides"
PUPPETEER_CONFIG="$ROOT_DIR/puppeteer-config.json"

if ! command -v mmdc >/dev/null 2>&1; then
  echo "ERROR: mmdc (mermaid-cli) not found in PATH."
  exit 1
fi

find "$SLIDES_DIR" -type d -name diagrams | while read -r DIAGRAM_DIR; do
  PAGE_DIR="$(dirname "$DIAGRAM_DIR")"
  RENDER_DIR="$PAGE_DIR/rendered"
  mkdir -p "$RENDER_DIR"
  rm -f "$RENDER_DIR"/diagram-*.png

  i=1
  find "$DIAGRAM_DIR" -maxdepth 1 -type f -name "*.mmd" | sort | while read -r MMD; do
    OUT_FILE=$(printf "%s/diagram-%02d.png" "$RENDER_DIR" "$i")
    echo "Rendering $(basename "$MMD") -> $OUT_FILE"
    mmdc --puppeteerConfigFile "$PUPPETEER_CONFIG" -i "$MMD" -o "$OUT_FILE" --backgroundColor transparent
    i=$((i + 1))
  done
done

echo "All Mermaid diagrams rendered."
