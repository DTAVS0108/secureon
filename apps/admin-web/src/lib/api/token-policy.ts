import { browserApiFetch as apiFetch } from "./browser-client";

export type TokenPolicy = {
  tabEnabled?: boolean;
  accessTokenTtlSeconds: number;
  refreshTokenTtlSeconds: number;
  authorizationCodeTtlSeconds: number;
  idTokenTtlSeconds: number;
  clientCredentialsTtlSeconds: number;
  deviceCodeTtlSeconds: number;
  refreshTokensEnabled: boolean;
  reuseRefreshTokens: boolean;
  rotateRefreshTokens: boolean;
  refreshTokenReuseDetection: boolean;
  /** Longest a sign-in can be kept alive by refreshing. */
  refreshTokenAbsoluteSeconds?: number;
  /** Sign in again after this long for this application; 0 = the platform session limit. */
  signInMaxAgeSeconds?: number;
  scope?: string;
  inheritsPlatformDefaults?: boolean;
};

export async function fetchPlatformTokenPolicy(): Promise<TokenPolicy> {
  return apiFetch<TokenPolicy>("/api/admin/v1/settings/token-policy");
}

export async function savePlatformTokenPolicy(body: TokenPolicy): Promise<TokenPolicy> {
  return apiFetch<TokenPolicy>("/api/admin/v1/settings/token-policy", {
    method: "PUT",
    body: JSON.stringify(body),
  });
}
