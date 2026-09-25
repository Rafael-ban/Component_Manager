import { createContext, useContext, useEffect, useMemo, useState, type PropsWithChildren } from "react";
import { loadLanguagePreferenceFrom, resolveLocale, saveLanguagePreferenceFrom, type LanguagePreference, type Locale } from "./locale.ts";

export type { LanguagePreference, Locale } from "./locale.ts";
export { LANGUAGE_KEY } from "./locale.ts";

function readPreference(): LanguagePreference {
  return loadLanguagePreferenceFrom(() => localStorage);
}

export const en = {
  "概览": "Dashboard", "库存": "Inventory", "同步": "Sync", "设置": "Settings",
  "独立管理台": "Admin console", "库存默认只读，服务端启用后可操作；同时支持 MQTT 配置。": "Inventory is read-only by default. Server settings can enable editing and MQTT configuration.",
  "已连接 API": "Connected API", "令牌有效": "Token valid", "退出登录": "Sign out",
  "语言": "Language", "跟随系统": "Use system language", "简体中文": "Simplified Chinese", "English": "English",
  "Component Vault 管理台": "Component Vault Admin", "管理台提供的功能": "Admin console features", "服务端库存统计与低库存提醒": "Server inventory totals and low-stock alerts", "完整库存检索与同步记录核对": "Search inventory and review sync records", "运行配置与部署状态": "Runtime configuration and deployment status", "供下次服务重启使用的 MQTT Broker 配置": "MQTT broker settings for the next server restart", "推荐的本地配置": "Recommended local setup", "连接 API": "Connect to API", "需要重新登录": "Sign in again", "API 地址": "API address", "粘贴部署时设置的 API_TOKEN": "Paste the deployed API_TOKEN", "隐藏令牌": "Hide token", "显示令牌": "Show token", "已复制": "Copied", "复制令牌": "Copy token", "首次部署？打开服务端配置与日志": "First deployment? Open server setup and logs", "连接失败": "Connection failed", "正在验证令牌…": "Verifying token…", "进入管理台": "Enter admin console", "无法登录管理台。": "Could not sign in to the admin console.", "浏览器未允许复制，请显示令牌后手动复制。": "The browser blocked copying. Show the token and copy it manually.",
  "Dashboard": "Dashboard", "Server-side inventory overview for the current self-hosted sync node.": "Server-side inventory overview for the current self-hosted sync node.", "Dashboard unavailable": "Dashboard unavailable", "Retry": "Retry", "Active components": "Active components", "Tracked SKUs on the server": "Tracked SKUs on the server", "Units on hand": "Units on hand", "Sum of server-side quantities": "Sum of server-side quantities", "Low stock": "Low stock", "Components at or below min_stock": "Components at or below min_stock", "Movements": "Movements", "Recorded inventory transactions": "Recorded inventory transactions", "Recent components": "Recent components", "Latest active component rows visible to the sync service.": "Latest active component rows visible to the sync service.", "No component records are available yet.": "No component records are available yet.", "SKU": "SKU", "Name": "Name", "Category": "Category", "Status": "Status", "Updated": "Updated", "Operational notes": "Operational notes", "Deployment and sync behaviors surfaced for administrators.": "Deployment and sync behaviors surfaced for administrators.",
  "库存核对": "Inventory", "无法确认库存操作开关": "Could not check inventory access", "无法读取库位": "Could not load locations", "重试": "Retry", "料号、名称、分类或库位": "SKU, name, category, or location", "例如 C30926、连接器或 A-01": "e.g. C30926, connector, or A-01", "库存状态": "Stock status", "全部": "All", "低库存": "Low stock", "库存充足": "In stock", "搜索": "Search", "无法读取库存": "Could not load inventory", "元器件列表": "Components", "正在加载…": "Loading…", "没有符合条件的元器件。请调整搜索词或库存状态。": "No matching components. Change the search or stock filter.", "库存结果": "Inventory results", "分类": "Category", "库位": "Location", "库存 / 最低": "Stock / minimum", "更新时间": "Updated", "料号": "SKU", "名称": "Name", "正在加载库存": "Loading inventory", "上一页": "Previous", "下一页": "Next", "元器件详情": "Component details", "关闭详情": "Close details", "无法读取详情": "Could not load details", "封装": "Package", "默认库位": "Default location", "库存 / 最低库存": "Stock / minimum stock", "库存模式": "Inventory mode", "独立库位库存": "Per-location inventory", "旧版标量库存": "Legacy total inventory", "说明": "Description", "库位分配": "Location allocations", "未提供独立库位分配": "No per-location allocation", "正在加载详情…": "Loading details…",
  "Sync": "Sync", "Recent stock movement activity and read-only sync posture.": "Recent stock movement activity and read-only sync posture.", "Sync view unavailable": "Sync view unavailable", "Stored stock movement events": "Stored stock movement events", "Components": "Components", "Active synchronized component rows": "Active synchronized component rows", "Current summed quantity on the server": "Current summed quantity on the server", "Recent stock movements": "Recent stock movements", "Latest movement activity received by the sync API.": "Latest movement activity received by the sync API.", "No stock movement activity is available yet.": "No stock movement activity is available yet.", "Component": "Component", "Type": "Type", "Qty": "Qty", "Reason": "Reason", "Happened": "Happened", "Sync posture": "Sync posture", "Current assumptions applied by the server.": "Current assumptions applied by the server.", "Attention items": "Attention items", "Deployment-sensitive checks for the current node.": "Deployment-sensitive checks for the current node.", "Inbound": "Inbound", "Outbound": "Outbound", "Adjustment": "Adjustment", "Transfer": "Transfer",
  "Settings": "Settings", "当前登录使用的 API 令牌": "Current API token", "隐藏": "Hide", "显示": "Show", "打开服务端配置与日志": "Open server setup and logs", "Settings view unavailable": "Settings unavailable", "Runtime configuration": "Runtime configuration", "Access posture": "Access posture", "Next backend additions": "Planned backend additions",
  "当前为只读模式": "Read-only mode", "服务端未启用 Web 库存操作。你仍可搜索、筛选和查看库存详情。": "Server-side Web inventory editing is disabled. You can still search, filter, and view inventory.", "操作未完成": "Action incomplete", "操作完成": "Action completed", "新建元器件": "New component", "收起新建元器件": "Close new component", "创建库位": "Create location", "收起创建库位": "Close create location", "取消": "Cancel", "库位编码": "Location code", "库位名称": "Location name", "例如 A01 或 主仓-01": "e.g. A01 or Main-01", "例如 主货架 A-01": "e.g. Main shelf A-01", "正在创建…": "Creating…", "请先创建一个库位。新元件会以 0 库存建立初始分配。": "Create a location first. The new component starts with zero stock.", "最低库存": "Minimum stock", "初始库位": "Initial location", "说明（可选）": "Description (optional)", "创建元器件": "Create component", "有一笔结果未确认的库存操作": "An inventory action has an uncertain result", "请先恢复上一笔操作。系统会识别这次重试，不会重复增加或扣减库存。你也可以先刷新详情核对最新库存。": "Recover the previous action first. The retry will not duplicate stock changes. You can also refresh details to check current stock.", "恢复上次操作": "Recover previous action", "刷新库存详情": "Refresh stock details", "收起编辑": "Close editor", "编辑资料": "Edit details", "收起入出库": "Close movement", "办理入出库": "Record stock movement", "编辑": "Edit", "正在保存…": "Saving…", "保存资料": "Save details", "库存入出库": "Stock movement", "这是旧版库存记录，入出库会继续沿用当前默认库位。": "This legacy record uses its default location for stock movements.", "操作": "Action", "入库": "Inbound", "出库": "Outbound", "数量": "Quantity", "操作库位": "Movement location", "原因": "Reason", "备注（可选）": "Note (optional)", "总库存预计：": "Expected total: ", "请先处理上次操作": "Resolve previous action first", "确认": "Confirm ", "请选择库位": "Select a location", "库位已创建，可以用于新元件和入库。": "Location created. It can be used for new components and inbound stock.", "元器件资料已保存。": "Component details saved.", "请先恢复并确认上一笔库存操作，再发起新的入出库。": "Recover and confirm the previous movement before starting another.", "操作失败，请稍后重试。": "Action failed. Please try again.", "库存操作": "Stock movement", "登录已失效，请重新验证 API 令牌。": "Session expired. Verify the API token again.",
  "服务端配置": "Server configuration", "使用当前登录的 API Token 修改部署配置。非空环境变量接管的项目需在部署环境中修改。": "Change deployment settings using the current API token. Values set by environment variables must be changed in the deployment environment.", "无法读取服务端配置": "Could not load server configuration", "新 API Token 至少需要 16 个字符。": "New API token must have at least 16 characters.", "服务端配置已保存，当前浏览器已使用新 Token 登录。请更新其他客户端。": "Server settings saved. This browser uses the new token. Update other clients.", "服务端配置已保存并生效。": "Server settings saved and applied.", "服务端已保存新配置，但浏览器未能确认新 Token 登录。请用新 Token 重新登录后检查配置。": "Settings were saved, but this browser could not confirm the new token. Sign in with the new token and check settings.", "保存配置失败。请读取服务端当前配置后重试。": "Could not save settings. Reload server settings before retrying.", "由 API_TOKEN 环境变量控制": "Controlled by API_TOKEN environment variable", "留空以保留当前令牌": "Leave blank to keep the current token", "可留空；反向代理子路径会保留。此地址用于服务端配置页的管理台入口。": "May be blank. Reverse proxy subpaths are preserved. This URL provides the admin link on the server setup page.", "每行一个 origin，不含路径。独立部署的管理台需把实际浏览器来源填在这里。": "One origin per line, without a path. Enter the browser origin for a separately deployed admin console.", "允许已登录的 Web 管理台修改库存": "Allow signed-in Web inventory edits", "环境变量接管：": "Environment overrides: ", "保存中…": "Saving…", "保存服务端配置": "Save server settings",
  "MQTT 运行状态": "MQTT runtime status", "当前服务进程实际使用的连接状态。": "Connection state used by the running server process.", "手动刷新": "Refresh", "运行时启用": "Enabled at runtime", "是": "Yes", "否": "No", "加载中": "Loading", "Broker 连接": "Broker connection", "已连接": "Connected", "未连接": "Disconnected", "待发送事件": "Pending events", "最近成功发布": "Last successful publish", "暂无": "None yet", "运行错误：": "Runtime error: ", "MQTT 连接配置": "MQTT connection settings", "保存的配置会在下次重启服务时生效，保存不会立即连接 Broker。": "Saved settings take effect after the next server restart. Saving does not connect to the broker immediately.", "配置加载失败": "Could not load settings", "启用 MQTT 发布": "Enable MQTT publishing", "Broker 主机": "Broker host", "端口": "Port", "用户名": "Username", "密码": "Password", "留空以保留现有密码": "Leave blank to keep the current password", "未配置": "Not configured", "Topic 前缀": "Topic prefix", "使用 TLS": "Use TLS", "明确清除已保存密码": "Clear saved password", "来源：": "Source: ", "网页已保存配置": "Web-saved settings", "环境变量默认配置": "Environment defaults", "。密码不会显示在页面中。": ". Password is never shown on this page.", "等待服务重启": "Waiting for server restart", "已保存配置与当前运行配置不同，重启 FastAPI 服务后生效。": "Saved settings differ from runtime settings. Restart FastAPI to apply them.", "保存 MQTT 配置": "Save MQTT settings", "保存 MQTT 配置失败。": "Could not save MQTT settings.", "启用 MQTT 时必须填写 Broker 主机。": "Broker host is required when MQTT is enabled.", "端口必须是 1 到 65535 的整数。": "Port must be an integer from 1 to 65535.", "Topic 前缀不能为空，且不能包含 +、# 或控制字符。": "Topic prefix is required and cannot contain +, #, or control characters.", "Client ID 不能为空或包含控制字符。": "Client ID is required and cannot contain control characters.", "请填写域名、IP 或 IPv6 地址，不要填写 URL、路径或用户信息。": "Enter a hostname, IP, or IPv6 address without a URL, path, or user information.",
  "令牌验证失败。": "Token verification failed.", "无法连接 API。": "Could not connect to the API.", "登录会话已失效。": "Session expired.", "无法确认配置是否已保存。请重新登录并读取服务端配置后再决定是否重试。": "Could not confirm whether settings were saved. Sign in and reload server settings before retrying.", "网络请求未能确认结果。请保留当前表单并重试，或刷新库存核对最终状态。": "Network request result is uncertain. Keep the form and retry, or refresh inventory to check the final state.", "Web 库存操作尚未启用。": "Web inventory editing is disabled.", "目标已不存在，请刷新后重试。": "The item no longer exists. Refresh and retry.", "数据已被其他设备更新，请刷新后重新提交。": "Another device updated this item. Refresh and submit again.", "提交内容不完整或格式不正确，请检查表单。": "Check the form for missing or invalid values.", "Unable to load admin data.": "Unable to load admin data.",
  "请求失败，状态码": "Request failed, status ",
  "MQTT 配置已保存。": "MQTT settings saved.",
  "账户管理": "Accounts", "创建用户": "Create user", "普通用户": "User", "管理员": "Administrator", "账户已停用": "Account disabled", "账户已启用": "Account enabled", "启用账户": "Enable account", "停用账户": "Disable account", "重置密钥": "Rotate key", "确认重置密钥": "Confirm key rotation", "取消重置": "Cancel rotation", "重置后旧密钥会立即失效。": "The old key stops working immediately after rotation.", "新密钥仅显示一次，请立即复制并妥善保存。": "The new key is shown once. Copy and store it now.", "复制密钥": "Copy key", "关闭密钥提示": "Dismiss key", "保存名称": "Save name", "账户已创建。": "Account created.", "账户已更新。": "Account updated.", "密钥已重置。": "Key rotated.", "暂无普通用户账户。": "No user accounts yet.", "无法读取账户": "Could not load accounts", "账户操作失败。": "Account action failed.", "请输入用户名。": "Enter a user name.", "当前账户": "Current account", "仅管理员可修改服务端配置和管理账户。": "Only administrators can change server settings and manage accounts.", "此账户的库存由服务端独立保存。": "This account's inventory is stored separately on the server.",
  "当前会话使用账户密钥访问 API。": "This session uses an account key to access the API.", "使用管理员或用户密钥连接自托管同步服务。每个账户拥有独立库存；服务端启用 Web 库存操作后，可以创建库位和元件并办理入出库。": "Connect to the self-hosted sync service with an administrator or user key. Each account has separate inventory. When Web inventory editing is enabled, you can create locations and components and record stock movements.", "管理员可在账户管理中创建用户并获取其密钥。": "Administrators can create users and get their keys in Accounts.",
  "连接地址提示": "Connection address", "通常在 API 地址中填写服务器 IP 和 8787 端口；Web 管理台默认使用 8081 端口。": "Enter the server IP and port 8787 as the API address. The Web admin console uses port 8081 by default.", "验证账户密钥后，加载此账户的库存。": "Verify the account key to load this account's inventory.",
  "正在读取管理会话…": "Loading admin session…",
  "搜索服务器上的有效元器件。服务端开启 Web 库存操作后，可创建库位和元件，并执行可靠的入出库。": "Search active components on the server. When Web inventory editing is enabled, you can create locations and components and record stock movements.",
  "连接自托管 FastAPI 同步服务，使用与同步 API 相同的 Bearer 令牌核对库存与配置 MQTT。库存默认只读；服务端启用 Web 库存操作后，可创建库位、元件并办理入出库。": "Connect to the self-hosted FastAPI sync service with the same bearer token used by the sync API. Inventory is read-only by default; server settings can enable locations, components, and stock movements.",
  "开发时可在": "For development, run the API at ", "运行 API，并在": " and the admin console at ", "运行管理台。": ".",
  "进入管理台前，将通过": "Before entering, your token will be checked through ", "验证令牌。": ".",
  "API Token 由部署者设置。可在服务端配置页显示与复制当前令牌；也可查看 API 容器的": "The deployer sets the API token. Show or copy it on the server setup page, or check the API container's ", "环境变量、部署目录的": " environment variable, the deployment directory's ", "，或映射目录中": ", or the mapped directory's ", "的": " ", "。它不是": ". It is not ", "或": " or ", "。非部署者请联系管理员。": ". Contact the administrator if you do not manage the deployment.",
  "这是浏览器当前会话持有的值。服务端令牌来自": "This is the value held by the current browser session. The server token comes from the ", "环境变量或数据目录的": " environment variable or the data directory's ", "。配置页提供文件位置、有效配置和最近运行日志。": ". The setup page shows file locations, effective settings, and recent logs.",
  "Runtime configuration and access posture read from the same FastAPI process.": "Runtime configuration and access posture read from the same FastAPI process.", "Effective process settings that drive this sync service.": "Effective process settings that drive this sync service.", "Operational URLs and deployment-sensitive settings.": "Operational URLs and deployment-sensitive settings.", "Follow-up capabilities intentionally left outside this first separated admin release.": "Follow-up capabilities intentionally left outside this first separated admin release.",
  "服务端配置页": "Server setup page",
  "新 API Token": "New API token", "Web 管理台实际访问地址": "Admin console URL", "允许访问 API 的管理台来源": "Allowed admin origins",
  "浏览器无法保存恢复信息，请保持本页打开并使用原表单重试。": "The browser could not save recovery data. Keep this page open and retry with the same form.", "共": "Total ", " 项": " items", "第": "Page ", " 页": "", "已创建": "Created", "，初始库存为 0。": " with initial stock of 0.", "完成，当前库存": " complete. Current stock: ", "已通过原请求编号核对操作结果，当前库存": "Checked the original request result. Current stock: ", "。": ".",
} as const;

