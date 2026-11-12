#!/usr/bin/env bash
set -euo pipefail
mkdir -p /backups
host="${MYSQL_HOST:-host.docker.internal}"
port="${MYSQL_PORT:-3306}"
while true; do
  stamp="$(date +%Y%m%d-%H%M%S)"
  mysqldump -h "$host" -P "$port" -ugolyt --single-transaction --no-tablespaces golyt > "/backups/golyt-$stamp.sql"
  find /backups -name 'golyt-*.sql' -mtime +14 -delete || true
  sleep 86400
done
