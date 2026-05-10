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

### `GET /sync/pull`

Downloads all remote changes after the provided timestamp. Omit `since` for a
full snapshot.

```powershell
curl "http://localhost:8787/sync/pull?since=2026-05-07T00:00:00Z" `
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
match.

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
`lcsc_openapi+local_rules` and the response can fill `name`, `brand`, or
`category_path` from LCSC while keeping local package or family inference.

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
