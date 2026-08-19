#!/usr/bin/env bash
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"
set -a; source .env; set +a
stamp="$(date +%Y%m%d-%H%M%S)"
target="${DATA_ROOT}/backups/${stamp}"
mkdir -p "$target"
compose exec -T mysql mysqldump --single-transaction --routines --triggers --events --hex-blob -uroot -p"$MYSQL_ROOT_PASSWORD" brain_health > "$target/brain_health.sql"
compose exec -T mongodb mongodump --uri="mongodb://${MONGO_USER}:${MONGO_PASSWORD}@localhost:27017/brain_health?authSource=admin" --archive --gzip > "$target/brain_health.archive.gz"
cp .env "$target/service.env"
chmod 600 "$target/service.env"
sha256sum "$target"/* > "$target/SHA256SUMS"
echo "备份完成：$target"
