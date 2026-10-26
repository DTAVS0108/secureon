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

  /**
   * Password sign-in for apps with their own sign-in screen. When Attack protection asks for a CAPTCHA (always, or
   * after failures), pass the token your CAPTCHA widget returned; a 400 with {@code captchaRequired} means show it.
   */
  async sessionLogin(identifier: string, password: string, captchaToken?: string): Promise<void> {
    const headers: Record<string, string> = { "Content-Type": "application/json", Accept: "application/json" };
    if (captchaToken) headers["X-Captcha-Token"] = captchaToken;
    const res = await this.fetchFn(
      `${this.baseUrl}/api/v1/applications/${this.applicationId}/auth/session/login`,
      {
        method: "POST",
        credentials: "include",
        headers,
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

  /** New access token (and, with rotation, a new refresh token) from a refresh token. */
  async refreshToken(params: { refreshToken: string; clientId: string; clientSecret?: string }): Promise<Record<string, unknown>> {
    const body = new URLSearchParams({
      grant_type: "refresh_token",
      refresh_token: params.refreshToken,
      client_id: params.clientId,
    });
    if (params.clientSecret) body.set("client_secret", params.clientSecret);
    const res = await this.fetchFn(`${this.baseUrl}/oauth2/token`, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded", Accept: "application/json" },
      body: body.toString(),
    });
    if (!res.ok) throw new Error(`Token refresh failed: ${res.status}`);
    return res.json() as Promise<Record<string, unknown>>;
  }

  /** The application's name, logo, colours and default language (for your own sign-in screens). */
  async getBranding(): Promise<Record<string, unknown>> {
    const res = await this.fetchFn(`${this.baseUrl}/api/v1/applications/${this.applicationId}/branding`, {
      headers: { Accept: "application/json" },
    });
    if (!res.ok) throw new Error(`Branding fetch failed: ${res.status}`);
    return res.json() as Promise<Record<string, unknown>>;
  }

  /**
   * Starts an email change for the signed-in user (their access token). A confirmation link goes to the new
   * address; the email changes when they open it.
   */
  async changeEmail(params: { accessToken: string; newEmail: string; currentPassword?: string }): Promise<{ message: string }> {
    const res = await this.fetchFn(`${this.baseUrl}/api/v1/account/email/change`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${params.accessToken}`,
        "Content-Type": "application/json",
        Accept: "application/json",
        "X-Application-Id": this.applicationId,
      },
      body: JSON.stringify({ newEmail: params.newEmail, currentPassword: params.currentPassword }),
    });
    if (!res.ok) throw new Error(`Email change failed (${res.status}): ${await res.text()}`);
    return res.json() as Promise<{ message: string }>;
  }

  /**
   * Checks a back-channel logout request (OIDC Back-Channel Logout 1.0): POST body field {@code logout_token}.
   * Verifies the RS256 signature against SecureOne's JWKS, the issuer, that your client is the audience, the
   * logout event, and that it is recent. Returns {@code sub} (and {@code sid} when present): end that user's
   * sessions. Throws when the token is not valid.
   */
  async verifyLogoutToken(params: { logoutToken: string; clientId: string; issuer?: string; maxAgeSeconds?: number }): Promise<{ sub?: string; sid?: string }> {
    const parts = params.logoutToken.split(".");
    if (parts.length !== 3) throw new Error("Malformed logout token");
    const decode = (s: string) => {
      const b64 = s.replace(/-/g, "+").replace(/_/g, "/") + "===".slice((s.length + 3) % 4);
      return Uint8Array.from(atob(b64), (c) => c.charCodeAt(0));
    };
    const header = JSON.parse(new TextDecoder().decode(decode(parts[0]))) as { alg?: string; kid?: string; typ?: string };
    const claims = JSON.parse(new TextDecoder().decode(decode(parts[1]))) as Record<string, unknown>;
    if (header.alg !== "RS256") throw new Error("Unexpected signing algorithm");
    const jwksRes = await this.fetchFn(`${this.baseUrl}/oauth2/jwks`, { headers: { Accept: "application/json" } });
    if (!jwksRes.ok) throw new Error(`JWKS fetch failed: ${jwksRes.status}`);
    const jwks = (await jwksRes.json()) as { keys: (JsonWebKey & { kid?: string })[] };
    const jwk = jwks.keys.find((k) => !header.kid || k.kid === header.kid);
    if (!jwk) throw new Error("Signing key not found");
    const key = await crypto.subtle.importKey("jwk", jwk, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]);
    const signed = new TextEncoder().encode(`${parts[0]}.${parts[1]}`);
    if (!(await crypto.subtle.verify("RSASSA-PKCS1-v1_5", key, decode(parts[2]), signed))) {
      throw new Error("Invalid logout token signature");
    }
    const issuer = params.issuer ?? this.baseUrl;
    const aud = claims.aud;
    const now = Math.floor(Date.now() / 1000);
    const events = claims.events as Record<string, unknown> | undefined;
    if (claims.iss !== issuer) throw new Error("Wrong issuer");
    if (!(aud === params.clientId || (Array.isArray(aud) && aud.includes(params.clientId)))) throw new Error("Wrong audience");
    if (!events || !("http://schemas.openid.net/event/backchannel-logout" in events)) throw new Error("Not a logout token");
    if ("nonce" in claims) throw new Error("Logout tokens must not have a nonce");
    if (typeof claims.iat !== "number" || now - claims.iat > (params.maxAgeSeconds ?? 300)) throw new Error("Logout token too old");
    if (typeof claims.exp === "number" && claims.exp < now) throw new Error("Logout token expired");
    if (!claims.sub && !claims.sid) throw new Error("Logout token names no user");
    return { sub: claims.sub as string | undefined, sid: claims.sid as string | undefined };
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
