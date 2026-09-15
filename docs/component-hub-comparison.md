# component-hub 对比与功能移植路线

分析日期：2026-09-15。

参考仓库：[ParkerNeol/component-hub](https://github.com/ParkerNeol/component-hub)。本报告固定源码版本为 `5d8d15266c549fdb0a7d2f003271e6c670035a70`，区分已实现代码与 README 的功能描述。未执行参考项目、未安装其依赖、未复制其实现。

## 总体区别

Component Vault 已有 Android Compose、Windows WinUI、各端 SQLite 与可选 FastAPI 同步，重点是离线扫码、现场库存操作和跨设备使用。component-hub 主要由 Vanilla JavaScript 页面及 Node 服务组成，业务集中在 `main.js`、`lcsc-import.js`、`server.js`，更偏向桌面浏览器中的表格整理、批量导入和库存检索。

| 维度 | Component Vault | component-hub | 取舍 |
|---|---|---|---|
| 客户端 | Android / Windows 原生客户端，独立管理网页 | 浏览器页面与 Node 服务 | 保留现有原生架构，移植业务能力 |
| 数据与同步 | 各端 SQLite、逐实体待同步队列、可选远端同步 | 浏览器 localStorage 即时副本，加 localhost JSON 整库覆盖镜像，无失败重试队列 | 不移植它的存储方式 |
| 扫码与离线 | CameraX、ML Kit、离线解析、OCR、标签 PNG/PDF、库存流水 | 本次未发现相机扫码实现；已有料号驱动的网页补全 | 当前原生扫码链路继续复用 |
| 批量表格 | Windows/Android CSV/XLSX BOM 匹配、缺料检查和事务批量出库 | SheetJS 读取 XLS/XLSX，映射料号、型号、数量并生成匹配候选 | 保留本地事务与出库确认 |
| 立创识别 | 原有本地规则、学习映射与可选服务端查询；本阶段新增 Android 直接公开页面查询 | Puppeteer 搜索国内商城，核对编号后提取 DOM 字段 | 借鉴编号校验和失败回退，避免在手机嵌入浏览器抓取服务 |
| 库位 | `location` 字符串、筛选与标签 | 字母前缀加数字的库位排序；未发现独立货架层级实体 | 后续可补自然排序与前缀建议 |
| MQTT | 当前不提供设备联动 | 通用 MQTT WebSocket 管理器，无元器件/库位业务调用点 | 不视为已实现的库位灯功能，不在本阶段引入 |

主要源码依据：

- [整库 localStorage 保存及后台镜像](https://github.com/ParkerNeol/component-hub/blob/5d8d15266c549fdb0a7d2f003271e6c670035a70/main.js#L3062-L3100)、[Node JSON 文件读写](https://github.com/ParkerNeol/component-hub/blob/5d8d15266c549fdb0a7d2f003271e6c670035a70/server.js#L324-L352)。服务监听 localhost，不能视为具有实体级冲突处理的多设备同步系统。
- [BOM 导入入口与匹配](https://github.com/ParkerNeol/component-hub/blob/5d8d15266c549fdb0a7d2f003271e6c670035a70/main.js#L5122-L5157)。
- [立创订单 XLS 行解析](https://github.com/ParkerNeol/component-hub/blob/5d8d15266c549fdb0a7d2f003271e6c670035a70/lcsc-import.js#L1774-L1830)。这不等于已实现通用 CSV 导入。
- [库位字符串排序](https://github.com/ParkerNeol/component-hub/blob/5d8d15266c549fdb0a7d2f003271e6c670035a70/main.js#L2174-L2212)。
- [公开商城网页抓取与编号核验](https://github.com/ParkerNeol/component-hub/blob/5d8d15266c549fdb0a7d2f003271e6c670035a70/server.js#L477-L610)。
- [批量抓取并发限制](https://github.com/ParkerNeol/component-hub/blob/5d8d15266c549fdb0a7d2f003271e6c670035a70/server.js#L835-L857)。
- [抓取失败后的手动搜索入口](https://github.com/ParkerNeol/component-hub/blob/5d8d15266c549fdb0a7d2f003271e6c670035a70/lcsc-import.js#L1943-L1985)。
- [通用 MQTT 客户端](https://github.com/ParkerNeol/component-hub/blob/5d8d15266c549fdb0a7d2f003271e6c670035a70/mqtt-manager.js#L14-L127)。包含 broker/topic 配置持久化，但没有库位到设备映射、command/ack 或库存业务联动。

参考仓库的 [LICENSE](https://github.com/ParkerNeol/component-hub/blob/5d8d15266c549fdb0a7d2f003271e6c670035a70/LICENSE) 标注 MIT。本阶段新增实现基于本项目架构和公开商品数据格式编写，没有引入参考仓库代码。

## C70565 直接联网查询的实际验证

`C70565` 是商品编号，国内 `item.szlcsc.com/<数字>.html` 中的数字是页面/商品内部 ID，不能简单去掉 C 后拼接。

本次国内搜索 `https://so.szlcsc.com/global.html?k=C70565` 返回 HTTP 203 的验证页面。没有尝试绕过验证。参考项目通过 Puppeteer 建立浏览器会话的路径不能保证在 Android 普通 HTTP 客户端上工作。

官方国际商城 [C70565 商品页](https://www.lcsc.com/product-detail/C70565.html) 使用与 Android 实现相同的 User-Agent 和 Accept 请求头时返回 HTTP 200，页面内公开 `Product` JSON-LD 包含：

- SKU：C70565。
- 厂商型号：X322512MOB4SI。
- 品牌：YXC Crystal Oscillators。
- 封装：SMD3225-4P。
- 商品描述包含晶体、12MHz、12pF 等参数。

因此“不申请 OpenAPI 密钥也能识别”已验证可行，但公开页面可访问性会变化，不能保证所有编号或所有网络都能自动补全。目前接入国际商城，描述可能是英文；国内商城直接查询仍作为后续独立适配需求。

本阶段实现链路：包装二维码或唯一 C 编号 → 本地解析/规则/学习映射 → Android 直接查询固定 HTTPS 商品页 → 精确 SKU 核验 → 补全名称、型号、品牌、封装 → 用户确认数量和仓位 → 本地入库。只发送编号，不发送订单号、包装原文、服务器 token。商城库存、价格不参与本地库存计算。

Android 默认启用直接查询，可在导入设置关闭；成功结果本地缓存七天，重复失败短暂退避。公开页不可用时仍可手动填写，或打开商品页。正常导入已移除服务端识别路径，服务端旧 API 只为旧客户端保留。

## 本阶段：双端 BOM 出库与迁移

用户追加确认 Windows 和 Android 同时实施，并要求单项目 BOM 批量出库、
商品图片和 component-hub 数据迁移。两端现在提供 CSV/XLSX 读取、工作表选择、
项目名/生产套数、匹配预览和确认扣库；领域解析与匹配放在独立 BOM 模块，
SQLite 事务留在各端库存存储层。缺料或冲突时整批回滚，本地批次标记防重复提交。
没有扩充同步协议；完整工程版本管理和跨设备幂等账本仍未实现。

迁移兼容 [浏览器导出对象](https://github.com/ParkerNeol/component-hub/blob/5d8d15266c549fdb0a7d2f003271e6c670035a70/main.js#L3735-L3743)
和服务端 `data/components.json` 的 `{components: [...]}` 结构。
规范料号字段是 `productCode`，库存为 `stock`，阈值为 `threshold`，图片为 `image`。
上游 [导入会直接追加并重生 ID](https://github.com/ParkerNeol/component-hub/blob/5d8d15266c549fdb0a7d2f003271e6c670035a70/main.js#L3789-L3824)，
允许重复料号；本项目默认报告冲突，用户可显式跳过，绝不默默合并或覆盖库存。

官方分类优先，已知英文目录映射中文，未知目录保留原文和来源。
截图中的 C49208388 已核实为国内商城“LED驱动”，国际页目录
`LED Drivers/LED Drivers ICs` 有对应中文映射和官方商品图片。
国内搜索仍有验证页限制，提供浏览器入口；自动抓取以已验证的国际页为基础。

格式、操作步骤、文件限制与兼容范围见 [BOM 与迁移指南](bom-and-migration.md)。
首版没有缺料 CSV 导出、任意表头映射编辑器、XLS reader、MQTT 设备联动或工程版本管理。
