#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
./gradlew :argent-android:assembleDebug --no-daemon
printf 'APK: %s\n' "$PWD/argent-android/build/outputs/apk/debug/argent-android-debug.apk"
