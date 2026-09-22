# M1 协议取证与下一阶段验证

本指南用于用户自己的汉印 M1。当前 App 只完成服务发现，没有直接打印功能。
已有诊断确认 FF02 可写、FF01/FF03 可通知；随后静态分析用户提供的 APK，
已定位 M1 的 ESC_POLI / LZO 位图路径及 3.4.6 的 Classic SPP 连接流程。
详见 [M1 APK 分析记录](m1-apk-analysis.md)。这些命令仍未经过本项目实机出纸验证。

## 路线一：SDK 或汉码安装包

向汉印索取资料时，可以使用以下需求描述；这段文字是咨询模板，项目不会自动发送：

> 需要将汉印 M1 标签打印机接入 Android 原生应用。请提供明确支持 M1 的 Android
> SDK/API 文档、支持型号及固件范围、BLE/SPP 连接说明、位图打印和状态回读示例，
> 以及 SDK 随开源应用分发的许可条件。现有实测是双模蓝牙，BLE 服务 FF00，
> FF02 支持 Write/Write Without Response，FF01 和 FF03 支持 Notify。

也可以先导出自己手机中能正常打印的汉码 APK。APK 供静态检查包结构、M1 型号分派、
握手及编码路径；不会自动安装到电脑或手机，也不会复制整个厂商 App 到本项目。
当前已知包名为 `hprt.com.hmark.release`，用户截图版本为 `3.6.4-cn`；
后续交付的两个 APK 清单实际为 `3.3.4-cn` 与 `3.4.6-cn`，其中没有 `3.3.6`。
这两个文件已完成选择性静态分析，无需再次导出。下面的命令仅供后续需要
对照其他版本或实机通信与现有样本不一致时使用。
官方 Android 入口位于[汉码下载页](https://hm.hprt.com/download/)，但本轮没有取得
可验证的安装包直链和版本信息；优先使用手机中已能驱动该 M1 的版本，不要求为调研降级。

Windows 导出方式：下载官方 [Android Platform-Tools](https://developer.android.com/tools/releases/platform-tools)，
解压后在该目录打开 PowerShell；手机开启 USB 调试并通过 USB 连接电脑，确认手机上的调试授权。

```powershell
.\adb.exe devices
.\adb.exe shell pm path hprt.com.hmark.release
```

第二条命令会返回 `package:/data/app/.../base.apk`。去掉 `package:` 前缀，
把实际路径复制到下一条命令中；不要直接使用下面的占位路径：

```powershell
.\adb.exe pull "/data/app/实际返回的目录/base.apk" ".\hanma-base.apk"
```

如果返回多个 `package:` 路径，也需要导出相应 split APK。若返回空结果，先确认手机上
安装的 App 包名和当前用户空间，不需要卸载 App、清空数据或降级。
相关命令见 Android 官方 [ADB 文档](https://developer.android.com/tools/adb)。

## 路线二：一次受控的蓝牙通信记录

仅在需要确认实际数据流时采集。Component Vault 的普通日志无法记录另一个 App 的
蓝牙负载，需要 Android 系统的 Bluetooth HCI snoop。

1. 关闭 Component Vault 的蓝牙诊断以及其他占用打印机的 App。
2. 手机开发者选项中启用“蓝牙 HCI 信息收集日志 / Bluetooth HCI snoop log”；
   若有模式选择，选完整/启用模式。关闭再开启蓝牙，使设置生效。
3. 打开汉码连接 M1，进入设备信息页一次，记下 App 版本、打印机固件版本和时间。
4. 选择与实际已装纸匹配的项目现有标签尺寸，打印一份只含 `M1-TEST-A` 的测试标签。
   再把文字改为 `M1-TEST-B`，打印一份，分别记录点击时间和是否出纸。
   不使用真实订单、元件明细或个人资料作为测试标签。
5. 立即通过手机开发者选项“提交错误报告”，或在电脑执行以下命令生成报告：

```powershell
.\adb.exe bugreport .
```

6. 完成后关闭 HCI 日志，并重启蓝牙。

Android 官方说明见 [Bluetooth 调试](https://source.android.com/docs/core/connect/bluetooth/verifying_debugging)
和 [生成 bug report](https://developer.android.com/studio/debug/bug-report)。
不同手机系统可能省略或截短蓝牙负载，不能保证每个 bug report 都包含可分析的完整数据。
仅有默认的内存蓝牙摘要也可能缺少负载。

## 交付哪些文件

APK 只需安装包，不需要汉码的账户数据。HCI 文件则可能包含设备地址和通信内容，
完整 bug report 还可能包含其他 App 日志；**不要把原始 bug report 直接发到公开 GitHub Issue**。
先保留原文件在自己电脑，优先定位 `btsnoop_hci.log` 或同类 snoop 文件。
Android 官方的 `btsnooz.py` 也能从部分报告的文本中提取嵌入的蓝牙记录，
但无法恢复未采集或被过滤的数据。可以先提供压缩包内的相关文件名，确认后再提供最小样本。
原始日志不提交到本仓库；只记录去标识的协议结论和人工构造的测试向量。

## 拿到证据后的实现与验收

1. 确认汉码实际走 BLE ATT 还是 Classic RFCOMM，识别会话初始化、通知订阅和握手。
2. 定位图像数据格式、宽高单位、分包和回执规则，区分传输接收成功与实际打印完成。
3. 优先用有依据的状态查询验证通信，然后只打印一张固定测试标签。
4. 验证现有二维码和文字模板的尺寸、可扫描性、走纸方向，以及缺纸、断连和取消行为。
   连接在发送中断开时不能盲目重发整个任务，需提示打印结果未确认，避免重复出纸。
5. 通过实机反馈后再开放正常标签打印，并将传输层与品牌协议分开，便于后续多品牌适配。

M1 官网标注 203 dpi，但最终位图尺寸和最大打印宽度仍要依据协议与纸张验证。
项目 `ComponentLabelRenderer` 的 `PreviewUnitsPerMm = 36f` 是预览比例，不能直接当作打印点密度。
此阶段保留现有模板的物理尺寸，不新增另一套标签规格。
