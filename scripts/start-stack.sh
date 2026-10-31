#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
docker compose --profile app up -d --build
docker compose ps
