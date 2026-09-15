import { DatabaseZap, LayoutDashboard, LogOut, Settings, ShieldCheck } from "lucide-react";
import { NavLink, Outlet } from "react-router-dom";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { useAuth } from "@/hooks/use-auth";

const navigationItems = [
  { to: "/dashboard", label: "Dashboard", icon: LayoutDashboard },
  { to: "/inventory", label: "Inventory", icon: DatabaseZap },
  { to: "/sync", label: "Sync", icon: ShieldCheck },
  { to: "/settings", label: "Settings", icon: Settings },
];

export function AppShell() {
  const { logout, session } = useAuth();

  return (
    <div className="min-h-screen">
      <div className="mx-auto flex min-h-screen max-w-[1600px] flex-col lg:flex-row">
        <aside className="border-b border-white/60 bg-white/85 px-6 py-6 backdrop-blur lg:w-72 lg:border-b-0 lg:border-r">
          <div className="space-y-2">
            <Badge variant="secondary" className="rounded-full px-3 py-1">
              Separate Web Admin
            </Badge>
            <div>
              <h1 className="text-xl font-semibold tracking-tight">Component Vault</h1>
              <p className="text-sm text-muted-foreground">
                Inventory monitoring and MQTT configuration for the self-hosted sync service.
              </p>
            </div>
          </div>

          <nav className="mt-8 grid gap-2 sm:grid-cols-2 lg:grid-cols-1">
            {navigationItems.map(({ icon: Icon, label, to }) => (
              <NavLink
                key={to}
                to={to}
                className={({ isActive }) =>
                  cn(
                    "flex items-center gap-3 rounded-2xl px-4 py-3 text-sm font-medium transition-colors",
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
          <header className="sticky top-0 z-20 border-b border-white/70 bg-background/75 px-6 py-4 backdrop-blur">
            <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
              <div>
                <p className="text-sm font-medium text-slate-600">Connected API</p>
                <p className="text-sm text-slate-500">{session?.apiBaseUrl}</p>
              </div>
              <div className="flex items-center gap-3">
                <Badge variant="success">Bearer token active</Badge>
                <Button variant="outline" onClick={logout}>
                  <LogOut className="mr-2 h-4 w-4" />
                  Sign out
                </Button>
              </div>
            </div>
          </header>

          <main className="px-6 py-6">
            <Outlet />
          </main>
        </div>
      </div>
    </div>
  );
}
