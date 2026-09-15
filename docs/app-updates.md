# 设置 / 关于与联网更新

Android 和 Windows 的“设置 → 关于”显示应用名称、当前安装版本、项目地址和
GPLv3 开源许可，并提供“检查更新”。检查不需要登录 GitHub、自建服务器或 API 密钥，
也不会向 GitHub 发送库存同步令牌。

## 用户操作

1. 打开“设置 → 关于”，点击“检查更新”。
2. 应用请求本项目最新公开正式 Release，比较数字版本，例如 `0.3.10` 大于
   `0.3.9`。Windows 的安装版本 `0.3.10.0` 与发布版本 `0.3.10` 等价。
3. 发现更高版本时查看发布日期和更新日志，点击本平台下载按钮。
4. 系统浏览器下载后，由用户通过操作系统完成安装。应用不静默安装或替换自身。

已是最新、本地版本更高、没有 Release、限流、网络错误和缺少安装包会分别提示。
没有更新时仍可打开发布页查看。检查只在点击时进行，不增加后台周期检查。
发布说明作为纯文本显示，不执行其中的 HTML 或脚本。

## 发布来源与文件名

版本源固定为 [Rafael-ban/Component_Manager Releases](https://github.com/Rafael-ban/Component_Manager/releases)，
API 为 `https://api.github.com/repos/Rafael-ban/Component_Manager/releases/latest`。
读取 `tag_name`、`published_at`、`body`、`html_url` 和 `assets`。本项目使用
`vMAJOR.MINOR.PATCH` 正式发布标签；draft、prerelease 和无法识别的标签不作为可安装更新。
API 的公开资源读取契约见 [GitHub 官方文档](https://docs.github.com/en/rest/releases/releases#get-the-latest-release)。

| 平台 | Release 资产 |
| --- | --- |
| Android | `component-vault-android-release.apk` |
| Windows MSIX | `component-vault-windows-x64.msix` |
| Windows 便携版 | `component-vault-windows-portable-x64.zip` |

这些名称与 `.github/workflows/release.yml` 一致。只有正确名称且属于同一仓库、
同一 tag 的 HTTPS GitHub 下载地址才会提供下载操作。缺失资产不会被源代码 ZIP
替代，也不会通过猜测地址伪装成可安装更新。

## 安装注意事项

Android 正式版需要使用与已安装版本一致的签名才能覆盖安装。开发 Debug APK 与
正式 Release APK 的签名通常不同，不能直接覆盖；不要为更新随意卸载当前应用，
卸载可能清除本地库存。应用不会自动申请安装未知应用权限或自动卸载旧版。

Windows 请继续使用当前的安装方式。便携版需关闭程序后手动更新文件；MSIX 安装由
Windows 处理，应用不会自动导入签名证书。当前发布流程包含测试签名证书，更新是否能
直接覆盖取决于包身份和证书信任；遇到阻止时查看 Release 附带说明。不要用更换安装方式
来代替数据迁移，也不要将覆盖程序文件理解为自动备份库存。

更新界面和解析逻辑可通过本地测试验证；实际下载、签名覆盖安装和设备数据保留仍需
使用正式 Release 在目标设备验证。本轮开发没有执行安装包下载或覆盖安装。