const zh: Partial<Record<keyof typeof en, string>> = {
  "Dashboard": "概览", "Server-side inventory overview for the current self-hosted sync node.": "当前自托管同步节点的服务端库存概览。", "Dashboard unavailable": "无法读取概览", "Retry": "重试", "Active components": "有效元器件", "Tracked SKUs on the server": "服务端跟踪的料号", "Units on hand": "现有库存", "Sum of server-side quantities": "服务端库存总量", "Low stock": "低库存", "Components at or below min_stock": "库存不高于最低值的元件", "Movements": "库存变动", "Recorded inventory transactions": "已记录的库存操作", "Recent components": "最近元件", "Latest active component rows visible to the sync service.": "同步服务可见的最新有效元件。", "No component records are available yet.": "尚无元器件记录。", "SKU": "料号", "Name": "名称", "Category": "分类", "Status": "状态", "Updated": "更新时间", "Operational notes": "运行说明", "Deployment and sync behaviors surfaced for administrators.": "供管理员查看的部署与同步行为。",
  "Sync": "同步", "Recent stock movement activity and read-only sync posture.": "最近库存变动与同步状态。", "Sync view unavailable": "无法读取同步页面", "Stored stock movement events": "已保存的库存变动记录", "Components": "元器件", "Active synchronized component rows": "已同步的有效元件", "Current summed quantity on the server": "当前服务端库存总量", "Recent stock movements": "最近库存变动", "Latest movement activity received by the sync API.": "同步 API 最近接收的变动。", "No stock movement activity is available yet.": "尚无库存变动记录。", "Component": "元器件", "Type": "类型", "Qty": "数量", "Reason": "原因", "Happened": "发生时间", "Sync posture": "同步状态", "Current assumptions applied by the server.": "当前服务端采用的同步规则。", "Attention items": "注意事项", "Deployment-sensitive checks for the current node.": "当前节点需要关注的部署检查。", "Inbound": "入库", "Outbound": "出库", "Adjustment": "调整", "Transfer": "转移",
  "Settings": "设置", "Settings view unavailable": "无法读取设置页面", "Runtime configuration": "运行配置", "Access posture": "访问配置", "Next backend additions": "后续后端功能", "Runtime configuration and access posture read from the same FastAPI process.": "从同一 FastAPI 进程读取运行配置与访问状态。", "Effective process settings that drive this sync service.": "当前同步服务实际使用的进程配置。", "Operational URLs and deployment-sensitive settings.": "运行地址及部署相关配置。", "Follow-up capabilities intentionally left outside this first separated admin release.": "首版独立管理台暂未包含的后续能力。", "Unable to load admin data.": "无法读取管理数据。",
};

