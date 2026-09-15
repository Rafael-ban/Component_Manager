# Integration Guide

## Base URL

The self-hosted server exposes HTTP endpoints under the configured base URL.
Examples in this document assume `http://localhost:8787`.

## Authentication

- `GET /health` does not require authentication.
- All sync endpoints require `Authorization: Bearer <API_TOKEN>`.
- All `/admin-api/*` endpoints also require `Authorization: Bearer <API_TOKEN>`.
- The server also accepts `X-API-Token`, but the client uses bearer auth.
- The separated `admin-web/` console authenticates through `POST /auth/ping`
  and then calls `/admin-api/*`.
- Android JLC import enrichment also uses bearer auth against
  `GET /admin-api/part-lookup`, while `GET /admin-api/lcsc/lookup` remains a
  direct compatibility route.

## Endpoints

### `GET /health`

Use this for smoke checks and container health probes.

```powershell
curl http://localhost:8787/health
```

Response body:

```json
{
  "status": "ok",
  "server_time": "2026-05-07T06:00:00Z"
}
```

### `POST /auth/ping`

Validates that the configured token is accepted.

```powershell
curl -X POST http://localhost:8787/auth/ping `
  -H "Authorization: Bearer change-me"
```

### `POST /sync/push`

Uploads the latest client-side state for synchronized entities.

```powershell
$body = @'
{
  "device_id": "desktop-1",
  "components": [
    {
      "id": "cmp-1",
      "sku": "ATMEGA328P-AU",
      "name": "MCU",
      "category": "Microcontroller",
      "package_name": "TQFP-32",
      "location": "Drawer A1",
      "description": "Main controller",
      "quantity": 24,
      "min_stock": 5,
      "updated_at": "2026-05-07T06:00:00Z",
      "deleted": false
    }
  ],
  "stock_movements": [
    {
      "id": "mov-1",
      "component_id": "cmp-1",
      "movement_type": "inbound",
      "quantity": 24,
      "reason": "Initial stock",
      "note": "",
      "happened_at": "2026-05-07T06:00:00Z",
      "updated_at": "2026-05-07T06:00:00Z",
      "deleted": false
    }
  ]
}
'@

curl -X POST http://localhost:8787/sync/push `
  -H "Authorization: Bearer change-me" `
  -H "Content-Type: application/json" `
  -d $body
```

Success response:

```json
{
  "accepted_components": 1,
  "accepted_stock_movements": 1,
  "server_time": "2026-05-07T06:00:01Z"
}
```

A push is atomic across components and stock movements. Duplicate active SKU or
missing component references return HTTP 409 and roll back the entire batch.
Legacy LWW compares UTC instants rather than timestamp text. Equal-timestamp arrivals
retain the existing last-arrival policy; retrying an identical payload does not
create duplicate entities, although the sync revision may advance.

### Multi-location inventory protocol 1

`GET /health` and authenticated `POST /auth/ping` advertise `inventory_protocol: 1`.
New native clients check this before uploading. They send the following additional fields:

```json
{
  "device_id": "desktop-1",
  "inventory_protocol": 1,
  "storage_locations": [
    {"id": "A1", "name": "Drawer A1", "updated_at": "2026-09-15T00:00:00Z", "deleted": false}
  ],
  "components": [],
  "stock_movements": []
}
```

Each component includes `allocations: [{"location_id":"A1","quantity":24}]`
and `base_updated_at` (nullable for a new record). Existing component fields are
still required. Allocations are a complete snapshot, including at least one row
for a zero-stock component, and must sum to its quantity. Codes are stable location
IDs; names are editable. All amounts are bounded integers, never supplier stock.

Existing managed records require the incoming base to equal the server's current
`updated_at` as a UTC instant. Exact identical retries are accepted. Conflicts
return HTTP 409 and roll back locations, components, movements, revisions and MQTT
outbox together. Legacy clients cannot overwrite managed records. This is an
optimistic concurrency check, not automatic reconciliation of offline deltas.

Movements may contain `location_id` and `destination_location_id`. New type
`transfer` requires a positive quantity and two different locations. It leaves
the component total unchanged. New movements accompany their component snapshot
in the same push; existing protocol-1 history cannot be rewritten. An unstocked
location can be tombstoned; a location with positive active stock cannot.

Pull responses add `inventory_protocol: 1` and the complete `storage_locations`
list, including tombstones. Locations, allocation snapshots, component/movement
rows and `sync_cursor` come from one read transaction. Existing legacy components
return `allocations: null`; new clients migrate these to a single-bin snapshot.
Clients must retain edits made after the upload snapshot and advance their base
only to the version actually acknowledged, rather than silently adopting a later
unseen remote edit. A failed push must not clear local queues.

### `GET /sync/pull`

Use `cursor=0` for the first full snapshot, then send the previous response's
`sync_cursor`. The non-negative integer cursor is issued by the server; never
convert a timestamp into it. `cursor` takes precedence over legacy `since`.

The response includes `server_time`, `sync_cursor`, `components`, and
`stock_movements`. Both collections and `sync_cursor` are read from one database
snapshot. Changes accepted after that snapshot are returned by the next pull,
even if their client `updated_at` is old because the device was offline.

Omitting both parameters also returns a full snapshot. Legacy `since` is still
accepted and compares UTC instants, but its timestamp semantics cannot discover
all late offline writes. Updated native clients fall back to full pulls when an
older server omits `sync_cursor`. Apply all returned entities successfully before
persisting the new cursor; clear it when changing servers.

```powershell
curl "http://localhost:8787/sync/pull?cursor=0" `
  -H "Authorization: Bearer change-me"
```

