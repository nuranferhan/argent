#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
./gradlew test :argent-terminal:compileKotlin :argent-android:compileDebugKotlin --no-daemon --max-workers=2
