#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
command -v docker >/dev/null || { echo 'Docker is required.' >&2; exit 2; }
docker compose exec -T postgres pg_isready -U argent -d argent >/dev/null || {
  echo "PostgreSQL is not ready. Start it with 'docker compose up -d postgres'." >&2
  exit 2
}
out=${1:-"${TMPDIR:-/tmp}/argent-backup-verification"}
mkdir -p "$out"
./scripts/backup-db.sh "$out"
backup=$(ls -t "$out"/argent-postgres-*.dump 2>/dev/null | head -n 1 || true)
[[ -n "$backup" && -s "$backup" ]] || { echo 'Backup creation failed.' >&2; exit 1; }
restore_db="argent_restore_verify_$(od -An -N6 -tx1 /dev/urandom | tr -d ' \n')"
container_dump="/tmp/${restore_db}.dump"
created=0
cleanup() {
  docker compose exec -T postgres rm -f "$container_dump" >/dev/null 2>&1 || true
  if (( created )); then docker compose exec -T postgres dropdb -U argent --if-exists "$restore_db" >/dev/null; fi
}
trap cleanup EXIT
docker compose exec -T postgres createdb -U argent -T template0 "$restore_db"
created=1
docker compose cp "$backup" "postgres:$container_dump" >/dev/null
docker compose exec -T postgres pg_restore -U argent -d "$restore_db" --no-owner --no-privileges --exit-on-error --single-transaction "$container_dump"
tables=$(docker compose exec -T postgres psql -U argent -d "$restore_db" -Atc "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name IN ('journal_entries','balances','orders','trades')" | tr -d '\r\n')
[[ "$tables" == 4 ]] || { echo "Restore is missing required tables (found $tables/4)." >&2; exit 1; }
unbalanced=$(docker compose exec -T postgres psql -U argent -d "$restore_db" -Atc 'SELECT count(*) FROM (SELECT asset FROM journal_entries GROUP BY asset HAVING sum(debit) <> sum(credit)) AS unbalanced' | tr -d '\r\n')
[[ "$unbalanced" == 0 ]] || { echo "Restored ledger has $unbalanced unbalanced asset(s)." >&2; exit 1; }
echo "PASS isolated backup/restore; ledger balanced. Backup retained at $backup"
