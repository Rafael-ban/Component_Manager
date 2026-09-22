# Docker Hub 服务端镜像

此镜像运行 FastAPI 同步服务（8787 端口），Web 管理界面仍单独部署。
目标仓库为 `rafaelikaros/component_manager`，实际可用版本以 Docker Hub 中的 tag
和 GitHub Actions 发布结果为准；仅完成工作流配置并不代表镜像已上传。

## 维护者配置发布

在 GitHub 仓库的 **Settings → Secrets and variables → Actions** 配置：

| 类型 | 名称 | 内容 |
| --- | --- | --- |
| Variable | `DOCKERHUB_USERNAME` | `rafaelikaros` |
| Variable | `DOCKERHUB_IMAGE` | `rafaelikaros/component_manager`，不含 tag |
| Secret | `DOCKERHUB_TOKEN` | Docker Hub 创建的可读写访问令牌 |

组织仓库的 namespace 可以与登录用户名不同。不要把 Token 写入源码、
Compose 文件、Issue 或聊天。服务端 `API_TOKEN` 与 Docker Hub Token 是两个用途不同的值。

正常发布会先完成客户端构建和 GitHub Release，然后调用 `Server Docker Image`。
容器启动、健康检查、正确/错误 API Token、挂载目录数据库验证通过后，才推送
`linux/amd64` 和 `linux/arm64` 镜像。稳定版本同时生成 `0.6.0` 这样的版本 tag 和 `latest`。
CI 的实际启动检查在 amd64 上执行；arm64 执行镜像构建。

如果发布时尚未完整配置用户名、仓库和 Token，Docker Hub 推送会明确提示并跳过，
不会产生可下载镜像；原有 APK、Windows ZIP、Web ZIP 发布不受影响。
配置完成后可在 Actions 手动运行 **Server Docker Image**：

1. `source_ref` 填要发布的 Git tag，例如 `v0.6.0`。
2. `release_tag` 填同一版本，例如 `v0.6.0`。
3. 勾选 `push_image`。
4. 确认工作流成功，并在 Docker Hub 中检查版本 tag 和两种架构。

构建流程采用 Docker 官方的
[先测试再推送](https://docs.docker.com/build/ci/github-actions/test-before-push/)模式。

## 用户部署

下载仓库中的 `docker-compose.hub.yml` 和 `.env.example`，将后者复制成 `.env`。
把镜像仓库改成维护者实际公布的地址，并设置自己的 API Token，例如：

```dotenv
COMPONENT_VAULT_IMAGE=rafaelikaros/component_manager:0.6.0
API_TOKEN=replace-with-your-own-token
ADMIN_WEB_ORIGINS=http://192.168.1.10:8081
```

在这些文件所在目录运行：

```sh
docker compose -f docker-compose.hub.yml pull
docker compose -f docker-compose.hub.yml up -d
docker compose -f docker-compose.hub.yml ps
docker compose -f docker-compose.hub.yml logs --tail=80 api
```

客户端服务端地址填写 `http://服务器IP:8787`，API Token 填 `.env` 中的
`API_TOKEN`。可访问 `/health` 检查存活；镜像自身也会定期检查该路径。

数据库在容器的 `/data/component_vault.db`，由 `component_vault_data` volume 持久化。
更新时修改镜像版本，再执行 `pull` 和 `up -d`，无需删除 volume。
从源码 Compose 切换时保持原目录/Compose project name，才能复用同一个数据卷；
如移动目录，用 `docker compose -p 原项目名 -f docker-compose.hub.yml ...`。
切换前保留库存备份，不要执行 `down -v`。

Web 管理页面可使用 GitHub Release 的 `component-vault-admin-web.zip` 单独部署，
并将其实际浏览器地址加入 `ADMIN_WEB_ORIGINS`。镜像不包含 Web 写库存功能。
