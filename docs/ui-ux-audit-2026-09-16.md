# 全端 UI / UX 审查与改进计划

日期：2026-09-16。范围：Android Compose、Windows WinUI 3、服务端配套的 React 管理台。

## 结论与证据边界

主要问题是功能归类、操作状态和返回路径不一致。继续添加等权重的大按钮会放大这些问题；应围绕“查库存 → 导入 / 出库 → 确认结果”组织日常操作，把库位维护、备份、迁移和连接配置归入设置。

本次使用 ui-ux-pro-max 的导航、可访问性、长列表、加载反馈以及 Compose / WinUI / shadcn 规则，结合代码审查和用户截图。规则是设计参考，不代表必须替换现有组件库。Android 保留 Material 3，Windows 保留 WinUI，管理台保留 shadcn/ui。

以下区分三类证据：

- **已确认**：源码可以直接确认的行为，或用户报告且代码能解释的问题。
- **待运行验证**：源码显示固定尺寸、缺少断点等风险，尚未通过实际窗口或设备复现。
- **能力缺口**：当前产品没有覆盖的工作流，不等同于现有功能故障。

覆盖了三端页面和主要入口；并未完成三端所有屏幕的截图、对比度测量、读屏器、不同 DPI 和真机相机验收。因此不提供虚假的视觉评分或“全部无障碍通过”结论。Android 此次新增的 4 项 Compose 交互回归已在 [CI 35000631592](https://github.com/Rafael-ban/Component_Manager/actions/runs/35000631592) 通过，关联提交为 757f77；该运行同时通过 Android 构建、Windows 构建与核心回归、服务端测试、管理台构建和版本检查。

## 覆盖清单

| 端 | 本次检查页面 / 流程 | 优先处理方向 |
| --- | --- | --- |
| Android | 主导航、库存 / 详情 / 编辑、变动、概览、设置 / 关于 / 反馈、单件查询、二维码 / OCR、标签预览、批量入库、BOM / 迁移、库位、Excel、启动失败恢复 | 精简入口；二级页面返回；长列表；真实处理进度 |
| Windows | MainWindow、Dashboard、Components / 详情 / 对话框、Movements、Batch、BOM、Settings / About / Backup / Feedback | 二级导航上下文；窗口适配；设置草稿语义；批量编辑密度 |
| 管理台 | 登录、Dashboard、Inventory、Sync、Settings / MQTT，以及对应 admin API | 可检索库存工作流；登录恢复；中文一致性；键盘和加载状态 |

`server/` 本身是 API 服务，没有另一套独立业务网页；本报告中的“服务端界面”指 `admin-web/`。

## 建议的信息架构

三端统一功能名称和任务归属，导航控件随平台变化。顶级目的地与临时操作分开：

```text
日常工作
  首页 / 概览
  库存 → 元件详情 → 入库、出库、标签
       → 导入与扫码 → 单件查询、批量嘉立创入库、手动录入
  变动记录
  项目 BOM → 匹配 → 缺料检查 → 确认扣减
设置
  库存与数据 → 库位管理、Excel 备份与恢复、数据迁移
  连接与同步 → 地址、测试连接、保存、同步状态
  应用 → 外观 / 语言、关于与更新、问题反馈
```

这是一套功能分组，不要求手机底栏塞入所有项目。Android 保持现有四个顶级目的地，BOM 通过导入入口进入；Windows 可以利用侧栏放置项目入口。管理台主要承担只读核对与服务配置，不复制客户端的入库写入流程。

当前 BOM 和 Component Hub 迁移共用页面，本次仍保留合并入口；未来拆分后再把迁移入口移入设置，避免只改名称却让用户找不到真实功能。

## Android：本次已经落实的修改

| 问题 | 处理 | 主要文件 |
| --- | --- | --- |
| “新增”菜单混合日常导入、维护和备份，七个按钮权重相同 | 改名“导入与扫码”；使用带说明的 ListItem，保留单件、批量、BOM / 迁移、手动四项；维护移至设置 | `ComponentVaultApp.kt`、`ComponentVaultShell.kt`、`InventoryScreen.kt`、`SettingsScreen.kt` |
| 批量数据过多后难以滚动和编辑 | 使用一个受约束的 LazyColumn；摘要默认折叠；确认操作固定在底部，列表为底栏留出空间 | `BatchJlcInboundScreen.kt` |
| 批量编辑字段可能超出对话框 | 编辑区域可滚动并处理输入法间距；状态筛选支持横向滚动 | `BatchJlcInboundScreen.kt` |
| 查询时缺少醒目的工作状态 | 查询超过短暂延迟后显示小型进度圈与文字；搜索按钮显示忙碌状态；批量解析显示计数进度和取消 | `JlcImportScreen.kt`、`BatchJlcInboundScreen.kt` |
| Excel / 库位等二级页按系统返回可能退出应用 | 页面消费 Back 并交回父页面；BOM 同样补齐；批量处理期间避免返回中断状态 | `InventoryBackupScreen.kt`、`StorageLocationsScreen.kt`、`BomImportScreen.kt` |

手动录入仍有价值：非立创元件、网络不可用、查不到型号时需要兜底。因此降低它的入口权重，不删除能力。相机等待用户对准二维码时不持续显示“正在处理”；进度指示用于相机初始化、OCR 和实际联网查询，避免让用户误以为程序卡住。

本次没有修改数据库结构、同步协议或库存加减规则。保留包装去重、私有草稿、批量确认和事务提交。

### Android 后续优先项

1. **P1，已确认：测试连接隐式保存草稿。** `SettingsScreen.kt` 的测试回调先执行 `saveDraft(false)`。建议测试使用当前输入的临时配置，仅“保存”持久化；失败测试不替换之前可用的地址。需与 Windows 同时统一语义。
2. **P1，架构风险：二级页面状态由多组布尔值维护。** `ComponentVaultApp.kt` 中分散的页面显示与返回逻辑容易遗漏处理。先集中整理目的地和返回规则，逐个迁移；不为这次局部修复重建整个导航框架。
3. **P2，待运行验证：库位页键盘和小高度布局。** `StorageLocationsScreen.kt` 把新增字段放在列表上方。后续改为标准 Scaffold，列表占用剩余空间，新增 / 编辑使用独立表单或对话框。
4. **P2，流程密度：单件导入承担多个任务。** `JlcImportScreen.kt` 同时承载输入、查询和资料确认。建议拆成清楚的三个阶段，参数与辅助识别折叠显示，始终突出数量、库位和确认入库。

## Windows：审查发现与建议，尚未实施

| 优先级 / 证据 | 发现 | 改进建议 | 代码入口 |
| --- | --- | --- | --- |
| P1 / 已确认 | 测试连接先调用 SaveSyncConfiguration，会持久化未验证输入 | 测试临时配置；明确显示保存结果，测试和保存分离 | `Views/SettingsView.xaml.cs` |
| P1 / 已确认的结构差异 | 主窗口禁用 Back；批量等二级工作流缺少统一的返回历史 | 为二级页提供标题、返回入口和父级上下文；按实际历史启用返回 | `MainWindow.xaml`、`MainWindow.xaml.cs` |
| P1 / 待运行验证 | Batch 待处理行横排多个编辑字段；窄窗口与高 DPI 容易拥挤 | 列表显示料号、数量、库位、状态；选中行后在侧栏或对话框编辑 | `Views/BatchJlcInboundView.xaml` |
| P1 / 待运行验证 | Dashboard 和 Movements 存在固定多列区域，适配策略不如库存页完整 | 增加 AdaptiveTrigger，统计卡从多列收为两列或单列 | `Views/DashboardView.xaml`、`Views/MovementsView.xaml` |
| P1 / 待运行验证 | 顶部同步入口未使用设置页同等的忙碌禁用绑定 | 全局共享同步状态，显示处理中；核验服务层是否已串行保护 | `MainWindow.xaml`、`Views/SettingsView.xaml` |
| P2 / 待运行验证 | 部分编辑对话框固定宽度，错误聚集在底部 | 宽度服从可用窗口；首个错误字段获得焦点并显示内联说明 | `Views/ComponentsView.xaml.cs` |
| P2 / 待运行验证 | BOM 动态匹配表单没有显式的滚动边界 | 先验证 ContentDialog 默认滚动行为，再按需要加受限滚动 / 虚拟列表 | `Views/BomView.xaml.cs` |
| P2 / 已确认 | 设置将连接、备份、反馈、更新放入长页 | 分为连接同步、库存数据、应用与关于，保留可直达入口 | `Views/SettingsView.xaml` |

批量页本身已有占剩余空间的列表，不能把 Android 的“列表不可滚动”直接认定为 Windows 同样存在。批量页打开时库存侧栏仍高亮属于正常父级归属，真正要补的是二级路径和返回行为。

## 管理台：审查发现与建议，尚未实施

| 优先级 / 证据 | 发现 | 改进建议 | 代码入口 |
| --- | --- | --- | --- |
| P1 / 能力缺口 | Inventory 显示最近更新记录，接口返回最近 12 条，无法核对任意 SKU | 保留最近活动在 Dashboard；库存页增加服务端搜索、分页、稳定排序和只读详情 | `pages/inventory-page.tsx`、`server/app/admin/data.py`、`api.py` |
| P1 / 能力缺口 | 库存页缺少通往具体元件资料的后续路径 | 从料号打开详情，展示现有接口可提供的字段；额外库存分配 / 历史按 API 能力补齐 | `App.tsx`、`pages/inventory-page.tsx` |
| P1 / 已确认 | 会话失效跳转登录，成功后回 Dashboard，不恢复原目的地 | 保存站内 returnTo；提示重新登录原因，登录后恢复页面 | `hooks/use-admin-resource.ts`、`components/routing/protected-route.tsx`、`pages/login-page.tsx` |
| P2 / 已确认 | 大部分页面英文、MQTT 局部中文 | 统一中文版术语，并保留集中式语言扩展入口 | `pages/*`、`components/mqtt-settings-card.tsx` |
| P2 / 待运行验证 | 库存和同步表格列多，主要依赖横向滚动 | 保留语义表格；为窄屏设置列优先级或简化摘要，验证键盘横向访问 | `components/ui/table.tsx`、`pages/inventory-page.tsx`、`pages/sync-page.tsx` |
| P2 / 已确认 | MQTT 自定义字段缺少完整错误关联语义 | label 与 input 绑定，补充 aria-invalid / aria-describedby，提交时聚焦首错 | `components/mqtt-settings-card.tsx` |
| P2 / 已确认 | 骨架加载及路由切换缺少完整辅助技术反馈 | 主区域提供忙碌语义、状态文字和路由标题焦点管理 | `components/layout/app-shell.tsx`、`pages/dashboard-page.tsx`、`pages/settings-page.tsx` |
| P2 / 产品选择 | 登录状态默认保存在 localStorage，没有会话期限偏好入口 | 根据部署场景明确“记住登录”语义，避免让共享设备用户猜测退出效果 | `hooks/use-auth.tsx`、`lib/storage.ts` |

当前库存卡明确写着“Recently updated inventory”，因此 12 条并不是悄悄丢掉全部库存的显示错误，而是完整库存浏览能力尚未提供。MQTT 清除密码已经要求明确勾选并保存，无需再机械增加重复确认；可以完善保存结果与需重启服务的说明。

## 设计规范和成熟参考

- **Android**：底栏负责顶级目的地，Bottom Sheet 负责少量临时选择；长任务用 Scaffold + 可滚动内容 + 固定主操作。参考 [Now in Android](https://github.com/android/nowinandroid)、[Compose Scaffold](https://developer.android.com/develop/ui/compose/components/scaffold) 与 [Compose Lists](https://developer.android.google.cn/develop/ui/compose/lists?hl=en)。
- **Windows**：NavigationView 提供稳定主导航，二级任务有返回；窄窗口重排而非压缩所有字段。参考 [WinUI Gallery](https://github.com/microsoft/WinUI-Gallery)。
- **管理台**：稳定侧栏 + 页标题与动作 + 查询区域 + 数据表 + 明确空态和失败重试。参考 [shadcn Data Table](https://ui.shadcn.com/docs/components/base/data-table) 与 [Sidebar](https://ui.shadcn.com/docs/components/base/sidebar)，仅借鉴交互组织，不按最新示例强行升级现有依赖。

跨端共享料号、型号、当前库存、入库数量、库位、已用数量等术语和数字语义。主操作保持一个明显的视觉重点；警告同时使用文字，不能仅靠颜色。Android 保持 48dp 触摸目标，桌面保留适合鼠标的密度。间距采用现有主题的 4 / 8 单位节奏，延续语义色，不在本次引入新品牌色或装饰性动画。

## 分阶段落地与验收

| 阶段 | 范围 / 接口 | 验收 | 回滚边界 |
| --- | --- | --- | --- |
| A，本次修复 | Android 导入菜单、Settings 回调、二级 BackHandler、批量布局、查询进度；无数据接口变化 | Compose 检查维护入口移出导入菜单、Excel / 库位系统返回不退出 Activity、100 个包装滚动到末项并进入编辑；GitHub CI 编译 | 独立 UI 提交，可回退而不影响数据库 |
| B，后续优先 | Android / Windows 设置草稿与保存语义；Windows 二级返回和窄窗口布局 | 失败测试不改已存配置；返回保持筛选 / 草稿；Windows 800 / 1280 宽与 100% / 150% / 200% DPI 实测 | 设置逻辑与布局分别提交，不变更同步 payload |
| C，后续能力 | 管理台只读库存搜索、分页、详情；服务端增量只读 API | 超过 12 条库存时可查任意 SKU；空结果 / 过期登录 / 网络失败 / 翻页一致性；更新 API 文档和对应测试 | 保留现有概览 API，新列表 API 独立部署与回退 |
| D，后续打磨 | 三端文字、键盘 / 读屏、字号与主题；Android 导入流程分段 | Android 小屏 / 横屏 / 大字号 / 深浅色，Windows 键盘与 DPI，Web 窄屏与键盘焦点；实际相机和输入法验证 | 每个工作流单独提交，避免一次改全端 |

阶段 B–D 是审查建议，不标记为已完成。本次不增加蓝牙打印范围。视觉和硬件验收中尚未执行的项目，不能用编译通过代替。
