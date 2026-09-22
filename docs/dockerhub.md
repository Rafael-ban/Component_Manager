# Docker Hub 镜像发布与部署教程

本文分为两部分：仓库维护者在 Docker Hub 和 GitHub 中配置镜像发布，以及部署者从 Docker Hub 拉取独立的 API / Web 镜像。

`v0.6.0` 首次发布时 Docker Hub 任务因凭据未配置而 skipped。`v0.7.0` 正式发布时，仓库已具备发布配置，[发布任务](https://github.com/Rafael-ban/Component_Manager/actions/runs/35722942557) 成功。2026-09-22 已直接核对 Docker Hub：API `0.7.0` 与 Web `web-0.7.0` 均为 active，并包含 `linux/amd64`、`linux/arm64`；可以直接按第七节开始部署。前六节保留给首次配置、Token 轮换和手动补发使用，不需要重复生成现有凭据。

已核对的版本 digest：

- API：`sha256:81985ac9cced8b12de193d67818e9fd5d814be4af46c0a42a32a87d174bf98dc`
- Web：`sha256:5e022b6aa65025fc360bdfda8b12a40ac4ef4d83a0a10e29ed69bd14219ef076`

未来版本仍应核对 Actions 与 Docker Hub Tags，不能仅凭 GitHub Release 存在就推断镜像发布成功。

## 先分清账号、仓库和两种 Token

本项目使用以下固定身份：

| 用途 | 值 |
| --- | --- |
| GitHub 账号与仓库 | `Rafael-ban/Component_Manager` |
| Docker Hub namespace | `rafaelikaros` |
| Docker Hub repository | `component_manager` |
| 完整镜像名 | `rafaelikaros/component_manager` |

GitHub 用户名和 Docker Hub namespace 不要求相同。填写 `DOCKERHUB_IMAGE` 时使用 `rafaelikaros/component_manager`，末尾不要加 `/`，也不要附加版本或 `latest`；API 与 Web 共用这个仓库，通过不同 tag 区分。

还要区分两种完全不同的凭据：

- `DOCKERHUB_TOKEN` 是 Docker Hub Personal Access Token，只供 GitHub Actions 登录 Docker Hub 并推送镜像。
- `API_TOKEN` 是本项目服务端 API 的访问令牌，由部署者写入 `.env`，供 Android、Windows 和 Web 客户端登录服务端。

不要把任何真实 Token 写进源码、Compose 文件、Issue、日志或聊天消息。

## 一、在 Docker Hub 创建公开仓库

1. 登录 [Docker Hub](https://hub.docker.com/)。
2. 打开 **My Hub → Repositories**。
3. 点击 **Create repository**。
4. **Namespace** 选择 `rafaelikaros`。
5. **Repository name** 填 `component_manager`。
6. **Visibility** 选择 **Public**。
7. 点击 **Create** 完成创建。

Public 仓库方便部署者直接执行 `docker pull`，无需额外登录。仓库创建后，页面地址通常为：

`https://hub.docker.com/r/rafaelikaros/component_manager`

此时仓库可能仍没有 tag；创建仓库本身不会上传镜像。Docker 官方说明见 [Create a repository](https://docs.docker.com/docker-hub/repos/create/)。

## 二、生成 Docker Hub Personal Access Token

1. 打开 [Docker 账号页面](https://app.docker.com/)，点击右上角头像。
2. 打开 **Account settings**。
3. 进入 **Personal access tokens**。
4. 点击 **Generate new token**。
5. 名称填 `component-manager-github-actions`。
6. 权限选择 **Read & Write**。
7. 不需要 **Delete** 权限。
8. 到期时间按自己的维护周期选择；到期后需要生成新 Token 并更新 GitHub Secret。
9. 点击生成后立即复制并保存到可信的密码管理器。

Token 只在创建时完整显示一次。不要把它发到聊天中，也不要为了让别人核对而截图。如果 Token 泄露，应立即在 Docker Hub 撤销并重新生成。

Docker 官方说明见 [Personal access tokens](https://docs.docker.com/security/access-tokens/personal-access-tokens/)。

## 三、核对 GitHub Actions Variables

打开 [GitHub 仓库 Actions 配置](https://github.com/Rafael-ban/Component_Manager/settings/secrets/actions)，对应路径为：

**Settings → Secrets and variables → Actions → Variables**

核对下面两个 Repository variable。它们上一轮已经设置，本轮通常只需检查，无需重复创建：

| Name | Value |
| --- | --- |
| `DOCKERHUB_USERNAME` | `rafaelikaros` |
| `DOCKERHUB_IMAGE` | `rafaelikaros/component_manager` |

检查 `DOCKERHUB_IMAGE` 没有尾部 `/`、没有协议、没有 tag。正确值是 `namespace/repository` 两段格式。

GitHub Variables 的官方说明见 [Store information in variables](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-variables)。

## 四、添加 GitHub Actions Secret

仍在 **Settings → Secrets and variables → Actions** 页面：

1. 切换到 **Secrets**。
2. 在 **Repository secrets** 区域点击 **New repository secret**。
3. **Name** 填 `DOCKERHUB_TOKEN`。
4. **Secret** 粘贴刚才生成的 Docker Hub Token。
5. 点击 **Add secret**。

这里使用 Repository secret 即可，不需要创建 GitHub Environment，也不需要配置 Environment secret。保存后 GitHub 不会再次显示 Secret 原文，这是正常行为。

GitHub Secrets 的官方说明见 [Using secrets in GitHub Actions](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets)。

## 五、补发 v0.7.0 API 与 Web 镜像

配置完成后，打开 [Server Docker Image 工作流](https://github.com/Rafael-ban/Component_Manager/actions/workflows/server-image.yml)：

1. 左侧选择 **Server Docker Image**。
2. 点击 **Run workflow**。
3. **Use workflow from** 选择 `master`。
4. `source_ref` 填已经存在且经过验证的 `v0.7.0` tag。
5. `release_tag` 填 `v0.7.0`。
6. `push_image` 选择 `true` 或勾选发布选项。
7. 点击 **Run workflow**。

手动运行 workflow 的官方说明见 [Manually running a workflow](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow)。

`source_ref` 决定实际构建哪一份源码，`release_tag` 决定镜像版本 tag。发布时两者应指向同一版本。

这次补发会同时推送：

- `rafaelikaros/component_manager:0.7.0`
- `rafaelikaros/component_manager:latest`
- `rafaelikaros/component_manager:web-0.7.0`
- `rafaelikaros/component_manager:web-latest`

因此，用旧版本做补发时要特别留意：API `latest` 和 Web `web-latest` 都会被改为该旧版本。补发历史版本前先判断是否允许两个浮动标签一起回退。

普通 CI 或手动运行但 `push_image=false` 时，只构建并检查镜像，不会登录或推送。自动 Release 在用户名、镜像名或 Token 不完整时，会保留原生客户端与 Web 发布，并跳过 Docker Hub 镜像任务。但直接手动运行 **Server Docker Image** 且设置 `push_image=true` 时，缺少任一项配置都会明确失败，不会静默跳过。

## 六、确认发布确实成功

先在本次 Actions run 中确认：

1. **Validate publishing configuration** 成功。
2. **Verify API startup, authentication and mounted database** 成功。
3. **Verify web startup and bundled assets** 成功。
4. **Login to Docker Hub** 成功，而不是 skipped。
5. **Publish multi-platform API image** 和 **Publish multi-platform web image** 成功。

再到 Docker Hub 仓库的 **Tags** 页面确认：

- 存在 `0.7.0` 和 `latest`；
- 存在 `web-0.7.0` 和 `web-latest`；
- manifest 包含 `linux/amd64` 和 `linux/arm64`。

工作流会在 `linux/amd64` 上实际启动两个容器，验证 API `/health`、鉴权、挂载数据库，以及 Web 首页生成的 JS/CSS 资源。`linux/arm64` 会参与多平台构建与发布，但当前 workflow 不会在 ARM 机器上实际启动。

如本地已安装 Docker，可选执行：

```sh
docker buildx imagetools inspect rafaelikaros/component_manager:0.7.0
docker buildx imagetools inspect rafaelikaros/component_manager:web-0.7.0
docker pull rafaelikaros/component_manager:0.7.0
docker pull rafaelikaros/component_manager:web-0.7.0
```

发布者完成上述网页配置和 Actions 补发只需要浏览器，本地无需安装 Docker；这些本地命令只是额外核验手段。

## 七、部署端准备

部署机需要 Docker Engine 与 Docker Compose 2，命令应为 `docker compose`。Windows 用户可使用 Docker Desktop，并切换到 Linux containers，因为本项目发布的是 Linux 容器镜像。

先执行 `docker version` 和 `docker compose version`，确认能连接 Docker 服务且 Compose 可用。
必须先完成上一节的镜像上传验证，再进行拉取部署。

镜像确认发布后，从对应的 `v0.7.0` tag 下载 `docker-compose.hub.yml` 和 `.env.example`。发布前可先准备配置，但不要尝试拉取尚不存在的 tag。

把两个文件放到一个固定部署目录，例如 `component-manager-server/`。将 `.env.example` 复制为同目录的 `.env`。如果目录中已经有 `.env`，先备份和对比，不要直接覆盖现有 Token、CORS 或 MQTT 配置。

至少设置：

```dotenv
COMPONENT_VAULT_IMAGE=rafaelikaros/component_manager:0.7.0
COMPONENT_VAULT_WEB_IMAGE=rafaelikaros/component_manager:web-0.7.0
API_TOKEN=replace-with-your-own-random-token
ADMIN_WEB_ORIGINS=http://192.168.1.10:8081
WEB_INVENTORY_ENABLED=false
```

把 `API_TOKEN` 的占位值换成自己的随机值。PowerShell 可执行
`[guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')`，
Linux 可执行 `openssl rand -hex 32`，把输出存入自己的 `.env`，无需发送给其他人。
确认文件名为 `.env` 而非 `.env.txt`。

`ADMIN_WEB_ORIGINS` 填浏览器访问 Web 管理页时的来源，格式是 `scheme://host:port`，例如 `http://192.168.1.10:8081`。它不是服务端 API URL，不要填 `http://192.168.1.20:8787`，除非浏览器中的 Web 页面本身确实由该 origin 提供。多个 Web 来源用逗号分隔。

## 八、拉取并启动服务端

在 Compose 文件与 `.env` 所在目录执行：

```sh
docker compose -f docker-compose.hub.yml pull
docker compose -f docker-compose.hub.yml up -d
docker compose -f docker-compose.hub.yml ps
docker compose -f docker-compose.hub.yml logs --tail=100 api
```

确认容器处于运行状态后访问：

```sh
curl http://服务器IP:8787/health
```

应返回包含 `"status":"ok"` 和 `"inventory_protocol":1` 的 JSON。`/health` 无需鉴权，所以健康检查成功不能代替 Token 验证。

随后在 Android 或 Windows 客户端中填写：

- 服务端地址：`http://服务器IP:8787`
- API Token：与部署目录 `.env` 中 `API_TOKEN` 完全一致

完成一次带 Token 的连接或同步测试，才能确认鉴权和网络路径都可用。
手机中的地址不能用 `localhost`，应使用手机能访问的服务器 IP 或域名；服务器防火墙需允许对应的 8787 端口。

端口 `8787` 只提供 FastAPI。确认 Web tag 已发布后，可启动可选 profile：

```sh
docker compose -f docker-compose.hub.yml --profile web pull
docker compose -f docker-compose.hub.yml --profile web up -d
```

Web 页面位于 `http://服务器IP:8081/`。若 Web 镜像尚未发布，仍可从 GitHub
Release 下载 `component-vault-admin-web.zip` 并用静态服务器单独部署。无论采用
哪种方式，都要把实际页面 origin 加入 `ADMIN_WEB_ORIGINS`。

Web 库存写入默认关闭。在 `.env` 中设置 `WEB_INVENTORY_ENABLED=true` 后，重新执行
`docker compose -f docker-compose.hub.yml --profile web up -d`，让 Compose 按新环境变量重建 API 容器；
仅执行 `docker compose restart` 不会应用 `.env` 的修改。
生效后，已通过共享 Token 登录的浏览器才能新增库位/元件、编辑资料和记录出入库。
关闭开关会停止后续 Web 写入，不会撤销已经提交的库存变动。

## 九、更新、迁移与备份

更新时将 `.env` 中两个镜像都固定到 Docker Hub 已实际发布的版本：

```dotenv
COMPONENT_VAULT_IMAGE=rafaelikaros/component_manager:0.7.0
COMPONENT_VAULT_WEB_IMAGE=rafaelikaros/component_manager:web-0.7.0
```

如果已启用 Web，在原部署目录执行以下命令以同时更新两个服务；只部署 API 时去掉 `--profile web`：

```sh
docker compose -f docker-compose.hub.yml --profile web pull
docker compose -f docker-compose.hub.yml --profile web up -d
docker compose -f docker-compose.hub.yml ps
```

固定版本 tag 便于审计和回退；`latest` 更适合临时体验。保持原部署目录和 Compose project name，才能自然复用原 named volume。
不要执行 `docker compose down -v`，`-v` 会删除库存数据库所在 volume。

从源码版 Compose 迁移到 Hub Compose 时，也应保留原目录和 project name。如果必须更换目录，在所有命令中显式使用原 project name，例如：

```sh
docker compose -p 原项目名 -f docker-compose.hub.yml up -d
```

旧部署若已有静态 Web 服务，启用 Hub Compose 的 `web` profile 前先确认端口与 origin，避免与旧服务同时占用 8081。API-only 部署无需启用 profile。

数据库位于容器 `/data/component_vault.db`，保存在 named volume 中。备份 SQLite 时需要一致 snapshot：优先使用 SQLite backup 机制；若只能复制文件，应先暂停写入或停止容器，再完整复制 `/data` 中的数据库相关文件，避免只复制主 `.db` 而遗漏仍有数据的 WAL 文件。
回退应用版本前还需确认数据库结构兼容，不能假设旧程序能够打开已经被新版迁移的数据库。

## 十、常见问题

| 现象 | 常见原因 | 处理方式 |
| --- | --- | --- |
| Actions 登录时报 `unauthorized` / authentication failed | Token 错误、过期或已撤销 | 重新生成 Read & Write Token，更新 `DOCKERHUB_TOKEN` |
| 推送时报 `denied` | namespace/repository 不匹配，或 Token 没有 Write 权限 | 核对 `rafaelikaros/component_manager` 与 Token 权限 |
| 拉取时报 `manifest unknown` | tag 尚未发布或拼写错误 | 在 Docker Hub Tags 核对 `0.7.0` / `web-0.7.0` 是否真实存在 |
| `v0.7.0` 拉不到但 `0.7.0` 存在 | API tag 去掉 Git tag 的 `v`；Web 另加 `web-` | 使用 `:0.7.0` 或 `:web-0.7.0` |
| GitHub Actions 全绿但 Hub 没有镜像 | 跑的是普通 CI、`push_image=false`，或自动 Release 跳过了镜像任务 | 检查 Login/Publish 是否 skipped，按第五节补发 |
| `pull` 超时 | Docker Hub 网络、DNS 或代理问题 | 先用 `docker pull` 单独诊断网络，再重试 Compose |
| Web 请求 API 出现 CORS 错误 | `ADMIN_WEB_ORIGINS` 填成 API 地址或遗漏实际 Web origin | 填浏览器地址栏中 Web 页面的 scheme、host 和 port |
| 换目录后库存为空 | Compose project name 变化，创建了新的空 volume | 回到原目录，或用 `-p 原项目名` 指向原 project |
| `/health` 正常但客户端 401 | 客户端 Token 与 `.env` 的 `API_TOKEN` 不一致 | 更新客户端 Token 后重试；无需给 `/health` 加 Token |

排障时先看 `docker compose ... ps` 和 `logs`，再区分是镜像发布、镜像拉取、容器启动、鉴权还是浏览器 CORS 问题，避免用删除 volume 作为通用重试手段。
