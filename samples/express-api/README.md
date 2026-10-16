# Express API that accepts SecureOne tokens

A small API that checks SecureOne access tokens and permissions with [`jose`](https://github.com/panva/jose). The React and CLI samples call it.

| Route | Needs |
|-------|-------|
| `GET /api/health` | nothing |
| `GET /api/me` | a valid access token; returns the user, roles and permissions from the token |
| `GET /api/orders` | a valid access token with the `orders:read` permission |

Nothing is registered in SecureOne for an API: it only checks tokens other apps send. In the console, **Connect an app → API that accepts SecureOne tokens → Node.js** shows the same setup.

## Run

```bash
cp .env.example .env        # set SECUREONE_ISSUER and ALLOWED_AUDIENCES
npm install
npm start                   # http://localhost:4000
```

`ALLOWED_AUDIENCES` lists the client IDs of the apps allowed to call the API: a SecureOne access token's audience (`aud`) is the client that requested it.

## Permissions

`/api/orders` needs `orders:read`. In the console, open the application → **Permissions** → add `orders:read`, put it in a role (**Roles**), and give the role to a user (**Users**). The next token that user gets carries it in the `permissions` claim.

## How it works

- Keys come from `{issuer}/oauth2/jwks` and are cached; key rotation needs no change here.
- `jwtVerify` checks signature, issuer, audience and expiry.
- Errors follow RFC 6750: `401` with `WWW-Authenticate: Bearer` for a missing or invalid token, `403` for a missing permission.
