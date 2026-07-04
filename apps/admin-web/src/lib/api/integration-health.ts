import { AUTH_SERVER_URL } from "@/lib/config";

export type IntegrationHealthResult = {
  id: string;
  label: string;
  ok: boolean;
  status: number;
  detail: string;
};

async function probe(
  id: string,
  label: string,
  run: () => Promise<Response>,
): Promise<IntegrationHealthResult> {
  try {
    const res = await run();
    const text = await res.text();
    let detail = text.slice(0, 120);
    if (!res.ok && text) {
      try {
        const json = JSON.parse(text) as { detail?: string; message?: string };
        detail = json.detail ?? json.message ?? detail;
      } catch {
        /* keep text slice */
      }
    } else if (res.ok && !detail) {
      detail = "OK";
    }
    return { id, label, ok: res.ok, status: res.status, detail };
  } catch (e) {
    return {
      id,
      label,
      ok: false,
      status: 0,
      detail: e instanceof Error ? e.message : "Request failed",
    };
  }
}

export async function runIntegrationHealthChecks(
  applicationId: string,
): Promise<IntegrationHealthResult[]> {
  const base = AUTH_SERVER_URL;
  const appBase = `${base}/api/v1/applications/${applicationId}`;
  return Promise.all([
    probe("info", "Auth server", () => fetch(`${base}/api/info`)),
    probe("oidc", "OIDC discovery", () => fetch(`${base}/.well-known/openid-configuration`)),
    probe("manifest", "Public application manifest", () => fetch(appBase)),
    probe("signup", "Signup options", () => fetch(`${appBase}/signup`)),
    probe("forgot-password", "Forgot password (app-scoped)", () =>
      fetch(`${appBase}/account/password/forgot`, {
        method: "POST",
        headers: { "Content-Type": "application/json", Accept: "application/json" },
        body: JSON.stringify({ email: "health-check@example.invalid" }),
      }),
    ),
    probe("session-login", "Native session login (app-scoped)", async () => {
      const res = await fetch(`${appBase}/auth/session/login`, {
        method: "POST",
        headers: { "Content-Type": "application/json", Accept: "application/json" },
        body: JSON.stringify({ email: "health-check@example.invalid", password: "invalid" }),
      });
      if (res.status === 404) return res;
      return new Response(await res.text(), { status: 200, statusText: "Route reachable" });
    }),
    probe("jwks", "JWKS endpoint", () => fetch(`${base}/oauth2/jwks`)),
    probe("account-sessions", "Account sessions API", async () => {
      const res = await fetch(`${base}/api/v1/account/sessions`, { credentials: "omit" });
      if (res.status === 401) {
        return new Response("Unauthorized (route reachable)", { status: 200 });
      }
      return res;
    }),
    probe("federation-providers", "Federation providers API", async () => {
      const res = await fetch(`${base}/api/v1/auth/federation/providers?applicationId=${applicationId}`);
      if (res.ok || res.status === 400) {
        return new Response(await res.text(), { status: 200, statusText: "Route reachable" });
      }
      return res;
    }),
    probe("passkey-options", "Passkey sign-in API", async () => {
      const res = await fetch(`${base}/api/v1/auth/passkey/options`, {
        method: "POST",
        headers: { "Content-Type": "application/json", Accept: "application/json" },
        body: JSON.stringify({ applicationId }),
      });
      if (res.ok || res.status === 400 || res.status === 500) {
        return new Response(await res.text(), { status: 200, statusText: "Route reachable" });
      }
      return res;
    }),
  ]);
}
