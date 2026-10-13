$ErrorActionPreference = "Stop"
Set-Location (Split-Path -Parent $PSScriptRoot)
& .\gradlew.bat :argent-terminal:run --no-daemon
