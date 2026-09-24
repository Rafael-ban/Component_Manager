import { useState } from "react";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import { useAdminResource } from "@/hooks/use-admin-resource";
import type { AdminSettingsResponse } from "@/lib/types";
import { MqttSettingsCard } from "@/components/mqtt-settings-card";
import { DeploymentSettingsCard } from "@/components/deployment-settings-card";
import { useAuth } from "@/hooks/use-auth";
import { copyText, serverSetupUrl } from "@/lib/clipboard";

export function SettingsPage() {
  const { session } = useAuth();
  const [showToken, setShowToken] = useState(false);
  const [copied, setCopied] = useState(false);
  const { data, error, loading, reload } = useAdminResource<AdminSettingsResponse>(
    "/admin-api/settings",
  );

  return (
    <section className="space-y-6">
      <header className="space-y-2">
        <h2 className="text-3xl font-semibold tracking-tight">Settings</h2>
        <p className="max-w-3xl text-sm text-slate-600">
          Runtime configuration and access posture read from the same FastAPI process.
        </p>
      </header>

      <Alert>
        <AlertTitle>当前登录使用的 API 令牌</AlertTitle>
        <AlertDescription className="mt-2 space-y-3">
          <p className="break-all font-mono">{showToken ? session?.token : "•".repeat(Math.min(session?.token.length ?? 12, 24))}</p>
          <div className="flex flex-wrap gap-2">
            <Button type="button" size="sm" variant="outline" onClick={() => setShowToken((value) => !value)}>{showToken ? "隐藏" : "显示"}</Button>
            <Button type="button" size="sm" variant="outline" onClick={async () => { if (!session) return; setCopied(await copyText(session.token)); }}>{copied ? "已复制" : "复制令牌"}</Button>
          </div>
          <p>这是浏览器当前会话持有的值。服务端令牌来自 <code>API_TOKEN</code> 环境变量或数据目录的 <code>config.json</code>。配置页提供文件位置、有效配置和最近运行日志。</p>
          {session && serverSetupUrl(session.apiBaseUrl) ? <a className="block underline" href={serverSetupUrl(session.apiBaseUrl)} target="_blank" rel="noopener noreferrer">打开服务端配置与日志</a> : null}
        </AlertDescription>
      </Alert>

      <DeploymentSettingsCard />

      {error ? (
        <Alert variant="destructive">
          <AlertTitle>Settings view unavailable</AlertTitle>
          <AlertDescription className="flex items-center justify-between gap-4">
            <span>{error}</span>
            <Button onClick={() => void reload()} size="sm" variant="outline">
              Retry
            </Button>
          </AlertDescription>
        </Alert>
      ) : null}

      {data ? (
        <>
          <div className="grid gap-6 xl:grid-cols-[1.2fr,0.8fr]">
            <Card className="bg-white/90">
              <CardHeader>
                <CardTitle>Runtime configuration</CardTitle>
                <CardDescription>
                  Effective process settings that drive this sync service.
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                {data.runtime_configuration.map((item, index) => (
                  <div key={item.label}>
                    <div className="flex flex-col gap-1 sm:flex-row sm:items-start sm:justify-between sm:gap-6">
                      <p className="text-sm font-medium text-slate-600">{item.label}</p>
                      <p className="text-sm text-slate-900 sm:max-w-[60%] sm:text-right">
                        {item.value}
                      </p>
                    </div>
                    {index < data.runtime_configuration.length - 1 ? (
                      <Separator className="mt-4" />
                    ) : null}
                  </div>
                ))}
              </CardContent>
            </Card>

            <Card className="bg-white/90">
              <CardHeader>
                <CardTitle>Access posture</CardTitle>
                <CardDescription>
                  Operational URLs and deployment-sensitive settings.
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                {data.access_posture.map((item, index) => (
                  <div key={item.label}>
                    <div className="flex flex-col gap-1 sm:flex-row sm:items-start sm:justify-between sm:gap-6">
                      <p className="text-sm font-medium text-slate-600">{item.label}</p>
                      <p className="text-sm text-slate-900 sm:max-w-[60%] sm:text-right">
                        {item.value}
                      </p>
                    </div>
                    {index < data.access_posture.length - 1 ? (
                      <Separator className="mt-4" />
                    ) : null}
                  </div>
                ))}
              </CardContent>
            </Card>
          </div>

          <Card className="bg-white/90">
            <CardHeader>
              <CardTitle>Next backend additions</CardTitle>
              <CardDescription>
                Follow-up capabilities intentionally left outside this first separated admin release.
              </CardDescription>
            </CardHeader>
            <CardContent>
              <ul className="space-y-3 text-sm text-slate-700">
                {data.next_backend_additions.map((item) => (
                  <li key={item} className="rounded-2xl border border-slate-200 bg-slate-50/80 p-4">
                    {item}
                  </li>
                ))}
              </ul>
            </CardContent>
          </Card>
        </>
      ) : null}

      {loading && !data ? <Card className="h-56 animate-pulse bg-white/70" /> : null}
      <MqttSettingsCard />
    </section>
  );
}
