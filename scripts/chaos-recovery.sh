#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
command -v docker >/dev/null || { echo 'Docker is required' >&2; exit 1; }
[[ "${ARGENT_CHAOS_CONFIRM:-}" == "yes" ]] || { echo 'Set ARGENT_CHAOS_CONFIRM=yes to restart the local gateway during the recovery check.' >&2; exit 2; }
docker compose restart gateway
for attempt in $(seq 1 60); do
  if curl -fsS --max-time 2 http://127.0.0.1:8080/health >/dev/null; then echo 'Gateway recovered'; exit 0; fi
  sleep 2
done
echo 'Gateway did not recover within 120 seconds' >&2
exit 1
