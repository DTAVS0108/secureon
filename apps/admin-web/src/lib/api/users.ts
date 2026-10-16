import { appScopeHeaders } from "./http";
import { browserApiFetch as apiFetch } from "./browser-client";
import type { User } from "@/lib/types";

type MfaFactorDto = {
  id: string;
  type: string;
  label: string;
  verified: boolean;
};

type UserDto = {
  id: string;
  tenantId: string;
  email: string;
  username: string;
  firstName: string;
  lastName: string;
  status: string;
  emailVerified: boolean;
  phone?: string | null;
  phoneVerified?: boolean;
  hasPassword?: boolean;
  locked?: boolean;
  failedLoginCount?: number;
  roleIds: string[];
  roleNames?: string[];
  mfaFactors?: MfaFactorDto[];
  allowedAuthMethods?: Record<string, boolean>;
  lastLoginAt: string | null;
  createdAt: string;
};

function mapUser(dto: UserDto): User {
  const status = dto.status.toLowerCase();
  const normalizedStatus =
    status === "pending" ? "invited" : (status as User["status"]);
  return {
    id: dto.id,
    tenantId: dto.tenantId,
    email: dto.email,
    username: dto.username,
    firstName: dto.firstName,
    lastName: dto.lastName,
    status: normalizedStatus,
    emailVerified: dto.emailVerified,
    phone: dto.phone ?? null,
    phoneVerified: dto.phoneVerified ?? false,
    hasPassword: dto.hasPassword ?? false,
    locked: dto.locked ?? false,
    failedLoginCount: dto.failedLoginCount ?? 0,
    roleIds: dto.roleIds,
    roleNames: dto.roleNames ?? [],
    mfaFactors: (dto.mfaFactors ?? []).map((f) => ({
      id: f.id,
      type: f.type as import("@/lib/types").MfaFactorType,
      label: f.label,
      verified: f.verified,
      addedAt: dto.createdAt,
    })),
    lastLoginAt: dto.lastLoginAt,
    createdAt: dto.createdAt,
  };
}

export async function listUsers(tenantId?: string, applicationId?: string): Promise<User[]> {
  const params = new URLSearchParams();
  if (tenantId) params.set("tenantId", tenantId);
  if (applicationId) params.set("applicationId", applicationId);
  const qs = params.toString();
  const path = qs ? `/api/admin/v1/users?${qs}` : "/api/admin/v1/users";
  const rows = await apiFetch<UserDto[]>(path, {
    headers: applicationId ? appScopeHeaders(applicationId) : undefined,
  });
  return rows.map(mapUser);
}

export async function listAdminUsers(tenantId?: string): Promise<User[]> {
  const params = new URLSearchParams({ adminOnly: "true" });
  if (tenantId) params.set("tenantId", tenantId);
  const rows = await apiFetch<UserDto[]>(`/api/admin/v1/users?${params}`);
  return rows.map(mapUser);
}

export async function getUser(id: string): Promise<User | null> {
  try {
    const dto = await apiFetch<UserDto>(`/api/admin/v1/users/${id}`);
    return mapUser(dto);
  } catch (e) {
    if (e instanceof Error && "status" in e && (e as { status: number }).status === 404) {
      return null;
    }
    throw e;
  }
}

export async function getApplicationUser(
  applicationId: string,
  userId: string,
): Promise<User | null> {
  try {
    const dto = await apiFetch<UserDto>(
      `/api/admin/v1/applications/${applicationId}/users/${userId}`,
      { headers: appScopeHeaders(applicationId) },
    );
    return mapUser(dto);
  } catch (e) {
    if (e instanceof Error && "status" in e && (e as { status: number }).status === 404) {
      return null;
    }
    throw e;
  }
}

export async function updateUserAuthMethodsApi(
  applicationId: string,
  userId: string,
  methods: Record<string, boolean>,
): Promise<User> {
  const dto = await apiFetch<UserDto>(
    `/api/admin/v1/applications/${applicationId}/users/${userId}/auth-methods`,
    {
      method: "PUT",
      headers: appScopeHeaders(applicationId),
      body: JSON.stringify({ methods }),
    },
  );
  return mapUser(dto);
}

export async function deleteUserMfaFactorApi(
  applicationId: string,
  userId: string,
  factorId: string,
): Promise<void> {
  await apiFetch<void>(
    `/api/admin/v1/applications/${applicationId}/users/${userId}/mfa/factors/${factorId}`,
    { method: "DELETE", headers: appScopeHeaders(applicationId) },
  );
}

export async function resetUserMfaMethodApi(
  applicationId: string,
  userId: string,
  methodId: string,
): Promise<User> {
  const dto = await apiFetch<UserDto>(
    `/api/admin/v1/applications/${applicationId}/users/${userId}/mfa/methods/${methodId}/reset`,
    { method: "POST", headers: appScopeHeaders(applicationId) },
  );
  return mapUser(dto);
}

export async function createUserApi(input: {
  tenantId: string;
  applicationId?: string;
  email: string;
  username?: string;
  firstName: string;
  lastName: string;
  status: string;
  roleIds: string[];
}): Promise<User> {
  const dto = await apiFetch<UserDto>("/api/admin/v1/users", {
    method: "POST",
    // The app's roles live in its own schema; the header tells the server which app this is for.
    ...(input.applicationId ? { headers: appScopeHeaders(input.applicationId) } : {}),
    body: JSON.stringify({
      tenantId: input.tenantId,
      applicationId: input.applicationId ?? null,
      email: input.email,
      username: input.username,
      firstName: input.firstName,
      lastName: input.lastName,
      status: input.status,
      roleIds: input.roleIds,
    }),
  });
  return mapUser(dto);
}

