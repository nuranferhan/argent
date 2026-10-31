#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
[[ -x "${JAVA_HOME:-}/bin/jpackage" ]] || { echo 'Set JAVA_HOME to a full JDK 21 installation (including jpackage).' >&2; exit 2; }
java_version=$("$JAVA_HOME/bin/java" -version 2>&1 | head -n 1)
[[ "$java_version" == *'21.'* ]] || { echo "JDK 21 is required; found: $java_version" >&2; exit 2; }
./gradlew :argent-terminal:packageDistributionForCurrentOS --no-daemon
echo 'Desktop package is under argent-terminal/build/compose/binaries.'
