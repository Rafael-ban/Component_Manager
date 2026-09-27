# Component Vault｜元器件库存管理

Component Vault 面向电子元器件的入库、出库、查找与盘点。Android 和 Windows 客户端以本机 SQLite 保存库存，可以离线使用；需要多设备共享时，再连接自托管服务同步。项目还提供独立的 Web 管理台，用于查看服务器库存和管理部署。

## 从这里开始

| 你想做什么 | 入口 |
| --- | --- |
| 下载 Android APK 或 Windows 便携版 | [GitHub Releases](https://github.com/Rafael-ban/Component_Manager/releases/latest)；安装与更新说明见[应用更新](docs/app-updates.md) |
| 在 Docker 或群晖上部署服务 | [Docker 快速部署](docs/docker-quickstart.md) |
| 使用 BOM、批量扫码、库位或备份 | 下方的[使用指南](#使用指南) |
| 开发或集成客户端 | [开发与集成](#开发与集成) |

原生客户端无需服务器即可管理本地库存。准备同步时，先部署 API，在客户端“设置 → 连接与同步”填写服务器地址与账户密钥，再测试连接并同步。首次部署通过 `http://服务器IP:8787/setup` 配置管理员密钥；不要把 Docker Hub 或 GitHub 的登录凭据填进这里。服务端库存保存在持久化的 `/data` 中，升级时保留该数据卷。

日常功能与界面调整先发布 **dev 预发布**，通过实际使用验证后再决定正式发布。应用“设置 → 关于”可选择更新通道；dev 安装包见 [全部 Releases](https://github.com/Rafael-ban/Component_Manager/releases)。发布渠道由更新日志的最新具体版本标题决定：`0.7.7-dev.1` 为预发布，`0.7.7` 为正式版；只修改 `Unreleased` 不发布。dev 不会替换 Docker `latest`，详见[发布流程](docs/runbook.md#versioning-workflow)。

## 平台与能力

| 平台 | 技术与用途 |
| --- | --- |
| Android | Jetpack Compose + 本机 SQLite；扫码、查询、库存操作、标签预览与已验证的汉印 M1 打印流程 |
| Windows | WinUI 3 + 本机 SQLite；库存、BOM、导入与同步；正式发布提供便携版 ZIP |
| 管理 Web | React + `shadcn/ui`；查看服务端库存、同步状态和设置；启用 `WEB_INVENTORY_ENABLED` 后可进行受控的库存写入 |
| 服务端 | FastAPI + SQLite；账户认证、同步、管理 API 与可选 MQTT 库存事件 |

库存检索支持型号片段、品牌与参数的多词匹配，例如 `FOJAN 10k` 或 `100uF 1206`；常见型号分隔符与微单位写法会归一。新导入默认名称使用品牌和型号（例如 `FOJAN FRH0603B1002TS`），不拼入电气参数；基本信息独立显示已取得的阻值、容量、电感量、耐压、精度、功率及封装，并参与检索。旧库存从已保存的官方资料读取参数，不批量改名；没有保存过的参数可在 Web 库存页使用“补全旧元件参数”批量查询，再同步回客户端，不能从型号编码猜测。操作步骤见[旧库存参数补全](docs/parameter-enrichment.md)。

主要工作流包括按 SKU 查找元件、入库与出库、库存流水、多库位分配与转移、嘉立创料号查询、BOM 匹配和缺料导出。Android 与 Windows 支持选择多个元件、复核数量后按库位一次批量转移，库存总量不变；Android 左滑直接展开入库、出库、转移、删除四个按钮，可对单个 SKU 快速操作；数量与库位在当前列表弹层内填写，删除仍需确认。客户端支持 Excel 备份与恢复，恢复前会预览冲突。活动元件的 SKU 唯一，库存数量和最低库存不能为负数。

同步遵循本地优先：原生客户端先写入自己的 SQLite，再按需推送和拉取。BOM 扣料是客户端本地事务；它没有跨设备共享的工程扣料账本。Web 库存写入默认关闭，启用后直接写入服务端 SQLite，并可同步回原生客户端。详见[架构与数据流](docs/architecture.md)和[接口集成](docs/integration-guide.md)。

项目 BOM 按“选择文件 → 库存匹配 → 确认出库”操作。嘉立创 EDA 导出的 `Supplier Part`、`Manufacturer Part`、`Quantity` 等列自动识别；需要修正时再展开带样例的列选择。没有料号的测试点等行保留待处理，可手选库存或明确跳过，并在确认前恢复。可选保存为本地项目预设，下次重新核对实时库存后使用。详见[嘉立创 BOM 导入与扣料教程](docs/bom-and-migration.md#嘉立创-eda-导出文件示例)。

Android 标签工具与元件标签快捷入口共用标签编辑与蓝牙打印页面：先选择标准元件、紧凑元件、元件纯文字、自由文字、自由二维码或混排模板，再选择常用或自定义纸张；新建旋转为 0°，恢复草稿保留已保存的角度。内建模板，可修改显示文字、拖动元素和数值微调，预览、导出与发送共用布局。自由标签不创建库存记录。普通流程移除独立诊断弹窗；操作与实机验证边界见 [M1 标签指南](docs/m1-printing-feature-matrix.md)。

### 账户与数据隔离

0.7.5 加入账户隔离：首次配置的密钥作为管理员账户，继续使用原有库存数据库；管理员可在 Web 设置中创建普通账户，普通账户使用各自的密钥和独立的库存数据库。客户端绑定已认证的服务器和账户身份后才上传本地数据，避免把原有库存推到其他账户。普通账户不能管理部署、日志、其他账户或 MQTT。操作步骤见[多用户账户教程](docs/accounts.md)，设计与迁移边界见[账户隔离实施计划](docs/account-isolation-plan.md)。安装包与镜像的发布状态以对应 [Release](https://github.com/Rafael-ban/Component_Manager/releases) 为准。

管理台使用 12 小时 HttpOnly 后端会话，账户密钥仅用于登录，不长期保存在浏览器。管理员可删除普通账户及其服务端数据库资料；客户端本地副本不会被远程清除，管理员原库不可删除。同步页面提供按设备登记、上传/拉取审计、冲突双方快照与人工处理记录。旧客户端未发送设备 ID 的拉取显示为未知设备；此前历史不会被补造。

Android 编辑元件支持从已有分类、库位选择，也可手动输入。记录页默认隐藏已删除元件的流水，打开“包含已删除元件”可追溯历史；不会抹掉已保存流水。

设置 → 关于显示作者、构建版本与正式版更新检查；不在应用内更新服务端。可按 [Watchtower 教程](docs/dockerhub.md#可选-watchtower-自动更新)选择配置容器自动更新，默认部署不启用。

## 快速部署 API 与管理 Web

将仓库中的 [`docker-compose.hub.yml`](docker-compose.hub.yml) 和 [`.env.example`](.env.example) 放进固定部署目录，复制 `.env.example` 为 `.env`。新部署让 `API_TOKEN`、`ADMIN_WEB_ORIGINS`、`ADMIN_WEB_URL` 和 `WEB_INVENTORY_ENABLED` 保持空值，再执行：

```sh
docker compose -f docker-compose.hub.yml pull api
docker compose -f docker-compose.hub.yml up -d api
```

打开 `http://服务器IP:8787/setup` 完成首次配置。需要完整管理 Web 时，再运行：

```sh
docker compose -f docker-compose.hub.yml --profile web pull
docker compose -f docker-compose.hub.yml --profile web up -d
```

管理 Web 默认位于 `http://服务器IP:8081/`，API 位于 `http://服务器IP:8787/`。Compose 默认拉取 API `latest` 与 Web `web-latest`；固定版本时应成对选用已发布的版本标签。升级前备份 `/data`，随后 `pull` 和 `up -d`；不要用 `down -v` 删除库存数据卷。群晖目录映射、首次配置、Token 查找与升级步骤见[Docker 快速部署](docs/docker-quickstart.md)，镜像发布与运维细节见[Docker Hub 指南](docs/dockerhub.md)和[运维手册](docs/runbook.md)。

客户端若提示“服务端版本过老”，先核对实际运行的 API 容器和 `/auth/me` 路由，详见 [Docker 排障](docs/docker-quickstart.md#7-客户端提示服务端版本过老或无法确认账户身份)。仅更新 APK、Web 容器或重启旧容器不能更新 API。

## 使用指南

| 任务 | 文档 |
| --- | --- |
| 创建普通用户、分发密钥、隔离库存及完整备份 | [多用户账户教程](docs/accounts.md) |
| BOM 预览、缺料导出、批量扣库及 component-hub 数据迁移 | [BOM 与迁移](docs/bom-and-migration.md) |
| 嘉立创包装扫码、连续采集及批量入库 | [批量入库](docs/batch-jlc-inbound.md) |
| 多库位、库存转移、Excel 备份与恢复 | [库位与备份](docs/storage-and-backup.md) |
| Android 蓝牙标签打印 | [M1 功能与操作](docs/m1-printing-feature-matrix.md)、[设备测试与兼容边界](docs/printer-compatibility.md) |
| 商品查询失败、扫码诊断及问题反馈 | [诊断与反馈](docs/feedback-and-scan-diagnostics.md) |
| MQTT 库存订阅和 Home Assistant | [MQTT 指南](docs/mqtt.md) |
| 安装更新、保留数据与发布文件 | [应用更新](docs/app-updates.md)、[更新日志](docs/CHANGELOG.md) |

## 开发与集成

仓库目录：[`android-client/`](android-client/) 是 Android 客户端，[`windows-client/`](windows-client/) 是 Windows 客户端，[`admin-web/`](admin-web/) 是管理 Web，[`server/`](server/) 是 API 服务。[`client/`](client/) 是旧 Flutter 参考实现，不是当前原生 UI 的开发入口。

服务端使用项目内的 `server/.venv`。在 Windows PowerShell 中执行：

```powershell
cd server
.\scripts\bootstrap.ps1
.\scripts\run-dev.ps1
.\.venv\Scripts\python.exe -m pytest
```

管理 Web 可在 `admin-web/` 执行 `npm install`、`npm run dev`、`npm run build`；Android 使用 `gradle -p android-client assembleRelease`；Windows 使用 `dotnet build windows-client\ComponentVault.WinUI\ComponentVault.WinUI.csproj`。平台前置条件、发布构建与排障步骤见[运维手册](docs/runbook.md)。

对接 API 时从[集成指南](docs/integration-guide.md)开始；理解本地写入、同步游标、冲突与账户数据库边界可读[架构说明](docs/architecture.md)。修改版本与发布流程前，请先看[更新日志](docs/CHANGELOG.md)及[版本维护说明](docs/runbook.md#versioning-workflow)。

## 项目与作者

作者：**Rafael-Ikaros**。源代码、问题反馈与发布文件位于 [Rafael-ban/Component_Manager](https://github.com/Rafael-ban/Component_Manager)。
