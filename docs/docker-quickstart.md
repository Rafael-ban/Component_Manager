# Docker 快速部署与 API Token 查找

这条路径面向普通部署者。拉取公开镜像不需要 Docker Hub 登录、Docker Hub Token 或 GitHub PAT。首次配置由 API 自带的同源页面完成，不依赖额外的管理 Web 镜像或 CORS 配置。

## 1. 准备并启动

下载同一版本的 `docker-compose.hub.yml` 和 `.env.example`，放进固定部署目录。将 `.env.example` 复制为同目录的 `.env`。新安装保持下面三项为空，让首次配置页面管理它们：

```dotenv
API_TOKEN=
ADMIN_WEB_ORIGINS=
WEB_INVENTORY_ENABLED=
```

首次网页配置从 `0.7.1` 开始提供。Hub Compose 默认使用 API `0.7.1` 和 Web `web-0.7.1`；部署前在 Docker Hub Tags 确认该版本已经发布。`0.7.0` 没有首次配置页，不能用旧镜像测试这条流程。需要部署另一个已发布版本时再覆盖 `COMPONENT_VAULT_IMAGE` 和 `COMPONENT_VAULT_WEB_IMAGE`。

在部署目录中先启动 API：

```sh
docker compose -f docker-compose.hub.yml pull api
docker compose -f docker-compose.hub.yml up -d api
docker compose -f docker-compose.hub.yml ps
```

打开 `http://服务器IP:8787/setup`。首次未配置时，可在这里生成或填写 `API_TOKEN`、设置管理 Web 的允许来源，以及选择是否允许 Web 库存写入。保存后配置写入 `/data/config.json`，匿名配置入口随即关闭；以后需要使用当前 Token 登录配置页，才能修改受支持的字段或查看日志。访问 API 根地址 `http://服务器IP:8787/` 会跳转到该页面。

如需完整管理 Web，再启动可选 profile：

```sh
docker compose -f docker-compose.hub.yml --profile web pull
docker compose -f docker-compose.hub.yml --profile web up -d
```

管理 Web 位于 `http://服务器IP:8081/`。库存数据保存在 `/data/component_vault.db`；不要执行 `docker compose down -v`。

## 2. 群晖 Container Manager 与文件映射

在群晖 File Station 中建立共享目录，例如 `/volume1/docker/component-manager/`，把 Compose 和 `.env` 放在其中，再建立 `data/` 子目录。通过 Container Manager 的 **项目** 导入 Compose 时，可将 API 的 volume 改成宿主目录映射：

```yaml
services:
  api:
    volumes:
      - /volume1/docker/component-manager/data:/data
```

部署后可在 File Station 看到这些持久文件：

数据目录由你管理，请使用私有目录。`config.json` 含 API Token；新文件遵循容器
的默认 umask 与挂载目录的访问规则，不额外设为仅容器 root 可读。

| 路径 | 内容 |
| --- | --- |
| `data/component_vault.db` | 库存、同步状态，以及从 Web 设置页保存的 MQTT 配置 |
| `data/config.json` | 首次配置页保存的 Token、允许来源和 Web 写入开关 |
| `data/logs/server.log` | API 滚动日志；大小和保留数量有上限 |
| `data/recognition_rules_cache.json` | 可选的服务端远程识别规则缓存，仅启用并刷新远程规则后生成 |

容器仍会向 stdout/stderr 输出日志，因此也能在 Container Manager 的容器日志页或 `docker compose ... logs` 中查看。`server.log` 保存应用运行事件，最多 1 MiB，保留 3 份轮转文件；容器标准输出还包括启动和运行时诊断。当前服务端保存的是元件元数据与图片 URL，没有上传图片目录需要映射。

仓库默认继续使用 named volume `component_vault_data`，方便普通 Docker 用户无修改升级。上面的 bind mount 更适合希望用 File Station 查看配置和日志的 NAS 新安装。

