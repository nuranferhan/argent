#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
backup=${1:?Usage: scripts/restore-db.sh path/to/backup.dump}
[[ -f "$backup" && -s "$backup" ]] || { echo 'Backup file does not exist or is empty.' >&2; exit 2; }
echo 'This replaces the ARGENT database contents. Stop the gateway before continuing.' >&2
read -r -p 'Type RESTORE ARGENT to continue: ' confirmation
[[ "$confirmation" == 'RESTORE ARGENT' ]] || { echo 'Restore cancelled.'; exit 2; }
docker compose cp "$backup" postgres:/tmp/argent-restore.dump
docker compose exec -T postgres pg_restore -U argent -d argent --clean --if-exists --no-owner --no-privileges --exit-on-error --single-transaction /tmp/argent-restore.dump
docker compose exec -T postgres rm -f /tmp/argent-restore.dump
echo 'Restore completed. Check /health before accepting orders.'
