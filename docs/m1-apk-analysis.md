# 汉码 APK 与 M1 打印路径静态分析

## Dev.3 连续打印调查补充（2026-09-23）

Dev.2 四份报告在不同图像尺寸下均为 `stage=connected`、
`query_model=noresponse`、普通/异步回复均为 0、`print_tested=not_sent`。
这排除了第二张图像发送或排版阶段，尚不能单凭报告认定打印机固件故障。

本地 APK 证据：`BasePrintManager.connectBluetooth` 在已有打开的 port 和识别信息时
直接复用；`ESCPOLIPrinterManager.printBitmap` 每张结束仅调用 `setConnectState(0)`，
显式 `disConnect` 才调用 `PortClose`。该状态 setter 属于 SDK 的读写协调，不能当作
发给打印机的结束命令。原客户端每张 finally 关闭 socket 是明确的生命周期差异。

`BTOperator.writeData` 的 Classic SPP 路径使用默认 1024 字节 write/flush 分块。
该分块改变发送粒度，不改变 RFCOMM 字节流，不足以单独认定根因。
图像头（含压缩长度）及 `setPollForm(960)` 与现有实现逐字段一致。

Dev.3 据此保留页面内连接并加入打印后只读探测，仍需真实设备验证。
不通过跳过型号检查、自动补发图像或添加未经证实的初始化命令掩盖无响应。

核对日期：2026-09-22。本文记录用户提供的两个安装包中的可复核事实，
用于设计 Component Vault 的 M1 测试驱动。后续已据此接入主动 SPP 型号/状态查询，
操作见 [设备测试步骤](printer-compatibility.md)。仍没有直接打印功能；
静态分析不等于实机打印验收。

## 样本与方法

包名均为 `hprt.com.hmark.release`。版本以 `aapt2 dump badging` 读取的清单为准：

| 文件 | versionName | versionCode | SHA-256 |
| --- | --- | --- | --- |
| `125_abaded09cfa2980f7f21fe55e34800cc.apk` | `3.3.4-cn` | `300300407` | `e3d1ccb03b73037fcc0a52dd51bc25e9eb649be960bf67113ea55286798f494c` |
| `106_9dde2f3cf4858af649ca7b58bf4471fd.apk` | `3.4.6-cn` | `300400602` | `139ad6b4e8f10d12952775a1aceed58b03a4c6dcdc243b5d72c1c45a579ee173` |

其中没有清单版本为 `3.3.6` 的样本。文件来自用户；上述元数据和摘要用于区分研究样本，
不表示已验证其官方签名。分析未安装或运行 APK，也未加载其原生库。

采用 ZIP/JSON 读取、DEX 类索引、JADX 1.5.6 选择性反编译，以及原生库符号字符串检查。
APK、反编译源码及中间文件留在本机，未纳入 Git 或产品。本文只记录调用关系和协议事实。
部分大型方法存在反编译缺失或不可靠循环，因此不把反编译结果当作可以直接复制的实现。

## M1 配置与分派

两包的 `assets/devices.json` 中，精确名称 `M1` 都位于数组索引 27，整个 M1 配置一致。
不能用包含 `M1` 的模糊检索替代这个定位，否则容易混入 M11 等型号。

| 字段 | M1 值 | 解释边界 |
| --- | --- | --- |
| `model_id` / `model_name` | `315` / `M1` | 设备配置身份 |
| `print_mode` | `"5"` | `PrinterBean.getInstruct()` 映射为 `ESC_POLI` |
| `command_version` | `"3"` | `PrinterBean.isPoli()` 的能力判断依据；不是 manager 分派开关 |
| `dpi` / `print_head_width` | `"203"` / `"384"` | 配置中的分辨率与打印头宽度，仍需验证有效打印区域 |
| `is_compress` / `subcontract_size` | `1` / `6` | 实际 M1 bitmap 路径优先使用分包配置 |
| `paper_type` | `"1,2"` | 支持的纸张类型；不能推断一次打印选择了哪种纸 |
| `is_check` / `is_encipher` | `2` / `1` | 不能只根据字段名判断会话校验或负载加密行为 |

两版均可追踪以下调用关系：

```text
assets/devices.json: M1.print_mode = "5"
  → com.prt.print.data.bean.PrinterBean.getInstruct(): ESC_POLI
  → com.prt.base.common.DeviceInfo.updatePrintParams()
  → com.prt.print.utils.printer.PrintManagerFacade
  → com.prt.print.utils.printer.ESCPOLIPrinterManager.printBitmap()
  → HPRTAndroidSDK.HPRTPrinterHelper.printBitmapPackage(bitmap, 0, 3072)
```

