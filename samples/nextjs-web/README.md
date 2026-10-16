# Next.js app (server-side sign-in)

A Next.js App Router app that signs in with SecureOne through [NextAuth.js](https://next-auth.js.org) v4. The client is **confidential**: the code is exchanged on the server with the client secret, and the browser only gets NextAuth's encrypted session cookie.

## Register it

Console → **Connect an app** → **Web app with a server** → **Next.js**. Keep the suggested redirect URL `http://localhost:3000/api/auth/callback/secureone`, and copy the client secret (shown once).

## Run

```bash
cp .env.example .env.local   # set NEXTAUTH_SECRET, SECUREONE_ISSUER, SECUREONE_CLIENT_ID, SECUREONE_CLIENT_SECRET
npm install
npm run dev                  # http://localhost:3000
```

## What to look at

- `lib/auth.js`: the SecureOne provider (discovery URL, client ID and secret, scopes, PKCE + state).
- `app/api/auth/[...nextauth]/route.js`: NextAuth's sign-in, callback and sign-out routes.
- `app/page.js`: reads the session on the server with `getServerSession`.

Signing out here ends the app's session; the user stays signed in to SecureOne (single sign-on) until they sign out there.
