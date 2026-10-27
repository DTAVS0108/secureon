"use client";

import { useRouter } from "next/navigation";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { deleteApplicationApi } from "@/lib/api/applications";
import type { ApplicationProduct } from "@/lib/types";

/**
 * Deletes an application from the browser, so the console can ask for a fresh second step. Its slug must be typed
 * again. With OAuth clients the deletion waits for a second admin (Access review).
 */
export function DeleteApplicationButton({ app }: { app: ApplicationProduct }) {
  const router = useRouter();
  return (
    <ConfirmDialog
      action={async (fd) => {
        try {
          const { pending } = await deleteApplicationApi(app.id, String(fd.get("confirm") ?? ""));
          router.refresh();
          if (pending) {
            return { error: "Another admin must approve deleting this application. The request is on Access review." };
          }
        } catch (e) {
          return { error: e instanceof Error ? e.message : "Could not delete the application." };
        }
      }}
      id={app.id}
      triggerLabel="Delete application"
      triggerVariant="danger"
      triggerSize="sm"
      title={`Delete ${app.name}?`}
      message="Permanently deletes the application with its roles, permissions, groups, settings, sign-in providers and webhooks, and its database schema, and signs everyone out of it. People keep their tenant accounts but lose access to this application. This cannot be undone."
      confirmLabel="Delete application"
      confirmText={app.slug}
    />
  );
}
