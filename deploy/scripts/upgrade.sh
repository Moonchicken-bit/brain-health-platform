#!/usr/bin/env bash
set -Eeuo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"
require_env
[[ -f "${PACKAGE_DIR}/images/brain-health-images.tar" ]] || die "缺少离线镜像"
echo "升级前自动执行数据库备份……"
"${SCRIPT_DIR}/backup.sh" database
docker load -i "${PACKAGE_DIR}/images/brain-health-images.tar"
compose config --quiet
compose up -d
"${SCRIPT_DIR}/health-check.sh" --wait

