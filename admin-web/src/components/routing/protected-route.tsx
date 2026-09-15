import { Navigate, Outlet, useLocation } from "react-router-dom";

import { useAuth } from "@/hooks/use-auth";

export function ProtectedRoute() {
  const { isReady, session } = useAuth();
  const location = useLocation();

  if (!isReady) {
    return (
      <div className="flex min-h-screen items-center justify-center text-sm text-slate-500">
        正在读取管理会话…
      </div>
    );
  }

  if (!session) {
    return <Navigate to="/login" replace state={{ from: `${location.pathname}${location.search}` }} />;
  }

  return <Outlet />;
}
