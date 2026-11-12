#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
mkdir -p "$root/backups"
stamp="$(date +%Y%m%d-%H%M%S)"
docker exec mysql mysqldump -h 127.0.0.1 -ugolyt -pgolyt --single-transaction --no-tablespaces golyt > "$root/backups/golyt-$stamp.sql"
echo "Wrote backups/golyt-$stamp.sql"
