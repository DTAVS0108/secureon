# React single-page app

Signs in with SecureOne (authorization code + PKCE, no secret) using [`react-oidc-context`](https://github.com/authts/react-oidc-context), shows the user, and calls the [Express API sample](../express-api) with the access token.

## Register it

Console → **Connect an app** → **Single-page app** → **React**. Keep the suggested redirect URL `http://localhost:5173/` and note the client ID.

## Run

```bash
cp .env.example .env.local   # set VITE_SECUREONE_ISSUER and VITE_SECUREONE_CLIENT_ID
npm install
npm run dev                  # http://localhost:5173
```

Start the API sample too (port 4000) and add this client ID to its `ALLOWED_AUDIENCES` to use the **Call API** buttons.

## What to look at

- `src/main.jsx`: the whole SecureOne configuration (issuer, client ID, redirect URLs, scopes).
- `src/App.jsx`: `signinRedirect()`, `signoutRedirect()`, and `Authorization: Bearer <access token>` on API calls.
- No CORS setup is needed in SecureOne: the origin of a registered redirect URL (`http://localhost:5173`) is allowed automatically.
- Tokens are kept in session storage and renewed with the refresh token before they expire.
