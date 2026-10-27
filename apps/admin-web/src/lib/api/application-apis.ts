import { appScopeHeaders } from "./http";
import { browserApiFetch as apiFetch } from "./browser-client";

export type ApiScope = { value: string; description: string };

/** An API (resource server) of an application: access tokens with one of its scopes name it in "aud". */
export type ApplicationApi = { id: string; applicationId: string; name: string; identifier: string; scopes: ApiScope[] };

export type ApplicationApiInput = { name: string; identifier: string; scopes: ApiScope[] };

const base = (applicationId: string) => `/api/admin/v1/applications/${applicationId}/settings/apis`;

export async function fetchApplicationApis(applicationId: string): Promise<ApplicationApi[]> {
  return apiFetch<ApplicationApi[]>(base(applicationId), { headers: appScopeHeaders(applicationId) });
}

export async function createApplicationApi(applicationId: string, api: ApplicationApiInput): Promise<ApplicationApi> {
  return apiFetch<ApplicationApi>(base(applicationId), {
    method: "POST",
    headers: appScopeHeaders(applicationId),
    body: JSON.stringify(api),
  });
}

export async function updateApplicationApi(
  applicationId: string,
  apiId: string,
  api: ApplicationApiInput,
): Promise<ApplicationApi> {
  return apiFetch<ApplicationApi>(`${base(applicationId)}/${apiId}`, {
    method: "PUT",
    headers: appScopeHeaders(applicationId),
    body: JSON.stringify(api),
  });
}

export async function deleteApplicationApi(applicationId: string, apiId: string): Promise<void> {
  await apiFetch<void>(`${base(applicationId)}/${apiId}`, { method: "DELETE", headers: appScopeHeaders(applicationId) });
}
