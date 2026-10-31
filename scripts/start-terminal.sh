#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
./gradlew :argent-terminal:run --no-daemon
