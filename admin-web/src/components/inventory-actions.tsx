import { useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import { RefreshCw } from "lucide-react";
import { useLocation, useNavigate } from "react-router-dom";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { useAuth } from "@/hooks/use-auth";
import { ApiError, postJson, putJson } from "@/lib/api";
import {
  clearPendingRequest, loadPendingRequest, pendingStorageKey, requestForPayload, savePendingRequest, shouldRetainPending,
} from "@/lib/pending-request";
import type { AdminComponentDetail, AdminStorageLocation } from "@/lib/types";

interface InventoryActionsProps {
  enabled: boolean;
  component: AdminComponentDetail | null;
  locations: AdminStorageLocation[];
  onChanged: (component?: AdminComponentDetail) => void;
  onLocationsChanged: () => void;
}

const emptyComponent = {
  sku: "", name: "", category: "", package_name: "", description: "", min_stock: 0, location_id: "",
};

export function InventoryActions({ enabled, component, locations, onChanged, onLocationsChanged }: InventoryActionsProps) {
  const { logout, session } = useAuth();
  const navigate = useNavigate();
  const route = useLocation();
  const [locationDraft, setLocationDraft] = useState({ id: "", name: "" });
  const [createDraft, setCreateDraft] = useState(emptyComponent);
  const [editDraft, setEditDraft] = useState(emptyComponent);
  const [movement, setMovement] = useState({ movement_type: "inbound", quantity: 1, reason: "库存操作", note: "", location_id: "" });
  const [busy, setBusy] = useState<string | null>(null);
  const [notice, setNotice] = useState<{ kind: "success" | "error"; text: string } | null>(null);
  const [recoverableMovement, setRecoverableMovement] = useState<ReturnType<typeof loadPendingRequest>>(null);
  const memoryPending = useRef(new Map<string, NonNullable<ReturnType<typeof loadPendingRequest>>>());

  useEffect(() => {
    if (!component) return;
    setEditDraft({
      sku: component.sku, name: component.name, category: component.category,
      package_name: component.package_name, description: component.description ?? "",
      min_stock: component.min_stock, location_id: "",
    });
    setMovement((value) => ({ ...value, location_id: "" }));
  }, [component]);

  useEffect(() => {
    if (!session || !component) {
      setRecoverableMovement(null);
      return;
    }
    const key = pendingStorageKey(session.apiBaseUrl, `movement:${component.id}`);
    setRecoverableMovement(loadPendingRequest(key) ?? memoryPending.current.get(key) ?? null);
  }, [component, session]);

  const movementLocations = useMemo(() => {
    if (!component?.inventory_managed) return [];
    if (movement.movement_type === "inbound") return locations;
    const quantities = new Map(component.allocations.map((item) => [item.location_id, item.quantity]));
    return locations.filter((item) => (quantities.get(item.id) ?? 0) > 0);
  }, [component, locations, movement.movement_type]);

  async function submit<T>(key: string, payload: object, request: (body: object) => Promise<T>, done: (value: T) => void) {
    if (!session) return;
    const storageKey = pendingStorageKey(session.apiBaseUrl, key);
    const previous = loadPendingRequest(storageKey) ?? memoryPending.current.get(storageKey) ?? null;
    const pending = requestForPayload(previous, payload);
    memoryPending.current.set(storageKey, pending);
    const persisted = savePendingRequest(storageKey, pending);
    if (key.startsWith("movement:")) setRecoverableMovement(pending);
    setBusy(key);
    setNotice(null);
    try {
      const value = await request({ ...payload, request_id: pending.requestId });
      clearPendingRequest(storageKey);
      memoryPending.current.delete(storageKey);
      if (key.startsWith("movement:")) setRecoverableMovement(null);
      done(value);
    } catch (error) {
      if (error instanceof ApiError && !shouldRetainPending(error.status)) {
        clearPendingRequest(storageKey);
        memoryPending.current.delete(storageKey);
        if (key.startsWith("movement:")) setRecoverableMovement(null);
      }
      if (error instanceof ApiError && error.status === 401) {
        logout();
        navigate("/login", { replace: true, state: { from: `${route.pathname}${route.search}`, reason: "登录已失效，请重新验证 API 令牌。" } });
        return;
      }
      if (error instanceof ApiError && error.status === 409) onChanged();
      const baseText = error instanceof ApiError ? error.message : "操作失败，请稍后重试。";
      const text = !persisted && error instanceof ApiError && error.status === 0
        ? `${baseText} 浏览器无法保存恢复信息，请保持本页打开并使用原表单重试。`
        : baseText;
      setNotice({ kind: "error", text });
    } finally {
      setBusy(null);
    }
  }

  function createLocation(event: FormEvent) {
    event.preventDefault();
    const payload = { id: locationDraft.id.trim(), name: locationDraft.name.trim() };
    void submit<AdminStorageLocation>("create-location", payload, (body) => postJson(session!, "/admin-api/storage-locations", body), () => {
      setLocationDraft({ id: "", name: "" });
      setNotice({ kind: "success", text: "库位已创建，可以用于新元件和入库。" });
      onLocationsChanged();
    });
  }

  function createComponent(event: FormEvent) {
    event.preventDefault();
    const payload = { ...createDraft, sku: createDraft.sku.trim(), name: createDraft.name.trim(), category: createDraft.category.trim(), package_name: createDraft.package_name.trim(), description: createDraft.description.trim() || null, min_stock: Number(createDraft.min_stock) };
    void submit<AdminComponentDetail>("create-component", payload, (body) => postJson(session!, "/admin-api/components", body), (value) => {
      setCreateDraft(emptyComponent);
      setNotice({ kind: "success", text: `已创建 ${value.sku}，初始库存为 0。` });
      onChanged(value);
    });
  }

  function updateComponent(event: FormEvent) {
    event.preventDefault();
    if (!component) return;
    const payload = { expected_updated_at: component.updated_at, sku: editDraft.sku.trim(), name: editDraft.name.trim(), category: editDraft.category.trim(), package_name: editDraft.package_name.trim(), description: editDraft.description.trim() || null, min_stock: Number(editDraft.min_stock) };
    void submit<AdminComponentDetail>(`edit:${component.id}`, payload, (body) => putJson(session!, `/admin-api/components/${encodeURIComponent(component.id)}`, body), (value) => {
      setNotice({ kind: "success", text: "元器件资料已保存。" });
      onChanged(value);
    });
  }

  function applyMovement(event: FormEvent) {
    event.preventDefault();
    if (!component) return;
    if (recoverableMovement) {
      setNotice({ kind: "error", text: "请先恢复并确认上一笔库存操作，再发起新的入出库。" });
      return;
    }
    const payload: Record<string, unknown> = {
      expected_updated_at: component.updated_at,
      movement_type: movement.movement_type,
      quantity: Number(movement.quantity),
      reason: movement.reason.trim(),
      note: movement.note.trim() || null,
    };
    payload.location_id = component.inventory_managed ? movement.location_id : null;
    void submit<AdminComponentDetail>(`movement:${component.id}`, payload, (body) => postJson(session!, `/admin-api/components/${encodeURIComponent(component.id)}/movements`, body), (value) => {
      setNotice({ kind: "success", text: `${movement.movement_type === "inbound" ? "入库" : "出库"}完成，当前库存 ${value.quantity}。` });
      setMovement((item) => ({ ...item, quantity: 1, note: "", location_id: "" }));
      onChanged(value);
    });
  }

  function recoverMovement() {
    if (!component || !recoverableMovement) return;
    const payload = JSON.parse(recoverableMovement.payload) as Record<string, unknown>;
    void submit<AdminComponentDetail>(`movement:${component.id}`, payload, (body) => postJson(session!, `/admin-api/components/${encodeURIComponent(component.id)}/movements`, body), (value) => {
      setNotice({ kind: "success", text: `已通过原请求编号核对操作结果，当前库存 ${value.quantity}。` });
      onChanged(value);
    });
  }

  if (!enabled) {
    return <Alert><AlertTitle>当前为只读模式</AlertTitle><AlertDescription>服务端未启用 Web 库存操作。你仍可搜索、筛选和查看库存详情。</AlertDescription></Alert>;
  }

  return (
    <div className="space-y-5">
      {notice ? <Alert variant={notice.kind === "error" ? "destructive" : "default"}><AlertTitle>{notice.kind === "error" ? "操作未完成" : "操作完成"}</AlertTitle><AlertDescription>{notice.text}</AlertDescription></Alert> : null}
      {component && recoverableMovement ? <Alert><AlertTitle>有一笔结果未确认的库存操作</AlertTitle><AlertDescription className="space-y-3"><p>请先恢复上一笔操作。系统会识别这次重试，不会重复增加或扣减库存。你也可以先刷新详情核对最新库存。</p><div className="flex flex-wrap gap-2"><Button type="button" size="sm" disabled={busy !== null} onClick={recoverMovement}>恢复上次操作</Button><Button type="button" size="sm" variant="outline" disabled={busy !== null} onClick={() => onChanged()}>刷新库存详情</Button></div></AlertDescription></Alert> : null}
      <div className="grid gap-5 xl:grid-cols-2">
        <Card className="bg-white/90"><CardHeader><CardTitle>创建库位</CardTitle></CardHeader><CardContent>
          <form className="flex flex-col gap-3 sm:flex-row sm:items-end" onSubmit={createLocation}>
            <Field label="库位编码" className="flex-1"><Input required minLength={1} maxLength={120} value={locationDraft.id} onChange={(event) => setLocationDraft({ ...locationDraft, id: event.target.value })} placeholder="例如 A01 或 主仓-01" disabled={busy === "create-location"} /></Field>
            <Field label="库位名称" className="flex-1"><Input required maxLength={200} value={locationDraft.name} onChange={(event) => setLocationDraft({ ...locationDraft, name: event.target.value })} placeholder="例如 主货架 A-01" disabled={busy === "create-location"} /></Field>
            <Button className="min-h-11" disabled={busy !== null}>{busy === "create-location" ? "正在创建…" : "创建库位"}</Button>
          </form>
        </CardContent></Card>
        <Card className="bg-white/90"><CardHeader><CardTitle>新建元器件</CardTitle></CardHeader><CardContent>
          {locations.length === 0 ? <p className="mb-4 text-sm text-amber-700">请先创建一个库位。新元件会以 0 库存建立初始分配。</p> : null}
          <form className="grid gap-3 sm:grid-cols-2" onSubmit={createComponent}>
            <TextField label="料号" required value={createDraft.sku} onChange={(value) => setCreateDraft({ ...createDraft, sku: value })} />
            <TextField label="名称" required value={createDraft.name} onChange={(value) => setCreateDraft({ ...createDraft, name: value })} />
            <TextField label="分类" required value={createDraft.category} onChange={(value) => setCreateDraft({ ...createDraft, category: value })} />
            <TextField label="封装" required value={createDraft.package_name} onChange={(value) => setCreateDraft({ ...createDraft, package_name: value })} />
            <TextField label="最低库存" type="number" min="0" required value={String(createDraft.min_stock)} onChange={(value) => setCreateDraft({ ...createDraft, min_stock: Number(value) })} />
            <LocationSelect label="初始库位" required locations={locations} value={createDraft.location_id} onChange={(value) => setCreateDraft({ ...createDraft, location_id: value })} />
            <TextField label="说明（可选）" className="sm:col-span-2" value={createDraft.description} onChange={(value) => setCreateDraft({ ...createDraft, description: value })} />
            <Button className="min-h-11 sm:col-span-2" disabled={busy !== null || locations.length === 0}>{busy === "create-component" ? "正在创建…" : "创建元器件"}</Button>
          </form>
        </CardContent></Card>
      </div>

      {component ? <div className="grid gap-5 xl:grid-cols-2">
        <Card className="bg-white/90"><CardHeader><CardTitle>编辑 {component.sku}</CardTitle></CardHeader><CardContent>
          <form className="grid gap-3 sm:grid-cols-2" onSubmit={updateComponent}>
            <TextField label="料号" required value={editDraft.sku} onChange={(value) => setEditDraft({ ...editDraft, sku: value })} />
            <TextField label="名称" required value={editDraft.name} onChange={(value) => setEditDraft({ ...editDraft, name: value })} />
            <TextField label="分类" required value={editDraft.category} onChange={(value) => setEditDraft({ ...editDraft, category: value })} />
            <TextField label="封装" required value={editDraft.package_name} onChange={(value) => setEditDraft({ ...editDraft, package_name: value })} />
            <TextField label="最低库存" type="number" min="0" required value={String(editDraft.min_stock)} onChange={(value) => setEditDraft({ ...editDraft, min_stock: Number(value) })} />
            <TextField label="说明（可选）" value={editDraft.description} onChange={(value) => setEditDraft({ ...editDraft, description: value })} />
            <Button className="min-h-11 sm:col-span-2" disabled={busy !== null}>{busy === `edit:${component.id}` ? "正在保存…" : "保存资料"}</Button>
          </form>
        </CardContent></Card>
        <Card className="bg-white/90"><CardHeader><CardTitle>库存入出库</CardTitle></CardHeader><CardContent>
          {!component.inventory_managed ? <p className="mb-4 text-sm text-muted-foreground">这是旧版库存记录，入出库会继续沿用当前默认库位。</p> : null}
          <form className="grid gap-3 sm:grid-cols-2" onSubmit={applyMovement}>
            <Field label="操作"><select className="h-11 w-full rounded-xl border bg-white px-3 text-sm" value={movement.movement_type} onChange={(event) => setMovement({ ...movement, movement_type: event.target.value, location_id: "" })}><option value="inbound">入库</option><option value="outbound">出库</option></select></Field>
            <TextField label="数量" type="number" min="1" required value={String(movement.quantity)} onChange={(value) => setMovement({ ...movement, quantity: Number(value) })} />
            {component.inventory_managed ? <LocationSelect label="操作库位" required locations={movementLocations} allocations={component.allocations} value={movement.location_id} onChange={(value) => setMovement({ ...movement, location_id: value })} /> : null}
            <TextField label="原因" required value={movement.reason} onChange={(value) => setMovement({ ...movement, reason: value })} />
            <TextField label="备注（可选）" className="sm:col-span-2" value={movement.note} onChange={(value) => setMovement({ ...movement, note: value })} />
            <Button className="min-h-11 sm:col-span-2" disabled={busy !== null || recoverableMovement !== null || (component.inventory_managed && movementLocations.length === 0)}>{busy?.startsWith("movement:") ? <RefreshCw className="mr-2 h-4 w-4 animate-spin" /> : null}{recoverableMovement ? "请先处理上次操作" : `确认${movement.movement_type === "inbound" ? "入库" : "出库"}`}</Button>
          </form>
        </CardContent></Card>
      </div> : null}
    </div>
  );
}

function Field({ label, className, children }: { label: string; className?: string; children: React.ReactNode }) {
  return <label className={`space-y-1.5 ${className ?? ""}`}><span className="text-sm font-medium">{label}</span>{children}</label>;
}

function TextField({ label, value, onChange, className, ...input }: { label: string; value: string; onChange: (value: string) => void; className?: string } & Omit<React.InputHTMLAttributes<HTMLInputElement>, "value" | "onChange">) {
  return <Field label={label} className={className}><Input value={value} onChange={(event) => onChange(event.target.value)} {...input} /></Field>;
}

function LocationSelect({ label, locations, allocations = [], value, onChange, required }: { label: string; locations: AdminStorageLocation[]; allocations?: Array<{ location_id: string; quantity: number }>; value: string; onChange: (value: string) => void; required?: boolean }) {
  const quantities = new Map(allocations.map((item) => [item.location_id, item.quantity]));
  return <Field label={label}><select required={required} className="h-11 w-full rounded-xl border bg-white px-3 text-sm" value={value} onChange={(event) => onChange(event.target.value)}><option value="">请选择库位</option>{locations.map((location) => <option key={location.id} value={location.id}>{location.name}{quantities.has(location.id) ? `（${quantities.get(location.id)}）` : ""}</option>)}</select></Field>;
}
