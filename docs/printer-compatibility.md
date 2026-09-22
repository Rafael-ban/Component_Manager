# 蓝牙标签机：当前进度与测试

当前发布版提供连接诊断，尚未完成 M1 或其他品牌的直接出纸支持。
2026-09-22 已继续完成两份汉码 APK 的 M1 静态路径分析，详见
[M1 APK 分析记录](m1-apk-analysis.md)；它不改变发布版的打印能力。
Android 的入口为 **元件详情 → 标签预览 → 蓝牙标签机诊断**。
原有标签尺寸、PNG/PDF 和标签 Excel 导出不变。Windows 当前继续使用已有导出流程。

## 用测试 APK 验证

1. 安装本次 GitHub Release 的 Android APK，保留原应用数据。
2. 打开打印机，先关闭汉码等其他占用连接的 App。
3. 打开标签预览中的诊断入口，点击“扫描设备”，允许系统蓝牙权限。
4. 扫描持续 10 秒；列表包括附近 BLE 设备与系统已配对设备。Android 11 及以下
   还需要定位权限和开启定位。只支持经典蓝牙的设备需要先在系统设置中配对。
5. 选择自己的 M1。BLE 服务发现最长等待 15 秒；经典蓝牙仅列出系统缓存的服务。
6. 点击“复制诊断报告”，在应用反馈中粘贴，并手动填写打印机型号与是否找到设备。
   报告不包含设备名称、蓝牙地址、标签明文。请勿将其他设备的截图一起提交。

“已取得服务信息”仅说明传输诊断完成。此入口不写入特征值、不发送打印命令，
不会出纸。停止、关闭对话框或应用进入后台都会清理扫描与连接；可重新扫描。
服务 UUID 能帮助确定后续接入方式，仍不能单独证明打印协议兼容。

## M1 实机报告结论（2026-09-22）

用户通过 Component Vault 0.7.1 (24)、Android SDK 37 提交了成功的服务发现报告。
这是该台设备的实测结果，不自动推广到所有 M1 固件或其他型号。

| 报告字段 | 已确定的含义 | 尚未验证的内容 |
| --- | --- | --- |
| `status=Complete`、`probe=services_only` | App 已完成 BLE 连接和 GATT 服务发现 | 未执行打印或测试写入 |
| `device_type=3`、`paired=true` | Android 将设备识别为 Classic/BLE 双模，系统已配对 | 汉码实际打印使用哪一种连接 |
| `cached_service=00001101-…` | 缓存中存在 SPP 服务 UUID，可作为 RFCOMM 候选 | 本次诊断未建立 RFCOMM 连接 |
| `FF00 / FF02`、`properties=12` | `0x0C = WRITE + WRITE_NO_RESPONSE`，设备声明允许这两种写入 | 包格式、握手、分包、流控、最大负载 |
| `FF00 / FF01`、`properties=16` | 支持通知，可作为接收候选 | 通知订阅是否成功、通知数据的具体用途 |
| `FF00 / FF03`、`properties=16` | 同样只声明通知能力 | 不能将其当作另一条写入通道 |
| `1800`、`1801 / 2A05` | 标准 Generic Access / Generic Attribute 服务；`2A05` 的 32 是 Indicate | 这些不是标签打印命令或“打印完成”回执 |

