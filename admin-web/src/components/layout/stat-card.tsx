import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

interface StatCardProps {
  title: string;
  value: number;
  subtitle: string;
}

export function StatCard({ subtitle, title, value }: StatCardProps) {
  return (
    <Card className="bg-white/90">
      <CardHeader className="pb-3">
        <CardTitle className="text-sm font-medium text-slate-600">{title}</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="text-3xl font-semibold tracking-tight text-slate-950">{value}</div>
        <p className="mt-2 text-sm text-muted-foreground">{subtitle}</p>
      </CardContent>
    </Card>
  );
}
