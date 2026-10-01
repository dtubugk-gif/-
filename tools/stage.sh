#!/usr/bin/env bash
# Copies web/ to build/web and overlays real face photos from .local-faces/ (git-ignored) when present.
set -e
cd "$(dirname "$0")/.."
rm -rf build/web && mkdir -p build && cp -r web build/web
if ls .local-faces/*.png >/dev/null 2>&1; then cp .local-faces/*.png build/web/assets/ && echo "using local faces"; else echo "using placeholder faces"; fi
