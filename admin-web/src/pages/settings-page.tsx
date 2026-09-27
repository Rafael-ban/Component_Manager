import { translateServerText, useI18n } from "@/lib/i18n";

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
import { serverSetupUrl } from "@/lib/clipboard";
import { LanguageSelect } from "@/components/language-select";
import { ServerAboutCard } from "@/components/server-about-card";
import { AccountsSettingsCard } from "@/components/accounts-settings-card";

export function SettingsPage() {
  const { t, locale } = useI18n();
  const { identity, session } = useAuth();
  const isAdmin = identity?.role === "admin";
  const { data, error, loading, reload } = useAdminResource<AdminSettingsResponse>(
    "/admin-api/settings",
  );

  return (
    <section className="space-y-6">
      <header className="space-y-2">
        <h2 className="text-3xl font-semibold tracking-tight">{t("Settings")}</h2>
        <p className="max-w-3xl text-sm text-slate-600">
          {isAdmin ? t("Runtime configuration and access posture read from the same FastAPI process.") : t("此账户的库存由服务端独立保存。")}
        </p>
      </header>

      <Card className="bg-white/90"><CardHeader><CardTitle>{t("语言")}</CardTitle></CardHeader><CardContent><LanguageSelect /></CardContent></Card>

      <ServerAboutCard />
      <Alert>
        <AlertTitle>{t("后端会话认证")}</AlertTitle>
        <AlertDescription className="mt-2 space-y-3">
          <p>{t("账户密钥仅用于登录。浏览器通过 HttpOnly 会话 Cookie 访问服务端，不保存原始密钥。")}</p>
          <p>{isAdmin ? t("管理员密钥可在部署环境的 API_TOKEN 或数据目录 config.json 中查看；普通用户密钥由管理员创建或重置后发放。") : t("如需在其他设备登录，请使用管理员发放的账户密钥。")}</p>
          {isAdmin && session && serverSetupUrl(session.apiBaseUrl) ? <a className="block underline" href={serverSetupUrl(session.apiBaseUrl)} target="_blank" rel="noopener noreferrer">{t("打开服务端配置与日志")}</a> : null}
        </AlertDescription>
      </Alert>

      {isAdmin ? <><AccountsSettingsCard /><DeploymentSettingsCard /></> : null}

      {error ? (
        <Alert variant="destructive">
          <AlertTitle>{t("Settings view unavailable")}</AlertTitle>
          <AlertDescription className="flex items-center justify-between gap-4">
            <span>{error}</span>
            <Button onClick={() => void reload()} size="sm" variant="outline">
              {t("Retry")}
            </Button>
          </AlertDescription>
        </Alert>
      ) : null}

      {isAdmin && data ? (
        <>
          <div className="grid gap-6 xl:grid-cols-[1.2fr,0.8fr]">
            <Card className="bg-white/90">
              <CardHeader>
                <CardTitle>{t("Runtime configuration")}</CardTitle>
                <CardDescription>
                  {t("Effective process settings that drive this sync service.")}
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                {data.runtime_configuration.map((item, index) => (
                  <div key={item.label}>
                    <div className="flex flex-col gap-1 sm:flex-row sm:items-start sm:justify-between sm:gap-6">
                      <p className="text-sm font-medium text-slate-600">{translateServerText(item.label, locale)}</p>
                      <p className="text-sm text-slate-900 sm:max-w-[60%] sm:text-right">
                        {translateServerText(item.value, locale)}
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
                <CardTitle>{t("Access posture")}</CardTitle>
                <CardDescription>
                  {t("Operational URLs and deployment-sensitive settings.")}
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                {data.access_posture.map((item, index) => (
                  <div key={item.label}>
                    <div className="flex flex-col gap-1 sm:flex-row sm:items-start sm:justify-between sm:gap-6">
                      <p className="text-sm font-medium text-slate-600">{translateServerText(item.label, locale)}</p>
                      <p className="text-sm text-slate-900 sm:max-w-[60%] sm:text-right">
                        {translateServerText(item.value, locale)}
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


        </>
      ) : null}

      {isAdmin && loading && !data ? <Card className="h-56 animate-pulse bg-white/70" /> : null}
      {isAdmin ? <MqttSettingsCard /> : null}
    </section>
  );
}