### `GET /admin-api/dashboard`

Returns the top-level read-only admin snapshot used by the separated web
console.

```powershell
curl http://localhost:8787/admin-api/dashboard `
  -H "Authorization: Bearer change-me"
```

### `GET /admin-api/inventory`

Returns low-stock watchlist data plus the latest server-side component rows.

### `GET /admin-api/sync`

Returns recent stock movement activity and sync posture details for the admin
console.

### `GET /admin-api/settings`

Returns runtime configuration and operational endpoint details used by the
admin console.

### `GET /admin-api/part-lookup`

Looks up hybrid recognition metadata for JLC/LCSC imports and other supplier
payloads. The server first applies bundled or refreshed rule-pack matches using
`sku`, `mpn`, `name`, `brand`, `package_hint`, and `source_type`. If LCSC
credentials are configured and the request includes `sku`, `mpn`, or `name`,
the server may then merge official supplier metadata on top of the local-rule
match. When `ENABLE_WEB_FALLBACK_RESOLVERS=true`, the same endpoint can also
fall back to public LCSC product pages if OpenAPI credentials are unavailable.

Canonical `name` should be treated as unresolved when blank. Clients should not
silently replace it with `mpn` or `sku`; those remain reference fields until
the user or the server confirms a real part name.

Query parameters:

- `sku`: LCSC/JLC part number such as `C30926`
- `mpn`: manufacturer part number such as `0603B104K500NT`
- `name`: optional product title or OCR text fragment
- `brand`: optional vendor or brand hint
- `package_hint`: optional package text such as `0603` or `SOT-23`
- `source_type`: optional source marker such as `JlcQr`, `JlcText`, or `SupplierOcr`

At least one of `sku`, `mpn`, `name`, `brand`, or `package_hint` is required.

```powershell
curl "http://localhost:8787/admin-api/part-lookup?sku=C30926&mpn=0603B104K500NT&source_type=JlcQr" `
  -H "Authorization: Bearer change-me"
```

Typical success response from bundled rules only:

```json
{
  "found": true,
  "source": "local_rules",
  "sku": "C30926",
  "mpn": "0603B104K500NT",
  "package_name": "0603",
  "category": "Capacitor / MLCC",
  "vendor": "Generic",
  "model_family": "MLCC",
  "official_url": "https://www.lcsc.com/product-detail/C30926.html",
  "matched_by": "jlc_generic_mlcc",
  "confidence": "medium",
  "cache_hit": false,
  "rule_version": "2026.05.10.2"
}
```

If official supplier credentials are configured, the `source` field may become
`lcsc_openapi+local_rules`. If public-web fallback is enabled instead, the
`source` field may become `lcsc_public_web+local_rules`. In both cases the
response can fill `name`, `brand`, or `category_path` while keeping local
package or family inference.

### `GET /admin-api/recognition-rules/meta`

Returns information about the active server-side recognition rule pack.

```powershell
curl http://localhost:8787/admin-api/recognition-rules/meta `
  -H "Authorization: Bearer change-me"
```

Typical response:

```json
{
  "version": "2026.05.10.2",
  "updated_at": "2026-05-10T12:00:00Z",
  "source": "bundled",
  "active_path": ".../server/app/recognition_rules.json",
  "override_path": ".../data/recognition_rules_cache.json",
  "remote_url": null,
  "web_fallback_enabled": false,
  "refreshed": false
}
```

### `POST /admin-api/recognition-rules/refresh`

Refreshes the cached server-side rule pack from `IMPORT_RULES_REMOTE_URL` when
that environment variable is configured. If no remote URL is configured, the
endpoint returns the active metadata without changing the bundled rule pack.

```powershell
curl -X POST http://localhost:8787/admin-api/recognition-rules/refresh `
  -H "Authorization: Bearer change-me"
