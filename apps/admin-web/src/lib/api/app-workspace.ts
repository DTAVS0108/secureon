import { unstable_rethrow } from "next/navigation";
import { fetchAdminContext, type AdminContext, type ApplicationContextItem } from "./context";

/** Resolve application metadata for app-scoped pages (only apps in the operator context). */
export async function resolveApplicationMeta(
  applicationId: string,
): Promise<ApplicationContextItem | null> {
  const ctx = await loadAdminContextSafe();
  return ctx.applications.find((a) => a.id === applicationId) ?? null;
}

export async function loadAdminContextSafe(): Promise<AdminContext> {
  try {
    return await fetchAdminContext();
  } catch (err) {
    // Let the sign-in redirect (no or expired session) through instead of rendering an empty console.
    unstable_rethrow(err);
    return {
      platformSuperAdmin: false,
      operatorTier: "application",
      principal: "",
      email: null,
      displayName: "",
      tenantId: null,
      tenantSlug: null,
      tenantName: null,
      userId: null,
      actAsEmail: null,
      oauthClientCount: 0,
      applications: [],
      breakGlass: false,
      sessionExpiresAt: null,
      sessionIdleTimeoutSeconds: 1800,
    };
  }
}
