param(
    [string]$OutputDirectory = "backups"
)

$ErrorActionPreference = "Stop"
$nativePreferenceWasSet = Test-Path Variable:PSNativeCommandUseErrorActionPreference
if ($nativePreferenceWasSet) { $PSNativeCommandUseErrorActionPreference = $false }
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

try { Invoke-DockerChecked @("info") | Out-Null }
catch { throw "Docker Engine is not ready. Start Docker Desktop and retry. $($_.Exception.Message)" }

$resolvedOutput = if ([System.IO.Path]::IsPathRooted($OutputDirectory)) {
    [System.IO.Path]::GetFullPath($OutputDirectory)
} else {
    [System.IO.Path]::GetFullPath((Join-Path $projectRoot $OutputDirectory))
}
New-Item -ItemType Directory -Force -Path $resolvedOutput | Out-Null
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$fileName = "argent-postgres-$stamp.dump"
$destination = Join-Path $resolvedOutput $fileName
$containerPath = "/tmp/$fileName"

try {
    Invoke-DockerChecked @("compose", "exec", "-T", "postgres", "sh", "-c", "pg_dump -U argent -d argent -Fc -f '$containerPath' && chmod 600 '$containerPath'")
    Invoke-DockerChecked @("compose", "cp", "postgres:$containerPath", $destination)
    if (-not (Test-Path -LiteralPath $destination) -or (Get-Item -LiteralPath $destination).Length -eq 0) {
        throw "Backup file was not created or is empty: $destination"
    }
    Write-Host "Database backup created: $destination"
} finally {
    try { Invoke-DockerChecked @("compose", "exec", "-T", "postgres", "rm", "-f", $containerPath) | Out-Null } catch { }
}
