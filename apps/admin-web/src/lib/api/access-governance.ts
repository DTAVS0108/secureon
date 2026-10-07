import { browserApiFetch as apiFetch } from "./browser-client";

export type PlatformOperator = {
  userId: string;
  email: string;
  displayName: string | null;
  status: string;
  grantedBy: string | null;
  grantedAt: string;
  lastLoginAt: string | null;
};

export type AccessReviewFlag = "ACCOUNT_NOT_ACTIVE" | "NEVER_SIGNED_IN" | "UNUSED_90_DAYS" | "NO_MFA";

export type AccessReviewEntry = {
  userId: string;
  email: string;
  displayName: string | null;
  accountStatus: string;
  tenantId: string;
  tenantSlug: string;
  tenantName: string;
  role: "PLATFORM_OPERATOR" | "TENANT_SUPER_ADMIN" | "TENANT_ADMIN" | "APPLICATION_ADMIN";
  assignmentId: string | null;
  applicationId: string | null;
  applicationName: string | null;
  grantedAt: string | null;
  grantedBy: string | null;
  expiresAt: string | null;
  lastLoginAt: string | null;
  mfaEnrolled: boolean;
  flags: AccessReviewFlag[];
};

export function listPlatformOperators(): Promise<PlatformOperator[]> {
  return apiFetch<PlatformOperator[]>("/api/admin/v1/platform-operators");
}

export function addPlatformOperator(body: {
  email: string;
  firstName?: string;
  lastName?: string;
}): Promise<PlatformOperator> {
  return apiFetch<PlatformOperator>("/api/admin/v1/platform-operators", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

export async function removePlatformOperator(userId: string): Promise<void> {
  await apiFetch<void>(`/api/admin/v1/platform-operators/${userId}`, { method: "DELETE" });
}

function reviewBase(tenantId?: string) {
  return tenantId ? `/api/admin/v1/tenants/${tenantId}/access-review` : "/api/admin/v1/access-review";
}

export function loadAccessReview(tenantId?: string): Promise<AccessReviewEntry[]> {
  return apiFetch<AccessReviewEntry[]>(reviewBase(tenantId));
}

export function completeAccessReview(
  tenantId: string | undefined,
  body: { note?: string; entriesReviewed: number },
): Promise<{ ok: boolean; entriesReviewed: number }> {
  return apiFetch(`${reviewBase(tenantId)}/complete`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}
