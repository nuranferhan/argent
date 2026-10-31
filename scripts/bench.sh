#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
command -v java >/dev/null || { echo 'Java 21 is required.' >&2; exit 2; }
java -version 2>&1 | head -n 1 | grep -q '21\.' || { echo 'Java 21 is required.' >&2; exit 2; }
./gradlew :argent-engine:jmh --no-daemon
echo 'JMH results: argent-engine/build/reports/jmh/results.json'
