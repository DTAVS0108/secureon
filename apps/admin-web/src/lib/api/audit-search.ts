import { appScopeHeaders } from "./http";
import { browserApiFetch as apiFetch } from "./browser-client";
import { withBasePath } from "@/lib/config";

export type AuditRow = {
  id: string;
  timestamp: string;
  actor: string;
  action: string;
  target: string;
  ip: string | null;
  result: "success" | "failure";
  tenant: string | null;
  application: string | null;
};

export type AuditFilter = {
  applicationId?: string;
  tenantId?: string;
  /** ISO date-times. */
  from?: string;
  to?: string;
  action?: string;
  actor?: string;
  result?: "" | "success" | "failure";
};

function query(filter: AuditFilter, limit?: number): string {
  const params = new URLSearchParams();
  Object.entries(filter).forEach(([k, v]) => {
    if (v) params.set(k, String(v));
  });
  if (limit) params.set("limit", String(limit));
  return params.toString();
}

function headers(filter: AuditFilter): Record<string, string> {
  return filter.applicationId ? appScopeHeaders(filter.applicationId) : {};
}

export function searchAudit(filter: AuditFilter, limit = 500): Promise<AuditRow[]> {
  return apiFetch<AuditRow[]>(`/api/admin/v1/audit?${query(filter, limit)}`, { headers: headers(filter) });
}

/** Downloads the filtered audit log as a CSV file (up to 100,000 rows). */
export async function downloadAuditCsv(filter: AuditFilter): Promise<void> {
  const res = await fetch(withBasePath(`/api/proxy/api/admin/v1/audit/export?${query(filter)}`), {
    credentials: "same-origin",
    headers: { Accept: "text/csv", ...headers(filter) },
    cache: "no-store",
  });
  if (!res.ok) {
    throw new Error((await res.text()) || `Export failed (${res.status})`);
  }
  const blob = await res.blob();
  const name = /filename="([^"]+)"/.exec(res.headers.get("Content-Disposition") ?? "")?.[1] ?? "secureone-audit.csv";
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = name;
  a.click();
  URL.revokeObjectURL(url);
}
