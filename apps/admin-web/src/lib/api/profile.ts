import { browserApiFetch as apiFetch } from "./browser-client";
import { ApiError } from "./http";
import type { OperatorTier } from "./context";

export type OperatorProfile = {
  operatorTier: string;
  platformSuperAdmin: boolean;
  principal: string;
  userId: string | null;
  tenantId: string | null;
  tenantSlug: string | null;
  tenantName: string | null;
  email: string | null;
  username: string | null;
  displayName: string;
  emailVerified: boolean;
  hasPassword: boolean;
  status: string;
  createdAt: string | null;
  /** The configured emergency account: nothing to edit here. */
  breakGlass: boolean;
};

type AdminContextDto = {
  platformSuperAdmin: boolean;
  operatorTier?: OperatorTier;
  principal: string;
  email?: string | null;
  displayName?: string;
  tenantId?: string | null;
  tenantSlug?: string | null;
  tenantName?: string | null;
  userId?: string | null;
  breakGlass?: boolean;
};

function profileFromContext(raw: AdminContextDto): OperatorProfile {
  const email = raw.email ?? null;
  const principal = raw.principal || "Operator";
  return {
    operatorTier: raw.operatorTier ?? (raw.platformSuperAdmin ? "platform" : "application"),
    platformSuperAdmin: raw.platformSuperAdmin,
    principal,
    userId: raw.userId ?? null,
    tenantId: raw.tenantId ?? null,
    tenantSlug: raw.tenantSlug ?? null,
    tenantName: raw.tenantName ?? null,
    email,
    username: email?.split("@")[0] ?? principal,
    displayName: raw.displayName ?? principal,
    emailVerified: Boolean(email),
    hasPassword: !raw.breakGlass,
    status: "active",
    createdAt: null,
    breakGlass: raw.breakGlass ?? false,
  };
}

export async function fetchOperatorProfile(): Promise<OperatorProfile> {
  try {
    return await apiFetch<OperatorProfile>("/api/admin/v1/me");
  } catch (e) {
    if (e instanceof ApiError && (e.status === 401 || e.status === 404)) {
      const ctx = await apiFetch<AdminContextDto>("/api/admin/v1/context");
      return profileFromContext(ctx);
    }
    throw e;
  }
}

export async function updateOperatorProfile(input: {
  username?: string;
  displayName?: string;
}): Promise<OperatorProfile> {
  try {
    return await apiFetch<OperatorProfile>("/api/admin/v1/me", {
      method: "PATCH",
      body: JSON.stringify(input),
    });
  } catch (e) {
    if (e instanceof ApiError && (e.status === 401 || e.status === 404)) {
      throw new ApiError(
        "Profile updates require a restarted auth-server (operator profile API not available yet).",
        e.status,
        e.body,
      );
    }
    throw e;
  }
}

export async function changeOperatorPassword(input: {
  currentPassword: string;
  newPassword: string;
}): Promise<void> {
  try {
    await apiFetch<void>("/api/admin/v1/me/password", {
      method: "POST",
      body: JSON.stringify(input),
    });
  } catch (e) {
    if (e instanceof ApiError && (e.status === 401 || e.status === 404)) {
      throw new ApiError(
        "Password change requires a restarted auth-server (operator profile API not available yet).",
        e.status,
        e.body,
      );
    }
    throw e;
  }
}
