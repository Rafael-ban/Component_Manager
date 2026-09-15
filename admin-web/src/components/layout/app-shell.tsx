import { DatabaseZap, LayoutDashboard, LogOut, Settings, ShieldCheck } from "lucide-react";
import { NavLink, Outlet } from "react-router-dom";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { useAuth } from "@/hooks/use-auth";

const navigationItems = [
  { to: "/dashboard", label: "概览", icon: LayoutDashboard },
  { to: "/inventory", label: "库存", icon: DatabaseZap },
  { to: "/sync", label: "同步", icon: ShieldCheck },
  { to: "/settings", label: "设置", icon: Settings },
];

export function AppShell() {
  const { logout, session } = useAuth();

  return (
    <div className="min-h-screen">
      <div className="mx-auto flex min-h-screen max-w-[1600px] flex-col lg:flex-row">
        <aside className="border-b border-white/60 bg-white/85 px-4 py-4 backdrop-blur lg:w-72 lg:border-b-0 lg:border-r lg:px-6 lg:py-6">
          <div className="space-y-2">
            <Badge variant="secondary" className="rounded-full px-3 py-1">
              独立管理台
            </Badge>
            <div>
              <h1 className="text-xl font-semibold tracking-tight">Component Vault</h1>
              <p className="text-sm text-muted-foreground">
                自托管同步服务的库存核对与 MQTT 配置。
              </p>
            </div>
          </div>

          <nav className="mt-4 grid grid-cols-2 gap-1.5 lg:mt-8 lg:grid-cols-1 lg:gap-2">
            {navigationItems.map(({ icon: Icon, label, to }) => (
              <NavLink
                key={to}
                to={to}
                className={({ isActive }) =>
                  cn(
                    "flex items-center gap-2 rounded-xl px-3 py-2 text-sm font-medium transition-colors lg:gap-3 lg:rounded-2xl lg:px-4 lg:py-3",
                    isActive
                      ? "bg-primary text-primary-foreground shadow-sm"
                      : "text-slate-700 hover:bg-slate-100",
                  )
                }
              >
                <Icon className="h-4 w-4" />
                {label}
              </NavLink>
            ))}
          </nav>
        </aside>

        <div className="flex-1">
          <header className="sticky top-0 z-20 border-b border-white/70 bg-background/75 px-4 py-3 backdrop-blur md:px-6 md:py-4">
            <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
              <div>
                <p className="text-sm font-medium text-slate-600">已连接 API</p>
                <p className="text-sm text-slate-500">{session?.apiBaseUrl}</p>
              </div>
              <div className="flex items-center gap-3">
                <Badge variant="success">令牌有效</Badge>
                <Button variant="outline" onClick={logout}>
                  <LogOut className="mr-2 h-4 w-4" />
                  退出登录
                </Button>
              </div>
            </div>
          </header>

          <main className="px-4 py-5 md:px-6 md:py-6">
            <Outlet />
          </main>
        </div>
      </div>
    </div>
  );
}
