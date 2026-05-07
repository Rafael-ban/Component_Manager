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
import type { AdminInventoryResponse } from "@/lib/types";
import { StatCard } from "@/components/layout/stat-card";

export function InventoryPage() {
  const { data, error, loading, reload } = useAdminResource<AdminInventoryResponse>(
    "/admin-api/inventory",
  );

  return (
    <section className="space-y-6">
      <header className="space-y-2">
        <h2 className="text-3xl font-semibold tracking-tight">Inventory</h2>
        <p className="max-w-3xl text-sm text-slate-600">
          Low-stock watchlists and recent server-side inventory snapshots.
        </p>
      </header>

      {error ? (
        <Alert variant="destructive">
          <AlertTitle>Inventory view unavailable</AlertTitle>
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
              subtitle="Tracked server-side component records"
            />
            <StatCard
              title="Low stock"
              value={data.metrics.low_stock_count}
              subtitle="Rows at or below min_stock"
            />
            <StatCard
              title="Healthy stock"
              value={Math.max(data.metrics.component_count - data.metrics.low_stock_count, 0)}
              subtitle="Active rows above the threshold"
            />
            <StatCard
              title="Units on hand"
              value={data.metrics.total_units}
              subtitle="Current summed quantity on the server"
            />
          </div>

          <div className="grid gap-6 xl:grid-cols-[1.3fr,0.7fr]">
            <Card className="bg-white/90">
              <CardHeader>
                <CardTitle>Low-stock watchlist</CardTitle>
                <CardDescription>
                  Components that currently need replenishment attention.
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-3">
                {data.low_stock_components.length === 0 ? (
                  <p className="text-sm text-muted-foreground">
                    No low-stock components are currently flagged.
                  </p>
                ) : (
                  data.low_stock_components.map((component) => (
                    <div
                      key={component.id}
                      className="flex flex-col gap-2 rounded-2xl border border-slate-200 bg-slate-50/70 p-4 md:flex-row md:items-center md:justify-between"
                    >
                      <div>
                        <p className="font-medium text-slate-900">
                          {component.name} ({component.sku})
                        </p>
                        <p className="text-sm text-muted-foreground">
                          {component.location} • Updated {formatDateTime(component.updated_at)}
                        </p>
                      </div>
                      <div className="text-sm font-medium text-amber-700">
                        {component.quantity} / {component.min_stock} on hand
                      </div>
                    </div>
                  ))
                )}
              </CardContent>
            </Card>

            <Card className="bg-white/90">
              <CardHeader>
                <CardTitle>Inventory posture</CardTitle>
                <CardDescription>
                  Current backend rules applied to synchronized inventory rows.
                </CardDescription>
              </CardHeader>
              <CardContent>
                <div className="space-y-3">
                  {data.inventory_rules.map((item) => (
                    <div
                      key={item.label}
                      className="rounded-2xl border border-slate-200 bg-slate-50/80 p-4"
                    >
                      <p className="text-sm font-medium text-slate-600">{item.label}</p>
                      <p className="mt-1 text-sm text-slate-900">{item.value}</p>
                    </div>
                  ))}
                </div>
              </CardContent>
            </Card>
          </div>

          <Card className="bg-white/90">
            <CardHeader>
              <CardTitle>Recently updated inventory</CardTitle>
              <CardDescription>
                Stable inventory listing for server-side verification.
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
                      <TableHead>Location</TableHead>
                      <TableHead>Qty</TableHead>
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
                        <TableCell>{component.location}</TableCell>
                        <TableCell>{component.quantity}</TableCell>
                        <TableCell>{component.status}</TableCell>
                        <TableCell>{formatDateTime(component.updated_at)}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </CardContent>
          </Card>
        </>
      ) : null}

      {loading && !data ? <Card className="h-56 animate-pulse bg-white/70" /> : null}
    </section>
  );
}
