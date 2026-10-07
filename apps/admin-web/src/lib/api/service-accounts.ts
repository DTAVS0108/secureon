import { browserApiFetch as apiFetch } from "./browser-client";

export type ServiceAccount = {
  id: string;
  tenantId: string;
  name: string;
  description?: string | null;
  status: string;
  createdAt?: string;
  updatedAt?: string;
};

export type ApiKeySummary = {
  id: string;
  keyPrefix: string;
  label?: string | null;
  createdAt?: string;
  expiresAt?: string | null;
  lastUsedAt?: string | null;
  revokedAt?: string | null;
};

export type CreatedApiKey = ApiKeySummary & { apiKey: string; scopes?: string[]; scopeNote?: string };

export async function listServiceAccounts(tenantId: string): Promise<ServiceAccount[]> {
  return apiFetch<ServiceAccount[]>(`/api/admin/v1/tenants/${tenantId}/service-accounts`);
}

export async function createServiceAccount(
  tenantId: string,
  body: { name: string; description?: string },
): Promise<ServiceAccount> {
  return apiFetch<ServiceAccount>(`/api/admin/v1/tenants/${tenantId}/service-accounts`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function deleteServiceAccount(tenantId: string, id: string): Promise<void> {
  await apiFetch<void>(`/api/admin/v1/tenants/${tenantId}/service-accounts/${id}`, { method: "DELETE" });
}

export async function listServiceAccountKeys(tenantId: string, accountId: string): Promise<ApiKeySummary[]> {
  return apiFetch<ApiKeySummary[]>(
    `/api/admin/v1/tenants/${tenantId}/service-accounts/${accountId}/keys`,
  );
}

export async function createServiceAccountKey(
  tenantId: string,
  accountId: string,
  label?: string,
): Promise<CreatedApiKey> {
  return apiFetch<CreatedApiKey>(
    `/api/admin/v1/tenants/${tenantId}/service-accounts/${accountId}/keys`,
    { method: "POST", body: JSON.stringify(label ? { label } : {}) },
  );
}

export async function revokeServiceAccountKey(
  tenantId: string,
  accountId: string,
  keyId: string,
): Promise<void> {
  await apiFetch<void>(
    `/api/admin/v1/tenants/${tenantId}/service-accounts/${accountId}/keys/${keyId}`,
    { method: "DELETE" },
  );
}

export async function impersonateUser(
  userId: string,
  body: { reason: string; applicationId?: string },
): Promise<{ consumeUrl: string; expiresInSeconds: number }> {
  return apiFetch(`/api/admin/v1/users/${userId}/impersonate`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}