export type MessageKey = keyof typeof en;
export type Translator = (key: MessageKey) => string;

interface I18nContextValue { preference: LanguagePreference; locale: Locale; setPreference: (value: LanguagePreference) => void; t: Translator }
const I18nContext = createContext<I18nContextValue | null>(null);

export function I18nProvider({ children }: PropsWithChildren) {
  const [preference, setPreference] = useState<LanguagePreference>(readPreference);
  const locale = resolveLocale(preference, navigator.language);
  useEffect(() => {
    document.documentElement.lang = locale;
    document.title = locale === "zh-CN" ? "Component Vault 管理台" : "Component Vault Admin";
  }, [locale]);
  const value = useMemo<I18nContextValue>(() => ({
    preference, locale,
    setPreference(next) {
      setPreference(next);
      saveLanguagePreferenceFrom(() => localStorage, next);
    },
    t: (key) => locale === "en" ? en[key] : zh[key] ?? key,
  }), [locale, preference]);
  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}

export function useI18n() {
  const value = useContext(I18nContext);
  if (!value) throw new Error("useI18n must be used within I18nProvider");
  return value;
}

export function currentTranslation(key: MessageKey): string {
  return resolveLocale(readPreference(), navigator.language) === "en" ? en[key] : zh[key] ?? key;
}

