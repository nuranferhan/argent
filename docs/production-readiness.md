# ARGENT production readiness

The intended product is a production-quality, multi-tenant internal exchange and settlement platform. It executes orders against ARGENT's own order books and synthetic sandbox liquidity; it has no external execution venue, licensed market data or custody provider. A green local health response confirms configured services are reachable; synthetic prices and internal balances are not real market data or customer funds.

## What is implemented in this repository

- Kotlin multi-module gateway, matching engine, ledger, risk/portfolio logic, market data feed, Compose Desktop client and a Jetpack Compose Android client using the same gateway API.
- Internal limit/market/IOC/FOK matching, event replay, idempotent order submission, durable order/trade storage and double-entry ledger settlement.
- Production matching does not keep a second unbounded in-memory copy of domain events; PostgreSQL remains the durable event source. Recovery captures replay-generated fills only for settlement recovery.
- Local Docker Compose services, health/metrics endpoints, Prometheus/Grafana configuration and Windows 11 start/package scripts.
- PostgreSQL backup/restore scripts plus isolated restore verification (`scripts/backup-db.ps1`, `scripts/restore-db.ps1`, `scripts/verify-db-backup-restore.ps1`).
- Configurable simulated market-data adapter, internal market-making strategy and fail-closed behavior for unconfigured external deposits/withdrawals.
- Tenant workspaces, OWNER/ADMIN/TRADER/VIEWER roles, email-bound invitations, invited-user onboarding, tenant-bound sessions, per-membership trading accounts and server-side read-only enforcement for VIEWER.

## Remaining work for the selected internal-exchange product

The owner selected a no-fee, externally disconnected platform. The following are core product work, independent of paid providers:

1. **Tenant administration**: member listing, role changes, serialized membership revocation with active-order cancellation, and owner workspace closure are implemented. Desktop management UI is implemented; final multi-user end-to-end acceptance and concurrent revocation/order submissions still need release-environment evidence.
2. **Matching and concurrency evidence**: deterministic/property tests, parallel same-symbol submit/cancel coverage, and a 100-iteration cancel-versus-crossing-fill race test are implemented. Longer soak tests, realistic high-volume workloads, and measured latency/throughput on the release hardware remain.
3. **Ledger and settlement evidence**: H2 tests cover post-commit failure injection, idempotent replay, concurrent holds, and balance-to-journal reconciliation. `GET /api/v1/admin/ledger/reconciliation` exposes an ADMIN-only report, and the local release verifier checks it. On 2026-10-13, the owner successfully ran `scripts/test-postgres-integration.ps1` against local Docker PostgreSQL: 40 concurrent holds, post-commit event-delivery failure/recovery, settlement replay after reconnect, legacy journal ownership migration, and reconciliation passed in an isolated schema. The owner then rebuilt the gateway and passed local reconciliation for all 38 account/asset pairs with no mismatches. The prior isolated PostgreSQL backup restore reported `journal_entries,orders,trades=18274,4732,4` and a balanced ledger; that is a separate backup/restore proof, not failover evidence.
4. **Market-maker controls**: admin kill switch, database-persisted enable state, quote freshness, per-quote notional cap, per-symbol inventory notional cap and available-inventory sell limits are implemented. Automated coverage now races kill/resume requests and verifies the final kill cancels resting maker orders and releases their held funds. Deployment-level bot/order load validation remains; the kill/resume control is exposed in the desktop workspace dialog for global admins.
5. **Security and production operations**: independent threat/security review, MFA/account recovery, TLS and secret management for deployment, network/least-privilege policies, off-host backups, actionable alerting and incident procedures.
6. **Release acceptance**: define supported users/workloads, SLOs, capacity limits, fault-injection scenarios, migration/rollback procedure and UI accessibility/usability acceptance. The owner reports that the Windows 1.0.3 MSI is installed and `scripts/verify-local-release.ps1` passed on 2026-10-13, including UP health, required dependencies, ledger reconciliation, installer presence and installed version. Longer load/soak and UI accessibility/usability acceptance remain. Android APK assembly and install were reported successful, but emulator/device workflow acceptance remains incomplete.

These requirements describe an internal simulated exchange. If the product later handles real external orders or funds, the separate provider/legal work below also becomes mandatory.

## Additional work only if real external trading or real funds are added

These items require product, provider or jurisdiction decisions. They are outside the currently selected no-fee internal-exchange scope. Do not enable real customer funds until each item has an owner, implementation, integration evidence and operational runbook.

