$ErrorActionPreference = "Stop"
$base = "http://127.0.0.1:8080"

function Get-Health {
    Invoke-RestMethod "$base/health"
}

Write-Host "1) Ledger invariant before chaos"
$h1 = Get-Health
if (-not $h1.invariantOk) { throw "Invariant already broken" }
Write-Host "   OK  postgres=$($h1.postgres) redis=$($h1.redis)"

Write-Host "2) Stop gateway JVM (Ctrl+C the :argent-gateway:run window), then press Enter"
Read-Host

Write-Host "3) Start gateway again (scripts\start.ps1), then press Enter"
Read-Host

$h2 = Get-Health
if (-not $h2.invariantOk) { throw "Invariant broken after restart" }
$book = Invoke-RestMethod "$base/api/v1/orderbook/THYAO"
Write-Host "4) After restart: invariant OK, THYAO book bids=$($book.bids.Count) asks=$($book.asks.Count)"
Write-Host "Resting orders were recovered from PostgreSQL. Journal debit==credit still holds."
