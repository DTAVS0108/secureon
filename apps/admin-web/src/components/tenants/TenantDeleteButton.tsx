"use client";

import { useRouter } from "next/navigation";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { browserApiFetch } from "@/lib/api/browser-client";

/**
 * Deletes a tenant from the browser, so the console can ask for a fresh second step. The slug must be typed
 * again; every user is signed out of their apps first.
 */
export function TenantDeleteButton({
  tenant,
  triggerVariant = "danger",
  triggerSize = "md",
}: {
  tenant: { id: string; name: string; slug: string };
  triggerVariant?: "ghost" | "danger";
  triggerSize?: "sm" | "md";
}) {
  const router = useRouter();
  return (
    <ConfirmDialog
      action={async () => {
        try {
          await browserApiFetch<void>(
            `/api/admin/v1/tenants/${tenant.id}?confirm=${encodeURIComponent(tenant.slug)}`,
            { method: "DELETE" },
          );
        } catch (e) {
          return { error: e instanceof Error ? e.message : "Could not delete the tenant." };
        }
        router.push("/tenants");
        router.refresh();
      }}
      id={tenant.id}
      triggerLabel="Delete"
      triggerVariant={triggerVariant}
      triggerSize={triggerSize}
      title={`Delete ${tenant.name}?`}
      message="This deletes the tenant with its users, applications and their data, and signs every user out of their apps. It cannot be undone."
      confirmText={tenant.slug}
    />
  );
}
