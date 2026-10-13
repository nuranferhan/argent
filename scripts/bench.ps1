$ErrorActionPreference = "Stop"
Set-Location (Split-Path -Parent $PSScriptRoot)

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw "Java 21 is required. Install JDK 21 and set JAVA_HOME before running this benchmark."
}
$javaVersion = (& java -version 2>&1 | Select-Object -First 1)
if ($javaVersion -notmatch '21\.') {
    Write-Warning "JMH is configured for the project's Java 21 toolchain. Active runtime: $javaVersion"
}

& .\gradlew.bat :argent-engine:jmh --no-daemon
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Host "JMH JSON results: $((Get-Location).Path)\argent-engine\build\reports\jmh\results.json"
