#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PACKAGE_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_FILE="${PACKAGE_DIR}/docker-compose.prod.yml"
ENV_FILE="${PACKAGE_DIR}/.env"

die() {
  echo "错误：$*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || die "未找到命令：$1"
}

require_env() {
  [[ -f "${ENV_FILE}" ]] || die "缺少 ${ENV_FILE}，请先运行 sudo ./scripts/install.sh"
}

compose() {
  docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" "$@"
}

