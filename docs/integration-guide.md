# Integration Guide

## Base URL

The self-hosted server exposes HTTP endpoints under the configured base URL.
Examples in this document assume `http://localhost:8787`.

## Authentication

- `GET /health` does not require authentication.
- All sync endpoints require `Authorization: Bearer <API_TOKEN>`.
- The server also accepts `X-API-Token`, but the client uses bearer auth.
- NiceGUI admin UI is served separately at `/admin/` and is not part of the API
  contract described here.

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
