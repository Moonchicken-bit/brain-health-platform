#!/usr/bin/env bash
set -Eeuo pipefail

PACKAGE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PACKAGE_DIR"

die() { echo "错误：$*" >&2; exit 1; }
need() { command -v "$1" >/dev/null 2>&1 || die "缺少命令：$1"; }
compose() { docker compose --env-file .env -f docker-compose.yml "$@"; }
random_hex() { openssl rand -hex "$1"; }

[[ "$(uname -s)" == Linux ]] || die "仅支持 Linux"
[[ "$(uname -m)" == x86_64 ]] || die "当前离线镜像仅支持 amd64/x86_64"
for cmd in docker openssl curl tar awk sed; do need "$cmd"; done
docker compose version >/dev/null 2>&1 || die "未安装 Docker Compose 插件"
docker info >/dev/null 2>&1 || die "Docker 不可用，请启动 Docker 并授予当前用户权限"

[[ -f SHA256SUMS ]] || die "缺少 SHA256SUMS"
sha256sum -c SHA256SUMS || die "文件校验失败，请重新传输部署包"

if [[ ! -f .env ]]; then
  cp .env.template .env
  replace() { sed -i "s|^$1=.*|$1=$2|" .env; }
  replace MYSQL_ROOT_PASSWORD "$(random_hex 24)"
  replace MYSQL_PASSWORD "$(random_hex 24)"
  replace MONGO_PASSWORD "$(random_hex 24)"
  replace MINIO_PASSWORD "$(random_hex 24)"
  replace REDIS_PASSWORD "$(random_hex 24)"
  replace RABBITMQ_PASSWORD "$(random_hex 24)"
  replace JWT_SECRET "$(random_hex 64)"
  replace OTP_ENCRYPTION_KEY "$(random_hex 32)"
  replace NACOS_IDENTITY_KEY "$(random_hex 16)"
  replace NACOS_IDENTITY_VALUE "$(random_hex 24)"
  replace NACOS_AUTH_TOKEN "$(openssl rand -base64 64 | tr -d '\n')"
  chmod 600 .env
  echo "已生成医院服务器专用密码：$PACKAGE_DIR/.env"
fi

set -a
# shellcheck disable=SC1091
source .env
set +a

[[ "$DATA_ROOT" == /* ]] || die "DATA_ROOT 必须是绝对路径"
case "$DATA_ROOT" in /|/home|/root|/usr|/var|/etc) die "DATA_ROOT 范围过大" ;; esac
[[ ! -e "$DATA_ROOT/.full-clone-restored" ]] || die "检测到已完成恢复；如需重装请先按回滚说明备份并清理目标目录"

mkdir -p "$DATA_ROOT"/{mysql,mongodb,minio,redis,elasticsearch,rabbitmq,nacos,unified-imports,backups}
sysctl -w vm.max_map_count=262144 >/dev/null
printf 'vm.max_map_count=262144\n' | sudo tee /etc/sysctl.d/99-brain-health.conf >/dev/null || true

echo "正在导入离线镜像……"
docker load -i images/brain-health-platform-images-1.0.0-full-clone.tar

echo "正在恢复文件存储与缓存快照……"
tar -tzf backups/minio/minio-data.tar.gz | grep -Eq '(^/|(^|/)\.\.(/|$))' && die "MinIO 归档包含不安全路径"
tar -xzf backups/minio/minio-data.tar.gz -C "$DATA_ROOT/minio"
tar -tzf backups/elasticsearch/elasticsearch-data.tar.gz | grep -Eq '(^/|(^|/)\.\.(/|$))' && die "Elasticsearch 归档包含不安全路径"
tar -xzf backups/elasticsearch/elasticsearch-data.tar.gz -C "$DATA_ROOT/elasticsearch"
cp backups/redis/dump.rdb "$DATA_ROOT/redis/dump.rdb"
chown -R 1000:0 "$DATA_ROOT/elasticsearch"

compose config --quiet
compose up -d mysql mongodb minio redis elasticsearch rabbitmq nacos mailpit

echo "等待数据库和基础设施健康……"
for _ in {1..90}; do
  if compose ps --format json | grep -q '"Health":"unhealthy"'; then sleep 3; continue; fi
  mysql_ok="$(compose exec -T mysql mysqladmin ping -uroot -p"$MYSQL_ROOT_PASSWORD" --silent 2>/dev/null || true)"
  mongo_ok="$(compose exec -T mongodb mongosh --quiet -u "$MONGO_USER" -p "$MONGO_PASSWORD" --authenticationDatabase admin --eval 'db.runCommand({ping:1}).ok' 2>/dev/null || true)"
  [[ "$mysql_ok" == *alive* && "$mongo_ok" == *1* ]] && break
  sleep 3
done
[[ "$mysql_ok" == *alive* ]] || die "MySQL 未就绪"
[[ "$mongo_ok" == *1* ]] || die "MongoDB 未就绪"

echo "正在恢复 MySQL……"
compose exec -T mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" brain_health < backups/mysql/brain_health.sql
echo "正在恢复 MongoDB……"
cat backups/mongodb/brain_health.archive.gz | compose exec -T mongodb mongorestore --uri="mongodb://$MONGO_USER:$MONGO_PASSWORD@localhost:27017/brain_health?authSource=admin" --archive --gzip --drop

date -Iseconds > "$DATA_ROOT/.full-clone-restored"
compose up -d
echo "系统容器已启动，继续执行健康检查……"
"$PACKAGE_DIR/scripts/health-check.sh" --wait

server_ip="$(hostname -I | awk '{print $1}')"
echo "部署完成：http://${server_ip}:${APP_PORT}"
echo "请妥善保存 $PACKAGE_DIR/.env，并立即按部署说明书执行验收。"
