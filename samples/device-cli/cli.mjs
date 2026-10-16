#!/usr/bin/env node
// A command-line app that signs in with SecureOne using the device authorization grant (RFC 8628):
// it shows a short code, the user approves it in a browser on any device, and the CLI receives tokens.
// No dependencies; Node 22+.
//
//   node cli.mjs login    sign in (stores tokens in ~/.secureone-demo-cli.json)
//   node cli.mjs whoami   call the API sample with the stored access token (refreshing it if needed)
//   node cli.mjs logout   forget the tokens
import { readFile, rm, writeFile } from "node:fs/promises";
import { homedir } from "node:os";
import { join } from "node:path";

const ISSUER = (process.env.SECUREONE_ISSUER ?? "http://localhost:9000").replace(/\/+$/, "");
const CLIENT_ID = process.env.SECUREONE_CLIENT_ID;
const API_URL = process.env.API_URL ?? "http://localhost:4000";
const STORE = process.env.SECUREONE_CLI_STORE ?? join(homedir(), ".secureone-demo-cli.json");

if (!CLIENT_ID) {
  console.error("Set SECUREONE_CLIENT_ID (see .env.example).");
  process.exit(1);
}

async function post(path, body) {
  const res = await fetch(ISSUER + path, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded", Accept: "application/json" },
    body: new URLSearchParams(body),
  });
  return { ok: res.ok, body: await res.json().catch(() => ({})) };
}

async function save(tokens) {
  const stored = { ...tokens, expires_at: Date.now() + tokens.expires_in * 1000 };
  await writeFile(STORE, JSON.stringify(stored, null, 2), { mode: 0o600 });
  return stored;
}

async function login() {
  const start = await post("/oauth2/device_authorization", { client_id: CLIENT_ID, scope: "profile email" });
  if (!start.ok) throw new Error(`device authorization failed: ${start.body.error ?? "unknown"} ${start.body.error_description ?? ""}`);
  const { device_code, user_code, verification_uri, verification_uri_complete, expires_in } = start.body;
  let interval = start.body.interval ?? 5;

  console.log(`\nTo sign in, open ${verification_uri}\nand enter the code:  ${user_code}\n`);
  console.log(`Or open: ${verification_uri_complete}\n`);
  console.log("Waiting for approval…");

  const deadline = Date.now() + expires_in * 1000;
  while (Date.now() < deadline) {
    await new Promise((r) => setTimeout(r, interval * 1000));
    const res = await post("/oauth2/token", {
      grant_type: "urn:ietf:params:oauth:grant-type:device_code",
      device_code,
      client_id: CLIENT_ID,
    });
    if (res.ok) {
      await save(res.body);
      console.log("Signed in.");
      return;
    }
    switch (res.body.error) {
      case "authorization_pending":
        continue;
      case "slow_down":
        interval += 5;
        continue;
      case "access_denied":
        throw new Error("Sign-in was denied.");
      case "expired_token":
        throw new Error("The code expired. Run login again.");
      default:
        throw new Error(`Sign-in failed: ${res.body.error}`);
    }
  }
  throw new Error("The code expired. Run login again.");
}

/** Stored tokens, refreshed when the access token is about to expire. */
async function tokens() {
  let stored;
  try {
    stored = JSON.parse(await readFile(STORE, "utf8"));
  } catch {
    throw new Error("Not signed in. Run: node cli.mjs login");
  }
  if (Date.now() < stored.expires_at - 30_000) return stored;
  const res = await post("/oauth2/token", { grant_type: "refresh_token", refresh_token: stored.refresh_token, client_id: CLIENT_ID });
  if (!res.ok) throw new Error("Session expired. Run: node cli.mjs login");
  return save({ refresh_token: stored.refresh_token, ...res.body });
}

async function whoami() {
  const { access_token } = await tokens();
  const res = await fetch(`${API_URL}/api/me`, { headers: { Authorization: `Bearer ${access_token}` } });
  const body = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(`API answered ${res.status}: ${JSON.stringify(body)}`);
  console.log(`You are ${body.email} (roles: ${body.roles.join(", ") || "none"})`);
}

const command = process.argv[2] ?? "login";
try {
  if (command === "login") await login();
  else if (command === "whoami") await whoami();
  else if (command === "logout") {
    await rm(STORE, { force: true });
    console.log("Signed out.");
  } else throw new Error(`Unknown command: ${command} (login | whoami | logout)`);
} catch (e) {
  console.error(e.message);
  process.exit(1);
}
