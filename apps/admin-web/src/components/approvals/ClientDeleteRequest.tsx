"use client";

import Link from "next/link";
import { useState } from "react";
import { NoteDialog } from "@/components/approvals/NoteDialog";
import { Button } from "@/components/ui/Button";
import { LocalTime } from "@/components/ui/LocalTime";
import { useToast } from "@/components/ui/Toast";
import { useAdminContext } from "@/components/AdminContextProvider";
import {
  loadOAuthClientDeleteRequest,
  requestOAuthClientDelete,
  withdrawChangeRequest,
  type ChangeRequest,
} from "@/lib/api/change-requests";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";

/**
 * Danger zone of an OAuth client: deleting needs a second admin. This asks for it, or shows the
 * pending request (with Withdraw for the person who asked).
 */
export function ClientDeleteRequest({ clientDbId, clientId, tenantId }: {
  clientDbId: string;
  clientId: string;
  tenantId: string;
}) {
  const { toast } = useToast();
  const ctx = useAdminContext();
  const [pending, setPending] = useState<ChangeRequest | null | undefined>(undefined);
  const [withdrawing, setWithdrawing] = useState(false);

  useDeferredEffect(() => {
    let cancelled = false;
    loadOAuthClientDeleteRequest(clientDbId)
      .then((row) => !cancelled && setPending(row))
      .catch(() => !cancelled && setPending(null));
    return () => {
      cancelled = true;
    };
  }, [clientDbId]);

  // Where approvers decide: the platform Access review, or this tenant's for a tenant super admin.
  const reviewHref =
    ctx.operatorTier === "platform"
      ? "/access-review"
      : ctx.operatorTier === "tenant_super"
        ? `/tenants/${tenantId}/access-review`
        : null;

  async function withdraw(row: ChangeRequest) {
    setWithdrawing(true);
    try {
      await withdrawChangeRequest(row.id);
      setPending(null);
      toast("Delete request withdrawn", "success");
    } catch (e) {
      toast(e instanceof Error ? e.message : "Could not withdraw the request", "error");
    } finally {
      setWithdrawing(false);
    }
  }

  if (pending === undefined) {
    return <p className="p-5 text-sm text-faint">Loading…</p>;
  }

  if (pending) {
    return (
      <div className="flex flex-col gap-3 p-5 sm:flex-row sm:items-center sm:justify-between">
        <div className="text-sm">
          <p className="font-medium text-amber-700 dark:text-amber-300">Deletion waiting for approval</p>
          <p className="mt-1 text-soft">
            {pending.mine ? "You" : pending.requestedBy} asked <LocalTime value={pending.requestedAt} />
            {pending.reason ? `: “${pending.reason}”` : "."} The client keeps working until a platform operator
            or the tenant&apos;s super admin approves it on{" "}
            {reviewHref ? (
              <Link href={reviewHref} className="text-brand underline-offset-2 hover:underline">
                Access review
              </Link>
            ) : (
              "Access review"
            )}
            .
          </p>
        </div>
        {pending.mine && (
          <Button variant="secondary" size="sm" onClick={() => void withdraw(pending)} disabled={withdrawing}>
            {withdrawing ? "Withdrawing…" : "Withdraw request"}
          </Button>
        )}
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-3 p-5 sm:flex-row sm:items-center sm:justify-between">
      <p className="text-sm text-muted">
        Permanently remove this OAuth client. Deleting needs approval from another admin (a platform operator or
        the tenant&apos;s super admin). The application product and its users remain.
      </p>
      <NoteDialog
        triggerLabel="Request deletion"
        triggerVariant="danger"
        triggerSize="md"
        title={`Request deletion of ${clientId}?`}
        message="Nothing is deleted yet. Another admin approves the request on Access review; then the client's credentials stop working immediately."
        noteLabel="Reason"
        notePlaceholder="e.g. App retired, replaced by web-v2"
        confirmLabel="Send for approval"
        confirmVariant="danger"
        onConfirm={async (reason) => {
          try {
            setPending(await requestOAuthClientDelete(clientDbId, reason));
            toast("Sent for approval", "success");
          } catch (e) {
            return e instanceof Error ? e.message : "Could not send the request";
          }
        }}
      />
    </div>
  );
}
