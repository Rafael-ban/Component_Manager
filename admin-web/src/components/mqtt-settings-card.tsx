import { useEffect, useMemo, useRef, useState } from "react";
import type { ReactNode } from "react";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { useAdminResource } from "@/hooks/use-admin-resource";
import { useAuth } from "@/hooks/use-auth";
import { ApiError, postJson } from "@/lib/api";
import type { MqttConfiguration, MqttRuntimeStatus } from "@/lib/types";

export function MqttSettingsCard() {
  const configResource = useAdminResource<MqttConfiguration>("/admin-api/mqtt/config");
  const statusResource = useAdminResource<MqttRuntimeStatus>("/admin-api/mqtt/status");
  const { logout, session } = useAuth();
  const [form, setForm] = useState<MqttConfiguration | null>(null);
  const [password, setPassword] = useState("");
  const [clearPassword, setClearPassword] = useState(false);
  const [dirty, setDirty] = useState(false);
  const dirtyRef = useRef(false);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [savedMessage, setSavedMessage] = useState<string | null>(null);

  useEffect(() => {
    if (configResource.data && !dirtyRef.current) setForm(configResource.data);
  }, [configResource.data]);

  const fieldErrors = useMemo(() => {
    const errors: Record<string, string> = {};
    if (!form) return errors;
    if (form.enabled && !form.host.trim()) errors.host = "启用 MQTT 时必须填写 Broker 主机。";
    if (!Number.isInteger(form.port) || form.port < 1 || form.port > 65535) errors.port = "端口必须是 1 到 65535 的整数。";
    if (!form.topic_prefix.trim() || /[+#\u0000-\u001f\u007f]/.test(form.topic_prefix)) errors.topic_prefix = "Topic 前缀不能为空，且不能包含 +、# 或控制字符。";
    if (!form.client_id.trim() || /[\u0000-\u001f\u007f]/.test(form.client_id)) errors.client_id = "Client ID 不能为空或包含控制字符。";
    if (/[:][/][/]|[@/\\?#]|[\u0000-\u0020\u007f]/.test(form.host)) errors.host = "请填写域名、IP 或 IPv6 地址，不要填写 URL、路径或用户信息。";
    return errors;
  }, [form]);
  const invalid = !form || Object.keys(fieldErrors).length > 0;

  const update = <K extends keyof MqttConfiguration,>(key: K, value: MqttConfiguration[K]) => {
    setForm((current) => current ? { ...current, [key]: value } : current);
    setDirty(true);
    dirtyRef.current = true;
    setSavedMessage(null);
  };

  const save = async () => {
    if (!session || !form || invalid) return;
    setSaving(true);
    setSaveError(null);
    try {
      const next = await postJson<MqttConfiguration>(session, "/admin-api/mqtt/config", {
        enabled: form.enabled, host: form.host.trim(), port: form.port, tls: form.tls,
        username: form.username.trim(), password, clear_password: clearPassword,
        topic_prefix: form.topic_prefix.trim(), client_id: form.client_id.trim(),
      });
      setForm(next);
      setPassword("");
      setClearPassword(false);
      setDirty(false);
      dirtyRef.current = false;
      setSavedMessage(next.message);
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) return logout();
      setSaveError(error instanceof Error ? error.message : "保存 MQTT 配置失败。");
    } finally {
      setSaving(false);
    }
  };

  const status = statusResource.data;
  return (
    <div className="space-y-6">
      <Card className="bg-white/90">
        <CardHeader>
          <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
            <div><CardTitle>MQTT 运行状态</CardTitle><CardDescription>当前服务进程实际使用的连接状态。</CardDescription></div>
            <Button variant="outline" size="sm" disabled={statusResource.loading} onClick={() => void statusResource.reload()}>手动刷新</Button>
          </div>
        </CardHeader>
        <CardContent className="grid gap-3 text-sm sm:grid-cols-2 lg:grid-cols-4">
          <Status label="运行时启用" value={status ? (status.enabled ? "是" : "否") : "加载中"} />
          <Status label="Broker 连接" value={status ? (status.connected ? "已连接" : "未连接") : "加载中"} />
          <Status label="待发送事件" value={status ? String(status.pending) : "—"} />
          <Status label="最近成功发布" value={status?.last_publish_at ? new Date(status.last_publish_at).toLocaleString() : "暂无"} />
          {status?.error ? <p className="sm:col-span-2 lg:col-span-4 text-sm text-red-700">运行错误：{status.error}</p> : null}
          {statusResource.error ? <p className="sm:col-span-2 lg:col-span-4 text-sm text-red-700">{statusResource.error}</p> : null}
        </CardContent>
      </Card>

      <Card className="bg-white/90">
        <CardHeader><CardTitle>MQTT 连接配置</CardTitle><CardDescription>保存的配置会在下次重启服务时生效，保存不会立即连接 Broker。</CardDescription></CardHeader>
        <CardContent className="space-y-5">
          {configResource.error ? <Alert variant="destructive"><AlertTitle>配置加载失败</AlertTitle><AlertDescription className="flex justify-between gap-4">{configResource.error}<Button size="sm" variant="outline" onClick={() => void configResource.reload()}>重试</Button></AlertDescription></Alert> : null}
          {form ? <>
            <label className="flex min-h-11 items-center gap-3"><input type="checkbox" checked={form.enabled} disabled={saving} onChange={(e) => update("enabled", e.target.checked)} /><span>启用 MQTT 发布</span></label>
            <div className="grid gap-4 md:grid-cols-2">
              <Field label="Broker 主机" error={fieldErrors.host}><Input value={form.host} disabled={saving} maxLength={255} onChange={(e) => update("host", e.target.value)} /></Field>
              <Field label="端口" error={fieldErrors.port}><Input type="number" min={1} max={65535} step={1} value={form.port} disabled={saving} onChange={(e) => update("port", Number(e.target.value))} /></Field>
              <Field label="用户名"><Input value={form.username} disabled={saving} maxLength={255} autoComplete="username" onChange={(e) => update("username", e.target.value)} /></Field>
              <Field label="密码"><Input type="password" value={password} disabled={saving || clearPassword} autoComplete="new-password" placeholder={form.password_configured ? "留空以保留现有密码" : "未配置"} onChange={(e) => { setPassword(e.target.value); setDirty(true); dirtyRef.current = true; }} /></Field>
              <Field label="Topic 前缀" error={fieldErrors.topic_prefix}><Input value={form.topic_prefix} disabled={saving} maxLength={255} onChange={(e) => update("topic_prefix", e.target.value)} /></Field>
              <Field label="Client ID" error={fieldErrors.client_id}><Input value={form.client_id} disabled={saving} maxLength={255} onChange={(e) => update("client_id", e.target.value)} /></Field>
            </div>
            <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
              <label className="flex min-h-11 items-center gap-3"><input type="checkbox" checked={form.tls} disabled={saving} onChange={(e) => update("tls", e.target.checked)} /><span>使用 TLS</span></label>
              <label className="flex min-h-11 items-center gap-3"><input type="checkbox" checked={clearPassword} disabled={saving || !form.password_configured} onChange={(e) => { setClearPassword(e.target.checked); setDirty(true); dirtyRef.current = true; if (e.target.checked) setPassword(""); }} /><span>明确清除已保存密码</span></label>
            </div>
            <p className="text-sm text-slate-600">来源：{form.source === "saved" ? "网页已保存配置" : "环境变量默认配置"}。密码不会显示在页面中。</p>
            {form.restart_required ? <Alert><AlertTitle>等待服务重启</AlertTitle><AlertDescription>已保存配置与当前运行配置不同，重启 FastAPI 服务后生效。</AlertDescription></Alert> : null}
            {savedMessage ? <p className="text-sm text-emerald-700" role="status">{savedMessage}</p> : null}
            {saveError ? <p className="text-sm text-red-700" role="alert">{saveError}</p> : null}
            <Button className="w-full sm:w-auto" disabled={!dirty || invalid || saving} onClick={() => void save()}>{saving ? "保存中…" : "保存 MQTT 配置"}</Button>
          </> : <div className="h-48 animate-pulse rounded-xl bg-slate-100" />}
        </CardContent>
      </Card>
    </div>
  );
}

function Field({ label, error, children }: { label: string; error?: string; children: ReactNode }) {
  return <label className="space-y-2 text-sm font-medium text-slate-700"><span>{label}</span>{children}{error ? <span className="block text-xs font-normal text-red-700">{error}</span> : null}</label>;
}

function Status({ label, value }: { label: string; value: string }) {
  return <div className="rounded-xl border border-slate-200 bg-slate-50 p-3"><p className="text-slate-500">{label}</p><p className="mt-1 font-medium text-slate-900">{value}</p></div>;
}
