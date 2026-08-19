#!/usr/bin/env bash
set -Eeuo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"
require_env
[[ "${1:-}" == "--confirm" && -n "${2:-}" ]] || die "恢复会覆盖现有数据。用法：$0 --confirm /备份目录"
backup_dir="$(readlink -f "$2")"
[[ -d "${backup_dir}" && -f "${backup_dir}/mysql.sql" ]] || die "备份目录无效"
set -a
source "${ENV_FILE}"
set +a

if [[ -f "${backup_dir}/data.tar.gz" ]]; then
  tar -tzf "${backup_dir}/data.tar.gz" | grep -Eq '(^/|(^|/)\.\.(/|$))' && die "备份压缩包包含不安全路径"
  compose down
  tar -xzf "${backup_dir}/data.tar.gz" -C "${DATA_ROOT}"
  compose up -d mysql
else
  compose up -d mysql
fi

echo "等待 MySQL……"
for _ in {1..60}; do
  compose exec -T mysql mysqladmin ping -uroot -p"${MYSQL_ROOT_PASSWORD}" --silent && break
  sleep 2
done
compose exec -T mysql mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" brain_health < "${backup_dir}/mysql.sql"
compose up -d
echo "恢复完成。"

