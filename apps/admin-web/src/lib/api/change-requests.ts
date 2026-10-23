import { browserApiFetch as apiFetch } from "./browser-client";

/** A high-impact change waiting for a second admin (docs/plans/change-approvals.md). */
export type ChangeRequest = {
  id: string;
  action: "oauth_client.delete" | string;
  tenantId: string | null;
  tenantName: string | null;
  applicationId: string | null;
  targetId: string;
  targetLabel: string;
  reason: string | null;
  /** The change in words, e.g. "Weaker Two Factor Authentication for Shop: mode: required → off". */
  details?: string | null;
  status: "PENDING" | "APPROVED" | "REJECTED" | "WITHDRAWN" | "FAILED";
  requestedBy: string;
  requestedAt: string;
  decidedBy: string | null;
  decidedAt: string | null;
  decisionNote: string | null;
  /** The signed-in admin asked for it (may withdraw, not decide). */
  mine: boolean;
  /** The signed-in admin may approve or reject it. */
  canDecide: boolean;
};

export const CHANGE_ACTION_LABELS: Record<string, string> = {
  "oauth_client.delete": "Delete OAuth client",
  "two_factor.app_off": "Turn Two Factor Authentication off",
  "two_factor.app_policy": "Weaker Two Factor Authentication (application)",
  "two_factor.tenant_policy": "Weaker Two Factor Authentication (tenant)",
  "two_factor.platform_policy": "Weaker Two Factor Authentication (platform)",
  "two_factor.platform_exposure": "Close Two Factor Authentication to applications",
  "two_factor.user_exempt": "Exempt a user from Two Factor Authentication",
};

/** Where the thing a request changes is managed in the console. */
export function changeTargetHref(r: ChangeRequest): string | null {
  switch (r.action) {
    case "oauth_client.delete":
      return `/applications/${r.targetId}`;
    case "two_factor.app_off":
    case "two_factor.app_policy":
      return r.applicationId ? `/app/${r.applicationId}/settings` : null;
    case "two_factor.tenant_policy":
      return `/tenants/${r.targetId}/two-step`;
    case "two_factor.platform_policy":
    case "two_factor.platform_exposure":
      return "/settings";
    default:
      return null;
  }
}

// ---- Two Factor Authentication review (Access review) ------------------------------------------------------

export type TwoFactorReview = {
  /** At least two people can approve, so changes that weaken 2FA wait for a second admin. */
  approvalsActive: boolean;
  approvers: number;
  applications: {
    id: string;
    name: string;
    tenantName: string;
    mode: "off" | "optional" | "required";
    /** Its Two Factor Authentication switch is on (own settings). */
    managed: boolean;
    /** Its switch is off. */
    switchedOff: boolean;
    /** The platform or tenant locks whether it is required. */
    lockedAbove: boolean;
  }[];
  exemptions: {
    userId: string;
    email: string;
    applicationId: string | null;
    applicationName: string | null;
    until: string;
    reason: string | null;
    exemptedBy: string | null;
  }[];
  adminsWithoutTwoFactor: { userId: string; email: string; role: string }[];
};

export function loadTwoFactorReview(tenantId?: string): Promise<TwoFactorReview> {
  return apiFetch<TwoFactorReview>(
    tenantId ? `/api/admin/v1/tenants/${tenantId}/two-factor/review` : "/api/admin/v1/two-factor/review",
  );
}

/** Pending requests first, then recent decisions. Without `tenantId`: every tenant (platform operators). */
export function listChangeRequests(tenantId?: string): Promise<ChangeRequest[]> {
  return apiFetch<ChangeRequest[]>(
    tenantId ? `/api/admin/v1/tenants/${tenantId}/change-requests` : "/api/admin/v1/change-requests",
  );
}

function decide(id: string, decision: "approve" | "reject" | "withdraw", note?: string) {
  return apiFetch<ChangeRequest>(`/api/admin/v1/change-requests/${id}/${decision}`, {
    method: "POST",
    body: JSON.stringify({ note: note ?? null }),
  });
}

export const approveChangeRequest = (id: string, note?: string) => decide(id, "approve", note);
export const rejectChangeRequest = (id: string, note: string) => decide(id, "reject", note);
export const withdrawChangeRequest = (id: string) => decide(id, "withdraw");

/** Asks for the client to be deleted; it is deleted when another admin approves. */
export function requestOAuthClientDelete(clientId: string, reason?: string): Promise<ChangeRequest> {
  const query = reason?.trim() ? `?reason=${encodeURIComponent(reason.trim())}` : "";
  return apiFetch<ChangeRequest>(`/api/admin/v1/oauth-clients/${clientId}${query}`, { method: "DELETE" });
}

/** The pending delete request for this client, or null. */
export async function loadOAuthClientDeleteRequest(clientId: string): Promise<ChangeRequest | null> {
  const row = await apiFetch<ChangeRequest | undefined>(`/api/admin/v1/oauth-clients/${clientId}/delete-request`);
  return row ?? null;
}
