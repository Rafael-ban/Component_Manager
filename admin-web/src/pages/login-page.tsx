import { useState } from "react";
import { Navigate, useLocation, useNavigate } from "react-router-dom";
import { LockKeyhole, Server } from "lucide-react";

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

const DEFAULT_API_BASE_URL =
  import.meta.env.VITE_DEFAULT_API_BASE_URL ?? "http://localhost:8787";

export function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { login, session } = useAuth();
  const [apiBaseUrl, setApiBaseUrl] = useState(session?.apiBaseUrl ?? DEFAULT_API_BASE_URL);
  const [token, setToken] = useState(session?.token ?? "");
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
              令牌进行只读库存核对与 MQTT 配置。
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
                  Bearer 令牌
                </span>
                <Input
                  type="password"
                  autoComplete="current-password"
                  value={token}
                  onChange={(event) => setToken(event.target.value)}
                  placeholder="change-me"
                />
              </label>

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
