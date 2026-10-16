import { appScopeHeaders } from "./http";
import { browserApiFetch as apiFetch } from "./browser-client";
import { withBasePath } from "@/lib/config";

export type UserDirectorySourceKey = "csv" | "excel" | "ldap";

export type UserDirectorySettings = {
  importEnabled: boolean;
  exportEnabled: boolean;
  sources: Record<UserDirectorySourceKey, { enabled: boolean }>;
  ldap: {
    host: string;
    port: number;
    baseDn: string;
    bindDn: string;
    bindPassword: string;
    userFilter: string;
    useTls: boolean;
  };
  scope?: string;
  inheritsPlatformDefaults?: boolean;
};

export type UserImportResult = {
  created: number;
  skipped: number;
  total: number;
  errors: string[];
};

const settingsBase = (appId: string) =>
  `/api/admin/v1/applications/${appId}/settings/user-directory`;

function scoped(appId: string, init?: RequestInit): RequestInit {
  return {
    ...init,
    headers: { ...appScopeHeaders(appId), ...(init?.headers as Record<string, string>) },
  };
}

export async function fetchUserDirectorySettings(appId: string): Promise<UserDirectorySettings> {
  return apiFetch<UserDirectorySettings>(settingsBase(appId), scoped(appId));
}

export async function saveUserDirectorySettings(
  appId: string,
  body: UserDirectorySettings,
): Promise<UserDirectorySettings> {
  return apiFetch<UserDirectorySettings>(
    settingsBase(appId),
    scoped(appId, { method: "PUT", body: JSON.stringify(body) }),
  );
}

export async function resetUserDirectorySettings(appId: string): Promise<void> {
  await apiFetch<void>(settingsBase(appId), scoped(appId, { method: "DELETE" }));
}

export async function exportUsersCsv(appId: string): Promise<Blob> {
  const path = `api/admin/v1/applications/${appId}/users/export?format=csv`;
  const res = await fetch(withBasePath(`/api/proxy/${path}`), {
    credentials: "same-origin",
    headers: {
      Accept: "text/csv, application/octet-stream",
      ...appScopeHeaders(appId),
    },
    cache: "no-store",
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(text || `Export failed (${res.status})`);
  }
  return res.blob();
}

export async function importUsersFile(
  appId: string,
  file: File,
  source: UserDirectorySourceKey,
): Promise<UserImportResult> {
  const form = new FormData();
  form.append("file", file);
  const path = `api/admin/v1/applications/${appId}/users/import?source=${source}`;
  const res = await fetch(withBasePath(`/api/proxy/${path}`), {
    method: "POST",
    credentials: "same-origin",
    headers: appScopeHeaders(appId),
    body: form,
    cache: "no-store",
  });
  const text = await res.text();
  let body: unknown = null;
  if (text) {
    try {
      body = JSON.parse(text);
    } catch {
      body = text;
    }
  }
  if (!res.ok) {
    const detail =
      typeof body === "object" && body !== null && "detail" in body
        ? String((body as { detail: unknown }).detail)
        : text || `Import failed (${res.status})`;
    throw new Error(detail);
  }
  return body as UserImportResult;
}

export async function previewLdapImport(appId: string): Promise<Record<string, unknown>> {
  return apiFetch<Record<string, unknown>>(
    `/api/admin/v1/applications/${appId}/users/import/ldap/preview`,
    scoped(appId, { method: "POST", body: JSON.stringify({}) }),
  );
}
