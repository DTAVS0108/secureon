export type SecureOneClientOptions = {
  baseUrl: string;
  applicationId: string;
  fetch?: typeof fetch;
};

export type ApplicationManifest = {
  applicationId?: string;
  authMethods?: Array<{ id: string; enabled: boolean; implemented?: boolean }>;
  account?: Record<string, string>;
  federation?: { providers?: unknown[]; providersEndpoint?: string };
  signup?: { enabled?: boolean; endpoint?: string };
};

export type PkcePair = { verifier: string; challenge: string };

function base64Url(bytes: Uint8Array): string {
  let binary = "";
  for (const b of bytes) binary += String.fromCharCode(b);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

export function generatePkcePair(): PkcePair {
  // Synchronous fallback — use generatePkcePairAsync() in browsers for correct S256.
  const verifierBytes = crypto.getRandomValues(new Uint8Array(32));
  const verifier = base64Url(verifierBytes);
  return { verifier, challenge: verifier };
}

export async function generatePkcePairAsync(): Promise<PkcePair> {
  const verifierBytes = crypto.getRandomValues(new Uint8Array(32));
  const verifier = base64Url(verifierBytes);
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(verifier));
  const challenge = base64Url(new Uint8Array(digest));
  return { verifier, challenge };
}

export class SecureOneClient {
  private readonly baseUrl: string;
  private readonly applicationId: string;
  private readonly fetchFn: typeof fetch;

  constructor(options: SecureOneClientOptions) {
    this.baseUrl = options.baseUrl.replace(/\/$/, "");
    this.applicationId = options.applicationId;
    this.fetchFn = options.fetch ?? fetch;
  }

  async getManifest(): Promise<ApplicationManifest> {
    const res = await this.fetchFn(`${this.baseUrl}/api/v1/applications/${this.applicationId}`, {
      headers: { Accept: "application/json" },
    });
    if (!res.ok) throw new Error(`Manifest fetch failed: ${res.status}`);
    return res.json() as Promise<ApplicationManifest>;
  }

  async sessionLogin(identifier: string, password: string): Promise<void> {
    const res = await this.fetchFn(
      `${this.baseUrl}/api/v1/applications/${this.applicationId}/auth/session/login`,
      {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json", Accept: "application/json" },
        body: JSON.stringify({ identifier, password }),
      },
    );
    if (res.status === 428) {
      const body = (await res.json()) as { mfaChallengeId?: string };
      throw new Error(`MFA required: ${body.mfaChallengeId ?? "unknown"}`);
    }
    if (res.status !== 204) {
      const body = await res.text();
      throw new Error(`Login failed (${res.status}): ${body}`);
    }
  }

  buildAuthorizeUrl(params: {
    clientId: string;
    redirectUri: string;
    scope?: string;
    state: string;
    codeChallenge: string;
  }): string {
    const q = new URLSearchParams({
      response_type: "code",
      client_id: params.clientId,
      redirect_uri: params.redirectUri,
      scope: params.scope ?? "openid profile email",
      state: params.state,
      code_challenge: params.codeChallenge,
      code_challenge_method: "S256",
    });
    return `${this.baseUrl}/oauth2/authorize?${q.toString()}`;
  }

  async exchangeAuthorizationCode(params: {
    code: string;
    redirectUri: string;
    clientId: string;
    codeVerifier: string;
    clientSecret?: string;
  }): Promise<Record<string, unknown>> {
    const body = new URLSearchParams({
      grant_type: "authorization_code",
      code: params.code,
      redirect_uri: params.redirectUri,
      client_id: params.clientId,
      code_verifier: params.codeVerifier,
    });
    if (params.clientSecret) body.set("client_secret", params.clientSecret);
    const res = await this.fetchFn(`${this.baseUrl}/oauth2/token`, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded", Accept: "application/json" },
      body: body.toString(),
    });
    if (!res.ok) throw new Error(`Token exchange failed: ${res.status}`);
    return res.json() as Promise<Record<string, unknown>>;
  }

  async introspectToken(params: {
    token: string;
    clientId: string;
    clientSecret?: string;
    tokenTypeHint?: "access_token" | "refresh_token";
  }): Promise<Record<string, unknown>> {
    const body = new URLSearchParams({
      token: params.token,
      client_id: params.clientId,
    });
    if (params.clientSecret) body.set("client_secret", params.clientSecret);
    if (params.tokenTypeHint) body.set("token_type_hint", params.tokenTypeHint);
    const res = await this.fetchFn(`${this.baseUrl}/oauth2/introspect`, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded", Accept: "application/json" },
      body: body.toString(),
    });
    if (!res.ok) throw new Error(`Token introspection failed: ${res.status}`);
    return res.json() as Promise<Record<string, unknown>>;
  }

  async revokeToken(params: {
    token: string;
    clientId: string;
    clientSecret?: string;
    tokenTypeHint?: "access_token" | "refresh_token";
  }): Promise<void> {
    const body = new URLSearchParams({
      token: params.token,
      client_id: params.clientId,
    });
    if (params.clientSecret) body.set("client_secret", params.clientSecret);
    if (params.tokenTypeHint) body.set("token_type_hint", params.tokenTypeHint);
    const res = await this.fetchFn(`${this.baseUrl}/oauth2/revoke`, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: body.toString(),
    });
    if (!res.ok) throw new Error(`Token revocation failed: ${res.status}`);
  }
}
