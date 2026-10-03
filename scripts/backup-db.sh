#!/bin/bash
# PatitoLand Postgres backup
# Dumps the local postgres container to a timestamped gzip, keeps 14 days.
# Scheduled via launchd (see com.patitoland.backup.plist) to run daily at 04:00.
set -euo pipefail

DOCKER="$HOME/.orbstack/bin/docker"
BACKUP_DIR="$HOME/patitoland-backups"
KEEP_DAYS=14
CONTAINER="patitoland-postgres"
DB_NAME="patitoland"
DB_USER="patitoland"

mkdir -p "$BACKUP_DIR"
STAMP="$(date +%Y%m%d-%H%M%S)"
OUT="$BACKUP_DIR/patitoland-${STAMP}.sql.gz"

echo "[$(date)] Starting backup -> $OUT"

# pg_dump inside the container; gzip on the host.
"$DOCKER" exec -t "$CONTAINER" pg_dump -U "$DB_USER" -d "$DB_NAME" --no-owner --no-privileges \
  | gzip > "$OUT"

# Verify the dump is non-trivial (gzip header + some content).
SIZE=$(stat -f%z "$OUT" 2>/dev/null || echo 0)
if [ "$SIZE" -lt 100 ]; then
  echo "[$(date)] ERROR: backup file is suspiciously small (${SIZE} bytes). Keeping for inspection." >&2
  exit 1
fi

# Prune old backups.
find "$BACKUP_DIR" -name 'patitoland-*.sql.gz' -type f -mtime +${KEEP_DAYS} -delete

echo "[$(date)] Backup OK (${SIZE} bytes). Retention: ${KEEP_DAYS} days."
echo "[$(date)] Current backups:"
ls -lh "$BACKUP_DIR" | tail -n +2
