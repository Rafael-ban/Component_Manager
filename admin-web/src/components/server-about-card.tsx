import { useState } from "react";
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { useAuth } from "@/hooks/use-auth";
import { useAdminResource } from "@/hooks/use-admin-resource";
import { postJson } from "@/lib/api";
import { useI18n } from "@/lib/i18n";

interface About { author: string; version: string; revision: string; deployment: string; repository_url: string; deployment_guide_url: string }
interface Update { latest_version: string; update_available: boolean | null; release_url: string }

export function ServerAboutCard() {
  const { t } = useI18n();
  const { session } = useAuth();
  const info = useAdminResource<About>("/admin-api/about");
  const [update, setUpdate] = useState<Update | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  return <Card><CardHeader><CardTitle>{t("关于服务端")}</CardTitle></CardHeader><CardContent className="space-y-4">
    <p className="font-medium">Component Vault Server</p>
    {info.data ? <>
      <dl className="grid grid-cols-[auto,1fr] gap-x-6 gap-y-2 text-sm">
        <dt>{t("作者")}</dt><dd>{info.data.author}</dd>
        <dt>{t("版本")}</dt><dd>{info.data.version} <span className="text-muted-foreground">({info.data.revision.slice(0, 8)})</span></dd>
        <dt>{t("部署方式")}</dt><dd>{info.data.deployment === "container" ? t("Docker / 容器") : t("源码或系统服务")}</dd>
      </dl>
      <p className="text-sm text-muted-foreground">{t("检查正式版更新。容器可使用 Watchtower 更新；其他部署按文档操作。更新前请备份数据目录。")}</p>
      <div className="flex flex-wrap gap-3">
        <Button disabled={busy} onClick={async () => {
          if (!session) return;
          setBusy(true); setError(null); setUpdate(null);
          try { setUpdate(await postJson<Update>(session, "/admin-api/about/check-update", {})); }
          catch { setError(t("无法检查更新，请稍后重试或打开发布页面。")); }
          finally { setBusy(false); }
        }}>{busy ? t("正在检查…") : t("检查更新")}</Button>
        <a className="self-center text-sm underline" href={`${info.data.repository_url}/releases`} target="_blank" rel="noreferrer">{t("发布记录")}</a>
        <a className="self-center text-sm underline" href={info.data.deployment_guide_url} target="_blank" rel="noreferrer">{t("部署与更新教程")}</a>
      </div>
    </> : <Button variant="outline" disabled={info.loading} onClick={() => void info.reload()}>{t("重新读取")}</Button>}
    {info.error || error ? <p role="alert" className="text-sm text-red-700">{info.error || error}</p> : null}
    {update ? <p role="status" className="text-sm">{update.update_available === null ? t("源码构建无法自动比较版本。最新正式版：") : update.update_available ? t("发现新版本：") : t("当前版本不低于最新正式版：")}<a className="underline" href={update.release_url} target="_blank" rel="noreferrer">{update.latest_version}</a></p> : null}
  </CardContent></Card>;
}
