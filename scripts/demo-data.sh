#!/usr/bin/env bash
#
# Copyright © 2026 Sven Ruppert (sven.ruppert@gmail.com)
#
# Licensed under the EUPL, Version 1.2 (the "Licence");
# you may not use this file except in compliance with the Licence.
# You may obtain a copy of the Licence at:
#
#     https://joinup.ec.europa.eu/software/page/eupl
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the Licence is distributed on an "AS IS" basis,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the Licence for the specific language governing permissions and
# limitations under the Licence.
#
# Saves and restores the runtime data (store, images, previews, keyword index) under a
# name, so a demo can start from the same state after every rehearsal.
#
# Usage: scripts/demo-data.sh save <name> | restore <name> | list
# Stop the application first: EclipseStore and Lucene keep their files open.
set -euo pipefail

cd "$(dirname "$0")/.."
SNAPSHOTS="_demo_snapshots"
DIRS=(_data _data_images _data_images_previews _data_keyword_index)

die() { echo "error: $*" >&2; exit 1; }

ensure_app_stopped() {
  for dir in "${DIRS[@]}"; do
    # Check lsof's output, not its exit code: unrelated warnings (e.g. an unreachable
    # network volume) make lsof exit with 1 even when it found open files.
    if [ -d "$dir" ] && [ -n "$(lsof +D "$dir" 2>/dev/null)" ]; then
      die "files in $dir are open — stop the application first"
    fi
  done
}

valid_name() {
  [[ "${1:-}" =~ ^[A-Za-z0-9._-]+$ ]] || die "name must consist of letters, digits, '.', '_' or '-'"
}

case "${1:-}" in
  save)
    valid_name "${2:-}"
    ensure_app_stopped
    target="$SNAPSHOTS/$2"
    [ -e "$target" ] && die "snapshot '$2' exists already"
    mkdir -p "$target"
    for dir in "${DIRS[@]}"; do
      [ -d "$dir" ] && cp -R "$dir" "$target/"
    done
    echo "saved snapshot '$2' ($(du -sh "$target" | cut -f1))"
    ;;
  restore)
    valid_name "${2:-}"
    ensure_app_stopped
    source_dir="$SNAPSHOTS/$2"
    [ -d "$source_dir" ] || die "no snapshot '$2' (see: $0 list)"
    for dir in "${DIRS[@]}"; do
      rm -rf "$dir"
      [ -d "$source_dir/$dir" ] && cp -R "$source_dir/$dir" .
    done
    echo "restored snapshot '$2'"
    ;;
  list)
    [ -d "$SNAPSHOTS" ] && ls -1 "$SNAPSHOTS" || echo "no snapshots"
    ;;
  *)
    echo "usage: $0 save <name> | restore <name> | list" >&2
    exit 2
    ;;
esac
