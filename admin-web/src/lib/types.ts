export interface AdminSession {
  apiBaseUrl: string;
  token: string;
}

export interface AdminMetricSnapshot {
  component_count: number;
  total_units: number;
  low_stock_count: number;
  movement_count: number;
}

export interface AdminComponentRecord {
  id: string;
  sku: string;
  name: string;
  category: string;
  location: string;
  quantity: number;
  min_stock: number;
  updated_at: string;
  status: string;
}

export interface AdminLowStockRecord {
  id: string;
  sku: string;
  name: string;
  location: string;
  quantity: number;
  min_stock: number;
  updated_at: string;
}

export interface AdminMovementRecord {
  id: string;
  sku: string;
  component_name: string;
  movement_type: "inbound" | "outbound" | "adjustment" | "transfer";
  quantity: number;
  reason: string;
  note: string | null;
  happened_at: string;
  updated_at: string;
}

export interface AdminKeyValueItem {
  label: string;
  value: string;
}

export interface AdminDashboardResponse {
  metrics: AdminMetricSnapshot;
  recent_components: AdminComponentRecord[];
  sync_notes: string[];
}

export interface AdminInventoryResponse {
  metrics: AdminMetricSnapshot;
  low_stock_components: AdminLowStockRecord[];
  recent_components: AdminComponentRecord[];
  inventory_rules: AdminKeyValueItem[];
}

export interface AdminComponentListItem {
  id: string;
  sku: string;
  name: string;
  category: string;
  package_name: string;
  location: string;
  quantity: number;
  min_stock: number;
  updated_at: string;
  low_stock: boolean;
}

export interface AdminComponentListResponse {
  items: AdminComponentListItem[];
  page: number;
  page_size: number;
  total: number;
  page_count: number;
}

export interface AdminComponentDetail extends AdminComponentListItem {
  description: string | null;
  allocations: Array<{ location_id: string; quantity: number }>;
}

export interface AdminSyncResponse {
  metrics: AdminMetricSnapshot;
  recent_movements: AdminMovementRecord[];
  sync_assumptions: AdminKeyValueItem[];
  attention_items: string[];
}

export interface AdminSettingsResponse {
  runtime_configuration: AdminKeyValueItem[];
  access_posture: AdminKeyValueItem[];
  next_backend_additions: string[];
}

export interface MqttConfiguration {
  enabled: boolean;
  host: string;
  port: number;
  tls: boolean;
  username: string;
  topic_prefix: string;
  client_id: string;
  password_configured: boolean;
  source: "environment" | "saved";
  restart_required: boolean;
  message: string;
}

export interface MqttRuntimeStatus {
  enabled: boolean;
  connected: boolean;
  pending: number;
  last_publish_at: string | null;
  error: string | null;
}
