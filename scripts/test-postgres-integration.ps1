$ErrorActionPreference = "Stop"
Set-Location (Split-Path -Parent $PSScriptRoot)

$previousJavaHome = $env:JAVA_HOME
$previousPath = $env:PATH
$previousGradleHome = $env:GRADLE_USER_HOME
$previousTestUrl = $env:ARGENT_TEST_POSTGRES_URL
$previousTestUser = $env:ARGENT_TEST_POSTGRES_USER
$previousTestPassword = $env:ARGENT_TEST_POSTGRES_PASSWORD
$postgresPassword = $null

try {
    $null = docker compose exec -T postgres pg_isready -U argent -d argent 2>$null
    if ($LASTEXITCODE -ne 0) {
        throw "PostgreSQL is not ready. Start it with 'docker compose up -d postgres' and retry."
    }

    # Read the password inside the running container. It is assigned only to this
    # PowerShell process and is never printed or written to the repository.
    $postgresPassword = (& docker compose exec -T postgres sh -c 'printf %s "$POSTGRES_PASSWORD"') -join ""
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($postgresPassword)) {
        throw "Could not read the PostgreSQL password from the running Compose service."
    }

    $jdkCandidates = @()
    if ($env:JAVA_HOME) { $jdkCandidates += Get-Item -LiteralPath $env:JAVA_HOME -ErrorAction SilentlyContinue }
    $jdkCandidates += Get-ChildItem -Path (Join-Path $env:USERPROFILE ".gradle\jdks") -Directory -ErrorAction SilentlyContinue
    $jdkCandidates += Get-ChildItem -Path (Join-Path $env:ProgramFiles "Java") -Directory -ErrorAction SilentlyContinue
    $jdk21 = $jdkCandidates | Where-Object {
        $release = Join-Path $_.FullName "release"
        (Test-Path (Join-Path $_.FullName "bin\java.exe")) -and
        (Test-Path $release) -and ((Get-Content -Raw $release) -match 'JAVA_VERSION="21(?:\.|\")')
    } | Select-Object -First 1
    if (-not $jdk21) { throw "A Java 21 JDK is required. Install one or set JAVA_HOME, then retry." }

    $env:JAVA_HOME = $jdk21.FullName
    $env:PATH = "$(Join-Path $jdk21.FullName 'bin');$previousPath"
    $env:GRADLE_USER_HOME = Join-Path (Get-Location) ".gradle-user-home"
    $env:ARGENT_TEST_POSTGRES_URL = "jdbc:postgresql://127.0.0.1:5432/argent"
    $env:ARGENT_TEST_POSTGRES_USER = "argent"
    $env:ARGENT_TEST_POSTGRES_PASSWORD = $postgresPassword

    Write-Host "Running isolated PostgreSQL ledger integration test (unique temporary schema)."
    & .\gradlew.bat --no-daemon --rerun-tasks :argent-ledger:test --tests com.argent.ledger.PostgresLedgerIntegrationTest
    if ($LASTEXITCODE -ne 0) { throw "PostgreSQL integration test failed (exit $LASTEXITCODE)." }

    Write-Host "PASS PostgreSQL concurrent holds, post-commit recovery, settlement replay, and ledger invariants."
} finally {
    $env:JAVA_HOME = $previousJavaHome
    $env:PATH = $previousPath
    $env:GRADLE_USER_HOME = $previousGradleHome
    $env:ARGENT_TEST_POSTGRES_URL = $previousTestUrl
    $env:ARGENT_TEST_POSTGRES_USER = $previousTestUser
    $env:ARGENT_TEST_POSTGRES_PASSWORD = $previousTestPassword
    $postgresPassword = $null
}
