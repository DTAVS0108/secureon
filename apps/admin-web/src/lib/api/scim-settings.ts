import { browserApiFetch as apiFetch } from "./browser-client";

export type ScimSettings = {
  enabled: boolean;
  bearerTokenConfigured: boolean;
  userNameDomain?: string;
};

export type ScimSettingsInput = {
  enabled?: boolean;
  bearerToken?: string;
  userNameDomain?: string;
};

export async function fetchScimSettings(): Promise<ScimSettings> {
  return apiFetch<ScimSettings>("/api/admin/v1/settings/scim");
}

export async function saveScimSettings(body: ScimSettingsInput): Promise<ScimSettings> {
  return apiFetch<ScimSettings>("/api/admin/v1/settings/scim", {
    method: "PUT",
    body: JSON.stringify(body),
  });
}