```

### `GET /admin-api/lcsc/lookup`

Looks up official supplier metadata for JLC/LCSC parts directly. This endpoint
is retained as a compatibility proxy when callers need the raw LCSC-focused
result without the server's hybrid local-rule merge behavior.

Query parameters:

- `sku`: LCSC/JLC part number such as `C30926`
- `mpn`: manufacturer part number such as `0603B104K500NT`
- `name`: optional fallback product name for search matching

At least one of `sku`, `mpn`, or `name` is required.

```powershell
curl "http://localhost:8787/admin-api/lcsc/lookup?sku=C30926&mpn=0603B104K500NT" `
  -H "Authorization: Bearer change-me"
```

Typical success response:

```json
{
  "found": true,
  "source": "lcsc_openapi",
  "sku": "C30926",
  "name": "Multilayer Ceramic Capacitors MLCC - SMD/SMT 100nF 50V 0603",
  "mpn": "0603B104K500NT",
  "package_name": "0603",
  "category": "Capacitor",
  "category_path": "Capacitors / Ceramic Capacitors",
  "brand": "FH(Guangdong Fenghua Advanced Tech)",
  "official_url": "https://www.lcsc.com/product-detail/C30926.html",
  "matched_by": "sku",
  "confidence": "exact",
  "cache_hit": false
}
```

Failure behavior:

- `422 Unprocessable Entity`: no query field was provided
- `503 Service Unavailable`: LCSC credentials are not configured on the server
- `502 Bad Gateway`: the upstream LCSC request failed

## Payload Rules

- Every synchronized entity must have `id`, `updated_at`, and `deleted`.
- `updated_at` should be an ISO 8601 UTC timestamp.
- `movement_type` must be `inbound`, `outbound`, or `adjustment`.
- `quantity` and `min_stock` must not be negative for components.
- Active components must have unique `sku`.

## Conflict Behavior

- The service keeps the newer row by comparing `updated_at`.
- Older pushed rows are ignored rather than merged field-by-field.
- Soft deletes replicate like any other entity update.

## Error Cases

- `401 Unauthorized`: bearer token missing or invalid.
- `409 Conflict`: active `sku` uniqueness violation during push.
- `422 Unprocessable Entity`: request body shape or field validation failed.
- `502 Bad Gateway`: upstream supplier lookup or rule-refresh fetch failed.
- `503 Service Unavailable`: LCSC credentials are not configured for the
  compatibility lookup path.

## Android direct public lookup

The import UI can resolve a scanned `C70565` by reading
`https://www.lcsc.com/product-detail/C70565.html` directly. It sends neither the
raw packaging QR payload nor the user's server token. It requires no server
endpoint or OpenAPI credentials. The normal Android import flow no longer calls
the server-assisted recognizer; the legacy server route is retained for older
clients. Windows uses the same public page and exact-SKU rule.

Only matching `Product` JSON-LD is accepted; unrelated recommendations and page
titles alone are insufficient. Manual SKU changes invalidate a pending result
for a different SKU. User-edited fields are preserved, and packaging quantity
is independent of supplier stock. Disable direct lookups in import settings for
an offline-only workflow. Cached results expire after seven days; repeated
failed lookups in the current importer back off for thirty seconds.

Product image URLs use the existing component description field via
`商品图片：<trusted HTTPS URL>`; original category paths are also retained there.
BOM depletion and component-hub migration produce ordinary component and
stock-movement sync entities. Their batch/file idempotency markers are local
only, not new sync objects. See [BOM and migration](bom-and-migration.md) for
formats and the limits of cross-device concurrent stock operations.

## MQTT inventory state

An optional server publisher emits accepted component snapshots to
`<MQTT_TOPIC_PREFIX>/components/<percent-encoded id>/state` with retained QoS 1.
The JSON carries `event_id`, `id`, `sku`, `name`, `category`, `package_name`,
`location`, `quantity`, `min_stock`, `updated_at`, `deleted`, and `sync_revision`.
Managed inventory also publishes `allocations` with per-location quantities.
No description, raw label data, or stock-delta command is included. Use quantity
as state; repeated delivery must not trigger another decrement. Deletions use
`deleted: true` retained tombstones. Offline client changes appear only after
successful sync. Existing sync request and response schemas remain unchanged.

`GET /admin-api/mqtt/status` requires the existing bearer token and returns
`enabled`, `connected`, `pending`, `last_publish_at`, and `error`. Configuration,
delivery semantics, and a Home Assistant sensor example are in [MQTT](mqtt.md).

`GET /admin-api/mqtt/config` returns the editable settings plus
`password_configured`, `source`, `restart_required`, and `message`, never a
password. `POST` to the same authenticated endpoint saves `enabled`, `host`,
`port`, `tls`, `username`, `topic_prefix`, `client_id`, optional `password`, and
`clear_password`. Empty/omitted password preserves the existing password;
`clear_password: true` clears it explicitly. Saved settings take effect on API
restart, override MQTT environment defaults, and do not modify inventory.
