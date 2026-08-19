#!/usr/bin/env bash
set -Eeuo pipefail
PACKAGE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PACKAGE_DIR"
compose() { docker compose --env-file .env -f docker-compose.yml "$@"; }
wait_mode="${1:-}"
attempts=1
[[ "$wait_mode" == --wait ]] && attempts=80
for ((i=1; i<=attempts; i++)); do
  running="$(compose ps --status running --services | wc -l)"
  http_code="$(curl -sS -o /dev/null -w '%{http_code}' "http://127.0.0.1:${APP_PORT:-80}/" || true)"
  if [[ "$running" -eq 20 && "$http_code" == 200 ]]; then
    echo "健康检查通过：20 个服务运行，Web 返回 200"
    exit 0
  fi
  sleep 3
done
compose ps
echo "健康检查失败：running=$running, http=$http_code" >&2
exit 1
