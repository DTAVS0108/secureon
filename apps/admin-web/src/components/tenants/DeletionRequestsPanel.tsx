"use client";

import { useCallback, useState } from "react";
import { useDialog } from "@/components/ui/Dialogs";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { LocalTime } from "@/components/ui/LocalTime";
import { useToast } from "@/components/ui/Toast";
import { browserApiFetch as apiFetch } from "@/lib/api/browser-client";

type DeletionRequest = {
  userId: string;
  email: string;
  displayName: string | null;
  requestedAt: string;
  deleteBy: string;
};

/**
 * Users who asked for their account to be deleted (Account › Privacy). Their account is already off; delete it
 * now, or cancel. Requests left alone are completed automatically after 30 days.
 */
export function DeletionRequestsPanel({ tenantId }: { tenantId: string }) {
  const { toast } = useToast();
  const [rows, setRows] = useState<DeletionRequest[]>([]);
  const dialog = useDialog();
  const [busy, setBusy] = useState<string | null>(null);
  const base = `/api/admin/v1/tenants/${tenantId}/deletion-requests`;

  const reload = useCallback(async () => {
    try {
      setRows(await apiFetch<DeletionRequest[]>(base));
    } catch (e) {
      toast(e instanceof Error ? e.message : "Failed to load deletion requests", "error");
    }
  }, [base, toast]);

  useDeferredEffect(() => {
    reload();
  }, [reload]);

  async function act(userId: string, action: "complete" | "cancel", email: string) {
    if (
      action === "complete" &&
      !(await dialog.confirm({
        title: `Delete ${email} now?`,
        message: "Their account, app access, roles and sessions are removed for good.",
        confirmLabel: "Delete account",
      }))
    ) {
      return;
    }
    setBusy(userId);
    try {
      await apiFetch<void>(`${base}/${userId}/${action}`, { method: "POST" });
      toast(action === "complete" ? "Account deleted" : "Request cancelled; the account is on again", "success");
      await reload();
    } catch (e) {
      toast(e instanceof Error ? e.message : "Could not do that", "error");
    } finally {
      setBusy(null);
    }
  }

  return (
    <Card padded={false}>
      <CardHeader
        title="Deletion requests"
        description="Users who asked for their account to be deleted. Their account is already turned off; it is deleted automatically 30 days after the request."
      />
      <div className="px-5 pb-5">
        {rows.length === 0 ? (
          <p className="text-sm text-muted">No pending requests.</p>
        ) : (
          <ul className="divide-y divide-ui rounded-xl border border-ui">
            {rows.map((r) => (
              <li key={r.userId} className="flex flex-wrap items-center justify-between gap-3 px-4 py-3">
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium text-ui">{r.displayName || r.email}</p>
                  <p className="text-xs text-muted">
                    {r.email} · asked <LocalTime value={r.requestedAt} format="datetime" /> · deleted by{" "}
                    <LocalTime value={r.deleteBy} format="date" />
                  </p>
                </div>
                <div className="flex gap-1">
                  <Button
                    variant="ghost"
                    size="sm"
                    disabled={busy === r.userId}
                    onClick={() => void act(r.userId, "cancel", r.email)}
                  >
                    Cancel request
                  </Button>
                  <Button
                    variant="danger"
                    size="sm"
                    disabled={busy === r.userId}
                    onClick={() => void act(r.userId, "complete", r.email)}
                  >
                    Delete now
                  </Button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>
    </Card>
  );
}
