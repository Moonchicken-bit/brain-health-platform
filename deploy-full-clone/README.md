# 脑健康数据平台 1.0.0 完整克隆包

本包用于在 Ubuntu 24.04 amd64 医院服务器离线恢复当前已验收系统。

部署前先阅读 `docs/医院服务器部署说明书.md`，然后执行：

```bash
sha256sum -c SHA256SUMS
chmod +x scripts/*.sh
sudo ./scripts/install.sh
```

不要单独复制某个目录；`images`、`backups`、`resources`、`scripts`、`docker-compose.yml` 和校验文件缺一不可。
