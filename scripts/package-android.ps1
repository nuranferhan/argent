$ErrorActionPreference = "Stop"
Set-Location (Split-Path -Parent $PSScriptRoot)

$jdkHome = $env:JAVA_HOME
function Test-Jdk17OrNewer([string]$jdkPath) {
    if (-not $jdkPath) { return $false }
    $releaseFile = Join-Path $jdkPath "release"
    if (-not (Test-Path (Join-Path $jdkPath "bin\java.exe")) -or -not (Test-Path $releaseFile)) { return $false }
    $releaseText = Get-Content -Raw $releaseFile
    if ($releaseText -match 'JAVA_VERSION="([0-9]+)') { return ([int]$Matches[1] -ge 17) }
    return $false
}

if (-not (Test-Jdk17OrNewer $jdkHome)) {
    $candidates = @()
    $candidates += Get-ChildItem -Path (Join-Path $env:USERPROFILE ".gradle\jdks") -Directory -ErrorAction SilentlyContinue
    $candidates += Get-ChildItem -Path (Join-Path $env:ProgramFiles "Java") -Directory -ErrorAction SilentlyContinue
    $jdk = $candidates | Where-Object { Test-Jdk17OrNewer $_.FullName } | Select-Object -First 1
    if (-not $jdk) { throw "Android build requires JDK 17 or newer. Set JAVA_HOME to a JDK installation." }
    $jdkHome = $jdk.FullName
}

$sdkHome = $env:ANDROID_HOME
if (-not $sdkHome) { $sdkHome = $env:ANDROID_SDK_ROOT }
if (-not $sdkHome) { $sdkHome = Join-Path $env:LOCALAPPDATA "Android\Sdk" }
if (-not (Test-Path (Join-Path $sdkHome "platforms\android-36\android.jar"))) {
    throw "Android SDK API 36 was not found at '$sdkHome'. Install Android SDK Platform 36 and Build Tools in Android Studio SDK Manager."
}

$oldJavaHome = $env:JAVA_HOME
$oldPath = $env:PATH
$oldAndroidHome = $env:ANDROID_HOME
$oldSdkRoot = $env:ANDROID_SDK_ROOT
try {
    $env:JAVA_HOME = $jdkHome
    $env:PATH = "$(Join-Path $jdkHome 'bin');$oldPath"
    $env:ANDROID_HOME = $sdkHome
    $env:ANDROID_SDK_ROOT = $sdkHome
    Write-Host "Building ARGENT Android debug APK with SDK: $sdkHome"
    & .\gradlew.bat --no-daemon :argent-android:assembleDebug
    if ($LASTEXITCODE -ne 0) { throw "Android APK build failed (exit $LASTEXITCODE)." }
    $apk = Join-Path $PSScriptRoot "..\argent-android\build\outputs\apk\debug\argent-android-debug.apk"
    if (-not (Test-Path $apk)) { throw "Gradle completed, but APK was not found at $apk." }
    Write-Host "Android APK created: $((Resolve-Path $apk).Path)"
} finally {
    $env:JAVA_HOME = $oldJavaHome
    $env:PATH = $oldPath
    $env:ANDROID_HOME = $oldAndroidHome
    $env:ANDROID_SDK_ROOT = $oldSdkRoot
}
