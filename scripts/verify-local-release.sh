#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
base=${ARGENT_GATEWAY_URL:-http://127.0.0.1:8080}
health=$(curl --fail --silent --show-error --max-time 30 "$base/health") || {
  echo 'Gateway health check failed.' >&2; exit 1;
}
printf '%s\n' "$health" | grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' || {
  echo "Gateway is not healthy: $health" >&2; exit 1;
}
printf '%s\n' "$health" | grep -Eq '"invariantOk"[[:space:]]*:[[:space:]]*true' || {
  echo "Ledger invariant failed: $health" >&2; exit 1;
}
printf '%s\n' "$health" | grep -Eq '"matchingEngine"[[:space:]]*:[[:space:]]*true' || {
  echo "Matching engine is unhealthy: $health" >&2; exit 1;
}
for service in postgres redis rabbit; do
  printf '%s\n' "$health" | grep -Eq "\"$service\"[[:space:]]*:[[:space:]]*true" || {
    echo "Required dependency $service is unhealthy: $health" >&2; exit 1;
  }
done
echo 'PASS gateway, ledger invariant, matching engine, PostgreSQL, Redis, RabbitMQ.'
echo "$health"
echo 'Platform installers and admin reconciliation remain additional release checks.'
