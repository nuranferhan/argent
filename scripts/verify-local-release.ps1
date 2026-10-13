$ErrorActionPreference = "Stop"
Set-Location (Split-Path -Parent $PSScriptRoot)

function Stop-Check([string]$Message) {
    Write-Error $Message
    exit 1
}

Write-Host "ARGENT local release acceptance"

$gatewayBaseUrl = if ($env:ARGENT_GATEWAY_URL) { $env:ARGENT_GATEWAY_URL.TrimEnd('/') } else { "http://127.0.0.1:8080" }
$healthUrl = "$gatewayBaseUrl/health"
$health = $null
$lastHealthError = "No response received."
for ($attempt = 0; $attempt -lt 100; $attempt++) {
    try {
        $health = Invoke-RestMethod -Uri $healthUrl -TimeoutSec 2
        break
    } catch {
        $lastHealthError = $_.Exception.Message
        if (($attempt + 1) % 10 -eq 0) {
            Write-Host "Gateway is still starting; waited $((($attempt + 1) * 3)) seconds..."
        }
        if ($attempt -lt 99) { Start-Sleep -Seconds 1 }
    }
}
if ($null -eq $health) {
    Stop-Check "Gateway health endpoint did not respond within 300 seconds at $healthUrl. Inspect gateway startup with 'docker compose --profile app logs --tail 120 gateway'. Last error: $lastHealthError"
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

$adminEmail = $env:ARGENT_ADMIN_EMAIL
if ([string]::IsNullOrWhiteSpace($adminEmail)) {
    $adminEmail = Read-Host "Admin email for ledger reconciliation check"
}
$adminPassword = $env:ARGENT_ADMIN_PASSWORD
$securePassword = $null
$passwordPointer = [IntPtr]::Zero
if ([string]::IsNullOrEmpty($adminPassword)) {
    $securePassword = Read-Host "Admin password" -AsSecureString
    $passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
    $adminPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
}
try {
    $loginBody = @{ email = $adminEmail; password = $adminPassword } | ConvertTo-Json -Compress
    $login = Invoke-RestMethod -Uri "$gatewayBaseUrl/api/v1/auth/login" -Method Post -ContentType "application/json" -Body $loginBody -TimeoutSec 8
    if ([string]::IsNullOrWhiteSpace($login.accessToken)) {
        Stop-Check "Admin login did not return an access token; ledger reconciliation was not checked."
    }
    $headers = @{ Authorization = "Bearer $($login.accessToken)" }
    $reconciliation = Invoke-RestMethod -Uri "$gatewayBaseUrl/api/v1/admin/ledger/reconciliation" -Headers $headers -TimeoutSec 15
    if ($reconciliation.ok -ne $true -or @($reconciliation.mismatches).Count -ne 0) {
        foreach ($mismatch in @($reconciliation.mismatches)) {
            Write-Host ("  account={0} asset={1} journalNet={2} balanceTotal={3}" -f `
                $mismatch.account, $mismatch.asset, $mismatch.journalNet, $mismatch.balanceTotal) -ForegroundColor Red
        }
        Stop-Check "Ledger reconciliation failed: checked=$($reconciliation.checkedAccountAssets), mismatches=$(@($reconciliation.mismatches).Count)."
    }
    Write-Host "PASS admin ledger reconciliation: $($reconciliation.checkedAccountAssets) account/asset pairs, no mismatches"
} catch {
    if ($_.Exception.Message -like "Ledger reconciliation failed:*") {
        Stop-Check $_.Exception.Message
    }
    Stop-Check "Could not verify the ADMIN ledger reconciliation endpoint. Check the admin credentials, rate limit, and gateway logs. $($_.Exception.Message)"
} finally {
    if ($login -and $login.refreshToken) {
        try {
            $logoutBody = @{ refreshToken = $login.refreshToken } | ConvertTo-Json -Compress
            $null = Invoke-RestMethod -Uri "$gatewayBaseUrl/api/v1/auth/logout" -Method Post -ContentType "application/json" -Body $logoutBody -TimeoutSec 8
        } catch {
            Write-Warning "Could not revoke the temporary release-check refresh token; it expires according to the normal session policy."
        }
    }
    $adminPassword = $null
    $loginBody = $null
    $logoutBody = $null
    $headers = $null
    $login = $null
    if ($passwordPointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
    }
    if ($securePassword) { $securePassword.Dispose() }
}

$terminalBuildFile = Join-Path $PSScriptRoot "..\argent-terminal\build.gradle.kts"
$packageVersionLine = Select-String -Path $terminalBuildFile -Pattern '^\s*packageVersion\s*=\s*"([^"]+)"' | Select-Object -First 1
if (-not $packageVersionLine) {
    Stop-Check "Could not read the desktop package version from $terminalBuildFile."
}
$expectedVersion = $packageVersionLine.Matches[0].Groups[1].Value
$installer = Join-Path $PSScriptRoot "..\argent-terminal\build\compose\binaries\main\msi\Argent-$expectedVersion.msi"
if (-not (Test-Path -LiteralPath $installer -PathType Leaf)) {
    Stop-Check "Argent-$expectedVersion.msi was not found at $([IO.Path]::GetFullPath($installer)). Run .\scripts\package-terminal.ps1 first."
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
    if ($installedVersion -ne $expectedVersion) {
        Stop-Check "Installed Argent version is '$installedVersion' at $($app.FullName); expected $expectedVersion. Install the Argent-$expectedVersion.msi before accepting this release."
    }
    Write-Host "PASS installed desktop executable: $($app.FullName)"
    Write-Host "PASS installed version: $installedVersion"
} else {
    Write-Warning "Argent.exe is not installed in the standard Program Files location. Install the MSI to complete desktop acceptance."
}

Write-Host "Local checks passed. This does not replace desktop workflow, load, security, backup/restore, or production operations acceptance."
