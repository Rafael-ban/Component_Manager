import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useI18n } from "@/lib/i18n";

import { useAuth } from "@/hooks/use-auth";

export function ProtectedRoute() {
  const { t } = useI18n();
  const { isReady, session } = useAuth();
  const location = useLocation();

  if (!isReady) {
    return (
      <div className="flex min-h-screen items-center justify-center text-sm text-slate-500">
        {t("正在读取管理会话…")}
      </div>
    );
  }

  if (!session) {
    return <Navigate to="/login" replace state={{ from: `${location.pathname}${location.search}` }} />;
  }

  return <Outlet />;
}
