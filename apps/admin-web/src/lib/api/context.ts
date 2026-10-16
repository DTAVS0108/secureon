import { apiFetch } from "./server-client";

export type OperatorTier = "platform" | "tenant_super" | "tenant" | "application";

export type ApplicationContextItem = {
  id: string;
  tenantId: string;
  tenantName: string;
  tenantSlug: string;
  name: string;
  slug: string;
  status: string;
  permissions: string[];
  /** Admin console sections this operator may access for this application. */
  consoleFeatures: string[];
  /** May register and manage this application's OAuth clients (Connect an app). */
  canManageClients: boolean;
};

export type AdminContext = {
  /** True only for the platform operator account without SECUREONE_ACT_AS_EMAIL. */
  platformSuperAdmin: boolean;
  operatorTier: OperatorTier;
  principal: string;
  email: string | null;
  displayName: string;
  tenantId: string | null;
  tenantSlug: string | null;
  tenantName: string | null;
  userId: string | null;
  actAsEmail: string | null;
  /** Total OAuth clients in the platform registry (all tenants). */
  oauthClientCount: number;
  applications: ApplicationContextItem[];
  /** Signed in with the configured emergency (break-glass) account. */
  breakGlass: boolean;
  /** ISO time at which the console session ends at the latest. */
  sessionExpiresAt: string | null;
  /** Session ends after this many seconds without activity. */
  sessionIdleTimeoutSeconds: number;
};

export async function fetchAdminContext(): Promise<AdminContext> {
  const raw = await apiFetch<{
    platformSuperAdmin: boolean;
    operatorTier?: OperatorTier;
    principal: string;
    email?: string | null;
    displayName?: string;
    tenantId?: string | null;
    tenantSlug?: string | null;
    tenantName?: string | null;
    userId?: string | null;
    actAsEmail: string | null;
    oauthClientCount?: number;
    applications: ApplicationContextItem[];
    breakGlass?: boolean;
    sessionExpiresAt?: string | null;
    sessionIdleTimeoutSeconds?: number;
  }>("/api/admin/v1/context");
  return {
    platformSuperAdmin: raw.platformSuperAdmin,
    operatorTier: raw.operatorTier ?? (raw.platformSuperAdmin ? "platform" : "application"),
    principal: raw.principal,
    email: raw.email ?? null,
    displayName: raw.displayName ?? raw.principal,
    tenantId: raw.tenantId ?? null,
    tenantSlug: raw.tenantSlug ?? null,
    tenantName: raw.tenantName ?? null,
    userId: raw.userId ?? null,
    actAsEmail: raw.actAsEmail,
    oauthClientCount: raw.oauthClientCount ?? raw.applications.length,
    applications: raw.applications.map((app) => ({
      ...app,
      permissions: app.permissions ?? [],
      consoleFeatures: app.consoleFeatures ?? [],
      canManageClients: app.canManageClients ?? false,
    })),
    breakGlass: raw.breakGlass ?? false,
    sessionExpiresAt: raw.sessionExpiresAt ?? null,
    sessionIdleTimeoutSeconds: raw.sessionIdleTimeoutSeconds ?? 1800,
  };
}
