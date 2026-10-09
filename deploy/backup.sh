#!/usr/bin/env bash
#
# Medic-stock (MSS) — daily backup.
#
#   sudo cp deploy/backup.sh /usr/local/bin/mss-backup
#   sudo chmod +x /usr/local/bin/mss-backup
#   sudo crontab -e
#   # every day at 02:30
#   30 2 * * * /usr/local/bin/mss-backup >> /var/log/mss-backup.log 2>&1
#
# Saves the two databases and the surgery consumption sheets. Keeps the last
# RETENTION_DAYS days.
#
# A backup nobody has ever restored is a hope, not a backup. The README has the
# restore commands — run them once, on a copy, before you need them for real.

set -euo pipefail

PROJECT_DIR="${PROJECT_DIR:-/opt/medic-stock}"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/mss}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"
COMPOSE="docker compose -f ${PROJECT_DIR}/deploy/docker-compose.yaml --env-file ${PROJECT_DIR}/deploy/.env"

STAMP="$(date +%Y-%m-%d_%H%M)"
mkdir -p "${BACKUP_DIR}"

echo "[$(date '+%F %T')] backup ${STAMP} — início"

# --- databases -------------------------------------------------------------
# pg_dump runs inside the container, so no PostgreSQL client is needed on the host.
${COMPOSE} exec -T postgres-mss \
    sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists' \
    | gzip > "${BACKUP_DIR}/mssdb_${STAMP}.sql.gz"

${COMPOSE} exec -T postgres-mail \
    sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists' \
    | gzip > "${BACKUP_DIR}/mssemaildb_${STAMP}.sql.gz"

# --- consumption sheets ----------------------------------------------------
${COMPOSE} exec -T mss tar -cf - -C /app/storage . \
    | gzip > "${BACKUP_DIR}/storage_${STAMP}.tar.gz"

# --- configuration ---------------------------------------------------------
# .env carries every password: the file is copied with no read permission for others.
install -m 600 "${PROJECT_DIR}/deploy/.env" "${BACKUP_DIR}/env_${STAMP}.bak"

# --- retention -------------------------------------------------------------
find "${BACKUP_DIR}" -maxdepth 1 -type f \
    \( -name '*.sql.gz' -o -name '*.tar.gz' -o -name 'env_*.bak' \) \
    -mtime "+${RETENTION_DAYS}" -delete

echo "[$(date '+%F %T')] backup ${STAMP} — fim"
du -sh "${BACKUP_DIR}"

# An empty dump means the backup failed without saying so. 1KB is far below any
# real dump of this schema and well above an empty gzip.
for f in "${BACKUP_DIR}/mssdb_${STAMP}.sql.gz" "${BACKUP_DIR}/storage_${STAMP}.tar.gz"; do
    size=$(stat -c%s "$f")
    if [ "$size" -lt 1024 ]; then
        echo "AVISO: ${f} tem apenas ${size} bytes — conferir." >&2
        exit 1
    fi
done