属性位依据 Android 官方
[BluetoothGattCharacteristic](https://developer.android.com/reference/android/bluetooth/BluetoothGattCharacteristic)，
服务编号依据 Bluetooth SIG [Assigned Numbers](https://www.bluetooth.com/specifications/assigned-numbers/)。
当前实现不读取特征值、不枚举描述符，也未订阅通知，因此这份报告不包含打印协议字节。
`WRITE` 的蓝牙层响应也不能直接解释成“标签已打印”。

### 开源实现对照

- [fichero-printer 的 D11s 协议](https://github.com/0xMH/fichero-printer/blob/main/docs/PROTOCOL.md)
  也有 FF00 / FF02 写、FF01 和 FF03 通知，但其命令是在 **AiYin D11s** 上验证的，
  包含型号相关的启停和位图帧。相同 UUID 不能证明 M1 使用该协议。
- [labelife 的 TSPL 文档](https://github.com/thermal-label/labelife/blob/main/docs/protocol/tspl.md)
  将其部分型号的 FF03 用作写入通道，与本次 M1 报告不同；该页自身也标明了硬件验证限制。
  可作为对照资料，不能直接移植成 M1 驱动。
- [flutter-label-printer](https://github.com/gogovan/flutter-label-printer)
  曾验证 HPRT HM-A300L（CPCL）和 N41BT（TSPL），没有 M1 支持证据。
  汉印同品牌不同机型不能视为同一协议。
- 汉印官方 [HM-T260/T360 参数](https://www.hprt.com.cn/ChanPin/173.html)
  明确列出“汉码打印协议”，说明还需要检查厂商自己的协议路径；该页面不能证明 M1 同样使用它。

本轮未复制这些项目代码，也未引入其依赖。后续复用时应逐个核实许可证和适用机型。

### APK 分析后下一步需要的证据

用户已提供清单版本为 3.3.4-cn 和 3.4.6-cn 的汉码 APK。
两版随包 M1 配置均通过 `print_mode=5` 选择 `ESC_POLI`，实际调用 LZO 分包位图路径；
3.4.6 的交互连接流程明确使用 Classic SPP，和报告中的缓存服务相符。
下一步先验证 SPP 名称/状态查询，再验证单张标签；若与静态证据不符，
再采集汉码正常连接并打印测试标签时的 HCI 记录。无需为继续研究重复提交 APK。
安装包是否加壳与蓝牙负载是否加密是不同问题，不能从“APK 未加密”推出通信明文。
仅重复运行当前服务诊断不会提供缺失的命令字节。

采集步骤、Windows 导出命令和实机验收顺序见 [M1 协议取证指南](m1-protocol-capture.md)。

## 核实过的资料

| 来源 | 可借鉴内容 | M1 支持证据 |
| --- | --- | --- |
| [HPRT M1 官方页面](https://www.hprt.com/Product/Label-Maker-M1.html) | 203 dpi、蓝牙连接和产品资料入口 | 支持蓝牙；未声明具体传输协议 |
| [M1 专属 SDK 目录](https://download.hprt.com/hprt/files/product_down_file/model/290/classify/47.html) | 厂商 SDK 获取入口 | 2026-09-22 查询时没有可下载条目 |
| [汉码官方下载页](https://hm.hprt.com/download/)（来自[汉印 APP 目录](https://www.hprt.com.cn/APP/)） | Android App 获取入口 | 官方直链未确定；另已静态分析用户提供的 3.3.4-cn / 3.4.6-cn，见 APK 分析记录 |
| [niimblue](https://github.com/MultiMote/niimblue)（MIT） | NIIMBOT 的连接、协议与标签工作流 | 无汉印 M1 支持记录 |
| [phomymo](https://github.com/transcriptionstream/phomymo)（MIT） | Phomemo 的多协议与多机型适配 | 无汉印 M1 支持记录 |
| [phomemo-tools](https://github.com/vivier/phomemo-tools)（GPL-3.0） | Linux/CUPS 与部分 Phomemo 协议 | 无汉印 M1 支持记录 |

后续按具有明确证据的品牌协议逐个适配，不把“蓝牙打印”视为通用协议。
M1 已有静态协议线索，仍需实际会话和出纸验证；取得厂商 SDK 后也需核对其分发许可。
不能把 HT300/HT330、通用 ESC/POS、TSPL 或其他品牌驱动直接当作 M1 驱动。
Android 实现参考官方 [权限](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions)、
[扫描](https://developer.android.com/develop/connectivity/bluetooth/ble/find-ble-devices) 与
[GATT 连接](https://developer.android.com/develop/connectivity/bluetooth/ble/connect-gatt-server) 契约。
