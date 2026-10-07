import "server-only";

import { apiFetch } from "./server-client";
import type { OperatorProfile } from "./profile";
import type { OperatorTier } from "./context";

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
    const ctx = await apiFetch<AdminContextDto>("/api/admin/v1/context");
    return profileFromContext(ctx);
  }
}