已有 named-volume 部署不要直接切换到空的宿主目录，否则会看起来像“库存消失”。迁移前停止写入，用 SQLite backup 取得一致备份，将数据库及需要保留的配置恢复到新的 `/data`，验证库存后再停用旧 volume。改变映射不会自动迁移或删除旧 volume。

## 3. 环境变量和网页配置的优先级

已有部署可继续在 `.env` 或群晖 **api 容器 → 环境变量** 中设置 `API_TOKEN`、`ADMIN_WEB_ORIGINS` 和 `WEB_INVENTORY_ENABLED`。非空环境变量优先于 `/data/config.json`；被环境变量接管的值不能在网页中修改。若希望网页管理某一项，应清空对应环境变量并执行 `up -d` 重建容器，仅执行 `restart` 不会重新读取 `.env`。

`ADMIN_WEB_ORIGINS` 填浏览器访问 Web 页面的 origin，例如 `http://NAS地址:8081`，不是 API 的 `8787` 地址。后端未配置时仍采用内置 CORS 默认值，Web 库存写入默认关闭。

`.env.example` 后半部分列出全部可由 Compose 传入的 LCSC、远程识别规则和 MQTT 高级选项。普通部署无需填写。Web 设置页保存的 MQTT 配置也位于 SQLite，并在 API 下次启动时优先于 MQTT 环境变量默认值。

`VITE_DEFAULT_API_BASE_URL` 只在从源码构建 Web 镜像时生效。Hub 预构建 Web 镜像不会因运行时 `.env` 中这个值改变而重新打包。

## 4. API Token 在哪里

按以下顺序检查：

1. 已登录的 `http://服务器IP:8787/setup` 配置页；
2. 部署目录 `.env`；
3. 群晖 Container Manager 的 **api 容器 → 环境变量 → API_TOKEN**；
4. bind mount 中的 `/data/config.json`。该文件包含秘密值，不要发到 Issue、聊天或日志中。

已经运行的 Hub Compose 部署也可在部署目录执行：

```sh
docker compose -f docker-compose.hub.yml exec api printenv API_TOKEN
```

源码 Compose 使用 `docker compose exec api printenv API_TOKEN`。命令只会显示环境变量来源的值；若输出为空，Token 可能由 `/data/config.json` 管理，应登录配置页查看。命令会在终端显示秘密值，注意终端历史、录屏和旁观者。粘贴到客户端时只复制值，不包含 `API_TOKEN=`。

如果忘记网页配置的 Token，可直接在 File Station 打开映射目录的 `config.json`，查看 `API_TOKEN` 的值；无需先登录网页或重置数据库。高级用户也可在容器终端仅输出该字段：

```sh
docker compose -f docker-compose.hub.yml exec api python -c 'from app.config import get_settings; print(get_settings().api_token)'
```

这是在自己管理的容器终端查看当前配置，结果不要放入公开反馈。

如果你没有部署或管理这台服务，请向部署者索取当前 Token，不要猜测初始口令。

## 5. 401 与忘记 Token

升级前使用旧默认 Token、已有库存但没有环境变量或配置文件的部署，会继续识别旧的
`change-me`，避免升级时开放匿名初始化。可用旧 Token 登录后设置新的随机 Token。
这是旧数据兼容行为；全新部署不会自动使用默认口令。

`/health` 成功但客户端返回 `401`，表示客户端 Token 与 API 当前生效值不一致。先按上一节确认当前值，再更新客户端。

忘记 Token 时，优先使用仍有效的登录会话进入配置页。若 Token 由环境变量提供，在 `.env` 中写入新随机值后执行 `docker compose -f docker-compose.hub.yml --profile web up -d`；源码 Compose 使用 `docker compose up -d`。环境变量改动不能靠 `docker compose restart` 生效。修改 Token 会让所有客户端的旧 Token 失效，但不会改变数据库 volume。

PowerShell 可用 `[guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')` 生成随机值；Linux/macOS 可用 `openssl rand -hex 32`。

完整的升级、备份、CORS、MQTT 与维护者发布说明见 [Docker Hub 镜像发布与部署教程](dockerhub.md) 和 [Runbook](runbook.md)。
