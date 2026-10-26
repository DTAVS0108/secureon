import { browserApiFetch as apiFetch } from "./browser-client";

export type Overview = {
  generatedAt: string;
  scope: "platform" | "tenant";
  counts: {
    tenants?: number;
    applications: number;
    oauthClients: number;
    users: number;
    lockedUsers: number;
    suspendedUsers: number;
  };
  signIns: {
    succeeded24h: number;
    failed24h: number;
    succeeded7d: number;
    failed7d: number;
    activeUsers30d: number;
  };
  daily: { day: string; succeeded: number; failed: number }[];
  twoFactor: { usersWithTwoFactor: number; percent: number };
  recentSecurityEvents: { at: string; action: string; actor: string | null; tenant: string | null; application: string | null }[];
};

/** Platform home; with {@code tenantId}, one tenant's home. */
export function loadOverview(tenantId?: string): Promise<Overview> {
  return apiFetch<Overview>(tenantId ? `/api/admin/v1/tenants/${tenantId}/overview` : "/api/admin/v1/overview");
}
