#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
adb_bin=${ADB:-adb}
command -v "$adb_bin" >/dev/null || { echo 'adb is required; install Android SDK Platform Tools.' >&2; exit 2; }
device_count=$("$adb_bin" devices | awk 'NR > 1 && $2 == "device" { n++ } END { print n+0 }')
(( device_count > 0 )) || { echo 'Connect an authorized Android device or start an emulator.' >&2; exit 2; }
./gradlew :argent-android:assembleDebug --no-daemon
apk=argent-android/build/outputs/apk/debug/argent-android-debug.apk
"$adb_bin" reverse tcp:8080 tcp:8080
"$adb_bin" install -r "$apk"
"$adb_bin" shell monkey -p com.argent.android 1
