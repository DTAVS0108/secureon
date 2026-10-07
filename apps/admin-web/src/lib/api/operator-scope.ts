import "server-only";

import type { ApplicationProduct, Tenant } from "@/lib/types";
import { getApplication } from "./applications";
import { loadAdminContextSafe } from "./app-workspace";
import type { ApplicationContextItem } from "./context";
import { ApiError } from "./http";
import { listTenants } from "./tenants";

const isForbidden = (e: unknown) => e instanceof ApiError && e.status === 403;

/**
 * Tenants the signed-in operator may see. Platform admins get the full list; tenant-level
 * operators (who may not call the platform tenant list) get the tenants of their applications.
 */
export async function listVisibleTenants(apps?: ApplicationContextItem[]): Promise<Tenant[]> {
  try {
    return await listTenants();
  } catch (e) {
    if (!isForbidden(e)) throw e;
    const scope = apps ?? (await loadAdminContextSafe()).applications;
    const byId = new Map<string, Tenant>();
    for (const app of scope) {
      if (!byId.has(app.tenantId)) {
        byId.set(app.tenantId, {
          id: app.tenantId,
          name: app.tenantName,
          slug: app.tenantSlug,
          status: "active",
          plan: "free",
          userCount: 0,
          appCount: scope.filter((a) => a.tenantId === app.tenantId).length,
          createdAt: "",
        });
      }
    }
    return [...byId.values()];
  }
}

/** Application details, falling back to the operator's context when the detail call is not permitted. */
export async function getVisibleApplication(
  applicationId: string,
  meta?: ApplicationContextItem | null,
): Promise<ApplicationProduct | null> {
  try {
    return await getApplication(applicationId);
  } catch (e) {
    if (!isForbidden(e) || !meta) throw e;
    return {
      id: meta.id,
      tenantId: meta.tenantId,
      name: meta.name,
      slug: meta.slug,
      status: meta.status?.toLowerCase() === "active" ? "active" : "disabled",
      oauthClientCount: 0,
      createdAt: "",
      updatedAt: "",
    };
  }
}
