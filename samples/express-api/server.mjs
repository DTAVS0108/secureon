// An API that accepts SecureOne access tokens.
// Every request to /api/* (except /api/health) needs "Authorization: Bearer <access token>".
// The token is checked against SecureOne's published keys, issuer and audience; /api/orders also
// requires the "orders:read" permission, which SecureOne puts in the token's "permissions" claim.
import cors from "cors";
import express from "express";
import { createRemoteJWKSet, jwtVerify } from "jose";

const ISSUER = required("SECUREONE_ISSUER").replace(/\/+$/, "");
// Client IDs of the apps allowed to call this API (a token's "aud" is the client that requested it).
const AUDIENCES = required("ALLOWED_AUDIENCES").split(",").map((s) => s.trim()).filter(Boolean);
// Browser apps that call this API directly (the React sample).
const CORS_ORIGINS = (process.env.CORS_ORIGINS ?? "http://localhost:5173").split(",").map((s) => s.trim());
const PORT = Number(process.env.PORT ?? 4000);

// Keys are fetched from SecureOne once and cached; rotated keys are picked up automatically.
const JWKS = createRemoteJWKSet(new URL(`${ISSUER}/oauth2/jwks`));

/** 401 without a valid token, 403 without the permission. Puts the token claims on req.user. */
function requireToken(permission) {
  return async (req, res, next) => {
    const token = req.headers.authorization?.match(/^Bearer (.+)$/i)?.[1];
    if (!token) {
      return res.status(401).set("WWW-Authenticate", "Bearer").json({ error: "missing_token" });
    }
    try {
      const { payload } = await jwtVerify(token, JWKS, { issuer: ISSUER, audience: AUDIENCES });
      req.user = payload;
    } catch (e) {
      return res.status(401).set("WWW-Authenticate", 'Bearer error="invalid_token"').json({ error: "invalid_token", detail: e.code ?? e.message });
    }
    if (permission && !(req.user.permissions ?? []).includes(permission)) {
      return res.status(403).json({ error: "insufficient_permission", required: permission });
    }
    next();
  };
}

const app = express();
app.use(cors({ origin: CORS_ORIGINS }));

app.get("/api/health", (_req, res) => res.json({ ok: true }));

app.get("/api/me", requireToken(), (req, res) => {
  // user_id is the stable account id (store your data against it); email and name follow the
  // email and profile scopes the app asked for.
  const { user_id, email, name, roles, permissions, aud } = req.user;
  res.json({ user_id, email, name, roles: roles ?? [], permissions: permissions ?? [], calledBy: aud });
});

app.get("/api/orders", requireToken("orders:read"), (req, res) => {
  res.json([
    { id: "A-1001", item: "Desk lamp", total: 1499 },
    { id: "A-1002", item: "Notebook", total: 249 },
  ]);
});

app.listen(PORT, () => {
  console.log(`API on http://localhost:${PORT}  (issuer ${ISSUER}; audiences ${AUDIENCES.join(", ")})`);
});

function required(name) {
  const value = process.env[name];
  if (!value) {
    console.error(`Set ${name} (see .env.example).`);
    process.exit(1);
  }
  return value;
}