export function formatCount(value: number, locale: Locale): string { return new Intl.NumberFormat(locale).format(value); }

// Only translate known server-authored UI metadata. Inventory names, SKUs, reasons and paths remain unchanged.
const serverZh: Record<string, string> = {
  "Database file has not been created yet.": "数据库文件尚未创建。",
  "Run the API once or push data from a client to populate records.": "运行 API 或从客户端推送数据以创建记录。",
  "Managed inventory uses base_updated_at conflict checks; legacy snapshots use last-write-wins.": "库位库存使用 base_updated_at 检查冲突；旧版快照采用最后写入胜出。",
  "Device registry is not implemented yet; sync visibility is derived from server-side inventory state.": "设备注册表尚未实现；同步状态由服务端库存决定。",
  "API token is still the default value. Replace it before deployment.": "API Token 仍为默认值，请在部署前更换。",
  "Custom API token is configured for authenticated routes.": "已为需认证的接口配置自定义 API Token。",
  "Authenticated web inventory writes are enabled.": "已启用经过认证的 Web 库存写入。",
  "Web inventory writes are disabled; clients own inventory writes.": "Web 库存写入已禁用；由客户端写入库存。",
  "Sync visibility is derived from the latest accepted server-side rows.": "同步状态来自服务端最近接受的记录。",
  "Conflict mode": "冲突模式", "Last write wins": "最后写入胜出", "Soft delete": "软删除", "Enabled": "已启用", "Disabled": "已禁用", "Device registry": "设备注册表", "Not implemented": "尚未实现", "Authenticated routes": "需认证的接口",
  "App name": "应用名称", "Host": "主机", "Port": "端口", "Database path": "数据库路径", "Admin web origins": "管理台来源", "API token status": "API Token 状态", "Default token in use": "使用默认令牌", "Custom token configured": "已配置自定义令牌", "LCSC lookup status": "立创查询状态", "Configured": "已配置", "Not configured": "未配置", "Recognition rules remote URL": "远程识别规则地址", "Web fallback resolvers": "Web 备用解析器", "Health URL": "健康检查地址", "Auth URL": "认证地址", "Admin API": "管理 API", "Sync API": "同步 API", "Token risk": "令牌状态", "Replace before deployment": "部署前更换", "Custom token present": "已配置自定义令牌",
  "Per-device sync audit log": "按设备记录的同步审计日志", "Conflict history and resolution records": "冲突历史与解决记录", "Backend session auth for the web admin": "Web 管理台后端会话认证",
  "active": "有效", "inactive": "停用", "deleted": "已删除", "Low stock": "低库存", "Healthy": "库存充足",
  "Account name is required.": "请输入账户名称。", "Account name already exists.": "账户名称已存在。", "Account not found.": "账户不存在。", "Specify name or active.": "请输入名称或启用状态。",
  "Administrator account required.": "需要管理员账户。",
};

export function translateServerText(value: string, locale: Locale): string {
  if (locale !== "zh-CN") return value;
  const health = /^API health endpoint remains available at (.+)\.$/.exec(value);
  if (health) return `API 健康检查地址：${health[1]}。`;
  return serverZh[value] ?? value;
}

export function currentServerTranslation(value: string): string {
  return translateServerText(value, resolveLocale(readPreference(), navigator.language));
}
