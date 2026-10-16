import { authServerInternalUrl } from "./config";

export type AuthServerInfo = {
  name?: string;
  description?: string;
  version?: string;
} & Record<string, unknown>;

export type OidcDiscovery = {
  issuer: string;
  authorization_endpoint: string;
  token_endpoint: string;
  jwks_uri: string;
  userinfo_endpoint?: string;
  end_session_endpoint?: string;
  introspection_endpoint?: string;
  revocation_endpoint?: string;
  grant_types_supported?: string[];
  scopes_supported?: string[];
  code_challenge_methods_supported?: string[];
} & Record<string, unknown>;

export type ProbeResult<T> = {
  ok: boolean;
  status: number | null;
  data: T | null;
  error: string | null;
};

async function probe<T>(path: string): Promise<ProbeResult<T>> {
  try {
    const res = await fetch(`${authServerInternalUrl()}${path}`, {
      cache: "no-store",
      // Keep the dashboard snappy even when the backend is down.
      signal: AbortSignal.timeout(4000),
    });
    const status = res.status;
    if (!res.ok) {
      return { ok: false, status, data: null, error: `HTTP ${status}` };
    }
    const data = (await res.json()) as T;
    return { ok: true, status, data, error: null };
  } catch (err) {
    const error = err instanceof Error ? err.message : "Request failed";
    return { ok: false, status: null, data: null, error };
  }
}

export function getInfo() {
  return probe<AuthServerInfo>("/api/info");
}

export function getHealth() {
  return probe<{ status: string }>("/actuator/health");
}

export function getDiscovery() {
  return probe<OidcDiscovery>("/.well-known/openid-configuration");
}
