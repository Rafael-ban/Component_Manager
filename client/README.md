# Legacy Flutter Client Reference

This directory contains the earlier Flutter proof-of-concept used to define
field names, local-first behavior, and page inventory before the project moved
to native clients.

## Current Role

- Keep this code as:
  - a migration reference for business fields
  - a reference for screen inventory and local-first flow
  - a fallback prototype if native clients need behavior comparison
- Do not treat this directory as the primary production client UI target.

## Features In This Initial Build

- Local SQLite inventory database
- Component CRUD
- Stock movement history
- Manual sync
- Optional background sync timer
- Server URL and API token settings
- Soft delete for synchronized records
- Last-write-wins sync on `updated_at`
- Duplicate active `sku` prevention

## Current Local Data Layout

- `components`: inventory records rendered by the dashboard and component list
- `stock_movements`: inbound, outbound, and adjustment history
- `sync_queue`: local-only queue of pending sync work

## Settings Required For Sync

- Server base URL
- API token
- Optional auto sync toggle

## Native Replacements

- Android production direction: `android-client/` with Jetpack Compose
- Windows production direction: `windows-client/` with WinUI 3
