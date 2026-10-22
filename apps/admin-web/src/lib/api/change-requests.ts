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
};

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
