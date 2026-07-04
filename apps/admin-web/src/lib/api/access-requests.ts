import { browserApiFetch as apiFetch } from "./browser-client";

export type AccessRequest = {
  id: string;
  tenantId: string;
  requesterUserId: string;
  targetUserId: string | null;
  resourceType: string;
  resourceId: string;
  applicationId: string | null;
  reason: string | null;
  status: string;
  reviewerId: string | null;
  createdAt: string;
  resolvedAt: string | null;
  grantApplied?: boolean;
  grantType?: string;
  grantedRoleId?: string;
  grantedRoleName?: string;
  grantedUserId?: string;
};

export async function listAccessRequests(tenantId: string): Promise<AccessRequest[]> {
  return apiFetch<AccessRequest[]>(`/api/admin/v1/tenants/${tenantId}/access-requests`);
}

export async function createAccessRequest(
  tenantId: string,
  body: {
    requesterUserId: string;
    targetUserId?: string;
    resourceType: string;
    resourceId: string;
    applicationId?: string;
    reason?: string;
  },
): Promise<AccessRequest> {
  return apiFetch(`/api/admin/v1/tenants/${tenantId}/access-requests`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function approveAccessRequest(
  tenantId: string,
  id: string,
  reviewerId: string,
): Promise<AccessRequest> {
  return apiFetch(
    `/api/admin/v1/tenants/${tenantId}/access-requests/${id}/approve?reviewerId=${encodeURIComponent(reviewerId)}`,
    { method: "POST" },
  );
}

export async function denyAccessRequest(
  tenantId: string,
  id: string,
  reviewerId: string,
): Promise<AccessRequest> {
  return apiFetch(
    `/api/admin/v1/tenants/${tenantId}/access-requests/${id}/deny?reviewerId=${encodeURIComponent(reviewerId)}`,
    { method: "POST" },
  );
}
