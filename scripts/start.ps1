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
        if (-not $current -or [string]::IsNullOrWhiteSpace($current.Value)) {
            Set-Item -Path "Env:$name" -Value $value
        }
    }
}

Import-ProjectEnvironment

$dockerInfoExitCode = Invoke-Docker -Arguments @("info") -Quiet
if ($dockerInfoExitCode -ne 0) {
    throw "Docker Engine is not ready (docker info exit $dockerInfoExitCode). Start or restart Docker Desktop, wait until its engine is running, then retry. Check 'docker version' if the problem continues."
}

if (-not $env:JWT_SECRET) {
    $env:JWT_SECRET = "argent-dev-secret-change-me-in-prod-32chars"
}
if (-not $env:BOOTSTRAP_ADMIN_EMAIL) {
    $env:BOOTSTRAP_ADMIN_EMAIL = Read-Host "Bootstrap admin email (existing account email if already initialized)"
}
if (-not $env:BOOTSTRAP_ADMIN_PASSWORD) {
    $securePassword = Read-Host "Bootstrap admin password (existing account password if already initialized)" -AsSecureString
    $passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
    try { $env:BOOTSTRAP_ADMIN_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer) }
}
if (-not $env:POSTGRES_PASSWORD) { $env:POSTGRES_PASSWORD = "argent" }
if (-not $env:REDIS_PASSWORD) { $env:REDIS_PASSWORD = "argent" }
if (-not $env:RABBITMQ_PASSWORD) { $env:RABBITMQ_PASSWORD = "argent" }
if (-not $env:DB_PASS) { $env:DB_PASS = $env:POSTGRES_PASSWORD }
if (-not $env:REDIS_URL) {
    $env:REDIS_URL = "redis://:$($env:REDIS_PASSWORD)@127.0.0.1:6379"
}
if (-not $env:RABBITMQ_URL) {
    $env:RABBITMQ_URL = "amqp://argent:$($env:RABBITMQ_PASSWORD)@127.0.0.1:5672"
}
if (-not $env:SEED_MARKET_MAKER) {
    $env:SEED_MARKET_MAKER = "true"
}
if (-not $env:SEED_DEMO_FUNDS) { $env:SEED_DEMO_FUNDS = "true" }
if (-not $env:MARKET_DATA_MODE) { $env:MARKET_DATA_MODE = "SIMULATED" }

Write-Host "Starting PostgreSQL, Redis, RabbitMQ, Prometheus, Grafana..."
$composeExitCode = Invoke-Docker -Arguments @("compose", "up", "-d")
if ($composeExitCode -ne 0) { throw "Docker Compose failed (exit $composeExitCode). Inspect with: docker compose logs" }

Write-Host "Waiting for Postgres..."
foreach ($dependency in @(
    @{ Name = "PostgreSQL"; Service = "postgres"; Command = @("pg_isready", "-U", "argent") },
    @{ Name = "Redis"; Service = "redis"; Command = @("redis-cli", "-a", $env:REDIS_PASSWORD, "ping") },
    @{ Name = "RabbitMQ"; Service = "rabbitmq"; Command = @("rabbitmq-diagnostics", "-q", "check_port_connectivity") }
)) {
    $ok = $false
    for ($i = 0; $i -lt 60; $i++) {
        $execArguments = @("compose", "exec", "-T", $dependency.Service) + $dependency.Command
        $execExitCode = Invoke-Docker -Arguments $execArguments -Quiet
        if ($execExitCode -eq 0) { $ok = $true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $ok) { throw "$($dependency.Name) did not become ready. Check Docker Desktop and run docker compose logs $($dependency.Service)" }
    Write-Host "  $($dependency.Name) ready"
}

Write-Host "Gateway http://127.0.0.1:8080"
Write-Host "Admin login: $env:BOOTSTRAP_ADMIN_EMAIL / (BOOTSTRAP_ADMIN_PASSWORD)"
Write-Host "Grafana http://127.0.0.1:3000  admin / configured GRAFANA_ADMIN_PASSWORD"
Write-Host "RabbitMQ UI http://127.0.0.1:15672  argent / configured RABBITMQ_PASSWORD"

& .\gradlew.bat :argent-gateway:run --no-daemon
