#!/usr/bin/env bash
# The resulting SQL contains live data: encrypt it and copy it off the VM.
set -euo pipefail
umask 077
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT/.env.prod"
COMPOSE="$ROOT/compose.prod.yml"
BACKUP_DIR="$ROOT/backups"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing $ENV_FILE" >&2
  exit 1
fi
mkdir -p "$BACKUP_DIR"
OUTPUT="$BACKUP_DIR/factoryflow-$(date -u +%Y%m%dT%H%M%SZ).sql"
if [[ -e "$OUTPUT" ]]; then
  echo "Backup exists: refusing to overwrite" >&2
  exit 1
fi
trap 'rm -f -- "$OUTPUT"' ERR
docker compose --env-file "$ENV_FILE" -f "$COMPOSE" exec -T mysql \
  sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysqldump --single-transaction --routines --triggers --set-gtid-purged=OFF -u "$MYSQL_USER" "$MYSQL_DATABASE"' \
  > "$OUTPUT"
if [[ ! -s "$OUTPUT" ]]; then
  rm -f -- "$OUTPUT"
  echo "Empty backup rejected" >&2
  exit 1
fi
echo "Created: $OUTPUT"
echo "IMPORTANT: encrypt and transfer off-host; verify restore separately."