分包参数为 `(subcontract_size / 2) * 1024`，M1 对应 3072。
这不是 BLE MTU，也不是“每个 GATT write 必须写 3072 字节”。
`subcontract > 0` 时不会进入普通 `printBitmap(..., compress, ...)` 分支。
两版普通分支对 `is_compress` 的转换存在差异，不能把所有型号的压缩逻辑视为相同。

## 位图与打印序列

以下底层字节格式已在 3.4.6 的 `HPRTPrinterHelper` 和 `PrinterDataCore` 中核对。
另对照了 3.3.4 的 `printBitmapPackage`、`SubcontractingLzo`、`lzoCompress` 和
`setPollForm`：分包决策、整行分块、压缩封包头与定位命令生成一致。
没有据此宣称两版的整个端口层或原生压缩库逐字节一致。

1. Manager 设置对齐方式。只有 `subcontract == 0` 时才先发送 `ESC @`，
   M1 默认 `subcontract=6`，不能把该初始化指令描述为这条路径的必发指令。
2. 每份打印前检查状态；M1 的 `locateFail` 为真时中止该次打印。
3. `printBitmapPackage` 生成逐行黑白位图，每行字节数为 `ceil(widthDots / 8)`，
   左侧像素在字节高位，黑色位为 1，行尾补齐至完整字节。
4. 原始位图超过 3072 字节时，按不超过该目标大小的完整行切块，分别 LZO 压缩。
   小于或等于阈值也走整图 LZO，不能因此假定小标签不压缩。
5. 每块具有以下 12 字节头，再接压缩负载：

```text
1D 76 30 30 | rowBytes:u16le | rows:u16le | compressedLength:u32le | compressedData
```

6. Manager 的 `isLabel=true` 分支调用 `setPollForm(960)`，对应 `1D 66 C0 03`；
   另一分支调用 `setPrintFeed(90)`，对应 `1B 1B 01 5A 00`。
   这些是上游调用参数，不能解释成“标签固定长 960 点”或直接替代项目现有尺寸。
7. 随后读取数据最多约 1000 ms；源码中的写入返回值或读等待结束，均不能单独证明出纸完成。

`lib/arm64-v8a/libLZO.so` 的字符串中可找到 `lzo1x_1_compress`、
`lzo1x_decompress_safe` 和 JNI 入口 `Java_LZO_1Compress_LZOCompress_lzoCompressData`。
这提供了 LZO1X 算法线索，但尚未通过原生调用跟踪或压缩样本往返验证确定完整契约。
后续需要选择许可适合本项目的独立实现，不能直接打包汉码的 `.so`。

