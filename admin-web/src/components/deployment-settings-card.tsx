import { useEffect, useState } from "react";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { useAdminResource } from "@/hooks/use-admin-resource";
import { useAuth } from "@/hooks/use-auth";
import { ApiError, saveDeploymentConfiguration } from "@/lib/api";
import type { DeploymentConfiguration } from "@/lib/types";

export function DeploymentSettingsCard() {
  const resource = useAdminResource<DeploymentConfiguration>("/setup/config");
  const { login, logout, session } = useAuth();
  const [form, setForm] = useState<DeploymentConfiguration | null>(null);
  const [origins, setOrigins] = useState("");
  const [newToken, setNewToken] = useState("");
  const [dirty, setDirty] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    if (!resource.data || dirty) return;
    setForm(resource.data);
    setOrigins(resource.data.admin_web_origins.join("\n"));
  }, [resource.data, dirty]);

  const locked = (name: string) => form?.environment_overrides.includes(name) ?? false;
  const setField = <K extends keyof DeploymentConfiguration,>(key: K, value: DeploymentConfiguration[K]) => {
    setForm((current) => current ? { ...current, [key]: value } : null);
    setDirty(true);
    setNotice(null);
  };

  const save = async () => {
    if (!session || !form || saving) return;
    if (newToken && newToken.length < 16) {
      setError("新 API Token 至少需要 16 个字符。");
      return;
    }
    setSaving(true);
    setError(null);
    setNotice(null);
    let saved = false;
    try {
      const next = await saveDeploymentConfiguration(session, {
        api_token: newToken,
        admin_web_origins: origins.split(/[\n,]+/).map((value) => value.trim()).filter(Boolean),
        admin_web_url: form.admin_web_url.trim(),
        web_inventory_enabled: form.web_inventory_enabled,
      });
      saved = true;
      if (newToken) await login(session.apiBaseUrl, newToken);
      setForm(next);
      setOrigins(next.admin_web_origins.join("\n"));
      setNewToken("");
      setDirty(false);
      setNotice(newToken ? "服务端配置已保存，当前浏览器已使用新 Token 登录。请更新其他客户端。" : "服务端配置已保存并生效。");
    } catch (caught) {
      if (saved) {
        setError("服务端已保存新配置，但浏览器未能确认新 Token 登录。请用新 Token 重新登录后检查配置。");
        return;
      }
      if (caught instanceof ApiError && caught.status === 401) {
        logout();
        return;
      }
      setError(caught instanceof Error ? caught.message : "保存配置失败。请读取服务端当前配置后重试。");
    } finally {
      setSaving(false);
    }
  };

  return <Card className="bg-white/90">
    <CardHeader>
      <CardTitle>服务端配置</CardTitle>
      <CardDescription>使用当前登录的 API Token 修改部署配置。非空环境变量接管的项目需在部署环境中修改。</CardDescription>
    </CardHeader>
    <CardContent className="space-y-5">
      {resource.error ? <Alert variant="destructive"><AlertTitle>无法读取服务端配置</AlertTitle><AlertDescription>{resource.error} <Button size="sm" variant="outline" onClick={() => void resource.reload()}>重试</Button></AlertDescription></Alert> : null}
      {form ? <form className="space-y-5" onSubmit={(event) => { event.preventDefault(); void save(); }}>
        <label className="block space-y-2 text-sm font-medium">新 API Token
          <Input type="password" autoComplete="new-password" value={newToken} minLength={16} disabled={saving || locked("API_TOKEN")} placeholder={locked("API_TOKEN") ? "由 API_TOKEN 环境变量控制" : "留空以保留当前令牌"} onChange={(event) => { setNewToken(event.target.value); setDirty(true); }} />
        </label>
        <label className="block space-y-2 text-sm font-medium">Web 管理台实际访问地址
          <Input type="url" value={form.admin_web_url} disabled={saving || locked("ADMIN_WEB_URL")} placeholder="https://example.com/admin/" onChange={(event) => setField("admin_web_url", event.target.value)} />
          <span className="block text-xs font-normal text-slate-600">可留空；反向代理子路径会保留。此地址用于服务端配置页的管理台入口。</span>
        </label>
        <label className="block space-y-2 text-sm font-medium">允许访问 API 的管理台来源
          <textarea className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm" rows={3} value={origins} disabled={saving || locked("ADMIN_WEB_ORIGINS")} placeholder="https://example.com" onChange={(event) => { setOrigins(event.target.value); setDirty(true); }} />
          <span className="block text-xs font-normal text-slate-600">每行一个 origin，不含路径。独立部署的管理台需把实际浏览器来源填在这里。</span>
        </label>
        <label className="flex min-h-11 items-center gap-3 text-sm font-medium"><input type="checkbox" checked={form.web_inventory_enabled} disabled={saving || locked("WEB_INVENTORY_ENABLED")} onChange={(event) => setField("web_inventory_enabled", event.target.checked)} />允许已登录的 Web 管理台修改库存</label>
        {form.environment_overrides.length > 0 ? <p className="text-xs text-slate-600">环境变量接管：{form.environment_overrides.join("、")}</p> : null}
        {notice ? <p role="status" className="text-sm text-emerald-700">{notice}</p> : null}
        {error ? <p role="alert" className="text-sm text-red-700">{error}</p> : null}
        <Button disabled={!dirty || saving} type="submit">{saving ? "保存中…" : "保存服务端配置"}</Button>
      </form> : !resource.error ? <div className="h-48 animate-pulse rounded-xl bg-slate-100" /> : null}
    </CardContent>
  </Card>;
}
