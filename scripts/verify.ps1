$ErrorActionPreference = "Stop"
Set-Location (Split-Path -Parent $PSScriptRoot)

& .\gradlew.bat test :argent-terminal:compileKotlin :argent-android:compileDebugKotlin --no-daemon --max-workers=2
if ($LASTEXITCODE -ne 0) { throw "Verification failed (exit $LASTEXITCODE)" }
