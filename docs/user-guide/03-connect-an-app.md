# 3. Connect an app

[User guide](README.md) › Connect an app

**Where:** sidebar **Connect an app**, or the **Connect an app** button on the OAuth clients page (`/admin/connect`).

**Who:** platform operators (any tenant, can create new applications); Tenant Super Admins (all applications of their tenant); Tenant Admins (only applications assigned to them, and they cannot create new applications). Tenant operators see the note *You can add clients to the applications you manage in {tenant}.*

**Availability:** a platform operator can turn the wizard off in **Platform settings › Feature flags › Platform features › Connect an app** (on by default). Then its links are hidden and the page is not found; use [Register application & OAuth client](04-oauth-clients.md#register-application--oauth-client) instead.

**When to use it:** this is the recommended way to register software. You pick what kind of app you have and its framework; SecureOne creates the application and the OAuth client with secure settings and gives you ready-to-paste code. If you want to choose every OAuth setting yourself, use [Register application & OAuth client](04-oauth-clients.md#register-application--oauth-client) instead.

## Step 1: What are you connecting?

| Kind | Use for | Examples | Client it creates |
|------|---------|----------|-------------------|
| **Web app with a server** | Pages rendered or backed by your server, which keeps a client secret | Next.js, Spring Boot, Express, ASP.NET Core | Confidential; authorization code + refresh token; PKCE; secret sent with HTTP Basic |
| **Single-page app** | Runs entirely in the browser; no secret, sign-in uses PKCE | React, Angular, plain JavaScript | Public; authorization code + refresh token; PKCE |
| **Mobile app** | iOS or Android app; sign-in opens the system browser and returns to the app | React Native, Android, iOS, Flutter | Public; authorization code + refresh token; PKCE |
| **Desktop app** | Windows, macOS or Linux app; sign-in opens the system browser and returns to a local loopback address | Electron, .NET (WPF, WinUI, MAUI), Python | Public; authorization code + refresh token; PKCE; loopback redirect `http://127.0.0.1:<port>/callback` |
| **Backend service** | A server calling your APIs as itself, with no user (machine-to-machine) | Batch jobs, integrations, internal services | Confidential; client credentials |
| **TV or command-line app** | No keyboard or browser: the user approves on their phone or computer with a short code | Smart TVs, consoles, CLIs | Public; device code + refresh token; scopes `profile email` |
| **API that accepts SecureOne tokens** | Your API checks the access tokens your apps send | Spring Boot, Node.js, ASP.NET Core, Python | **Nothing is registered**; you only get validation code |

Web, single-page and mobile apps request the scopes `openid profile email`.

## Step 2: Framework

**Framework** picks the setup code you receive. Choosing one also fills sensible local URLs.

| Kind | Frameworks (default app URL → redirect path) |
|------|-----------|
| Web app | Next.js (NextAuth.js) `http://localhost:3000` → `/api/auth/callback/secureone`; Spring Boot `http://localhost:8080` → `/login/oauth2/code/secureone`; Node.js / Express `http://localhost:3000` → `/callback`; ASP.NET Core `https://localhost:5001` → `/signin-oidc`; Python (Flask) `http://localhost:5000` → `/auth/callback`; Python (Django) `http://localhost:8000` → `/oidc/callback/`; **Python backend + any frontend (FastAPI)** `http://localhost:5173` → `/auth/callback` |
| Single-page app | React `http://localhost:5173` → `/`; Angular `http://localhost:4200` → `/`; Vue `http://localhost:5173` → `/callback`; Plain JavaScript `http://localhost:5173` → `/callback.html` |
| Mobile app | React Native, Android (Kotlin), iOS (Swift), Flutter |
| Desktop app | Electron, .NET (WPF, WinUI, MAUI), Python — all `http://127.0.0.1:8765` → `/callback` |
| Backend service | curl, Node.js, Java (Spring), Python |
| TV or command-line app | curl / shell, Node.js CLI, Python CLI |
| API | Spring Boot, Node.js (Express), ASP.NET Core, Python (FastAPI), Python (any framework) |

### Choosing between the Python options

| You are building | Choose | How it works |
|------------------|--------|--------------|
| A Python website that renders its own pages | **Web app › Python (Flask)** or **Python (Django)** | Python signs users in and keeps them in a session. |
| A Python backend **and** a separate frontend (React, Vue, Angular…) | **Web app › Python backend + any frontend (FastAPI)** | Backend-for-frontend: Python is the OAuth client and keeps the secret and tokens; the frontend calls Python (`/api/...`) with an HttpOnly session cookie and never sees a token. In development the frontend's dev server proxies `/auth` and `/api` to Python so both share one origin. The safest choice for browser apps. |
| A frontend that holds its own tokens, calling a Python API | **Single-page app** for the frontend, then **API › Python (FastAPI)** for the API | The frontend sends `Authorization: Bearer <token>`; the API checks the token and its `permissions`. |
| A Python job with no user | **Backend service › Python** | Client credentials. |
| A Python desktop tool or CLI | **Desktop app › Python** or **TV or command-line app › Python CLI** | Loopback redirect, or a short code approved on another device. |

### Mobile and desktop are set up differently

- **Mobile** apps return through their own URL scheme (`com.example.app:/oauthredirect`) registered with the operating system; enter it in **App scheme**.
- **Desktop** apps listen on a loopback address (`http://127.0.0.1:8765/callback`) only while signing in; enter it in **Redirect URL**. If that port is busy on users' machines, register another port on the client.
- Both are public clients: no secret, PKCE, the system browser (never an embedded web view), and the refresh token kept in the operating system's secure store.

## Step 3: Register the app

The card is titled **Register the app** (**Your API** for the API kind).

| Field | What to enter |
|-------|---------------|
| **Tenant** | Whose users can sign in to the app (*Users of this tenant can sign in to the app.*). Tenant operators see only their own tenant. |
| **Application** | An existing application, or **New application…** to create one. Tenant Admins can only pick applications assigned to them. |
| **Application name** | Shown when **New application…** is chosen: *what users see when signing in*, for example `Demo Store`. |
| **Client ID** | *Your app sends this; lowercase and dashes*, for example `demo-store-web`. Must be unique. |
| **App URL** | *Where the app runs*, for example `http://localhost:3000`. Web and single-page apps. |
| **Redirect URL** | *After sign-in*. Filled from the app URL and the framework's callback path. *Must match what the app sends exactly. Add production URLs later on the client page.* |
| **After sign-out URL** | Where users land after signing out. |
| **App scheme** | Mobile apps only: *reverse domain of your app id*, for example `com.example.shop`. *Sign-in returns to the app through this URL scheme (RFC 8252).* The redirect becomes `com.example.shop:/oauthredirect`. |
| **Apps allowed to call it** | API kind only: *client IDs, comma-separated*, for example `shop-web, shop-mobile`. *Checked against the token's audience (aud), which is the client ID of the app that got the token.* Used only in the generated code. |
| **Allow a test sign-in from the console** | Adds the console's test page as an extra redirect URL so you can try a real sign-in before writing any code. Remove it later from the client page if you like. |

The summary line **Also registers…** lists anything extra that will be created (for example the test redirect).

Press **Create client**. Nothing is saved until you do; leaving the page discards the form. For the API kind nothing is created; the code appears straight away.

Tenant operators who cannot create applications see the hint *The applications you manage. Ask a platform operator to create a new one.* on **Application**.

## Step 4: Result

The card **Client created** says *Users of the selected tenant can now sign in to {app}.*

| Section | Contents |
|---------|----------|
| **Client secret, shown only now** | Confidential clients only. *Store it as SECUREONE_CLIENT_SECRET on your server. Lost it? Rotate it on the client page.* Use **Copy**. |
| **Issuer (authority)** | The issuer URL your OIDC library needs. |
| **Discovery** | The `.well-known/openid-configuration` URL. |
| **Add it to your app** | *Standard OpenID Connect libraries; values are filled in. Secrets come from the environment, never from code.* **Install** shows the package command, followed by the files to paste (for example `.env.local`, `auth.ts`, `SecurityConfig.java`). |
| **Test sign-in ↗** | Opens the test page and runs a real sign-in against the new client (needs the test option). |
| **Client settings** | Opens the client page ([chapter 4](04-oauth-clients.md#client-page)). |
| **Users & roles** | Opens the application console for the application. |

## After connecting

1. Give users access: invite users in the application console, or let them self-register (feature flag **Self Registration**).
2. Add production redirect URLs on the client page before going live.
3. For a confidential client, keep the secret only in the server's environment or secret store.
