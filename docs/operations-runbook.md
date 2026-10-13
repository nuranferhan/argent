# ARGENT local operations runbook

This runbook applies to the Windows 11 local internal-exchange deployment. Orders, balances and prices are internal sandbox data. Do not expose these services to the public internet or treat balances as customer funds.

## Start and verify

1. Start Docker Desktop and wait until its engine is ready.
2. Open PowerShell in `C:\Users\Lenovo\Projects\argent`.
3. Start the local stack with `.\scripts\start-stack.ps1` or start infrastructure with `docker compose up -d` and run `.\scripts\start.ps1` for the host gateway. Keep the foreground gateway window open.
4. To build the updated desktop client, run `.\scripts\package-terminal.ps1` in another PowerShell window, then install `argent-terminal\build\compose\binaries\main\msi\Argent-1.0.3.msi`. Keep the gateway window open while building/installing.
5. Run `.\scripts\verify-local-release.ps1`. A successful check reports API health, ledger invariant, PostgreSQL, Redis, RabbitMQ, the 1.0.3 installer and installed executable. The check is read-only.
6. Open ARGENT from the Windows Start menu. If login is rate-limited, wait for the configured window or clear only the login rate-limit keys in Redis; do not flush Redis or delete the database.

## Android sandbox client

- Install Android Studio with Android SDK Platform 36, Build Tools 36.0.0 and Platform Tools; JDK 17 or newer is needed for AGP.
- With the local gateway running, `scripts/package-android.ps1` builds a debug APK. `scripts/install-android.ps1` builds, connects to an authorized USB device/emulator, forwards port 8080 with `adb reverse`, and installs the APK.
- On an Android emulator, the gateway URL is `http://10.0.2.2:8080`; with USB reverse on a physical device, use `http://127.0.0.1:8080`.
- The debug variant allows local HTTP for this sandbox. The release variant disables cleartext traffic; configure HTTPS before using it with a remote gateway. The debug APK is not a signed distribution release.
- Android feature acceptance still needs a real emulator/device run: login and invitation, quote/book updates, order/rejection/cancel, portfolio, workspace membership/role changes, and market-maker kill/resume. Do not use real funds; the gateway has internal matching and simulated BIST prices.

## Stop

Stop the host gateway with Ctrl+C in the window running `start.ps1`. Stop Compose services with `docker compose --profile app down` when they were started with the app profile, or `docker compose down` for the base stack. Do not add `-v`; that deletes database volumes.

## Routine backup and restore

- Create a backup: `.\scripts\backup-db.ps1`.
- Store the resulting dump outside the machine for a real disaster-recovery plan. The local script does not provide off-host encryption, retention, or immutable storage by itself.
- Verify a dump without replacing production data: `.\scripts\verify-db-backup-restore.ps1`. This creates a disposable database, restores into it, checks representative table counts and ledger balance, then removes the disposable database.
- To restore a selected backup, stop the gateway first and run `.\scripts\restore-db.ps1 <dump-path>`. The script requires the explicit phrase `RESTORE ARGENT` because it replaces existing database contents. Confirm the dump path and retain a separate copy before proceeding.

The project owner reported a successful isolated restore on 2026-10-12: 18,274 journal entries, 4,732 orders, 4 trades, and `ledger invariant=balanced`. Repeat this verification after material schema or backup-script changes.

## Health and incident triage

| Signal | First action |
|---|---|
| `/health` unreachable | Check Docker Desktop and `docker compose --profile app ps`; if using the host gateway, confirm the `start.ps1` window is still running. |
| PostgreSQL or Redis false | Inspect `docker compose logs --tail 100 postgres redis`; do not reset volumes as a first response. |
| RabbitMQ false | Inspect `docker compose logs --tail 100 rabbitmq`; do not accept the stack as healthy until `/health` returns `UP`. |
| `invariantOk` false or engine unhealthy | Stop submitting new orders, preserve logs and database volume, and investigate before restarting. Do not hand-edit ledger rows. |
| Login rate limited | Wait for the configured rate window. If an operator clears a key, delete only `rl:auth:login:*` keys in Redis; never use `FLUSHALL`. |
| Suspected market-maker issue | Use the desktop admin kill switch first, verify it is disabled, then preserve logs and inspect bot orders/inventory before resuming. |
| Suspected secret exposure | Rotate `JWT_SECRET` and bootstrap/admin passwords using the deployment's secret source; invalidate sessions by restarting the gateway after rotation. Do not paste new values into chat or commit them. |

Record the timestamp, affected service, observed health JSON, recent gateway/container logs, operator actions and outcome. Keep a copy of the pre-incident database before attempting repair.

## Security and release boundaries

- Keep database, Redis, RabbitMQ, Prometheus and Grafana bound to localhost for this local profile. Never publish these ports to an untrusted network.
- Use unique secrets for any shared/staging deployment. `.env.example` credentials and the development JWT value are local-only.
- Current health checks and Grafana dashboards are local observability; paging, alert routing, on-call ownership, encrypted off-host backups, TLS termination, secret-manager integration, MFA/recovery, independent review and disaster-recovery objectives still require deployment-specific setup and acceptance.
- This build uses internal matching and simulated prices. No real exchange order, BIST licensed feed, deposit, withdrawal, custody or payment is provided.

