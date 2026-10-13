$ErrorActionPreference = "Stop"
Set-Location (Split-Path -Parent $PSScriptRoot)

$jdkCandidates = @()
if ($env:JAVA_HOME) {
    $jdkCandidates += Get-Item -LiteralPath $env:JAVA_HOME -ErrorAction SilentlyContinue
}
$jdkCandidates += Get-ChildItem -Path (Join-Path $env:USERPROFILE ".gradle\jdks") -Directory -ErrorAction SilentlyContinue
$jdkCandidates += Get-ChildItem -Path (Join-Path $env:ProgramFiles "Java") -Directory -ErrorAction SilentlyContinue
$java21Home = $jdkCandidates | Where-Object {
    $releaseFile = Join-Path $_.FullName "release"
    (Test-Path (Join-Path $_.FullName "bin\java.exe")) -and
    (Test-Path (Join-Path $_.FullName "bin\jpackage.exe")) -and
    (Test-Path $releaseFile) -and
    ((Get-Content -Raw $releaseFile) -match 'JAVA_VERSION="21(?:\.|\")')
} | Select-Object -First 1

if (-not $java21Home) {
    throw "A full Java 21 JDK with jpackage.exe is required. Install JDK 21 or run .\gradlew.bat javaToolchains to inspect detected toolchains."
}

$previousJavaHome = $env:JAVA_HOME
$previousPath = $env:PATH
try {
    $env:JAVA_HOME = $java21Home.FullName
    $env:PATH = "$(Join-Path $java21Home.FullName 'bin');$previousPath"
    Write-Host "Packaging ARGENT Desktop with Java 21: $env:JAVA_HOME"
    & .\gradlew.bat :argent-terminal:packageDistributionForCurrentOS --no-daemon
    if ($LASTEXITCODE -ne 0) { throw "Windows terminal packaging failed (exit $LASTEXITCODE)." }
    Write-Host "Package task completed. Find the installer under argent-terminal\build\compose\binaries."
} finally {
    $env:JAVA_HOME = $previousJavaHome
    $env:PATH = $previousPath
}
