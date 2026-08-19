# 脑健康数据平台医院内网部署包

本包适用于 Ubuntu 24.04 x86_64 单机、无互联网环境。完整步骤见
`docs/医院内网部署说明书.md`。

快速安装：

```bash
chmod +x scripts/*.sh
sudo ./scripts/install.sh
```

默认仅开放 Web 端口 `80`，数据库、中间件均不对内网直接暴露。

