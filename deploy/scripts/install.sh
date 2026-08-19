#!/usr/bin/env bash
set -Eeuo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

[[ "$(uname -s)" == "Linux" ]] || die "安装脚本仅支持 Linux"
[[ "$(uname -m)" == "x86_64" ]] || die "当前离线镜像仅支持 x86_64/amd64"
require_command docker
require_command openssl
require_command curl
docker compose version >/dev/null 2>&1 || die "未安装 Docker Compose 插件"
docker info >/dev/null 2>&1 || die "Docker 服务不可用，请确认已启动且当前用户有权限"

if [[ ! -f "${ENV_FILE}" ]]; then
  cp "${PACKAGE_DIR}/.env.example" "${ENV_FILE}"
  replace_env() {
    local key="$1" value="$2"
    sed -i "s|^${key}=.*|${key}=${value}|" "${ENV_FILE}"
  }
  replace_env MYSQL_ROOT_PASSWORD "$(openssl rand -hex 24)"
  replace_env MYSQL_PASSWORD "$(openssl rand -hex 24)"
  replace_env MONGO_PASSWORD "$(openssl rand -hex 24)"
  replace_env REDIS_PASSWORD "$(openssl rand -hex 24)"
  replace_env MINIO_PASSWORD "$(openssl rand -hex 24)"
  replace_env NACOS_AUTH_TOKEN "$(openssl rand -base64 64 | tr -d '\n')"
  replace_env NACOS_IDENTITY_KEY "$(openssl rand -hex 16)"
  replace_env NACOS_IDENTITY_VALUE "$(openssl rand -hex 24)"
  replace_env JWT_SECRET "$(openssl rand -hex 64)"
  replace_env OTP_ENCRYPTION_KEY "$(openssl rand -hex 32)"
  chmod 600 "${ENV_FILE}"
  echo "已生成随机服务密码：${ENV_FILE}"
fi

require_env
set -a
source "${ENV_FILE}"
set +a

[[ "${DATA_ROOT}" == /* ]] || die "DATA_ROOT 必须是绝对路径"
case "${DATA_ROOT}" in
  /|/home|/root|/usr|/var|/etc) die "DATA_ROOT 范围过大，请使用专用目录，例如 /data/brain-health" ;;
esac

mkdir -p \
  "${DATA_ROOT}/mysql" "${DATA_ROOT}/mongodb" "${DATA_ROOT}/redis" \
  "${DATA_ROOT}/minio" "${DATA_ROOT}/elasticsearch" "${DATA_ROOT}/rabbitmq" \
  "${DATA_ROOT}/nacos" "${DATA_ROOT}/uploads/imaging" \
  "${DATA_ROOT}/uploads/unified-imports" "${DATA_ROOT}/backups"

# Elasticsearch requires this Linux kernel setting and runs as uid 1000.
sysctl -w vm.max_map_count=262144 >/dev/null
if [[ -w /etc/sysctl.d ]]; then
  printf 'vm.max_map_count=262144\n' > /etc/sysctl.d/99-brain-health.conf
fi
chown -R 1000:0 "${DATA_ROOT}/elasticsearch"
chmod -R u+rwX,g+rwX "${DATA_ROOT}/elasticsearch"

if [[ -f "${PACKAGE_DIR}/images/brain-health-images.tar" ]]; then
  echo "正在导入离线 Docker 镜像，请耐心等待……"
  docker load -i "${PACKAGE_DIR}/images/brain-health-images.tar"
else
  die "缺少离线镜像文件 images/brain-health-images.tar"
fi

compose config --quiet
echo "正在启动系统……"
compose up -d
"${SCRIPT_DIR}/health-check.sh" --wait

SERVER_IP="$(hostname -I 2>/dev/null | awk '{print $1}')"
echo
echo "安装完成。访问地址：http://${SERVER_IP:-服务器IP}:${APP_PORT}"
echo "请立即按照 docs/医院内网部署说明书.md 修改系统内置账号密码。"
