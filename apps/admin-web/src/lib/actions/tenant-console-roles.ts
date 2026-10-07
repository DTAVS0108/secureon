"use server";

// Shared API helpers called here run as the signed-in user (registers the lookup).
import "@/lib/api/server-auth";

import { revalidatePath } from "next/cache";
import { apiFetch } from "@/lib/api/server-client";
import type { AdminConsoleRoleType } from "@/lib/api/admin-console-access";

export async function saveConsoleRoleFeaturesAction(
  tenantId: string,
  roleType: AdminConsoleRoleType,
  features: string[],
): Promise<string[]> {
  const updated = await apiFetch<string[]>(
    `/api/admin/v1/tenants/${tenantId}/console-roles/${roleType}/features`,
    {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ features }),
    },
  );
  revalidatePath(`/tenants/${tenantId}/roles`);
  revalidatePath(`/tenants/${tenantId}`);
  return updated;
}
