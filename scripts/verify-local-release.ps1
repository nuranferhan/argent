$ErrorActionPreference = "Stop"
Set-Location (Split-Path -Parent $PSScriptRoot)

function Stop-Check([string]$Message) {
    Write-Error $Message
    exit 1
}

Write-Host "ARGENT local release acceptance (read-only)"

$healthUrl = if ($env:ARGENT_GATEWAY_URL) { "$($env:ARGENT_GATEWAY_URL.TrimEnd('/'))/health" } else { "http://127.0.0.1:8080/health" }
try {
    $health = Invoke-RestMethod -Uri $healthUrl -TimeoutSec 8
} catch {
    Stop-Check "Gateway health endpoint is unavailable at $healthUrl. Start the stack with .\scripts\start-stack.ps1 or .\scripts\start.ps1, then retry. $($_.Exception.Message)"
}

if ($health.status -ne "UP") {
    Stop-Check "Gateway reports status '$($health.status)'. Inspect the health fields below and service logs before accepting this release."
}
if ($health.invariantOk -ne $true -or $health.matchingEngine -ne $true) {
    Stop-Check "Ledger invariant or matching engine is unhealthy. Refusing local release acceptance."
}
foreach ($service in @("postgres", "redis", "rabbit")) {
    if ($health.$service -ne $true) {
        Stop-Check "Required service '$service' is unhealthy according to $healthUrl."
    }
}
Write-Host "PASS API health: $healthUrl"
Write-Host "PASS ledger invariant and matching engine"
Write-Host "PASS PostgreSQL, Redis, RabbitMQ"
Write-Host "Execution mode: $($health.executionMode)"
Write-Host "Crypto market data: $($health.cryptoMarketData)"
Write-Host "BIST market data: $($health.bistMarketData)"

$installer = Join-Path $PSScriptRoot "..\argent-terminal\build\compose\binaries\main\msi\Argent-1.0.3.msi"
if (-not (Test-Path -LiteralPath $installer -PathType Leaf)) {
    Stop-Check "Argent-1.0.3.msi was not found at $([IO.Path]::GetFullPath($installer)). Run .\scripts\package-terminal.ps1 first."
}
$installerItem = Get-Item -LiteralPath $installer
if ($installerItem.Length -lt 10MB) {
    Stop-Check "Installer is unexpectedly small ($($installerItem.Length) bytes): $($installerItem.FullName)"
}
Write-Host "PASS Windows installer exists: $($installerItem.FullName) ($([math]::Round($installerItem.Length / 1MB, 1)) MB)"

$appCandidates = @(
    (Join-Path $env:ProgramFiles "Argent\Argent.exe")
    (Join-Path ${env:ProgramFiles(x86)} "Argent\Argent.exe")
)
$appPaths = @($appCandidates | Where-Object { $_ -and (Test-Path -LiteralPath $_ -PathType Leaf) })
if ($appPaths.Count -gt 0) {
    $app = Get-Item -LiteralPath $appPaths[0]
    $installedVersion = $app.VersionInfo.ProductVersion
    if ($installedVersion -ne "1.0.3") {
        Stop-Check "Installed Argent version is '$installedVersion' at $($app.FullName); expected 1.0.3. Install the 1.0.3 MSI before accepting this release."
    }
    Write-Host "PASS installed desktop executable: $($app.FullName)"
    Write-Host "PASS installed version: $installedVersion"
} else {
    Write-Warning "Argent.exe is not installed in the standard Program Files location. Install the MSI to complete desktop acceptance."
}

Write-Host "Local checks passed. This does not replace desktop workflow, load, security, backup/restore, or production operations acceptance."
