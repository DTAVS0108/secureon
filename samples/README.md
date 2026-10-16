# SecureOne sample apps

Small, complete apps that sign in with SecureOne using standard OpenID Connect / OAuth 2 libraries. Each one is standalone: copy its folder out of this repository and run it.

| Sample | Kind | Library | Port |
|--------|------|---------|------|
| [react-spa](react-spa) | Single-page app (PKCE, no secret) | `react-oidc-context` | 5173 |
| [nextjs-web](nextjs-web) | Web app with a server (client secret) | NextAuth.js v4 | 3000 |
| [express-api](express-api) | API that accepts SecureOne tokens | `jose` | 4000 |
| [device-cli](device-cli) | Command-line app (device code) | none (Node 22 `fetch`) | — |

The React app and the CLI call the Express API with their access tokens; the API checks the token and the `orders:read` permission.

## Before you start

1. SecureOne running (for example the quickstart: `docker compose -f deploy/docker-compose.quickstart.yml up --build`, then http://localhost:9000/admin).
2. A user who can sign in to the application (console → application → **Users**).
3. Register each sample in the console with **Connect an app**: pick the kind and framework from the table and keep the suggested redirect URL. The wizard shows the client ID (and, for the Next.js app, the secret) to put in the sample's `.env` file.

Every sample has a `.env.example` listing exactly what it needs.

## Other frameworks

**Connect an app** also gives ready-to-paste setup for Spring Boot, Express (server-rendered), ASP.NET Core, Angular, plain JavaScript, React Native, Android, iOS, Flutter, and backend services in curl, Node.js, Java and Python.
