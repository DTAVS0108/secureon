import { browserApiFetch as apiFetch } from "./browser-client";

export type Organization = {
  id: string;
  tenantId: string;
  parentId: string | null;
  name: string;
  slug: string;
  createdAt: string;
};

export async function listOrganizations(tenantId: string): Promise<Organization[]> {
  return apiFetch<Organization[]>(`/api/admin/v1/tenants/${tenantId}/organizations`);
}

export async function createOrganization(
  tenantId: string,
  body: { name: string; slug?: string; parentId?: string },
): Promise<Organization> {
  return apiFetch(`/api/admin/v1/tenants/${tenantId}/organizations`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function updateOrganization(
  tenantId: string,
  id: string,
  body: { name?: string; slug?: string; parentId?: string | null },
): Promise<Organization> {
  return apiFetch(`/api/admin/v1/tenants/${tenantId}/organizations/${id}`, {
    method: "PUT",
    body: JSON.stringify(body),
  });
}

export async function deleteOrganization(tenantId: string, id: string): Promise<void> {
  await apiFetch<void>(`/api/admin/v1/tenants/${tenantId}/organizations/${id}`, { method: "DELETE" });
}
