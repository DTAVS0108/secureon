# admin-web

Next.js admin console and platform operator UI for SecureOne.

## Prerequisites

- Node.js 20+
- **auth-server** running on `http://localhost:9000` (see [Installation](../../docs/developer/local-setup.md))
- Platform super-admin credentials (dev default: `admin` / `admin`)

## Setup

```bash
cd apps/admin-web
cp .env.local.example .env.local   # if present; set AUTH_SERVER_URL
npm install
npm run dev
```

Open **http://localhost:3001/admin** (dev server; the console lives under `/admin`), or http://localhost:9000/admin through the auth-server.

## Key routes

| Area | Who | Path |
|------|-----|------|
| Login | Everyone | `/login` |
| OAuth client registry | Platform super-admin | `/applications` |
| Application console | App operators | `/app/{applicationId}/users` (and roles, settings, …) |
| Tenants | Platform / tenant super-admin | `/tenants` |
| Platform settings | Platform super-admin | `/settings` |
| **SecureOne Documentation** | All signed-in operators | `/documentation` |

## SecureOne Documentation

Standalone in-app documentation browser : one **Documentation** section with architecture, database, installation, application integration, ADRs, and more.

| Access | URL |
|--------|-----|
| UI | `/documentation` |
| Open from console | **Platform admin → SecureOne Documentation** (opens in a **new tab**) |
| Catalog API | `GET /api/documentation` (requires login session) |
| Page content API | `GET /api/documentation/{slug}` |
| Assets API | `GET /api/documentation/assets/{path}` (`.drawio` download) |
| Public discovery | `GET {auth-server}/api/v1/documentation` |

### UX

- **Standalone layout** — `/documentation` routes skip `ConsoleShell` (no platform/app sidebar or topbar); only the Documentation doc catalog sidebar is shown.
- **Categories** — sidebar groups pages: Getting started, Platform (fundamentals / operations / reference), Application integration.
- **Draw.io** — diagrams embed **inline on the parent doc page** (not a separate viewer route). Requires internet for `embed.diagrams.net`.
- **Legacy diagram URLs** — `/documentation/diagram/...` redirect to the doc page that contains the embed.

Source markdown is read from the monorepo `docs/` folder at runtime (`../../docs` from admin-web cwd).

See [Environment & operations](../../docs/install/environment-and-operations.md) for `start-all.ps1`, migrations, and schedulers.

Implementation: `src/lib/documentation/`, `src/app/documentation/`, `src/components/documentation/`.

## Register application + OAuth client

On **/applications**, use **+ Register application & OAuth client** for a new product and its first OAuth credentials in one form. Use **+ Add OAuth client** to attach another client to an existing application.

See [Applications & OAuth clients](../../docs/integrate/applications-and-oauth-clients.md).

## Environment

| Variable | Purpose |
|----------|---------|
| `AUTH_SERVER_URL` | Backend base URL (default `http://localhost:9000`) |

## Scripts

```bash
npm run dev      # development server
npm run build    # production build
npm run start    # run production build
npm run lint     # ESLint
```

## Architecture notes

- Server actions in `src/lib/actions.ts` call auth-server admin APIs.
- Browser calls go through `/api/proxy` where needed for cookies/CORS.
- `ConsoleShell` switches between **platform sidebar** and **app sidebar** based on route (`/app/...`). `/documentation` uses a standalone layout (no console chrome).
