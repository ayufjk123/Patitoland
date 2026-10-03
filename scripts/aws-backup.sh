#!/usr/bin/env bash
set -euo pipefail

readonly APP_ROOT="/opt/patitoland"
readonly BACKUP_ROOT="${APP_ROOT}/backups/daily"
readonly POSTGRES_CONTAINER="patitoland-postgres"
readonly INVENTORY_DB="${APP_ROOT}/Inventario/data/inventory.db"
readonly KEEP_DAYS=14

exec 9>"/run/lock/patitoland-backup.lock"
flock -n 9 || exit 0

stamp="$(date +%Y%m%d-%H%M%S)"
backup_dir="${BACKUP_ROOT}/${stamp}"
mkdir -p "${backup_dir}"
chmod 700 "${BACKUP_ROOT}" "${backup_dir}"

cleanup_failed_backup() {
  rm -rf "${backup_dir}"
}
trap cleanup_failed_backup ERR

docker exec "${POSTGRES_CONTAINER}" \
  pg_dump -U patitoland -d patitoland --format=custom --no-owner --no-privileges \
  > "${backup_dir}/patitoland.dump"

sqlite3 "${INVENTORY_DB}" ".backup '${backup_dir}/inventory.db'"

docker exec -i "${POSTGRES_CONTAINER}" pg_restore --list \
  < "${backup_dir}/patitoland.dump" >/dev/null
test "$(sqlite3 "${backup_dir}/inventory.db" 'PRAGMA integrity_check;')" = "ok"
test -s "${backup_dir}/patitoland.dump"
test -s "${backup_dir}/inventory.db"

chmod 600 "${backup_dir}"/*
ln -sfn "${backup_dir}" "${BACKUP_ROOT}/latest"
find "${BACKUP_ROOT}" -mindepth 1 -maxdepth 1 -type d -mtime +"${KEEP_DAYS}" -exec rm -rf {} +

trap - ERR
printf 'PatitoLand backup completed: %s\n' "${backup_dir}"
