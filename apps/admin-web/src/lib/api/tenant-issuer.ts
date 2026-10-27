import { browserApiFetch as apiFetch } from "./browser-client";

export type TenantDomain = {
  id: string;
  domain: string;
  status: "pending" | "verified";
  issuer: string;
  txtRecordName: string;
  txtRecordValue: string;
  httpCheckUrl: string;
  verifiedAt: string | null;
  lastCheckedAt: string | null;
  lastError: string | null;
};

/** A tenant's issuers: its own (/t/<slug>), its verified domains, and the shared one unless it uses its own only. */
export type TenantIssuer = {
  issuerMode: "shared" | "tenant";
  sharedIssuer: string;
  issuers: string[];
  domains: TenantDomain[];
};

const base = (tenantId: string) => `/api/admin/v1/tenants/${tenantId}/issuer`;

export async function fetchTenantIssuer(tenantId: string): Promise<TenantIssuer> {
  return apiFetch<TenantIssuer>(base(tenantId));
}

export async function setTenantIssuerMode(tenantId: string, issuerMode: TenantIssuer["issuerMode"]): Promise<TenantIssuer> {
  return apiFetch<TenantIssuer>(base(tenantId), { method: "PUT", body: JSON.stringify({ issuerMode }) });
}

export async function addTenantDomain(tenantId: string, domain: string): Promise<TenantDomain> {
  return apiFetch<TenantDomain>(`${base(tenantId)}/domains`, { method: "POST", body: JSON.stringify({ domain }) });
}

export async function verifyTenantDomain(tenantId: string, domainId: string): Promise<TenantDomain> {
  return apiFetch<TenantDomain>(`${base(tenantId)}/domains/${domainId}/verify`, { method: "POST" });
}

export async function deleteTenantDomain(tenantId: string, domainId: string): Promise<void> {
  await apiFetch<void>(`${base(tenantId)}/domains/${domainId}`, { method: "DELETE" });
}
