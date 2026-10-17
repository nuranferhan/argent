# Market data and Binance Spot Testnet

ARGENT has three separate functions here: simulated prices, caller-supplied BIST history replay, and orders to Binance's Spot Testnet. None provides live BIST quotes or real-money execution.

## Public crypto prices

The local default is `MARKET_DATA_MODE=BINANCE_PUBLIC`. It reads public crypto tickers from Binance's WebSocket without API credentials; the gateway needs internet access. BIST equities continue to use ARGENT's simulated feed. This public stream is market data only: regular ARGENT orders still use the internal matching engine. Set `MARKET_DATA_MODE=SIMULATED` to run without internet. Binance Spot Testnet execution is configured separately with its own Testnet-only credentials.

## Binance Spot Testnet

Create credentials specifically for Binance Spot Testnet by following Binance's [official Spot Testnet terms and documentation](https://developers.binance.com/en/docs/products/spot/testnet/TESTNET-TERMS-OF-USE). Do not use a production key. Keep both values in the ignored local `.env` file at the repository root:

```dotenv
BINANCE_SPOT_TESTNET_API_KEY=your_testnet_key
BINANCE_SPOT_TESTNET_SECRET=your_testnet_secret
```

Never commit `.env`, paste credentials in chat, or enable withdrawals on any API key. Restart/rebuild the local gateway after setting values:

```powershell
docker compose --profile app up -d --build gateway
```

After the gateway is healthy, sign in to the desktop as the global `ADMIN` and open **Çalışma alanı**. The Spot Testnet panel reports whether credentials are configured. LIMIT (GTC/IOC/FOK) and MARKET orders, status lookups, and cancellation go only to `https://testnet.binance.vision`.

For a newly submitted testnet order, ARGENT records the client/order mapping locally and reserves the required simulated asset before sending the request. It fetches Binance's per-order trade executions and applies each fill exactly once to the ARGENT ledger, including base/quote commissions and third-asset commissions when that asset has enough simulated available balance. Partial fills update balances while the open remainder stays reserved. Cancellation or terminal completion releases the unspent reserve. A background poller continues syncing active orders while the gateway runs and resumes tracked active orders after restart. If fill history is incomplete or a commission cannot be settled locally, the order is marked `SETTLEMENT_REVIEW` and the hold remains in place for safety.

ARGENT's seeded development balances are **not imported from Binance Testnet** and therefore do not necessarily equal the starting Testnet wallet. After executions, ARGENT mirrors the fill deltas and commissions into its own simulated balances; Binance separately enforces its Testnet wallet funds. Testnet balances and actual orders remain in Binance's sandbox, and no real money is used.

The gateway uses a single local Testnet key pair, so access is ADMIN-only. It is intended for a local development instance, not a shared/public deployment. The key and secret are read from environment variables and are never returned by the status endpoint or deliberately written to logs. The Testnet host is a constant in code; there is no configurable production endpoint.

Admin API:

- `GET /api/v1/admin/binance-spot-testnet` — reports whether the two credentials are configured.
- `POST /api/v1/admin/binance-spot-testnet/orders` — JSON: `{"symbol":"BTCUSDT","side":"BUY","type":"LIMIT","quantity":"0.001","price":"30000","timeInForce":"GTC","clientOrderId":"argent-demo-1"}`. `type` is `LIMIT` or `MARKET`; `timeInForce` for LIMIT is `GTC`, `IOC`, or `FOK`.
- `GET /api/v1/admin/binance-spot-testnet/orders/{orderId}?symbol=BTCUSDT` — query order status.
- `DELETE /api/v1/admin/binance-spot-testnet/orders/{orderId}?symbol=BTCUSDT` — cancel an order.

All endpoints require the ARGENT admin Bearer token. An exchange rejection (for example, invalid symbol filters or insufficient Testnet balance) is reported; the adapter does not silently route the order into internal matching.

## BIST history replay

The simulator remains the default BIST stream. The replay API accepts a CSV supplied by the local admin; ARGENT does not download, redistribute, or grant rights to BIST data. Check the data provider's terms before importing or sharing files. Do not include licensed datasets or private data in GitHub.

Accepted CSV columns, in this order:

```csv
timestamp,symbol,price,volume24h
2026-01-02T10:00:00Z,THYAO,300.25,1000
2026-01-02T10:01:00Z,THYAO,300.50,1250
```

`volume24h` is optional. Timestamps may be Unix milliseconds or ISO-8601 and rows must be time ordered. Symbols must be configured equity instruments (`THYAO`, `GARAN`, `ASELS`, `EREGL`, `SISE`). Limits: 50,000 rows and 2 MiB. The `speed` value divides timestamp gaps; the default `1000` compresses one minute to about 60 ms. Long gaps are capped at five seconds. During replay, the equity simulator pauses; crypto simulation/public data continues.

After obtaining an ARGENT admin token, PowerShell can send a local CSV:

```powershell
$csv = Get-Content .\bist-history.csv -Raw
$payload = @{ csv = $csv; speed = 1000 } | ConvertTo-Json -Compress
Invoke-RestMethod http://127.0.0.1:8080/api/v1/admin/market-data/bist/replay `
  -Method Post -Headers @{ Authorization = "Bearer $token" } `
  -ContentType "application/json" -Body $payload

Invoke-RestMethod http://127.0.0.1:8080/api/v1/admin/market-data/bist/replay `
  -Headers @{ Authorization = "Bearer $token" }
```

The response includes a replay ID, state, row count, and processed count. Ticker updates are emitted to the normal ticker stream with source `bist-csv-replay`. This is historical playback, not a licensed live BIST feed and not a BIST execution venue.
Binance Spot Testnet signed requests synchronize their timestamp against the fixed Testnet server-time endpoint to tolerate local clock drift. Credentials remain gateway-local and are never sent to the desktop client.
