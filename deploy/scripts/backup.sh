#!/usr/bin/env bash
set -Eeuo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"
require_env
require_command tar
set -a
source "${ENV_FILE}"
set +a

mode="${1:-database}"
timestamp="$(date +%Y%m%d-%H%M%S)"
backup_dir="${DATA_ROOT}/backups/${timestamp}"
mkdir -p "${backup_dir}"
chmod 700 "${backup_dir}"

echo "导出 MySQL 数据库……"
compose exec -T mysql mysqldump -uroot -p"${MYSQL_ROOT_PASSWORD}" \
  --single-transaction --routines --events brain_health > "${backup_dir}/mysql.sql"
cp "${ENV_FILE}" "${backup_dir}/deployment.env"
chmod 600 "${backup_dir}/deployment.env"

if [[ "${mode}" == "full" ]]; then
  echo "执行完整冷备份，系统将短暂停止……"
  compose stop
  trap 'compose start >/dev/null 2>&1 || true' EXIT
  tar --exclude="${DATA_ROOT}/backups" -czf "${backup_dir}/data.tar.gz" -C "${DATA_ROOT}" .
  compose start
  trap - EXIT
elif [[ "${mode}" != "database" ]]; then
  die "用法：$0 [database|full]"
fi

(cd "${backup_dir}" && sha256sum * > SHA256SUMS)
echo "备份完成：${backup_dir}"

