import { ApiError } from "./http";
import { browserApiFetch as apiFetch } from "./browser-client";
import { publicAuthServerUrl } from "@/lib/config";
import type { AppType, OAuthClient, OAuthEndpoints, Status } from "@/lib/types";

function defaultEndpoints(): OAuthEndpoints {
  const issuer = publicAuthServerUrl();
  return {
    issuer,
    authorizationEndpoint: `${issuer}/oauth2/authorize`,
    tokenEndpoint: `${issuer}/oauth2/token`,
    jwksUri: `${issuer}/oauth2/jwks`,
  };
}

type OAuthClientDto = {
  id: string;
  applicationId: string;
  tenantId: string;
  applicationName: string;
  clientId: string;
  type: string;
  status: string;
  grantTypes: string[];
  scopes: string[];
  redirectUris: string[];
  postLogoutRedirectUris?: string[];
  backchannelLogoutUri?: string | null;
  consentRequired?: boolean;
  jwksUri?: string | null;
  jwks?: Record<string, unknown> | null;
  dynamicallyRegistered?: boolean;
  confidential: boolean;
  pkceRequired: boolean;
  tokenEndpointAuthMethod: string;
  subjectType?: "user_id" | "legacy";
  setupFramework?: string | null;
  clientSecretConfigured: boolean;
  createdAt: string;
  updatedAt: string;
  oAuthEndpoints: OAuthEndpoints;
};

function mapClient(dto: OAuthClientDto): OAuthClient {
  const type = dto.type as AppType;
  return {
    id: dto.id,
    applicationId: dto.applicationId,
    tenantId: dto.tenantId,
    applicationName: dto.applicationName,
    clientId: dto.clientId,
    type,
    status: dto.status as Status,
    grantTypes: dto.grantTypes ?? [],
    scopes: dto.scopes ?? [],
    redirectUris: dto.redirectUris ?? [],
    postLogoutRedirectUris: dto.postLogoutRedirectUris ?? [],
    backchannelLogoutUri: dto.backchannelLogoutUri ?? null,
    consentRequired: dto.consentRequired ?? false,
    jwksUri: dto.jwksUri ?? null,
    jwks: dto.jwks ?? null,
    dynamicallyRegistered: dto.dynamicallyRegistered ?? false,
    confidential: dto.confidential ?? (type === "web" || type === "m2m"),
    pkceRequired: dto.pkceRequired ?? type !== "m2m",
    tokenEndpointAuthMethod:
      dto.tokenEndpointAuthMethod ?? (type === "web" || type === "m2m" ? "client_secret_basic" : "none"),
    subjectType: dto.subjectType ?? "legacy",
    setupFramework: dto.setupFramework ?? null,
    clientSecretConfigured: dto.clientSecretConfigured ?? false,
    createdAt: dto.createdAt,
    updatedAt: dto.updatedAt ?? dto.createdAt,
    oAuthEndpoints: dto.oAuthEndpoints ?? defaultEndpoints(),
  };
}

export async function listOAuthClients(params?: {
  tenantId?: string;
  applicationId?: string;
}): Promise<OAuthClient[]> {
  const search = new URLSearchParams();
  if (params?.tenantId) search.set("tenantId", params.tenantId);
  if (params?.applicationId) search.set("applicationId", params.applicationId);
  const qs = search.toString();
  const path = qs ? `/api/admin/v1/oauth-clients?${qs}` : "/api/admin/v1/oauth-clients";
  return (await apiFetch<OAuthClientDto[]>(path)).map(mapClient);
}

export async function getOAuthClient(id: string): Promise<OAuthClient | null> {
  try {
    return mapClient(await apiFetch<OAuthClientDto>(`/api/admin/v1/oauth-clients/${id}`));
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) return null;
    throw e;
  }
}

export type OAuthClientWriteInput = {
  type: string;
  status: string;
  grantTypes: string[];
  scopes: string[];
  redirectUris: string[];
  postLogoutRedirectUris?: string[];
  pkceRequired?: boolean;
  tokenEndpointAuthMethod?: string;
  subjectType?: string;
  /** OIDC Back-Channel Logout URL; "" clears it. */
  backchannelLogoutUri?: string;
  /** Third-party client: users see a consent screen. */
  consentRequired?: boolean;
  /** Framework chosen in Connect an app (setup code on the client page). */
  setupFramework?: string;
  /** private_key_jwt: JWKS URL; "" clears it. */
  jwksUri?: string;
  /** private_key_jwt: public keys; {} clears them. */
  jwks?: Record<string, unknown>;
};

export async function createOAuthClientApi(
  input: OAuthClientWriteInput & {
    applicationId?: string;
    applicationName?: string;
    tenantId?: string;
    clientId?: string;
  },
): Promise<{ client: OAuthClient; clientSecret?: string }> {
  const res = await apiFetch<{ client: OAuthClientDto; clientSecret?: string | null }>(
    "/api/admin/v1/oauth-clients",
    { method: "POST", body: JSON.stringify(input) },
  );
  return {
    client: mapClient(res.client),
    clientSecret: res.clientSecret ?? undefined,
  };
}

export async function updateOAuthClientApi(
  id: string,
  input: OAuthClientWriteInput,
): Promise<OAuthClient> {
  return mapClient(
    await apiFetch<OAuthClientDto>(`/api/admin/v1/oauth-clients/${id}`, {
      method: "PUT",
      body: JSON.stringify(input),
    }),
  );
}

/** Remembers which framework's setup code the client page shows (console display only). */
export async function saveOAuthClientSetupFrameworkApi(id: string, setupFramework: string): Promise<void> {
  await apiFetch(`/api/admin/v1/oauth-clients/${id}`, {
    method: "PUT",
    body: JSON.stringify({ setupFramework }),
  });
}

/** {@code keepOldHours}: the old secret keeps working that long (0 to 168; 0 when it leaked). */
export async function rotateOAuthClientSecretApi(id: string, keepOldHours = 0): Promise<string> {
  const res = await apiFetch<{ clientSecret: string }>(
    `/api/admin/v1/oauth-clients/${id}/rotate-secret?keepOldHours=${keepOldHours}`,
    { method: "POST" },
  );
  return res.clientSecret;
}

