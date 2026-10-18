$ErrorActionPreference = "Stop"
Set-Location (Split-Path -Parent $PSScriptRoot)

function Invoke-Docker {
    param([string[]]$Arguments, [switch]$Quiet)
    $previousPreference = $ErrorActionPreference
    $nativePreferenceWasSet = Test-Path Variable:PSNativeCommandUseErrorActionPreference
    if ($nativePreferenceWasSet) {
        $previousNativePreference = $PSNativeCommandUseErrorActionPreference
        $PSNativeCommandUseErrorActionPreference = $false
    }
    try {
        $ErrorActionPreference = "Continue"
        if ($Quiet) {
            & docker @Arguments 2>&1 | Out-Null
        } else {
            & docker @Arguments 2>&1 | ForEach-Object { Write-Host $_ }
        }
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousPreference
        if ($nativePreferenceWasSet) {
            $PSNativeCommandUseErrorActionPreference = $previousNativePreference
        }
    }
    return $exitCode
}

function Import-ProjectEnvironment {
    $envFile = Join-Path (Get-Location) ".env"
    if (-not (Test-Path -LiteralPath $envFile)) { return }
    foreach ($line in Get-Content -LiteralPath $envFile) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith("#")) { continue }
        if ($trimmed -notmatch '^([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$') { continue }
        $name = $Matches[1]
        $value = $Matches[2].Trim()
        if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        $current = Get-Item -Path "Env:$name" -ErrorAction SilentlyContinue
        $legacyDefault =
            ($name -eq "JWT_SECRET" -and $current.Value -eq "argent-dev-secret-change-me-in-prod-32chars") -or
            ($name -eq "BOOTSTRAP_ADMIN_EMAIL" -and $current.Value -eq "admin@argent.local") -or
            ($name -eq "BOOTSTRAP_ADMIN_PASSWORD" -and $current.Value -eq "ChangeMeNow1!")
        if (-not $current -or [string]::IsNullOrWhiteSpace($current.Value) -or $legacyDefault) {
            Set-Item -Path "Env:$name" -Value $value
        }
    }
}

Import-ProjectEnvironment
if (-not $env:BOOTSTRAP_ADMIN_EMAIL) {
    $env:BOOTSTRAP_ADMIN_EMAIL = Read-Host "Bootstrap admin email (existing database: enter the same email)"
}
if (-not $env:BOOTSTRAP_ADMIN_PASSWORD) {
    $securePassword = Read-Host "Bootstrap admin password (existing database: enter the same password)" -AsSecureString
    $passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
    try {
        $env:BOOTSTRAP_ADMIN_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
    }
}
if ([string]::IsNullOrWhiteSpace($env:BOOTSTRAP_ADMIN_EMAIL) -or [string]::IsNullOrWhiteSpace($env:BOOTSTRAP_ADMIN_PASSWORD)) {
    throw "Bootstrap administrator email and password are required. Set them in the environment or project .env file and retry."
}

$dockerInfoExitCode = Invoke-Docker -Arguments @("info") -Quiet
if ($dockerInfoExitCode -ne 0) {
    throw "Docker Engine is not ready (docker info exit $dockerInfoExitCode). Start or restart Docker Desktop, wait until its engine is running, then retry. Check `docker version` if the problem continues."
}

Write-Host "Building and starting ARGENT services..."
$composeExitCode = Invoke-Docker -Arguments @("compose", "--profile", "app", "up", "-d", "--build")
if ($composeExitCode -ne 0) { throw "Docker Compose failed (exit $composeExitCode). Inspect with: docker compose --profile app logs" }

Write-Host "Waiting for ARGENT API..."
$ready = $false
$lastHealth = "No health response received yet."
$startedAt = Get-Date
$deadline = $startedAt.AddMinutes(5)
$nextProgress = $startedAt.AddSeconds(30)
while (-not $ready -and (Get-Date) -lt $deadline) {
    try {
        $health = Invoke-RestMethod "http://127.0.0.1:8080/health" -TimeoutSec 15
        $lastHealth = $health | ConvertTo-Json -Compress
        if ($health.status -eq "UP") { $ready = $true; break }
    } catch {
        $lastHealth = $_.Exception.Message
    }
    if ((Get-Date) -ge $nextProgress) {
        $elapsed = [int]((New-TimeSpan -Start $startedAt -End (Get-Date)).TotalSeconds)
        Write-Host "Gateway is still recovering; waited $elapsed seconds. Last health result: $lastHealth"
        $nextProgress = (Get-Date).AddSeconds(30)
    }
    if ((Get-Date) -lt $deadline) { Start-Sleep -Seconds 2 }
}
if (-not $ready) {
    Write-Host "Last health result: $lastHealth"
    Invoke-Docker -Arguments @("compose", "--profile", "app", "logs", "--tail", "80", "gateway") | Out-Null
    throw "ARGENT API did not become healthy. Check the gateway logs above."
}

Write-Host "API ready. Starting the desktop terminal."
Write-Host "Admin account: $env:BOOTSTRAP_ADMIN_EMAIL"
$jdkCandidates = @()
$jdkCandidates += Get-ChildItem -Path (Join-Path $env:USERPROFILE ".gradle\jdks") -Directory -ErrorAction SilentlyContinue
$jdkCandidates += Get-ChildItem -Path (Join-Path $env:ProgramFiles "Java") -Directory -ErrorAction SilentlyContinue
$java21Home = $jdkCandidates | Where-Object {
    $releaseFile = Join-Path $_.FullName "release"
    (Test-Path (Join-Path $_.FullName "bin\java.exe")) -and
    (Test-Path $releaseFile) -and
    ((Get-Content -Raw $releaseFile) -match 'JAVA_VERSION="21(?:\.|")')
} | Select-Object -First 1
if (-not $java21Home) {
    throw "A Java 21 runtime is required for the desktop terminal. Gradle's Java 21 toolchain was not found under $env:USERPROFILE\.gradle\jdks or $env:ProgramFiles\Java."
}

$previousJavaHome = $env:JAVA_HOME
$previousPath = $env:PATH
try {
    $env:JAVA_HOME = $java21Home.FullName
    $env:PATH = "$(Join-Path $java21Home.FullName 'bin');$previousPath"
    Write-Host "Using Java 21: $env:JAVA_HOME"
    & .\gradlew.bat :argent-terminal:compileKotlin --no-daemon
    if ($LASTEXITCODE -ne 0) { throw "Terminal compilation failed (exit $LASTEXITCODE)." }
    & .\gradlew.bat :argent-terminal:run --no-daemon
    if ($LASTEXITCODE -ne 0) { throw "Desktop terminal exited with code $LASTEXITCODE." }
} finally {
    $env:JAVA_HOME = $previousJavaHome
    $env:PATH = $previousPath
}
