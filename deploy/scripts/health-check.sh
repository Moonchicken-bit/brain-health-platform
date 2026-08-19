#!/usr/bin/env bash
set -Eeuo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"
require_env
set -a
source "${ENV_FILE}"
set +a

WAIT=false
[[ "${1:-}" == "--wait" ]] && WAIT=true
attempts=1
$WAIT && attempts=60

for ((i=1; i<=attempts; i++)); do
  unhealthy="$(compose ps --format json 2>/dev/null | grep -E '"State":"(exited|dead|restarting)"' || true)"
  if [[ -z "${unhealthy}" ]] && curl -fsS "http://127.0.0.1:${APP_PORT}/" >/dev/null 2>&1; then
    echo "健康检查通过：http://127.0.0.1:${APP_PORT}"
    exit 0
  fi
  $WAIT || break
  echo "等待服务就绪（${i}/${attempts}）……"
  sleep 5
done

echo "健康检查未通过，当前容器状态："
compose ps
echo "请运行 ./scripts/logs.sh 查看日志。"
exit 1

