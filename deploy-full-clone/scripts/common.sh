#!/usr/bin/env bash
set -Eeuo pipefail
PACKAGE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PACKAGE_DIR"
[[ -f .env ]] || { echo "缺少 .env，请先运行 install.sh" >&2; exit 1; }
compose() { docker compose --env-file .env -f docker-compose.yml "$@"; }
