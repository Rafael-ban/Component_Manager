import { useState } from "react";
import { Navigate, useNavigate } from "react-router-dom";
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
  const { login, session } = useAuth();
  const [apiBaseUrl, setApiBaseUrl] = useState(session?.apiBaseUrl ?? DEFAULT_API_BASE_URL);
  const [token, setToken] = useState(session?.token ?? "");
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  if (session) {
    return <Navigate to="/dashboard" replace />;
  }

  return (
    <div className="flex min-h-screen items-center justify-center px-4 py-10">
      <div className="grid w-full max-w-5xl gap-6 lg:grid-cols-[1.1fr,0.9fr]">
        <Card className="border-white/70 bg-white/85 backdrop-blur">
          <CardHeader>
            <CardTitle className="text-3xl">Component Vault Admin</CardTitle>
            <CardDescription className="max-w-xl text-base">
              Connect the separated web admin to your self-hosted FastAPI sync
              service. This console is read-only and uses the same shared bearer
              token as the sync API.
            </CardDescription>
          </CardHeader>
          <CardContent className="grid gap-4 text-sm text-slate-600">
            <div className="rounded-2xl border border-slate-200 bg-slate-50/80 p-4">
              <p className="font-medium text-slate-900">What this console shows</p>
              <ul className="mt-3 space-y-2">
                <li>Server-side inventory metrics and low-stock watchlists</li>
                <li>Recent synchronized components and stock movements</li>
                <li>Runtime configuration and deployment posture</li>
              </ul>
            </div>
            <div className="rounded-2xl border border-sky-100 bg-sky-50/80 p-4">
              <p className="font-medium text-sky-950">Recommended local setup</p>
              <p className="mt-2">
                Run the API on <code>http://localhost:8787</code> and the admin
                web app on <code>http://localhost:5173</code> during development.
              </p>
            </div>
          </CardContent>
        </Card>

        <Card className="border-white/70 bg-white/92">
          <CardHeader>
            <CardTitle>Connect to API</CardTitle>
            <CardDescription>
              The token is verified with <code>/auth/ping</code> before the
              dashboard unlocks.
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
                  navigate("/dashboard", { replace: true });
                } catch (loginError) {
                  setError(
                    loginError instanceof Error
                      ? loginError.message
                      : "Unable to sign in.",
                  );
                } finally {
                  setIsSubmitting(false);
                }
              }}
            >
              <label className="block space-y-2">
                <span className="flex items-center gap-2 text-sm font-medium text-slate-700">
                  <Server className="h-4 w-4" />
                  API base URL
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
                  Bearer token
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
                  <AlertTitle>Connection failed</AlertTitle>
                  <AlertDescription>{error}</AlertDescription>
                </Alert>
              ) : null}

              <Button className="w-full" disabled={isSubmitting} type="submit">
                {isSubmitting ? "Validating token..." : "Enter admin console"}
              </Button>
            </form>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