SDK 另有 `1D 76 30 00` 加未压缩位图的路径，其行宽/行高格式与
[Epson 的 GS v 0 说明](https://download4.epson.biz/sec_pubs/pos/reference_en/escpos/gs_lv_0.html)
一致。**这不能证明 M1 默认接受未压缩测试，也不能将上述带长度字段的压缩帧交给通用 ESC/POS 驱动。**

## 状态与传输边界

`HPRTAndroidSDK.DataFilter` 识别 ASCII `pooli_sta=` 后的一个二进制状态字节。
`com.prt.print.data.bean.PrinterStatus(int)` 的位定义为：

| 掩码 | 源码含义 |
| --- | --- |
| `0x01` | 缺纸 |
| `0x02` / `0x04` | 温度过高 / 过低 |
| `0x08` | 低电量 |
| `0x10` | 开盖 |
| `0x20` | 低电压 |
| `0x40` | 定位失败 |

这是解析器证据，尚无该 M1 的对应回包样本。接收实现必须能处理一个状态帧跨多次通知、
多帧合并，以及打印过程中异步到达的状态；不能把一次蓝牙回调当作一条完整回复。

用户已有报告确认双模蓝牙和 FF00 服务：FF02 可写，FF01/FF03 可通知。
3.4.6 中进一步确认了交互连接链：

```text
BluetoothFragment → BluetoothService.BluetoothBinder.connectDevice
  → PrintManagerFacade.connectBluetooth → BasePrintManager.connectBluetooth
  → IsBLEType(false) → PortOpen("Bluetooth," + address) → BTOperator.OpenPort
  → createInsecureRfcommSocketToServiceRecord(SPP UUID)
```

SPP UUID 为 `00001101-0000-1000-8000-00805F9B34FB`，与用户缓存服务相符。
正常连接和 `connectBluetoothNoFailEvent` 都明确传入 `IsBLEType(false)`，
所以应优先验证 Classic RFCOMM。SDK 的 BLE 分支存在，但它不是这条已确认路径。
本项目 0.7.3 已在该台机器上建立 RFCOMM 连接并取得 M1 型号与状态回复。

`is_encipher` 被复制到 `DeviceInfo.encryption`，在本次检查的连接、manager 和端口写入路径中
未找到其参与负载加密。真正控制 SDK 可选握手/XOR 分支的是 `HPRTConst.isShack`，
其初始值为 `false`，已选类中没有发现赋值点；按此默认值，打开端口后不调用
`Check.ChackHands`，写入也直接走 RFCOMM 流。该结论限于静态检查的默认路径，
不排除未检查的初始化、其他版本或运行时配置改变行为。

连接后 `BasePrintManager` 查询名称、固件版本与序列号，再加载设备配置。
`HPRTPrinterHelper.getPrintName` 的非 MT 首选查询为：

```text
1B 1C + ASCII("& V1 getval \"printer_name\"\r\n")
```

这是 0.7.3 主动连接测试已验证的查询，不包含打印或设置指令。
随后可核对 SDK 的 `getPrintStatus` 查询 `1B 12 73`。
其主动回复与异步 `pooli_sta=` 不同，不能用同一帧头强行解析。
初次测试不需要采集设备序列号。实际 App 的型号配置还可从本地 `CloudDeviceInfo` 表加载，
本文的 M1 分包值是随包配置值，不能保证用户设备上的缓存配置永远一致。

## 实现与验收顺序

### 首张出纸后的补充（2026-09-23）

用户提供了上一 Dev 版的首张测试标签出纸照片，同时报告内容不完整、第二次点击无反应。
照片证明已发生实际打印，但不能单独判断纸张宽高、偏移或停止位置；第二次诊断报告尚待提供。

静态核对发现 `HPRTPrinterHelper.ReadDataMillisecond` 会经 `DataFilter.filter`，
把完整的 `pooli_sta=` 加一个状态字节从普通回复中剥离。原独立实现却对型号查询的
整段原始回复比较 `M1`，混入异步帧会导致误判。Dev.2 将此处改为会话内查询分流，
保留跨读取和跨查询的片段；这修复了已知解析缺陷，但不等同于确认了本次设备故障根因。

汉码纸张菜单的具体选项来自动态 `Param.hashList`，经 `BluetoothService.toSetPaperLearn`
与 `PrintManagerFacade` 分派。ESC_POLI 路径存在 page type 和 gap learn 调用，
但静态样本不足以确认当前 M1 的所有页型取值。MT 系列的等待/双查询路径不能套用给 M1。
Dev.2 只独立实现图像画布尺寸、旋转和偏移，不写设备页型、浓度、纸张学习或校准参数。

1. **已在用户 M1 上通过**：0.7.3 (26)、Android SDK 37 的报告确认 SPP 连接、
   两字节 M1 型号回复，以及 17 字节主动状态回复中的状态值 0。
   详见 [实机记录](printer-compatibility.md)。仍不推断剩余回复字段含义；
   如后续打印与静态路径不一致，再考虑汉码 HCI 记录。
2. 已用独立 `lzokay 1.1.8` 解码器验证 literal-only LZO1X 的边界和随机样本。
   Kotlin 实现按整行分块，每块原始数据不超过 3072 字节；标准解码验证不代表设备验收。
3. 先做用户主动触发的连接/状态测试，再开放单张固定测试标签；不把“写入成功”展示成“打印成功”。
4. 验证项目原有 40×10 mm、40×30 mm 等模板的实际尺寸、方向、二维码可扫描性，
   以及缺纸、开盖、断连、取消。203 dpi 的物理点数需单独换算，不能使用预览比例。
5. 实机通过后再接入正常标签打印。传输层、POLI 编码和模板渲染分别实现，便于扩展其他品牌。

无需为继续研究强制补交 3.3.6。当前两个样本已经提供实际 M1 分派与压缩路径，
剩余重点是会话行为和实机验证。HCI 采集方法见 [取证指南](m1-protocol-capture.md)。
