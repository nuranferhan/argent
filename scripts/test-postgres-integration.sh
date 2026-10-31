#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

command -v docker >/dev/null || { echo 'Docker is required.' >&2; exit 2; }
docker compose exec -T postgres pg_isready -U argent -d argent >/dev/null || {
  echo "PostgreSQL is not ready. Start it with 'docker compose up -d postgres'." >&2
  exit 2
}
command -v java >/dev/null || { echo 'Java 21 is required.' >&2; exit 2; }
java_version=$(java -version 2>&1 | head -n 1)
[[ "$java_version" == *'21.'* ]] || { echo "Java 21 is required; found: $java_version" >&2; exit 2; }

# Read credentials from the running container into this process only.
postgres_password=$(docker compose exec -T postgres sh -c 'printf %s "$POSTGRES_PASSWORD"' | tr -d '\r\n')
[[ -n "$postgres_password" ]] || { echo 'Could not read PostgreSQL credentials from Compose.' >&2; exit 2; }
export ARGENT_TEST_POSTGRES_URL=${ARGENT_TEST_POSTGRES_URL:-jdbc:postgresql://127.0.0.1:5432/argent}
export ARGENT_TEST_POSTGRES_USER=${ARGENT_TEST_POSTGRES_USER:-argent}
export ARGENT_TEST_POSTGRES_PASSWORD=$postgres_password
unset postgres_password

./gradlew --no-daemon --rerun-tasks :argent-ledger:test --tests com.argent.ledger.PostgresLedgerIntegrationTest
echo 'PASS PostgreSQL integration test (uses an isolated temporary schema).'
