# 0.5.2 之后的功能核对与后续更新建议

历史核对基线：2026-09-22，v0.6.0 / `389d4f5`。
用户所说的“0.52”按 0.5.2 理解。本文保留 0.6.0 时的核对结论，并记录其后已经完成的实现与仍需验收的边界。
建议版本号用于安排工作，不是发布时间承诺。

## 0.7.0 实施与验收（2026-09-22）

本轮功能与编译修复已合入 `master`。发布提交 `775dddb` 的 [完整 CI](https://github.com/Rafael-ban/Component_Manager/actions/runs/35722942268) 和 [发布工作流](https://github.com/Rafael-ban/Component_Manager/actions/runs/35722942557) 均已通过，包含 Android、Windows、服务端、Web 和容器检查。[GitHub Release](https://github.com/Rafael-ban/Component_Manager/releases/tag/v0.7.0) 中的签名 APK、自包含 Windows ZIP 与 Web ZIP 三个附件均已确认存在；API 与 Web Docker Hub 版本镜像也已核对。

- [x] 扩展确定的官方中英文分类映射，历史库存的显示、分组、搜索和筛选一致，不改用户原始数据。
- [x] Windows 内外网地址回退与旧设置迁移，并补充连接语义回归测试。
- [x] Android、Windows BOM 列映射和缺料 CSV 导出。
- [x] 默认关闭的 Web 写库存 API、独立库位创建、事务与持久化请求回执。
- [x] 响应式 Web 库存操作和同一 Docker Hub 仓库内的独立 Web 镜像构建配置。
- [x] 首轮 CI 的服务端、Web 和容器检查。
- [x] 完成 Windows 修复后的 CI，确认功能提交的所有检查成功，包含自包含 Windows 程序启动。
- [x] 完成按需展开表单的新布局复验：1440/375 px、中文库位、HTTP UUID 回退、创建元件、入库/出库、真实服务器提交后响应丢失并刷新恢复、409 冲突刷新、只读模式均通过，无浏览器异常或横向溢出。
- [x] 正式发布时 Docker Hub 凭据已就绪，API `0.7.0` 与 Web `web-0.7.0` 已上传，两个 tag 均为 active，包含 amd64/arm64。
- [x] 更新 0.7.0 版本与更新日志，发布交由 GitHub CI 构建和产物启动检查；真实设备的网络、蓝牙及触摸体验另行记录。

0.7.1 已落实后续资料比较，见 [打印方案与测试边界](printer-compatibility.md)。
已核对 NIIMBOT、Phomemo 和汉印官方 M1 资料；这些开源协议尚不能证明兼容 M1。
Android 增加只读连接诊断供用户测试，直接出纸仍未实现。后续需依据具体机型协议和实机证据，
以可扩展到多个品牌/型号的适配方式实施；汉印 M1 是首批验证设备之一，不是唯一目标。
旧 `.xls` 和跨设备 BOM 工程账本继续暂缓。

## 已经补齐

| 功能 | 当前状态与依据 |
| --- | --- |
| Android 导入入口、设置归类、长批次滚动、查询进度、返回行为 | 0.5.2 已改，0.5.3 继续统一库位、BOM/迁移和设置页面；见 [更新日志](CHANGELOG.md) 与 [实施报告](ui-consistency-implementation.md) |
| Windows 核心 UI、返回、连接草稿、窄窗布局 | 后续已补，原 0.5.2 记录中的 planned 是当时状态 |
| Windows 启动修复和自包含 ZIP | 0.5.4 已发布，不再发布 MSIX |
| 嘉立创型号/描述、超长名称同步、Android 内外网回退 | 0.5.4 已补 |
| Windows 内外网回退 | 0.7.0 已补：旧配置安全读取，每次同步先探测主地址，只在 DNS、连接或超时失败时回退；选定地址后整次 push/pull 固定使用，写入中断不换地址重放 |
| BOM 自动匹配、手动选择库存、列映射和缺料 CSV | 0.5.4 已有 CSV/XLSX、工作表选择、确认扣料和本地防重复；0.7.0 补齐 Android、Windows 任意列映射与缺料 CSV |
| 连续扫码间隔、声震反馈、标签 XLSX、按语言选择商城 | 0.6.0 已补 |
| MQTT 库存事件、独立库位/转移、Excel 恢复及迁移 | 已有对应模块，见 [MQTT](mqtt.md)、[库位与备份](storage-and-backup.md)、[BOM 与迁移](bom-and-migration.md) |
| 可选 Web 库存操作 | 0.7.0 已补：默认只读，可通过开关启用元件新增/编辑、入出库与库位创建；使用 CAS、持久化请求回执和短事务 |
| Web 响应式操作与容器部署 | 0.7.0 已补：手机/桌面界面、独立 Web 镜像及 Compose `web` profile；按需展开表单的新布局浏览器复验通过 |

## 仍未实现或仍需验收

| 项目 | 当前情况 | 后续动作 |
| --- | --- | --- |
| 0.7.0 CI 与发布验收 | 功能提交的完整 CI 和自包含 Windows 启动检查已通过 | 发布产物以 GitHub Release 为准；真实 Windows 网络环境后续实测 |
| Web 设备体验 | 新布局的九项浏览器业务场景、375/1440 px 布局和无横向溢出通过 | 真实手机触摸、输入法、完整读屏器流程仍需设备验收 |
| Docker Hub 实际发布 | 0.7.0 的 API 与 Web 镜像均已上传并通过公开 tag 元数据核验 | 直接按 [教程](dockerhub.md) 部署；以后发布继续检查对应版本标签 |
| 旧 `.xls` 文件 | CSV/XLSX 读取器不支持 | 暂缓；先另存为 `.xlsx`，有真实样本需求时再评估依赖 |
| 跨设备项目/BOM 扣料账本 | 本地 `bom_releases` 和提交防重复已有，服务器没有共享工程版本/扣料账本 | 暂缓；明确多端协作场景后再扩展同步协议 |
| 汉印 M1 蓝牙直打 | 有标签渲染、PNG/PDF/XLSX 导出，没有蓝牙传输适配或已验证的 M1 协议 | 按用户要求继续后置；协议和实机测试前无法可靠估算 |

## 阶段 A：Windows 连接能力（实现完成，待发布验收）

当前实现遵循以下语义：

1. 配置增加可选备用地址；旧配置缺字段时读取为空，不清空已有 Token、库存或同步状态。
2. 设置页显示首选地址、备用地址和实际使用地址；测试草稿不会自动保存。
3. 每次同步先探测首选地址，仅在 DNS、连接或超时失败后尝试备用地址；401、422、409 等 HTTP 业务响应不触发回退。
4. 选中后整次 push/pull 固定同一地址；已发出的写请求失败时不换地址重放。
5. 下次同步重新优先探测首选地址；取消会贯穿探测和同步请求。

实现涉及 Windows `Models/SyncConfiguration.cs`、`Services/SyncApiClient.cs`、
`Services/InventoryStore.cs`、`Views/SettingsView.xaml(.cs)`、同步结果与对应 CoreTests，
不需要服务端数据库迁移。

旧单地址配置、首选成功、主地址传输失败、两者失败、401 不回退、用户取消、push 中断不切站和下次重新优先主地址均已有回归覆盖。发布以最终 CI 与便携 ZIP 启动检查为依据，真实内外网部署后的网络表现仍需实测。

## 阶段 B：可选 Web 库存操作（实现与浏览器验收完成）

`WEB_INVENTORY_ENABLED` 已实现且默认 `false`。关闭时 Web 维持只读；启用后，保留现有 API Token 鉴权，并提供元件新增、资料编辑、入库、出库、库位列表与库位创建。资料编辑不直接改变数量或库位分配，数量变更统一经过 movement。

业务写入使用短事务，将元件、库位分配、流水、sync revision、MQTT outbox 与 `admin_operation_receipts` 一起提交。请求携带 `request_id` 与 `expected_updated_at`；相同请求重试返回当前元件状态，同一 ID 改变 operation、target 或 canonical payload 会返回冲突，事务回滚不留下回执。MQTT 沿用 outbox/QoS 1，消费者仍需按事件 ID/revision 去重。

已实现的管理接口包括：

- `GET` / `POST /admin-api/storage-locations`
- `POST /admin-api/components`
- `PUT /admin-api/components/{id}`
- `POST /admin-api/components/{id}/movements`
- `GET /admin-api/settings` 返回 `web_inventory_enabled`

后端测试覆盖默认禁写、相同与不同内容重试、并发版本、库存不足/溢出、多库位、legacy 库存、pull 可见、失败回滚、MQTT outbox 不重复和旧数据库初始化。第一轮 CI 中服务端、Web 与容器检查成功。

按需展开的新布局已完成浏览器复验，九项业务场景通过：中文库位；浏览器没有 `crypto.randomUUID` 时仍可生成请求 ID；元件创建；入库；出库；真实服务器已经提交写入但响应丢失后，reload 能恢复且不重复执行；409 后刷新；375 px 手机宽度无横向溢出；只读模式隐藏写入按钮。1440 px 与 375 px 截图已检查。验收也修复了详情刷新时操作组件被卸载、导致成功/错误提示立即消失的问题。

部署提供独立 Web 镜像和可选 Compose `web` profile。API 和 Web 使用同一个 `rafaelikaros/component_manager` 仓库，以普通版本标签和 `web-` 前缀标签区分；实际发布仍取决于 Docker Hub 凭据配置。

回退方式是关闭 `WEB_INVENTORY_ENABLED`，停止后续 Web 写入；已经完成的库存变动不会撤销。首版仍不包含 Web BOM、浏览器 OCR、离线写入或多用户权限。

## 阶段 C：硬件打印与按需增强

汉印 M1 先按已有标签尺寸完成一张可扫描标签的真实打印，再实现设备连接、权限、
断连恢复、打印队列、取消和错误提示。汉码 App 能打印、APK 未加密或设备有蓝牙地址，
都不能证明它兼容 ESC/POS、TSPL 或直接接收 PNG。Windows 蓝牙支持单独评估。

旧 XLS 和跨设备 BOM 账本按实际样本和协作需求安排，不捆绑进已经完成的软件阶段。

## 旧草稿与实机验收边界

本地 `.tmp/deferred-next-version/` 是上一轮收敛时保留的未发布草稿。Windows 回退和 Web 操作已经按当前架构逐模块移植与补强，草稿不再是实现依据，也不能整体恢复。草稿曾缺少当前事务控制、持久化 request_id 语义以及 0.6.0 后新增的 Windows 设置内容。

已有功能仍需按发布边界做实机验收，不应因此再次全量重做 UI：

- Android：连续扫描、声震、相机/图片导入、输入法、大字号和明暗主题。
- Windows：800/1280 宽度、100%/150%/200% DPI、真实网络环境和便携包启动。
- Web：按需展开布局的手机触摸、键盘焦点和完整读屏器流程。

历史发现见 [UI 审计](ui-ux-audit-2026-09-16.md)，修复状态以
[实施报告](ui-consistency-implementation.md)、当前代码和最新验收结果为准。
