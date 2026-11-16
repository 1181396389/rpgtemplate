#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}java"
if ! command -v "$JAVA_BIN" >/dev/null 2>&1; then
  echo "ERROR: Java 21 not found. Install JDK 21 (Temurin/OpenJDK) or set JAVA_HOME." >&2
  exit 1
fi

exec "$JAVA_BIN" "$SCRIPT_DIR/tools/BuildNeoForgeMod.java" "$@"