1. **Execution venues**: choose each crypto venue and BIST broker, obtain sandbox and production access, agree symbol mapping/order semantics, and implement venue adapters with reconciliation for acknowledgements, partial fills, rejects, cancel races, outages and duplicate delivery. Binance public market data is not an execution adapter.
2. **Market data rights**: choose a licensed BIST feed, confirm display/redistribution rights, implement its adapter and stale/halts/corporate-action handling. The current BIST simulated feed is dev-only.
3. **Custody and cash movement**: select licensed custody/banking/payment providers; implement deposit confirmation, withdrawal approval, signing/key custody, limits, idempotency, reconciliation, reversals and audit trails. The current deposit/withdrawal routes intentionally return 503.
4. **Customer lifecycle and compliance**: decide supported countries/customer types and obtain qualified legal/compliance review. Implement the required identity verification, sanctions/AML checks, consent, privacy retention, account recovery, support and regulatory reporting flows for those decisions.
5. **Production security**: threat-model and independently review the application; add MFA and account/session controls appropriate to the product; protect administrative endpoints; configure TLS, secret manager/KMS, key rotation, dependency/image scanning, patch ownership and security incident response.
6. **Production operations**: provision managed, authenticated services; define network policies and least-privilege identities; configure encrypted off-host backups and retention; perform restore and disaster-recovery exercises; add actionable alerts, dashboards, on-call ownership, capacity targets and incident procedures.
7. **Release evidence**: select availability/latency/data freshness objectives, run load and fault-injection exercises, review migration/rollback procedures, complete venue/custody sandbox certification, accessibility/usability review and independent accounting/security review.

## Inputs ARGENT's owner must choose or obtain

No external credential is needed to run the current local stack. Before implementing real provider adapters, provide decisions and sandbox access for:

- BIST broker and licensed market-data vendor, with their sandbox documentation and test account.
- Crypto execution venue(s) and sandbox documentation. Start with trade-only sandbox credentials; do not provide withdrawal-enabled keys.
- Custody/bank/payment provider and sandbox account, including the provider's signing model. Private signing keys must stay in a KMS/HSM or equivalent secret system and must never be pasted into chat or committed to this repository.
- Customer jurisdictions and account types, plus the compliance/legal requirements confirmed by qualified counsel.
- Production hosting, domain/TLS and secret-management choices; email/SMS and identity-verification vendors if required by the chosen customer flows.

When these choices are made, store credentials in the selected secret manager and expose only the required environment variables at deploy time. `.env.example` is a local development template and its sample passwords are not production secrets.

## Verification record

The owner reports that `scripts/verify-db-backup-restore.ps1` completed successfully on Windows 11. The isolated restore verified ledger balance. The owner generated and installed the Windows 1.0.3 MSI; after rebuilding the gateway with the ledger migration fix, the local release check passed health, ledger invariant, matching engine, PostgreSQL, Redis, RabbitMQ, 38 account/asset reconciliation pairs, installer presence and installed version. Desktop workflows have also been exercised for tenant invitation acceptance, order fill/cancellation, and market-maker kill/resume. These are local engineering checks, not external production certification.

On 2026-10-13, `:argent-engine:test`, `:argent-ledger:test`, `:argent-gateway:test`, `:argent-market:test`, `:argent-risk:test`, and `:argent-terminal:compileKotlin` passed on JDK 21. The gateway emitted two Ktor `Principal` deprecation warnings. The owner passed the opt-in PostgreSQL test on Windows/Docker, including legacy journal ownership migration, and passed the 1.0.3 local release acceptance after rebuilding the gateway. The JMH 1.37 result is a microbenchmark, not a production capacity claim. Android emulator workflow acceptance remains open.

The immediate remaining security work for the internal/sandbox product is a threat-model and independent review, MFA/account recovery, and release/deployment hardening. TLS, secret-manager integration, network policies, alert routing, backup retention/off-host storage, incident ownership and production workload limits require a target deployment and named operator. Do not enable real customer funds or external execution based on the local verification record.

The follow-up matching-engine change disables its redundant in-memory event history in the live gateway and replay/benchmark instances. Engine/gateway regression tests and desktop Kotlin compilation passed on 2026-10-13, including replayed-fill recovery with event retention disabled and concurrent market-maker kill/resume with order cancellation and hold release. A local Docker health/release check was not rerun because Docker Desktop's engine pipe was inaccessible in the current execution session; the previously reported 1.0.3 acceptance predates this source change.
