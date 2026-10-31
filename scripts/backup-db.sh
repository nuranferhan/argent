#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
out=${1:-backups}
mkdir -p "$out"
stamp=$(date -u +%Y%m%d-%H%M%S)
file="$out/argent-postgres-$stamp.dump"
docker compose exec -T postgres pg_dump -U argent -d argent -Fc > "$file"
[[ -s "$file" ]] || { rm -f -- "$file"; echo 'Backup is empty.' >&2; exit 1; }
chmod 600 "$file"
echo "Database backup created: $file"
