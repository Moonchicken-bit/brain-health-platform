# 心理及脑健康数据管理平台

面向临床科研场景的多模态数据管理平台，覆盖受试者与访视、量表评估、影像、遗传、实验室、检索、导出、审计及管理配置等功能。

> 本仓库仅保存可复现的源代码、配置模板和数据库迁移脚本。患者资料、医学附件、运行数据库、真实密码及完整离线部署包不进入 Git 仓库。

## 技术架构

- 前端：Vue 3、TypeScript、Vite、Element Plus、Pinia
- 后端：Java 21、Spring Boot、多模块 Maven 工程
- 网关与服务：Gateway、Auth、Subject、Scale、Imaging、Genetics、Lab、Search、Export、Audit、ADNI
- 基础设施：MySQL、MongoDB、Redis、MinIO、Elasticsearch、RabbitMQ、Nacos、Mailpit
- 本地编排：Docker Compose，共 20 个服务

## 目录说明

| 路径 | 用途 |
| --- | --- |
| `brain-health-web/` | Vue 前端 |
| `common/` | 后端公共模块 |
| `*-service/` | 各业务微服务 |
| `gateway/` | API 网关 |
| `sql/` | 数据库结构、迁移和修复脚本 |
| `docs/` | 设计、测试与部署文档 |
| `deploy/` | 通用部署模板 |
| `deploy-full-clone/` | 完整克隆部署脚本模板，不包含运行数据和离线镜像 |

## 本地启动

### 前置条件

- Docker Desktop（启用 Docker Compose）
- 如需脱离 Docker 构建：JDK 21、Maven、Node.js 和 npm

### 使用 Docker Compose

在仓库根目录运行：

```powershell
docker compose up -d
docker compose ps
```

默认本机入口：

```text
http://127.0.0.1:5173/login
```

停止系统：

```powershell
docker compose down
```

### 分别构建

后端：

```powershell
mvn clean package -DskipTests
```

前端：

```powershell
cd brain-health-web
npm ci
npm run build
```

## 配置约定

- 不提交真实 `.env` 文件。
- 仅提交 `.env.example` 或 `.env.template`，其中必须使用占位值。
- 正式环境密码、JWT 密钥和第三方令牌应在部署目标上单独生成。
- 医疗数据和对象存储内容应通过受控的备份/恢复流程交付。

## 数据库迁移

数据库迁移脚本位于 `sql/`，按 `V001`、`V002` 等版本顺序执行。部署或升级前应先备份数据库，并在隔离环境验证迁移结果。

## 安全与隐私

严禁向本仓库提交：

- 患者或受试者的原始数据、影像、遗传、实验室附件；
- 数据库快照、MinIO 文件、统一导入压缩包；
- 真实账号密码、访问令牌、私钥和生产环境 `.env`；
- Docker 离线镜像、JAR、`node_modules`、`target`、日志等生成物。

发现凭据误提交时，应立即撤销/轮换凭据并清理 Git 历史，而不是只在后续提交中删除文件。

## 部署包

医院完整克隆部署包包含离线镜像和运行数据，体积较大且可能含敏感资料，因此不随源码发布，也不上传至普通 GitHub Release。部署包通过批准的内网介质、加密点对点传输或受控网盘单独交付，并使用 SHA-256 校验完整性。

## 许可与使用范围

当前项目用于内部临床科研试用。未经项目负责人确认，不得公开仓库、转发数据或用于生产诊疗决策。

