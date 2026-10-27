import { appScopeHeaders } from "./http";
import { browserApiFetch as apiFetch } from "./browser-client";

export type RegistrationMode = "off" | "token" | "open";

/** Dynamic client registration of an application: who may register and what a registered client may ask for. */
export type ClientRegistration = {
  mode: RegistrationMode;
  allowedScopes: string[];
  allowedGrantTypes: string[];
  trustedHosts: string[];
  maxClients: number;
  unusedDays: number;
  registeredClients: number;
  registrationEndpoint: string;
  openRegistrationEndpoint: string;
};

export type InitialAccessToken = {
  id: string;
  label: string | null;
  maxUses: number;
  uses: number;
  expiresAt: string;
  revokedAt: string | null;
  createdAt: string;
};

const base = (applicationId: string) => `/api/admin/v1/applications/${applicationId}/settings/client-registration`;

export async function fetchClientRegistration(applicationId: string): Promise<ClientRegistration> {
  return apiFetch<ClientRegistration>(base(applicationId), { headers: appScopeHeaders(applicationId) });
}

export async function saveClientRegistration(
  applicationId: string,
  input: Partial<Omit<ClientRegistration, "registeredClients" | "registrationEndpoint" | "openRegistrationEndpoint">>,
): Promise<ClientRegistration> {
  return apiFetch<ClientRegistration>(base(applicationId), {
    method: "PUT",
    headers: appScopeHeaders(applicationId),
    body: JSON.stringify(input),
  });
}

export async function fetchInitialAccessTokens(applicationId: string): Promise<InitialAccessToken[]> {
  return apiFetch<InitialAccessToken[]>(`${base(applicationId)}/tokens`, { headers: appScopeHeaders(applicationId) });
}

export async function createInitialAccessToken(
  applicationId: string,
  input: { label?: string; maxUses: number; expiresInHours: number },
): Promise<{ token: InitialAccessToken; value: string }> {
  return apiFetch<{ token: InitialAccessToken; value: string }>(`${base(applicationId)}/tokens`, {
    method: "POST",
    headers: appScopeHeaders(applicationId),
    body: JSON.stringify(input),
  });
}

export async function revokeInitialAccessToken(applicationId: string, tokenId: string): Promise<void> {
  await apiFetch<void>(`${base(applicationId)}/tokens/${tokenId}`, { method: "DELETE", headers: appScopeHeaders(applicationId) });
}
