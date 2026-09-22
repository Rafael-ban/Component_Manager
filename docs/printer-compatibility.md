# 蓝牙标签机：当前进度与测试

本轮是连接诊断阶段，尚未完成 M1 或其他品牌的直接出纸支持。
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

## 核实过的资料

| 来源 | 可借鉴内容 | M1 支持证据 |
| --- | --- | --- |
| [HPRT M1 官方页面](https://www.hprt.com/Product/Label-Maker-M1.html) | 203 dpi、蓝牙连接和产品资料入口 | 支持蓝牙；未声明具体传输协议 |
| [M1 专属 SDK 目录](https://download.hprt.com/hprt/files/product_down_file/model/290/classify/47.html) | 厂商 SDK 获取入口 | 2026-09-22 查询时没有可下载条目 |
| [niimblue](https://github.com/MultiMote/niimblue)（MIT） | NIIMBOT 的连接、协议与标签工作流 | 无汉印 M1 支持记录 |
| [phomymo](https://github.com/transcriptionstream/phomymo)（MIT） | Phomemo 的多协议与多机型适配 | 无汉印 M1 支持记录 |
| [phomemo-tools](https://github.com/vivier/phomemo-tools)（GPL-3.0） | Linux/CUPS 与部分 Phomemo 协议 | 无汉印 M1 支持记录 |

后续按具有明确证据的品牌协议逐个适配，不把“蓝牙打印”视为通用协议。
M1 需要进一步取得适用 SDK/协议资料或实机通信证据；取得厂商 SDK 后也需核对其分发许可。
不会把 HT300/HT330、ESC/POS、TSPL 或其他品牌命令直接当作 M1 指令发送。
Android 实现参考官方 [权限](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions)、
[扫描](https://developer.android.com/develop/connectivity/bluetooth/ble/find-ble-devices) 与
[GATT 连接](https://developer.android.com/develop/connectivity/bluetooth/ble/connect-gatt-server) 契约。
