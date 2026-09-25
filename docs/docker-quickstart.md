# Docker 快速部署与 API Token 查找

这条路径面向普通部署者。拉取公开镜像不需要 Docker Hub 登录、Docker Hub Token 或 GitHub PAT。首次配置由 API 自带的同源页面完成，不依赖额外的管理 Web 镜像或 CORS 配置。

## 1. 准备并启动

下载配套的 `docker-compose.hub.yml` 和 `.env.example`，放进固定部署目录。将 `.env.example` 复制为同目录的 `.env`。新安装保持下面的配置为空，让首次配置页面管理支持的项目：

```dotenv
API_TOKEN=
ADMIN_WEB_ORIGINS=
ADMIN_WEB_URL=
WEB_INVENTORY_ENABLED=
```

首次网页配置从 `0.7.1` 开始提供。Hub Compose 默认使用 API `latest` 和 Web `web-latest`；部署前在 Docker Hub Tags 核对它们当前对应的版本。`0.7.4` 与 `web-0.7.4` 已发布，可在 `.env` 中用 `COMPONENT_VAULT_IMAGE` 和 `COMPONENT_VAULT_WEB_IMAGE` 固定这两个版本。`0.7.0` 没有首次配置页，不能用旧镜像测试这条流程。

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

以后使用默认浮动标签更新时，先在 Docker Hub 核对 `latest` 和 `web-latest` 当前对应的版本，再在原部署目录执行 `docker compose -f docker-compose.hub.yml --profile web pull` 和 `docker compose -f docker-compose.hub.yml --profile web up -d`；仅部署 API 时去掉 `--profile web`。单独执行 `restart` 不会拉取新镜像。

## 2. 群晖 Container Manager 与文件映射

在群晖 File Station 中建立共享目录，例如 `/volume1/docker/component-manager/`，把 Compose 和 `.env` 放在其中，再建立 `data/` 子目录。通过 Container Manager 的 **项目** 导入 Compose 时，可将 API 的 volume 改成宿主目录映射：

```yaml
services:
  api:
    volumes:
      - /volume1/docker/component-manager/data:/data
```

若通过 Container Manager 图形界面同时启用 Web，在项目的 Compose 编辑器中删除 `admin-web` 下的 `profiles:` 和紧随其后的 `- web` 两行，保留 `8081:80` 端口映射，然后重新部署项目。这样会启动独立的 Web 容器，API 与 Web 镜像仍分开，原有 `/data` 映射不变；只重启 API 容器不会创建 Web 服务。部署后用 `http://NAS地址:8081/` 打开管理台，以 `http://NAS地址:8787` 为 API 地址，使用同一 API Token 登录，并将 `http://NAS地址:8081` 加入 `ADMIN_WEB_ORIGINS`。

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

已有部署可继续在 `.env` 或群晖 **api 容器 → 环境变量** 中设置 `API_TOKEN`、`ADMIN_WEB_ORIGINS`、`ADMIN_WEB_URL` 和 `WEB_INVENTORY_ENABLED`。非空环境变量优先于 `/data/config.json`；被环境变量接管的值不能在网页中修改。若希望网页管理某一项，应清空对应环境变量并执行 `up -d` 重建容器，仅执行 `restart` 不会重新读取 `.env`。

`ADMIN_WEB_ORIGINS` 填浏览器访问 Web 页面的 origin，例如 `http://NAS地址:8081`，不是 API 的 `8787` 地址。后端未配置时仍采用内置 CORS 默认值，Web 库存写入默认关闭。
`0.7.4` 的 API 支持设置管理台实际地址 `ADMIN_WEB_URL`；`0.7.4` 的 Web 管理台支持直接编辑服务端配置。默认的 `latest` 和 `web-latest` 应核对实际指向；开发预发布不发布 Docker 镜像。已有 `0.7.1` 部署可继续使用已认证的 `/setup` 修改配置。`ADMIN_WEB_URL` 可填写实际 Web 页面完整地址（支持反向代理子路径）；首次保存后先复制 API Token，再点“进入管理台”并用同一 Token 登录。

`.env.example` 后半部分列出全部可由 Compose 传入的 LCSC、远程识别规则和 MQTT 高级选项。普通部署无需填写。Web 设置页保存的 MQTT 配置也位于 SQLite，并在 API 下次启动时优先于 MQTT 环境变量默认值。

`VITE_DEFAULT_API_BASE_URL` 只在从源码构建 Web 镜像时生效。Hub 预构建 Web 镜像不会因运行时 `.env` 中这个值改变而重新打包。

## 4. API Token 在哪里

首次配置或环境变量中的 `API_TOKEN` 是**管理员账户密钥**，对应原有库存。不要把管理员密钥统一分发给所有用户：在 Web 管理台的“设置 → 账户管理”创建普通账户，把该账户的密钥交给对应用户。用户的 Android、Windows 和 Web 使用同一账户密钥，数据按账户独立保存。

创建或重置普通账户时只显示一次完整密钥，请当场复制。之后管理员可重置密钥；旧密钥立即失效，库存保留。停用账户也不会删除库存。普通用户不能进入服务端配置或账户管理页面，遗失密钥应联系管理员。

升级账户版本时，先更新 API 和 Web 两个镜像，再更新原生客户端。旧版客户端仍能使用原管理员密钥，但普通用户需要支持账户身份检查的新版客户端。一个原生安装目前对应一份本地工作区；更换到其他账户时会停止同步并保留数据，不会自动把旧库存导入新账户。

除原有数据库和配置文件外，普通账户库存位于 `/data/users/<账户ID>.db`，继续使用同一 `/data` 挂载，无需为每个用户添加 volume。备份时须停止写入并保存整个数据目录，包括管理员数据库中的账户注册信息和 `users/`。若仅恢复管理员数据库而遗漏用户文件，受影响账户会提示数据不可用，需要补回文件，不能当作空库存继续使用。

管理台的登录页和设置页可选择“跟随系统 / 简体中文 / English”；`/setup` 页眉也提供语言选择。选择只保存在当前浏览器的网站存储中，两个端口的网页需要分别设置，不影响其他用户或库存内容。

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
