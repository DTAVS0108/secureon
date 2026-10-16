import { browserApiFetch as apiFetch } from "./browser-client";
import { appScopeHeaders } from "./http";

export type FederationProviderType = "GOOGLE" | "GITHUB" | "OIDC" | "SAML";

export type FederationProvider = {
  id: string;
  tenantId: string;
  applicationId: string | null;
  alias: string;
  type: FederationProviderType;
  displayName: string;
  enabled: boolean;
  authMethodId: string;
  /** OAuth/OIDC providers: the redirect URI to register at the IdP. */
  callbackUrl?: string;
  /** SAML providers: what to enter at the IdP (or hand it as SP metadata). */
  serviceProvider?: { entityId: string; acsUrl: string; sloUrl?: string; metadataUrl: string };
  config: {
    clientId?: string;
    clientSecretConfigured?: boolean;
    jitProvisioning?: boolean;
    linkByEmail?: boolean;
    authorizationUri?: string;
    tokenUri?: string;
    userInfoUri?: string;
    scopes?: string;
    metadataUrl?: string;
    idpEntityId?: string;
    ssoUrl?: string;
    idpCertificate?: string;
    emailAttribute?: string;
    trustEmail?: boolean;
    sloUrl?: string;
    allowIdpInitiated?: boolean;
    landingUrl?: string;
  };
};

export type FederationProviderInput = {
  alias: string;
  type: FederationProviderType;
  displayName?: string;
  enabled?: boolean;
  clientId?: string;
  clientSecret?: string;
  jitProvisioning?: boolean;
  linkByEmail?: boolean;
  /** Type-specific settings: OIDC endpoints, or SAML metadata URL / entity ID / SSO URL / certificate. */
  config?: Record<string, string | boolean>;
};

function appScoped(appId: string, init?: RequestInit): RequestInit {
  return { ...init, headers: { ...appScopeHeaders(appId), ...(init?.headers as Record<string, string>) } };
}

export async function listApplicationFederationProviders(appId: string): Promise<FederationProvider[]> {
  return apiFetch<FederationProvider[]>(
    `/api/admin/v1/applications/${appId}/federation/providers`,
    appScoped(appId),
  );
}

export async function createApplicationFederationProvider(
  appId: string,
  body: FederationProviderInput,
): Promise<FederationProvider> {
  return apiFetch<FederationProvider>(
    `/api/admin/v1/applications/${appId}/federation/providers`,
    appScoped(appId, { method: "POST", body: JSON.stringify(body) }),
  );
}

export async function updateApplicationFederationProvider(
  appId: string,
  providerId: string,
  body: Partial<FederationProviderInput>,
): Promise<FederationProvider> {
  return apiFetch<FederationProvider>(
    `/api/admin/v1/applications/${appId}/federation/providers/${providerId}`,
    appScoped(appId, { method: "PUT", body: JSON.stringify(body) }),
  );
}

export async function deleteApplicationFederationProvider(appId: string, providerId: string): Promise<void> {
  await apiFetch<void>(
    `/api/admin/v1/applications/${appId}/federation/providers/${providerId}`,
    appScoped(appId, { method: "DELETE" }),
  );
}

export async function listTenantFederationProviders(tenantId: string): Promise<FederationProvider[]> {
  return apiFetch<FederationProvider[]>(`/api/admin/v1/tenants/${tenantId}/federation/providers`);
}

export async function createTenantFederationProvider(
  tenantId: string,
  body: FederationProviderInput,
): Promise<FederationProvider> {
  return apiFetch<FederationProvider>(`/api/admin/v1/tenants/${tenantId}/federation/providers`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function updateTenantFederationProvider(
  tenantId: string,
  providerId: string,
  body: Partial<FederationProviderInput>,
): Promise<FederationProvider> {
  return apiFetch<FederationProvider>(
    `/api/admin/v1/tenants/${tenantId}/federation/providers/${providerId}`,
    { method: "PUT", body: JSON.stringify(body) },
  );
}

export async function deleteTenantFederationProvider(tenantId: string, providerId: string): Promise<void> {
  await apiFetch<void>(`/api/admin/v1/tenants/${tenantId}/federation/providers/${providerId}`, {
    method: "DELETE",
  });
}
