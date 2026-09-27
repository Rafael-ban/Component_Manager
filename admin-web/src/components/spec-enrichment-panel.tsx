import { useEffect, useRef, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { RefreshCw } from "lucide-react";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { useAuth } from "@/hooks/use-auth";
import { useAdminResource } from "@/hooks/use-admin-resource";
import { ApiError, postJson, requestJson } from "@/lib/api";
import { formatCount, useI18n } from "@/lib/i18n";
import { pendingStorageKey } from "@/lib/pending-request";
import { canRetryEnrichment, displayEnrichmentReason, trackedJobAfterRecovery } from "@/lib/spec-enrichment";

interface Candidate { id: string; sku: string; name: string }
interface Candidates { total: number; items: Candidate[] }
interface JobItem { id: string; sku: string; status: string; reason: string | null }
interface Job {
  id: string;
  state: "queued" | "running" | "completed" | "cancelled";
  total: number;
  processed: number;
  updated: number;
  skipped: number;
  failed: number;
  items: JobItem[];
  omitted_items?: number;
}

const candidatesPath = "/admin-api/components/spec-enrichment/candidates";
const jobsPath = "/admin-api/components/spec-enrichment/jobs";

function storedJobId(key: string | null): string | null {
  if (!key) return null;
  try { return sessionStorage.getItem(key); } catch { return null; }
}

function saveJobId(key: string | null, id: string | null) {
  if (!key) return;
  try {
    if (id) sessionStorage.setItem(key, id);
    else sessionStorage.removeItem(key);
  } catch { /* The current page can still track the job. */ }
}

export function SpecEnrichmentPanel({ enabled, onChanged }: { enabled: boolean; onChanged: () => void }) {
  const { t, locale } = useI18n();
  const { identity, logout, session } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [open, setOpen] = useState(false);
  const [jobId, setJobId] = useState<string | null>(null);
  const [job, setJob] = useState<Job | null>(null);
  const [busy, setBusy] = useState<"start" | "cancel" | "retry" | "clear" | null>(null);
  const [error, setError] = useState<string | null>(null);
  const onChangedRef = useRef(onChanged);
  const refreshedJobRef = useRef<string | null>(null);
  useEffect(() => { onChangedRef.current = onChanged; }, [onChanged]);
  const storageKey = session && identity
    ? pendingStorageKey(session.apiBaseUrl, "spec-enrichment-job", identity.account_id)
    : null;
  const candidates = useAdminResource<Candidates>(open ? candidatesPath : null);

  useEffect(() => {
    let active = true;
    setJob(null);
    const savedId = storedJobId(storageKey);
    setJobId(savedId);
    if (session && storageKey && !savedId) {
      void requestJson<Job | null>(session, `${jobsPath}/active`).then((current) => {
        if (!active) return;
        const recovered = trackedJobAfterRecovery(current, null);
        if (recovered.job && recovered.id) {
          setJobId((previous) => previous ?? recovered.id);
          setJob((previous) => previous ?? recovered.job);
          if (!storedJobId(storageKey)) saveJobId(storageKey, recovered.id);
        }
      }).catch((requestError) => {
        if (!active) return;
        if (requestError instanceof ApiError && requestError.status === 401) {
          logout();
          navigate("/login", { replace: true, state: { from: `${location.pathname}${location.search}`, reason: t("登录已失效，请重新验证 API 令牌。") } });
        }
      });
    }
    return () => { active = false; };
  }, [location.pathname, location.search, logout, navigate, session, storageKey, t]);

  async function recoverActiveJob(): Promise<boolean> {
    if (!session) return false;
    const active = await requestJson<Job | null>(session, `${jobsPath}/active`);
    const recovered = trackedJobAfterRecovery(active, null);
    if (!recovered.job || !recovered.id) return false;
    setJob(recovered.job);
    setJobId(recovered.id);
    saveJobId(storageKey, recovered.id);
    setError(null);
    return true;
  }

  useEffect(() => {
    if (!session || !jobId) return;
    let active = true;
    let timer: ReturnType<typeof setTimeout> | undefined;
    const poll = async () => {
      try {
        const next = await requestJson<Job>(session, `${jobsPath}/${encodeURIComponent(jobId)}`);
        if (!active) return;
        setJob(next);
        if ((next.state === "completed" || next.state === "cancelled") && refreshedJobRef.current !== jobId) {
          refreshedJobRef.current = jobId;
          onChangedRef.current();
        }
        setError(null);
        if (next.state === "queued" || next.state === "running") timer = setTimeout(poll, 2000);
      } catch (requestError) {
        if (!active) return;
        if (requestError instanceof ApiError && requestError.status === 401) {
          logout();
          navigate("/login", { replace: true, state: { from: `${location.pathname}${location.search}`, reason: t("登录已失效，请重新验证 API 令牌。") } });
          return;
        }
        if (requestError instanceof ApiError && requestError.status === 404) {
          try {
            const current = await requestJson<Job | null>(session, `${jobsPath}/active`);
            if (!active) return;
            const recovered = trackedJobAfterRecovery(current, null);
            if (recovered.job && recovered.id) {
              setJob(recovered.job);
              setJobId(recovered.id);
              saveJobId(storageKey, recovered.id);
              setError(null);
              return;
            }
          } catch (recoveryError) {
            if (recoveryError instanceof ApiError && recoveryError.status === 401) {
              logout();
              navigate("/login", { replace: true, state: { from: `${location.pathname}${location.search}`, reason: t("登录已失效，请重新验证 API 令牌。") } });
              return;
            }
          }
          saveJobId(storageKey, null);
          setJobId(null);
          setJob(null);
          setError(t("原任务已不可查询；已完成的更新仍会保留。请重新预览候选。"));
          setOpen(true);
          return;
        }
        setError(requestError instanceof Error ? requestError.message : t("无法读取补全进度。"));
        timer = setTimeout(poll, 5000);
      }
    };
    void poll();
    return () => { active = false; if (timer) clearTimeout(timer); };
  }, [jobId, location.pathname, location.search, logout, navigate, session, storageKey, t]);

  async function act(kind: "start" | "cancel" | "retry") {
    if (!session || !enabled || busy) return;
    setBusy(kind);
    setError(null);
    try {
      const path = kind === "start" ? jobsPath : `${jobsPath}/${encodeURIComponent(jobId ?? "")}/${kind}`;
      const next = await postJson<Job>(session, path, {});
      setJob(next);
      setJobId(next.id);
      saveJobId(storageKey, next.id);
    } catch (requestError) {
      if (requestError instanceof ApiError && requestError.status === 401) {
        logout();
        navigate("/login", { replace: true, state: { from: `${location.pathname}${location.search}`, reason: t("登录已失效，请重新验证 API 令牌。") } });
        return;
      }
      if (kind === "start" && requestError instanceof ApiError && [0, 409].includes(requestError.status)) {
        try {
          if (await recoverActiveJob()) return;
        } catch (recoveryError) {
          if (recoveryError instanceof ApiError && recoveryError.status === 401) {
            logout();
            navigate("/login", { replace: true, state: { from: `${location.pathname}${location.search}`, reason: t("登录已失效，请重新验证 API 令牌。") } });
            return;
          }
        }
        void candidates.reload();
        setOpen(true);
        setError(requestError.status === 0
          ? t("启动结果未确认，且当前没有可查询的运行中任务。请重新预览剩余候选。")
          : t("当前账户已有任务但暂未找回进度。请稍后重试查询。"));
      } else setError(requestError instanceof Error ? requestError.message : t("补全操作未完成。"));
    } finally {
      setBusy(null);
    }
  }

  async function clearResult() {
    if (!jobId || running) return;
    setBusy("clear");
    if (session && enabled) {
      try { await postJson(session, `${jobsPath}/${encodeURIComponent(jobId)}/clear`, {}); }
      catch { /* Clearing the local result remains available if the server already pruned it. */ }
    }
    saveJobId(storageKey, null);
    setJobId(null);
    setJob(null);
    setError(null);
    setBusy(null);
  }

  const running = job?.state === "queued" || job?.state === "running";
  const failedItems = job?.items.filter((item) => item.status === "failed").slice(0, 3) ?? [];

  return <Card className="bg-white/90">
    <CardHeader className="flex flex-row items-center justify-between gap-3">
      <CardTitle>{t("补全旧元件参数")}</CardTitle>
      <Button type="button" variant="outline" size="sm" onClick={() => setOpen((value) => !value)}>{open ? t("收起预览") : t("预览候选")}</Button>
    </CardHeader>
    <CardContent className="space-y-4 text-sm">
      <p className="text-muted-foreground">{t("仅从官方资料补全已有元件的描述与参数；名称、库存和库位保持不变。范围限当前账户。")}</p>
      {!enabled ? <Alert><AlertTitle>{t("当前为只读模式")}</AlertTitle><AlertDescription>{t("请先在服务端设置中启用 Web 库存操作，再启动参数补全。")}</AlertDescription></Alert> : null}
      {open ? <div className="space-y-3 rounded-xl border bg-slate-50 p-4">
        {candidates.loading ? <p>{t("正在预览候选…")}</p> : null}
        {candidates.error ? <Alert variant="destructive"><AlertTitle>{t("无法预览候选")}</AlertTitle><AlertDescription>{candidates.error}</AlertDescription></Alert> : null}
        {candidates.data ? <>
          <p>{t("当前账户待补全")}{formatCount(candidates.data.total, locale)}{t(" 项")}</p>
          {candidates.data.total > 0 ? <>
            <p className="text-muted-foreground">{t("以下显示前 5 个 SKU；确认后处理全部候选。")}</p>
            <p className="break-words">{candidates.data.items.slice(0, 5).map((item) => item.sku).join(" · ")}{candidates.data.total > 5 ? " …" : ""}</p>
            <Button type="button" disabled={!enabled || busy !== null || running} onClick={() => void act("start")}>{busy === "start" ? t("正在启动…") : t("确认补全全部候选")}</Button>
          </> : <p className="text-muted-foreground">{t("暂无需要补全的旧元件。")}</p>}
        </> : null}
        <Button type="button" variant="ghost" size="sm" disabled={candidates.loading} onClick={() => void candidates.reload()}>{t("重新预览")}</Button>
      </div> : null}
      {job ? <div className="space-y-3 rounded-xl border p-4" aria-live="polite">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <strong>{job.state === "completed" ? t("补全已完成") : job.state === "cancelled" ? t("补全已取消") : t("正在补全参数…")}</strong>
          {running ? <RefreshCw className="h-4 w-4 animate-spin" aria-hidden="true" /> : null}
        </div>
        <progress className="w-full" value={job.processed} max={Math.max(job.total, 1)} aria-label={t("补全进度")} />
        <p>{t("已处理")}{formatCount(job.processed, locale)} / {formatCount(job.total, locale)} · {t("已更新")}{formatCount(job.updated, locale)} · {t("已跳过")}{formatCount(job.skipped, locale)} · {t("失败")}{formatCount(job.failed, locale)}</p>
        {failedItems.length ? <div className="space-y-1 text-amber-800">
          <p>{t("这里只显示最多 3 条失败原因；重试会处理全部失败及未处理项。")}</p>
          {failedItems.map((item) => <p key={item.id}>{item.sku}：{displayEnrichmentReason(item.reason, locale) || t("未提供失败原因")}</p>)}
        </div> : null}
        {job.omitted_items ? <p className="text-muted-foreground">{t("还有其他结果未在此处展开。")}</p> : null}
        <div className="flex flex-wrap gap-2">
          {running ? <Button type="button" variant="outline" size="sm" disabled={!enabled || busy !== null} onClick={() => void act("cancel")}>{t("取消任务")}</Button> : null}
          {canRetryEnrichment(job) ? <Button type="button" variant="outline" size="sm" disabled={!enabled || busy !== null} onClick={() => void act("retry")}>{t("重试失败及未处理项")}</Button> : null}
          {!running ? <Button type="button" variant="ghost" size="sm" disabled={busy !== null} onClick={() => void clearResult()}>{t("收起结果")}</Button> : null}
        </div>
      </div> : null}
      {error ? <Alert variant="destructive"><AlertTitle>{t("补全操作未完成")}</AlertTitle><AlertDescription>{error}</AlertDescription></Alert> : null}
    </CardContent>
  </Card>;
}
