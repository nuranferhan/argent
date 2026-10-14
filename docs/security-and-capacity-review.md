# Security, dependency and capacity review

Review date: 2026-10-14

## Dependency security scan

The resolved Windows build classpaths captured by Gradle's Kotlin compiler options contained 142 Maven coordinates across the project modules. OSV's public query API reported known advisories for the existing versions of Logback 1.5.12, RabbitMQ Java client 5.22.0, Micrometer 1.13.6, Netty 4.1.114.Final and PostgreSQL JDBC 42.7.4. The Kotlin Gradle plugin 2.0.21 was also flagged for unsafe deserialization in build-cache metadata.

The dependency declarations were updated to these patched versions:

| Component | Previous | Updated |
|---|---:|---:|
| Logback | 1.5.12 | 1.5.38 |
| Netty | 4.1.114.Final | 4.1.137.Final via the Netty BOM |
| RabbitMQ Java client | 5.22.0 | 5.37.0 |
| Micrometer core and Prometheus registry | 1.13.6 / 1.12.13 | 1.16.6 |
| PostgreSQL JDBC | 42.7.4 | 42.7.14 |
| Kotlin Gradle/serialization/Compose compiler plugins | 2.0.21 | 2.4.21 |
| Compose Multiplatform Gradle plugin | 1.7.0 | 1.12.1 |

OSV exact-version queries for the updated artifacts, including Kotlin Gradle plugin 2.4.21, returned no advisories on the review date. References: [OSV database](https://osv.dev/), [Logback advisory](https://osv.dev/vulnerability/GHSA-25qh-j22f-pwp8), [Netty advisory](https://osv.dev/vulnerability/GHSA-38f8-5428-x5cv), [RabbitMQ Java client advisory](https://osv.dev/vulnerability/GHSA-5m9f-rphj-c435), [Micrometer advisory](https://osv.dev/vulnerability/GHSA-g3pr-3p32-fp23), [PostgreSQL JDBC advisory](https://osv.dev/vulnerability/GHSA-98qh-xjc8-98pq), [Kotlin Gradle plugin advisory](https://osv.dev/vulnerability/GHSA-r937-wjx7-w2jp).

The scan covers resolved application/compiler classpaths and queried the Gradle plugin versions directly; it does not cover Docker base images, operating-system packages, or future dependency updates. Kotlin 2.4.21 is a stable patch released October 8, 2026; Compose Multiplatform 1.12.1 is the selected plugin version. The Kotlin and Compose toolchain versions were upgraded together. On October 14, 2026, the user ran `:argent-gateway:compileKotlin`, `:argent-ledger:compileKotlin`, `:argent-market:compileKotlin` and `:argent-terminal:compileKotlin` on Windows with JDK 21; the build completed successfully. The run still reports non-blocking deprecations, including Kotlin Android `sourceSets`, Kotlin `Principal`, and Gradle 8.11.1 (the plugin warns a future Kotlin 2.5.0 will require Gradle 8.14.4 or newer). Android's Kotlin compile task was not included in that run. Docker image scanning was not run because Docker Engine access was denied from the review shell.

## License and source-distribution review

The project is MIT-licensed. Maven POM license metadata was available for 117 of the 142 scanned coordinates; 21 POMs had no license declaration, and four Android/JetBrains artifacts were hosted outside Maven Central and were not resolved by this metadata check. Declarations observed include Apache-2.0, MIT, BSD-2-Clause, EPL-2.0, ISC, Public Domain and multiple licenses for the RabbitMQ client (Apache-2.0, GPL-2.0 and MPL-2.0). The POM metadata does not establish which alternative license applies to every redistributed artifact.

No generated third-party attribution bundle was found in the repository. Before distributing the MSI/APK broadly, verify the 25 metadata exceptions against their upstream license files and include required third-party notices in the packaged distribution. This is a technical inventory, not a legal compatibility opinion.

## Repository and application security checks

- Tracked-file names were checked for `.env`, private-key, keystore, certificate, database-dump and heap-dump files; none were tracked.
- Targeted secret-pattern search found only documented placeholders and development defaults in `.env.example`, README and local scripts; no populated Binance key or private key was found in tracked source.
- `.gitignore` excludes `.env`, `.env.*` (except `.env.example`), build outputs, installers, APK/AAB files, backups, dumps, heap dumps and signing keys.
- Docker Compose publishes service ports on `127.0.0.1`. Local development uses intentionally weak sample passwords and a development JWT secret; production configuration rejects those defaults and requires unique secrets. Do not expose the development stack to an untrusted network.
- Account passwords are BCrypt-hashed; new passwords require at least 12 characters. Production readiness documentation still lists MFA, account recovery, independent threat review, TLS/secret-manager deployment, network least privilege, off-host backup, alerting and incident ownership as unfinished.
- Docker image scanning was not run in this environment because Docker Engine access was denied.

## Read-only HTTP soak measurement

`scripts/load-readonly.js` performs a 10-minute constant-arrival-rate soak against only `GET /api/v1/ticker/BTCUSDT` at 3 requests/second (1,800 scheduled requests). It checks `GET /health` once before the load and refuses to run unless health is `UP`; the preflight timeout is 30 seconds because the health route probes backing services and was observed to take 7.5 seconds. It does not submit or cancel orders and does not mutate balances. The rate stays below the ticker route's 300-requests/minute application limit.

Run from the repository root with the local services up:

```powershell
k6 run --summary-export reports\ticker-soak-summary.json scripts\load-readonly.js
```

Acceptance thresholds: failed HTTP requests below 1%, checks above 99%, p95 below 750 ms and p99 below 1,500 ms. This is a local read-path measurement only; it does not establish order-submit capacity, multi-user scaling, external venue behavior or production SLOs.

### Measurement result

The first 10-minute run on the local Windows 11 amd64/JDK 21 host completed 1,800 ticker requests at a measured 2.97 requests/second. Results: median 10.24 ms, p95 133.08 ms, p99 359.53 ms, maximum 6.85 s, 275 failed requests (15.27%), and two dropped iterations. It failed the error-rate and check thresholds. The HTTP status distribution was not captured in that run, so the failures are not attributed to a specific status code.

A 30-second diagnostic run at the same target rate passed with 90 requests, zero failures, p95 171.86 ms, p99 1.46 s and maximum 8.02 s. This short run does not override the first failed 10-minute result. One repeat failed before starting the load because its 10-second health preflight timed out; the preflight timeout was raised to 30 seconds.

A second 10-minute run completed on 2026-10-14 against the local Windows 11 amd64 host after health returned `UP`. It scheduled 1,800 reads and completed 1,797 iterations (2.978 iterations/s); 5 arrivals were dropped. All 1,797 HTTP requests returned successfully and all 3,592 checks passed. Latency: median 8.09 ms, average 49.14 ms, p95 215.98 ms, p99 786.96 ms, maximum 3.00 s. The run met the configured HTTP error and latency thresholds. It is evidence for the read-only ticker route at approximately 3 requests/second, not order-submit capacity or production scale. The earlier failed soak remains a reliability signal and should be reproduced/diagnosed before making stronger availability claims.

The supplied gateway log excerpt showed recent ticker responses as HTTP 200 in 1–43 ms and no 4xx/5xx in that excerpt; it also showed a `/health` request taking 7.5 seconds and frequent `/metrics` scrapes. This excerpt does not identify the earlier soak failures. CPU and RAM model could not be collected because Windows CIM access is denied in this execution environment. Do not use the historical JMH engine microbenchmark as a substitute for this gateway measurement.
