import { translateServerText, useI18n } from "@/lib/i18n";
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
  const { t, locale } = useI18n();
  const { data, error, loading, reload } = useAdminResource<AdminSyncResponse>(
    "/admin-api/sync",
  );

  return (
    <section className="space-y-6">
      <header className="space-y-2">
        <h2 className="text-3xl font-semibold tracking-tight">{t("Sync")}</h2>
        <p className="max-w-3xl text-sm text-slate-600">
          {t("Recent stock movement activity and read-only sync posture.")}
        </p>
      </header>

      {error ? (
        <Alert variant="destructive">
          <AlertTitle>{t("Sync view unavailable")}</AlertTitle>
          <AlertDescription className="flex items-center justify-between gap-4">
            <span>{error}</span>
            <Button onClick={() => void reload()} size="sm" variant="outline">
              {t("Retry")}
            </Button>
          </AlertDescription>
        </Alert>
      ) : null}

      {data ? (
        <>
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            <StatCard
              title={t("Movements")}
              value={data.metrics.movement_count}
              subtitle={t("Stored stock movement events")}
            />
            <StatCard
              title={t("Components")}
              value={data.metrics.component_count}
              subtitle={t("Active synchronized component rows")}
            />
            <StatCard
              title={t("Low stock")}
              value={data.metrics.low_stock_count}
              subtitle={t("Components at or below min_stock")}
            />
            <StatCard
              title={t("Units on hand")}
              value={data.metrics.total_units}
              subtitle={t("Current summed quantity on the server")}
            />
          </div>

          <div className="grid gap-6 xl:grid-cols-[1.45fr,0.9fr]">
            <Card className="bg-white/90">
              <CardHeader>
                <CardTitle>{t("Recent stock movements")}</CardTitle>
                <CardDescription>
                  {t("Latest movement activity received by the sync API.")}
                </CardDescription>
              </CardHeader>
              <CardContent>
                {data.recent_movements.length === 0 ? (
                  <p className="text-sm text-muted-foreground">
                    {t("No stock movement activity is available yet.")}
                  </p>
                ) : (
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>{t("SKU")}</TableHead>
                        <TableHead>{t("Component")}</TableHead>
                        <TableHead>{t("Type")}</TableHead>
                        <TableHead>{t("Qty")}</TableHead>
                        <TableHead>{t("Reason")}</TableHead>
                        <TableHead>{t("Happened")}</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {data.recent_movements.map((movement) => (
                        <TableRow key={movement.id}>
                          <TableCell className="font-medium">{movement.sku}</TableCell>
                          <TableCell>{movement.component_name}</TableCell>
                          <TableCell>{formatMovementType(movement.movement_type, locale)}</TableCell>
                          <TableCell>{new Intl.NumberFormat(locale).format(movement.quantity)}</TableCell>
                          <TableCell>{movement.reason}</TableCell>
                          <TableCell>{formatDateTime(movement.happened_at, locale)}</TableCell>
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
                  <CardTitle>{t("Sync posture")}</CardTitle>
                  <CardDescription>
                    {t("Current assumptions applied by the server.")}
                  </CardDescription>
                </CardHeader>
                <CardContent className="space-y-3">
                  {data.sync_assumptions.map((item) => (
                    <div
                      key={item.label}
                      className="rounded-2xl border border-slate-200 bg-slate-50/80 p-4"
                    >
                      <p className="text-sm font-medium text-slate-600">{translateServerText(item.label, locale)}</p>
                      <p className="mt-1 text-sm text-slate-900">{translateServerText(item.value, locale)}</p>
                    </div>
                  ))}
                </CardContent>
              </Card>

              <Card className="bg-white/90">
                <CardHeader>
                  <CardTitle>{t("Attention items")}</CardTitle>
                  <CardDescription>
                    {t("Deployment-sensitive checks for the current node.")}
                  </CardDescription>
                </CardHeader>
                <CardContent>
                  <ul className="space-y-3 text-sm text-slate-700">
                    {data.attention_items.map((item) => (
                      <li key={item} className="rounded-2xl border border-amber-100 bg-amber-50/90 p-4">
                        {translateServerText(item, locale)}
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
