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
import { formatDateTime, formatMovementType } from "@/lib/format";
import type { AdminSyncResponse } from "@/lib/types";
import { StatCard } from "@/components/layout/stat-card";

export function SyncPage() {
  const { data, error, loading, reload } = useAdminResource<AdminSyncResponse>(
    "/admin-api/sync",
  );

  return (
    <section className="space-y-6">
      <header className="space-y-2">
        <h2 className="text-3xl font-semibold tracking-tight">Sync</h2>
        <p className="max-w-3xl text-sm text-slate-600">
          Recent stock movement activity and read-only sync posture.
        </p>
      </header>

      {error ? (
        <Alert variant="destructive">
          <AlertTitle>Sync view unavailable</AlertTitle>
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
              title="Movements"
              value={data.metrics.movement_count}
              subtitle="Stored stock movement events"
            />
            <StatCard
              title="Components"
              value={data.metrics.component_count}
              subtitle="Active synchronized component rows"
            />
            <StatCard
              title="Low stock"
              value={data.metrics.low_stock_count}
              subtitle="Components at or below min_stock"
            />
            <StatCard
              title="Units on hand"
              value={data.metrics.total_units}
              subtitle="Current summed quantity on the server"
            />
          </div>

          <div className="grid gap-6 xl:grid-cols-[1.45fr,0.9fr]">
            <Card className="bg-white/90">
              <CardHeader>
                <CardTitle>Recent stock movements</CardTitle>
                <CardDescription>
                  Latest movement activity received by the sync API.
                </CardDescription>
              </CardHeader>
              <CardContent>
                {data.recent_movements.length === 0 ? (
                  <p className="text-sm text-muted-foreground">
                    No stock movement activity is available yet.
                  </p>
                ) : (
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>SKU</TableHead>
                        <TableHead>Component</TableHead>
                        <TableHead>Type</TableHead>
                        <TableHead>Qty</TableHead>
                        <TableHead>Reason</TableHead>
                        <TableHead>Happened</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {data.recent_movements.map((movement) => (
                        <TableRow key={movement.id}>
                          <TableCell className="font-medium">{movement.sku}</TableCell>
                          <TableCell>{movement.component_name}</TableCell>
                          <TableCell>{formatMovementType(movement.movement_type)}</TableCell>
                          <TableCell>{movement.quantity}</TableCell>
                          <TableCell>{movement.reason}</TableCell>
                          <TableCell>{formatDateTime(movement.happened_at)}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                )}
              </CardContent>
            </Card>

            <div className="space-y-6">
              <Card className="bg-white/90">
                <CardHeader>
                  <CardTitle>Sync posture</CardTitle>
                  <CardDescription>
                    Current assumptions applied by the server.
                  </CardDescription>
                </CardHeader>
                <CardContent className="space-y-3">
                  {data.sync_assumptions.map((item) => (
                    <div
                      key={item.label}
                      className="rounded-2xl border border-slate-200 bg-slate-50/80 p-4"
                    >
                      <p className="text-sm font-medium text-slate-600">{item.label}</p>
                      <p className="mt-1 text-sm text-slate-900">{item.value}</p>
                    </div>
                  ))}
                </CardContent>
              </Card>

              <Card className="bg-white/90">
                <CardHeader>
                  <CardTitle>Attention items</CardTitle>
                  <CardDescription>
                    Deployment-sensitive checks for the current node.
                  </CardDescription>
                </CardHeader>
                <CardContent>
                  <ul className="space-y-3 text-sm text-slate-700">
                    {data.attention_items.map((item) => (
                      <li key={item} className="rounded-2xl border border-amber-100 bg-amber-50/90 p-4">
                        {item}
                      </li>
                    ))}
                  </ul>
                </CardContent>
              </Card>
            </div>
          </div>
        </>
      ) : null}

      {loading && !data ? <Card className="h-56 animate-pulse bg-white/70" /> : null}
    </section>
  );
}
