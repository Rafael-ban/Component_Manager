import { useState } from "react";
import { Navigate, useLocation, useNavigate } from "react-router-dom";
import { Copy, Eye, EyeOff, LockKeyhole, Server } from "lucide-react";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { useAuth } from "@/hooks/use-auth";
import { copyText, serverSetupUrl } from "@/lib/clipboard";

const DEFAULT_API_BASE_URL = import.meta.env.VITE_DEFAULT_API_BASE_URL
  || `${window.location.protocol}//${window.location.hostname}:8787`;

export function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { login, session } = useAuth();
  const [apiBaseUrl, setApiBaseUrl] = useState(session?.apiBaseUrl ?? DEFAULT_API_BASE_URL);
  const [token, setToken] = useState(session?.token ?? "");
  const [showToken, setShowToken] = useState(false);
  const [tokenCopied, setTokenCopied] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const routeState = location.state as { from?: unknown; reason?: unknown } | null;
  const returnTo = safeReturnTo(routeState?.from);
  const sessionReason = typeof routeState?.reason === "string" ? routeState.reason : null;

  if (session) {
    return <Navigate to={returnTo} replace />;
  }

  return (
    <div className="flex min-h-screen items-center justify-center px-4 py-10">
      <div className="grid w-full max-w-5xl gap-6 lg:grid-cols-[1.1fr,0.9fr]">
        <Card className="border-white/70 bg-white/85 backdrop-blur">
          <CardHeader>
            <CardTitle className="text-3xl">Component Vault 管理台</CardTitle>
            <CardDescription className="max-w-xl text-base">
              连接自托管 FastAPI 同步服务，使用与同步 API 相同的 Bearer
              令牌核对库存与配置 MQTT。库存默认只读；服务端启用 Web 库存操作后，可创建库位、元件并办理入出库。
            </CardDescription>
          </CardHeader>
          <CardContent className="grid gap-4 text-sm text-slate-600">
            <div className="rounded-2xl border border-slate-200 bg-slate-50/80 p-4">
              <p className="font-medium text-slate-900">管理台提供的功能</p>
              <ul className="mt-3 space-y-2">
                <li>服务端库存统计与低库存提醒</li>
                <li>完整库存检索与同步记录核对</li>
                <li>运行配置与部署状态</li>
                <li>供下次服务重启使用的 MQTT Broker 配置</li>
              </ul>
            </div>
            <div className="rounded-2xl border border-sky-100 bg-sky-50/80 p-4">
              <p className="font-medium text-sky-950">推荐的本地配置</p>
              <p className="mt-2">
                开发时可在 <code>http://localhost:8787</code> 运行 API，并在
                <code>http://localhost:5173</code> 运行管理台。
              </p>
            </div>
          </CardContent>
        </Card>

        <Card className="border-white/70 bg-white/92">
          <CardHeader>
            <CardTitle>连接 API</CardTitle>
            <CardDescription>
              进入管理台前，将通过 <code>/auth/ping</code> 验证令牌。
            </CardDescription>
          </CardHeader>
          <CardContent>
            <form
              className="space-y-5"
              onSubmit={async (event) => {
                event.preventDefault();
                setIsSubmitting(true);
                setError(null);
                try {
                  await login(apiBaseUrl, token);
                  navigate(returnTo, { replace: true });
                } catch (loginError) {
                  setError(
                    loginError instanceof Error
                      ? loginError.message
                      : "无法登录管理台。",
                  );
                } finally {
                  setIsSubmitting(false);
                }
              }}
            >
              {sessionReason ? (
                <Alert>
                  <AlertTitle>需要重新登录</AlertTitle>
                  <AlertDescription>{sessionReason}</AlertDescription>
                </Alert>
              ) : null}
              <label className="block space-y-2">
                <span className="flex items-center gap-2 text-sm font-medium text-slate-700">
                  <Server className="h-4 w-4" />
                  API 地址
                </span>
                <Input
                  autoComplete="url"
                  value={apiBaseUrl}
                  onChange={(event) => setApiBaseUrl(event.target.value)}
                  placeholder="http://localhost:8787"
                />
              </label>

              <label className="block space-y-2">
                <span className="flex items-center gap-2 text-sm font-medium text-slate-700">
                  <LockKeyhole className="h-4 w-4" />
                  API Token
                </span>
                <Input
                  type={showToken ? "text" : "password"}
                  autoComplete="current-password"
                  value={token}
                  onChange={(event) => { setToken(event.target.value); setTokenCopied(false); }}
                  placeholder="粘贴部署时设置的 API_TOKEN"
                />
                <div className="flex flex-wrap gap-2">
                  <Button type="button" size="sm" variant="outline" onClick={() => setShowToken((value) => !value)}>
                    {showToken ? <EyeOff className="mr-2 h-4 w-4" /> : <Eye className="mr-2 h-4 w-4" />}
                    {showToken ? "隐藏令牌" : "显示令牌"}
                  </Button>
                  <Button
                    type="button"
                    size="sm"
                    variant="outline"
                    disabled={!token.trim()}
                    onClick={async () => {
                      const copied = await copyText(token);
                      setTokenCopied(copied);
                      if (!copied) setError("浏览器未允许复制，请显示令牌后手动复制。");
                    }}
                  >
                    <Copy className="mr-2 h-4 w-4" />
                    {tokenCopied ? "已复制" : "复制令牌"}
                  </Button>
                </div>
                <p className="text-xs text-muted-foreground">API Token 由部署者设置。可在服务端配置页显示与复制当前令牌；也可查看 API 容器的 <code>API_TOKEN</code> 环境变量、部署目录的 <code>.env</code>，或映射目录中 <code>config.json</code> 的 <code>API_TOKEN</code>。它不是 <code>GPG_KEY</code> 或 <code>DOCKERHUB_TOKEN</code>。非部署者请联系管理员。</p>
              </label>

              {serverSetupUrl(apiBaseUrl) ? <a className="block text-sm font-medium text-teal-700 underline" href={serverSetupUrl(apiBaseUrl)} target="_blank" rel="noopener noreferrer">首次部署？打开服务端配置与日志</a> : null}

              {error ? (
                <Alert variant="destructive">
                  <AlertTitle>连接失败</AlertTitle>
                  <AlertDescription>{error}</AlertDescription>
                </Alert>
              ) : null}

              <Button className="w-full" disabled={isSubmitting} type="submit">
                {isSubmitting ? "正在验证令牌…" : "进入管理台"}
              </Button>
            </form>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

function safeReturnTo(value: unknown) {
  if (typeof value !== "string" || !value.startsWith("/") || value.startsWith("//")) {
    return "/dashboard";
  }
  const parsed = new URL(value, "https://component-vault.local");
  return ["/dashboard", "/inventory", "/sync", "/settings"].includes(parsed.pathname)
    ? `${parsed.pathname}${parsed.search}${parsed.hash}`
    : "/dashboard";
}
