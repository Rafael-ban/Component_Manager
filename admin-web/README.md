# Admin Web

This directory contains the separated web admin console built with `React`,
`Vite`, `Tailwind CSS`, and `shadcn/ui`.

## Current State

- Separated login flow implemented against `POST /auth/ping`
- Read-only dashboard, inventory, sync, and settings pages implemented
- Shared bearer token model aligned with the FastAPI sync service
- Protected route flow implemented with browser local storage session state
- `npm run build` verified successfully on `2026-05-08`

## Local Development

```powershell
cd admin-web
cmd /c npm install
cmd /c npm run dev
```

Default local URL:

- `http://localhost:5173`

Default API target:

- `http://localhost:8787`

The login page allows overriding the API base URL before validating the token.

## Build

```powershell
cd admin-web
cmd /c npm run build
```

Production output:

- `admin-web/dist/`

GitHub `release.yml` also packages this directory as:

- `component-vault-admin-web.zip`

## Container

The Docker image uses a Node build stage and an Nginx runtime stage.

When using `docker compose up --build`, the admin web container is published at:

- `http://localhost:8081`

## Backend Dependency

The web admin expects these FastAPI endpoints:

- `POST /auth/ping`
- `GET /admin-api/dashboard`
- `GET /admin-api/inventory`
- `GET /admin-api/sync`
- `GET /admin-api/settings`

The backend must allow the browser origin through `ADMIN_WEB_ORIGINS`.
