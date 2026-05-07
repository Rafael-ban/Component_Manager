import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { useAdminResource } from "@/hooks/use-admin-resource";
import { formatDateTime } from "@/lib/format";
import type { AdminDashboardResponse } from "@/lib/types";
import { StatCard } from "@/components/layout/stat-card";

export function DashboardPage() {
  const { data, error, loading, reload } = useAdminResource<AdminDashboardResponse>(
    "/admin-api/dashboard",
  );

  return (
    <section className="space-y-6">
      <header className="space-y-2">
        <h2 className="text-3xl font-semibold tracking-tight">Dashboard</h2>
        <p className="max-w-3xl text-sm text-slate-600">
          Server-side inventory overview for the current self-hosted sync node.
        </p>
      </header>

      {error ? (
        <Alert variant="destructive">
          <AlertTitle>Dashboard unavailable</AlertTitle>
          <AlertDescription className="flex items-center justify-between gap-4">
            <span>{error}</span>
            <Button onClick={() => void reload()} size="sm" variant="outline">
              Retry
            </Button>
          </AlertDescription>
        </Alert>
      ) : null}

      {data ? (
        <>
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            <StatCard
              title="Active components"
              value={data.metrics.component_count}
              subtitle="Tracked SKUs on the server"
            />
            <StatCard
              title="Units on hand"
              value={data.metrics.total_units}
              subtitle="Sum of server-side quantities"
            />
            <StatCard
              title="Low stock"
              value={data.metrics.low_stock_count}
              subtitle="Components at or below min_stock"
            />
            <StatCard
              title="Movements"
              value={data.metrics.movement_count}
              subtitle="Recorded inventory transactions"
            />
          </div>

          <div className="grid gap-6 xl:grid-cols-[1.5fr,1fr]">
            <Card className="bg-white/90">
              <CardHeader>
                <CardTitle>Recent components</CardTitle>
                <CardDescription>
                  Latest active component rows visible to the sync service.
                </CardDescription>
              </CardHeader>
              <CardContent>
                {data.recent_components.length === 0 ? (
                  <p className="text-sm text-muted-foreground">
                    No component records are available yet.
                  </p>
                ) : (
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>SKU</TableHead>
                        <TableHead>Name</TableHead>
                        <TableHead>Category</TableHead>
                        <TableHead>Status</TableHead>
                        <TableHead>Updated</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {data.recent_components.map((component) => (
                        <TableRow key={component.id}>
                          <TableCell className="font-medium">{component.sku}</TableCell>
                          <TableCell>{component.name}</TableCell>
                          <TableCell>{component.category}</TableCell>
                          <TableCell>{component.status}</TableCell>
                          <TableCell>{formatDateTime(component.updated_at)}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                )}
              </CardContent>
            </Card>

            <Card className="bg-white/90">
              <CardHeader>
                <CardTitle>Operational notes</CardTitle>
                <CardDescription>
                  Deployment and sync behaviors surfaced for administrators.
                </CardDescription>
              </CardHeader>
              <CardContent>
                <ul className="space-y-3 text-sm text-slate-700">
                  {data.sync_notes.map((note) => (
                    <li key={note} className="rounded-2xl border border-slate-200 bg-slate-50/80 p-4">
                      {note}
                    </li>
                  ))}
                </ul>
              </CardContent>
            </Card>
          </div>
        </>
      ) : null}

      {loading && !data ? (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
          {Array.from({ length: 4 }).map((_, index) => (
            <Card key={index} className="h-32 animate-pulse bg-white/70" />
          ))}
        </div>
      ) : null}
    </section>
  );
}
