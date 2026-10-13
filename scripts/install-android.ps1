$ErrorActionPreference = "Stop"
Set-Location (Split-Path -Parent $PSScriptRoot)

$sdkHome = $env:ANDROID_HOME
if (-not $sdkHome) { $sdkHome = $env:ANDROID_SDK_ROOT }
if (-not $sdkHome) { $sdkHome = Join-Path $env:LOCALAPPDATA "Android\Sdk" }
$adb = Join-Path $sdkHome "platform-tools\adb.exe"
if (-not (Test-Path $adb)) { throw "adb.exe not found. Install Android SDK Platform Tools." }
$apk = Join-Path $PSScriptRoot "..\argent-android\build\outputs\apk\debug\argent-android-debug.apk"
if (-not (Test-Path $apk)) { & (Join-Path $PSScriptRoot "package-android.ps1") }
$devices = & $adb devices | Select-String "\tdevice$"
if (-not $devices) { throw "No authorized Android device/emulator found. Enable USB debugging or start an Android emulator." }
& $adb reverse tcp:8080 tcp:8080
if ($LASTEXITCODE -ne 0) { throw "Could not forward gateway port to the Android device." }
& $adb install -r $apk
if ($LASTEXITCODE -ne 0) { throw "APK installation failed (exit $LASTEXITCODE)." }
Write-Host "Installed ARGENT on the connected device. Keep the local gateway running and use http://127.0.0.1:8080 in the app."
