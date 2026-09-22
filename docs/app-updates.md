# 设置 / 关于与联网更新

Android 和 Windows 的“设置 → 关于”显示应用名称、作者 Rafael-Ikaros、当前安装版本、
项目地址和 GPLv3 开源许可，并提供独立的“检查更新”区域。Android 从主导航的“设置”
进入，Windows 从侧栏底部的“设置”进入。检查不需要登录 GitHub、自建服务器或 API 密钥，
也不会向 GitHub 发送库存同步令牌。

## 用户操作

1. 打开“设置 → 关于”，选择“更新通道”，然后点击“检查更新”。默认是正式版。
2. 正式版只检查稳定 Release；Dev 测试版检查公开预发布和正式版，选择更高版本。
   例如 `0.7.4-dev.2` 高于 `0.7.4-dev.1`，但低于 `0.7.4` 正式版。
3. 发现更高版本时查看发布日期和更新日志，点击本平台下载按钮。
4. 系统浏览器下载后，由用户通过操作系统完成安装。应用不静默安装或替换自身。

已是最新、本地版本更高、没有 Release、限流、网络错误和缺少安装包会分别提示。
没有更新时仍可打开发布页查看。检查只在点击时进行，不增加后台周期检查。
发布说明作为纯文本显示，不执行其中的 HTML 或脚本。

通道选择保存在本机，不会修改库存或同步服务端。选择 Dev 不会立即安装，也不会换应用身份。
切回正式版后，如果当前 dev 高于最新正式版，应用不会自动降级；可继续使用当前版本，
等待同一版本或更高的正式版发布后再升级。不要通过卸载清数据来切换通道。
0.7.3 等旧版尚无通道选择，需要先从 GitHub 手动安装一次支持此设置的新版本。

## 发布来源与文件名

版本源固定为 [Rafael-ban/Component_Manager Releases](https://github.com/Rafael-ban/Component_Manager/releases)，
正式通道 API 为 `https://api.github.com/repos/Rafael-ban/Component_Manager/releases/latest`；
Dev 通道读取公开 Release 列表。
读取 `tag_name`、`published_at`、`body`、`html_url` 和 `assets`。本项目使用
`vMAJOR.MINOR.PATCH` 正式发布标签和 `vMAJOR.MINOR.PATCH-dev.N` 测试标签。
草稿及无法识别的版本不作为可安装更新；预发布只对主动选择 Dev 的用户显示。
API 的公开资源读取契约见 [GitHub 官方文档](https://docs.github.com/en/rest/releases/releases#get-the-latest-release)。

| 平台 | Release 资产 |
| --- | --- |
| Android | `component-vault-android-release.apk` |
| Windows 便携版 | `component-vault-windows-portable-x64.zip` |

这些名称与 `.github/workflows/release.yml` 一致。只有正确名称且属于同一仓库、
同一 tag 的 HTTPS GitHub 下载地址才会提供下载操作。缺失资产不会被源代码 ZIP
替代，也不会通过猜测地址伪装成可安装更新。

## 安装注意事项

Android 正式版和发布的 Dev APK 使用同一发布签名、保留相同应用身份。开发 Debug APK 与
正式 Release APK 的签名通常不同，不能直接覆盖；不要为更新随意卸载当前应用，
卸载可能清除本地库存。应用不会自动申请安装未知应用权限或自动卸载旧版。

Windows 发布自包含便携 ZIP，已停止发布 MSIX，不需要导入包签名证书。
先关闭程序再解压更新文件；覆盖程序文件不等于自动备份库存。

更新界面和解析逻辑可通过本地测试验证；实际下载、签名覆盖安装和设备数据保留仍需
使用正式 Release 在目标设备验证。本轮开发没有执行安装包下载或覆盖安装。
