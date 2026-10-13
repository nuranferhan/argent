param(
    [string]$OutputDirectory = (Join-Path $env:TEMP "argent-backup-verification")
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
        $detail = (($result | Select-Object -Last 4) -join " ").Trim()
        throw "docker $($Arguments -join ' ') failed (exit $exitCode): $detail"
    }
    return $result
}

try { Invoke-DockerChecked @("info") | Out-Null }
catch { throw "Docker Engine is not ready. Start Docker Desktop and retry. $($_.Exception.Message)" }
$runningServices = @(Invoke-DockerChecked @("compose", "ps", "--status", "running", "--services") |
    ForEach-Object { $_.ToString().Trim() })
if ($runningServices -notcontains "postgres") {
    throw "PostgreSQL is not running. Start it with 'docker compose up -d postgres', wait until 'docker compose ps postgres' shows healthy, then rerun this script."
}

$resolvedOutput = [System.IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Force -Path $resolvedOutput | Out-Null
$backup = & "$PSScriptRoot\backup-db.ps1" -OutputDirectory $resolvedOutput
if ($LASTEXITCODE -ne 0) { throw "Database backup creation failed." }
$backupPath = (Get-ChildItem -LiteralPath $resolvedOutput -Filter "argent-postgres-*.dump" |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName
if (-not $backupPath) { throw "Backup file was not found in $resolvedOutput" }

$restoreDb = "argent_restore_verify_$([guid]::NewGuid().ToString('N').Substring(0, 12))"
$containerDump = "/tmp/$restoreDb.dump"
$databaseCreated = $false
try {
    Invoke-DockerChecked @("compose", "exec", "-T", "postgres", "createdb", "-U", "argent", "-T", "template0", $restoreDb) | Out-Null
    $databaseCreated = $true
    Invoke-DockerChecked @("compose", "cp", $backupPath, "postgres:$containerDump") | Out-Null
    Invoke-DockerChecked @("compose", "exec", "-T", "postgres", "pg_restore", "-U", "argent", "-d", $restoreDb, "--no-owner", "--no-privileges", "--exit-on-error", "--single-transaction", $containerDump) | Out-Null

    $schemaSql = "SELECT CASE WHEN to_regclass('public.journal_entries') IS NOT NULL AND to_regclass('public.balances') IS NOT NULL AND to_regclass('public.orders') IS NOT NULL AND to_regclass('public.trades') IS NOT NULL THEN 1 ELSE 0 END"
    $schemaOk = (Invoke-DockerChecked @("compose", "exec", "-T", "postgres", "psql", "-U", "argent", "-d", $restoreDb, "-t", "-A", "-c", $schemaSql)).Trim()
    if ($schemaOk -ne "1") { throw "Restore verification failed: required tables are missing." }

    $ledgerSql = "SELECT count(*) FROM (SELECT asset FROM journal_entries GROUP BY asset HAVING sum(debit) <> sum(credit)) AS unbalanced"
    $unbalanced = (Invoke-DockerChecked @("compose", "exec", "-T", "postgres", "psql", "-U", "argent", "-d", $restoreDb, "-t", "-A", "-c", $ledgerSql)).Trim()
    if ($unbalanced -ne "0") { throw "Restored ledger is not balanced for $unbalanced assets." }

    $countsSql = "SELECT (SELECT count(*) FROM journal_entries) || ',' || (SELECT count(*) FROM orders) || ',' || (SELECT count(*) FROM trades)"
    $counts = (Invoke-DockerChecked @("compose", "exec", "-T", "postgres", "psql", "-U", "argent", "-d", $restoreDb, "-t", "-A", "-c", $countsSql)).Trim()
    Write-Host "Backup/restore verified in isolated database $restoreDb. journal_entries,orders,trades=$counts; ledger invariant=balanced."
    Write-Host "Backup retained at $backupPath"
} finally {
    Invoke-DockerChecked @("compose", "exec", "-T", "postgres", "rm", "-f", $containerDump) | Out-Null
    if ($databaseCreated) {
        Invoke-DockerChecked @("compose", "exec", "-T", "postgres", "dropdb", "-U", "argent", "--if-exists", $restoreDb) | Out-Null
    }
}
