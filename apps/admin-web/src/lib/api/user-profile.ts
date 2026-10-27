import { appScopeHeaders } from "./http";
import { browserApiFetch as apiFetch } from "./browser-client";

/** One extra sign-up field of an application, saved in the user's attributes under `key`. */
export type SignupField = {
  key: string;
  label: string;
  type: "text" | "select" | "checkbox";
  required: boolean;
  options: string[];
  maxLength: number;
};

export type SignupPolicy = { allowedDomains: string[]; blockedDomains: string[]; fields: SignupField[] };

export async function fetchSignupPolicy(applicationId: string): Promise<SignupPolicy> {
  return apiFetch<SignupPolicy>(`/api/admin/v1/applications/${applicationId}/settings/signup-policy`, {
    headers: appScopeHeaders(applicationId),
  });
}

export async function saveSignupPolicy(applicationId: string, policy: SignupPolicy): Promise<SignupPolicy> {
  return apiFetch<SignupPolicy>(`/api/admin/v1/applications/${applicationId}/settings/signup-policy`, {
    method: "PUT",
    headers: appScopeHeaders(applicationId),
    body: JSON.stringify(policy),
  });
}

export type UserAttributes = { attributes: Record<string, string | number | boolean>; fields?: SignupField[] };

/** With `applicationId`, also that application's sign-up fields (to label the attributes). */
export async function fetchUserAttributes(userId: string, applicationId?: string): Promise<UserAttributes> {
  return applicationId
    ? apiFetch(`/api/admin/v1/applications/${applicationId}/users/${userId}/attributes`, {
        headers: appScopeHeaders(applicationId),
      })
    : apiFetch(`/api/admin/v1/users/${userId}/attributes`);
}

export async function saveUserAttributes(
  userId: string,
  attributes: Record<string, string | number | boolean>,
  applicationId?: string,
): Promise<UserAttributes> {
  const init = { method: "PUT", body: JSON.stringify({ attributes }) };
  return applicationId
    ? apiFetch(`/api/admin/v1/applications/${applicationId}/users/${userId}/attributes`, {
        ...init,
        headers: appScopeHeaders(applicationId),
      })
    : apiFetch(`/api/admin/v1/users/${userId}/attributes`, init);
}

export type LinkedIdentity = {
  id: string;
  providerId: string;
  provider: string;
  type: string | null;
  email: string | null;
  linkedAt: string | null;
};

const identitiesPath = (userId: string, applicationId?: string) =>
  applicationId
    ? `/api/admin/v1/applications/${applicationId}/users/${userId}/identities`
    : `/api/admin/v1/users/${userId}/identities`;

export async function listUserIdentities(userId: string, applicationId?: string): Promise<LinkedIdentity[]> {
  return apiFetch<LinkedIdentity[]>(identitiesPath(userId, applicationId), {
    headers: applicationId ? appScopeHeaders(applicationId) : undefined,
  });
}

export async function unlinkUserIdentity(userId: string, identityId: string, applicationId?: string): Promise<void> {
  await apiFetch<void>(`${identitiesPath(userId, applicationId)}/${identityId}`, {
    method: "DELETE",
    headers: applicationId ? appScopeHeaders(applicationId) : undefined,
  });
}

export type RoleAssignment = { roleId: string; roleName: string; grantedAt: string | null; expiresAt: string | null };

export async function listRoleAssignments(userId: string, applicationId: string): Promise<RoleAssignment[]> {
  return apiFetch<RoleAssignment[]>(`/api/admin/v1/applications/${applicationId}/users/${userId}/role-assignments`, {
    headers: appScopeHeaders(applicationId),
  });
}

/** `expiresAt` null: for good. */
export async function setRoleAssignment(
  userId: string,
  applicationId: string,
  roleId: string,
  expiresAt: string | null,
): Promise<RoleAssignment> {
  return apiFetch<RoleAssignment>(
    `/api/admin/v1/applications/${applicationId}/users/${userId}/role-assignments/${roleId}`,
    { method: "PUT", headers: appScopeHeaders(applicationId), body: JSON.stringify({ expiresAt }) },
  );
}
