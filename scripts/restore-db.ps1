param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$BackupFile
)

$ErrorActionPreference = "Stop"
$nativePreferenceWasSet = Test-Path Variable:PSNativeCommandUseErrorActionPreference
if ($nativePreferenceWasSet) { $PSNativeCommandUseErrorActionPreference = $false }
$resolvedBackup = (Resolve-Path -LiteralPath $BackupFile).Path
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

function Invoke-DockerChecked {
    param([string[]]$Arguments)
    $previousPreference = $ErrorActionPreference
    $nativePreferenceWasSet = Test-Path Variable:PSNativeCommandUseErrorActionPreference
    if ($nativePreferenceWasSet) { $previousNativePreference = $PSNativeCommandUseErrorActionPreference }
    try {
        $ErrorActionPreference = "Continue"
        if ($nativePreferenceWasSet) { $PSNativeCommandUseErrorActionPreference = $false }
        $result = & docker @Arguments 2>&1
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousPreference
        if ($nativePreferenceWasSet) { $PSNativeCommandUseErrorActionPreference = $previousNativePreference }
    }
    if ($exitCode -ne 0) {
        throw "docker $($Arguments -join ' ') failed with exit code ${exitCode}: $result"
    }
    return $result
}

if ((Get-Item -LiteralPath $resolvedBackup).Length -eq 0) { throw "Backup file is empty." }

Write-Warning "This replaces the ARGENT database contents. Stop the gateway before continuing."
$confirmation = Read-Host "Type RESTORE ARGENT to replace the current database"
if ($confirmation -cne "RESTORE ARGENT") { throw "Restore cancelled." }

try { Invoke-DockerChecked @("info") | Out-Null }
catch { throw "Docker Engine is not ready. Start Docker Desktop and retry. $($_.Exception.Message)" }

$containerPath = "/tmp/argent-restore-$([guid]::NewGuid().ToString('N')).dump"
try {
    Invoke-DockerChecked @("compose", "cp", $resolvedBackup, "postgres:$containerPath")
    Invoke-DockerChecked @("compose", "exec", "-T", "postgres", "pg_restore", "-U", "argent", "-d", "argent", "--clean", "--if-exists", "--no-owner", "--no-privileges", "--exit-on-error", "--single-transaction", $containerPath)
    Write-Host "Database restore completed. Start ARGENT and inspect /health before accepting orders."
} finally {
    try { Invoke-DockerChecked @("compose", "exec", "-T", "postgres", "rm", "-f", $containerPath) | Out-Null } catch { }
}