export async function updateUserApi(
  id: string,
  input: {
    email: string;
    username?: string;
    firstName: string;
    lastName: string;
    status: string;
    emailVerified: boolean;
    roleIds: string[];
  },
  /** Application whose roles are being set (its roles live in its own schema). */
  applicationId?: string,
): Promise<User> {
  const dto = await apiFetch<UserDto>(`/api/admin/v1/users/${id}`, {
    method: "PUT",
    ...(applicationId ? { headers: appScopeHeaders(applicationId) } : {}),
    body: JSON.stringify({
      email: input.email,
      username: input.username,
      firstName: input.firstName,
      lastName: input.lastName,
      status: input.status,
      emailVerified: input.emailVerified,
      roleIds: input.roleIds,
    }),
  });
  return mapUser(dto);
}

export async function deleteUserApi(id: string): Promise<void> {
  await apiFetch<void>(`/api/admin/v1/users/${id}`, { method: "DELETE" });
}

export async function setUserStatusApi(id: string, status: string): Promise<User> {
  const dto = await apiFetch<UserDto>(`/api/admin/v1/users/${id}/status`, {
    method: "PATCH",
    body: JSON.stringify({ status }),
  });
  return mapUser(dto);
}

export async function unlockUserApi(id: string): Promise<User> {
  const dto = await apiFetch<UserDto>(`/api/admin/v1/users/${id}/unlock`, { method: "POST" });
  return mapUser(dto);
}

export async function adminSetUserPasswordApi(
  id: string,
  password: string,
  applicationId?: string,
): Promise<void> {
  if (applicationId) {
    await apiFetch<void>(
      `/api/admin/v1/applications/${applicationId}/users/${id}/password/set`,
      {
        method: "POST",
        body: JSON.stringify({ password }),
        headers: { "X-Application-Id": applicationId },
      },
    );
    return;
  }
  await apiFetch<void>(`/api/admin/v1/users/${id}/password/set`, {
    method: "POST",
    body: JSON.stringify({ password }),
  });
}

export async function sendUserPasswordResetEmailApi(id: string, applicationId?: string): Promise<void> {
  if (applicationId) {
    await apiFetch(
      `/api/admin/v1/applications/${applicationId}/users/${id}/password/reset-email`,
      { method: "POST", headers: appScopeHeaders(applicationId) },
    );
    return;
  }
  await apiFetch(`/api/admin/v1/users/${id}/password/reset-email`, { method: "POST" });
}

export async function removeUserPasswordApi(id: string, applicationId?: string): Promise<void> {
  if (applicationId) {
    await apiFetch<void>(
      `/api/admin/v1/applications/${applicationId}/users/${id}/password/remove`,
      { method: "POST", headers: appScopeHeaders(applicationId) },
    );
    return;
  }
  await apiFetch<void>(`/api/admin/v1/users/${id}/password/remove`, { method: "POST" });
}

export async function sendUserSetPasswordInviteEmailApi(
  id: string,
  applicationId?: string,
): Promise<void> {
  if (applicationId) {
    await apiFetch(
      `/api/admin/v1/applications/${applicationId}/users/${id}/password/set-password-email`,
      { method: "POST", headers: appScopeHeaders(applicationId) },
    );
    return;
  }
  await apiFetch(`/api/admin/v1/users/${id}/password/set-password-email`, { method: "POST" });
}

export async function resendUserVerificationEmailApi(id: string, applicationId?: string): Promise<void> {
  if (applicationId) {
    await apiFetch(
      `/api/admin/v1/applications/${applicationId}/users/${id}/email/resend-verification`,
      { method: "POST", headers: appScopeHeaders(applicationId) },
    );
    return;
  }
  await apiFetch(`/api/admin/v1/users/${id}/email/resend-verification`, { method: "POST" });
}

export async function markUserEmailVerifiedApi(id: string, applicationId?: string): Promise<User> {
  if (applicationId) {
    const dto = await apiFetch<UserDto>(
      `/api/admin/v1/applications/${applicationId}/users/${id}/email/verify`,
      { method: "POST", headers: appScopeHeaders(applicationId) },
    );
    return mapUser(dto);
  }
  const dto = await apiFetch<UserDto>(`/api/admin/v1/users/${id}/email/verify`, { method: "POST" });
  return mapUser(dto);
}

export async function setUserEmailVerifiedApi(
  id: string,
  verified: boolean,
  applicationId?: string,
): Promise<User> {
  const body = JSON.stringify({ verified });
  if (applicationId) {
    const dto = await apiFetch<UserDto>(
      `/api/admin/v1/applications/${applicationId}/users/${id}/email-verification`,
      { method: "PATCH", body, headers: appScopeHeaders(applicationId) },
    );
    return mapUser(dto);
  }
  const dto = await apiFetch<UserDto>(`/api/admin/v1/users/${id}/email-verification`, {
    method: "PATCH",
    body,
  });
  return mapUser(dto);
}

export async function resetUserMfaApi(id: string): Promise<void> {
  await apiFetch<void>(`/api/admin/v1/users/${id}/mfa/reset`, { method: "POST" });
}
