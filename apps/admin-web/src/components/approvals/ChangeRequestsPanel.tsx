"use client";

import Link from "next/link";
import { useState } from "react";
import { NoteDialog } from "@/components/approvals/NoteDialog";
import { Badge, type BadgeTone } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { LocalTime } from "@/components/ui/LocalTime";
import { Table, TBody, TD, TH, THead, TR } from "@/components/ui/Table";
import { useToast } from "@/components/ui/Toast";
import {
  approveChangeRequest,
  CHANGE_ACTION_LABELS,
  changeTargetHref,
  listChangeRequests,
  rejectChangeRequest,
  withdrawChangeRequest,
  type ChangeRequest,
} from "@/lib/api/change-requests";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";

const STATUS: Record<ChangeRequest["status"], { label: string; tone: BadgeTone }> = {
  PENDING: { label: "Waiting", tone: "warning" },
  APPROVED: { label: "Approved", tone: "success" },
  REJECTED: { label: "Rejected", tone: "danger" },
  WITHDRAWN: { label: "Withdrawn", tone: "neutral" },
  FAILED: { label: "Failed", tone: "danger" },
};

const HISTORY_PREVIEW = 8;

/**
 * Changes waiting for approval (OAuth client deletes, Two Factor Authentication weakened). Platform operators see every tenant;
 * with `tenantId`, the tenant super admin's view of their tenant. Nobody decides their own request.
 */
export function ChangeRequestsPanel({ tenantId }: { tenantId?: string }) {
  const { toast } = useToast();
  const [rows, setRows] = useState<ChangeRequest[] | null>(null);
  const [showAllHistory, setShowAllHistory] = useState(false);
  const [reload, setReload] = useState(0);

  useDeferredEffect(() => {
    let cancelled = false;
    listChangeRequests(tenantId)
      .then((next) => !cancelled && setRows(next))
      .catch((e: unknown) => {
        if (cancelled) return;
        toast(e instanceof Error ? e.message : "Could not load pending approvals", "error");
        setRows([]);
      });
    return () => {
      cancelled = true;
    };
  }, [tenantId, reload, toast]);

  const pending = (rows ?? []).filter((r) => r.status === "PENDING");
  const history = (rows ?? []).filter((r) => r.status !== "PENDING");
  const shownHistory = showAllHistory ? history : history.slice(0, HISTORY_PREVIEW);
  const waitingForMe = pending.filter((r) => r.canDecide).length;

  async function run(work: () => Promise<unknown>, done: string): Promise<string | void> {
    try {
      await work();
      toast(done, "success");
      setReload((n) => n + 1);
    } catch (e) {
      setReload((n) => n + 1);
      return e instanceof Error ? e.message : "Something went wrong";
    }
  }

  return (
    <Card padded={false} className="mt-6">
      <CardHeader
        title="Pending approvals"
        description={
          rows === null
            ? "Loading…"
            : pending.length === 0
              ? "Nothing is waiting. High-impact changes (deleting an OAuth client, anything that weakens Two Factor Authentication) wait here until another admin approves them."
              : `${pending.length} waiting, ${waitingForMe} for you to decide. You cannot decide a change you asked for.`
        }
        action={
          <Button size="sm" variant="secondary" onClick={() => setReload((n) => n + 1)}>
            Refresh
          </Button>
        }
      />
      {pending.length > 0 && (
        <div className="overflow-x-auto">
          <Table>
            <THead>
              <tr>
                <TH>Change</TH>
                {!tenantId && <TH>Tenant</TH>}
                <TH>Asked by</TH>
                <TH>Reason</TH>
                <TH className="text-right">Decision</TH>
              </tr>
            </THead>
            <TBody>
              {pending.map((r) => (
                <TR key={r.id}>
                  <TD>
                    <p className="font-medium">{CHANGE_ACTION_LABELS[r.action] ?? r.action}</p>
                    {changeTargetHref(r) ? (
                      <Link
                        href={changeTargetHref(r)!}
                        className="font-mono text-xs text-brand underline-offset-2 hover:underline"
                      >
                        {r.targetLabel}
                      </Link>
                    ) : (
                      <p className="font-mono text-xs text-soft">{r.targetLabel}</p>
                    )}
                    {r.details && <p className="mt-1 max-w-md text-xs text-muted">{r.details}</p>}
                  </TD>
                  {!tenantId && <TD className="text-sm text-soft">{r.tenantName ?? "—"}</TD>}
                  <TD className="text-sm">
                    <p>{r.mine ? "You" : r.requestedBy}</p>
                    <p className="text-xs text-faint"><LocalTime value={r.requestedAt} /></p>
                  </TD>
                  <TD className="max-w-xs text-sm text-soft">{r.reason ?? <span className="text-faint">—</span>}</TD>
                  <TD>
                    <div className="flex flex-wrap justify-end gap-2">
                      {r.canDecide && (
                        <>
                          <NoteDialog
                            triggerLabel="Approve"
                            triggerVariant="primary"
                            title={`Approve: ${CHANGE_ACTION_LABELS[r.action] ?? r.action}?`}
                            message={
                              r.action === "oauth_client.delete" ? (
                                <>
                                  <strong className="font-mono">{r.targetLabel}</strong> is deleted now and its
                                  credentials stop working immediately. Asked by {r.requestedBy}.
                                </>
                              ) : (
                                <>
                                  {r.details ?? r.targetLabel}. This weakens Two Factor Authentication and applies
                                  now. Asked by {r.requestedBy}.
                                </>
                              )
                            }
                            noteLabel="Note"
                            confirmLabel={r.action === "oauth_client.delete" ? "Approve and delete" : "Approve and apply"}
                            confirmVariant="danger"
                            onConfirm={(note) =>
                              run(
                                () => approveChangeRequest(r.id, note),
                                r.action === "oauth_client.delete" ? "Approved: client deleted" : "Approved: change applied",
                              )
                            }
                          />
                          <NoteDialog
                            triggerLabel="Reject"
                            title="Reject this request?"
                            message={<>Nothing changes. {r.requestedBy} sees your reason.</>}
                            noteLabel="Reason"
                            noteRequired
                            notePlaceholder="e.g. Still used by the mobile app"
                            confirmLabel="Reject"
                            onConfirm={(note) => run(() => rejectChangeRequest(r.id, note), "Request rejected")}
                          />
                        </>
                      )}
                      {r.mine && (
                        <>
                          <Badge tone="info">Waiting for another admin</Badge>
                          <Button
                            size="sm"
                            variant="ghost"
                            onClick={() => void run(() => withdrawChangeRequest(r.id), "Request withdrawn").then(
                              (error) => error && toast(error, "error"),
                            )}
                          >
                            Withdraw
                          </Button>
                        </>
                      )}
                      {!r.mine && !r.canDecide && <Badge tone="neutral">Waiting</Badge>}
                    </div>
                  </TD>
                </TR>
              ))}
            </TBody>
          </Table>
        </div>
      )}
      {history.length > 0 && (
        <div className="border-t border-ui">
          <p className="px-5 pt-4 text-xs font-medium uppercase tracking-wide text-muted">Recent decisions</p>
          <div className="overflow-x-auto">
            <Table>
              <THead>
                <tr>
                  <TH>Change</TH>
                  {!tenantId && <TH>Tenant</TH>}
                  <TH>Asked by</TH>
                  <TH>Outcome</TH>
                  <TH>Decided by</TH>
                </tr>
              </THead>
              <TBody>
                {shownHistory.map((r) => (
                  <TR key={r.id}>
                    <TD>
                      <p className="text-sm">{CHANGE_ACTION_LABELS[r.action] ?? r.action}</p>
                      <p className="font-mono text-xs text-faint">{r.targetLabel}</p>
                      {r.details && <p className="max-w-md text-xs text-faint">{r.details}</p>}
                    </TD>
                    {!tenantId && <TD className="text-sm text-soft">{r.tenantName ?? "—"}</TD>}
                    <TD className="text-sm text-soft">
                      {r.requestedBy}
                      {r.reason && <p className="text-xs text-faint">“{r.reason}”</p>}
                    </TD>
                    <TD>
                      <Badge tone={STATUS[r.status].tone}>{STATUS[r.status].label}</Badge>
                      {r.decisionNote && <p className="mt-1 max-w-xs text-xs text-faint">{r.decisionNote}</p>}
                    </TD>
                    <TD className="text-sm text-soft">
                      {r.decidedBy ?? "—"}
                      <p className="text-xs text-faint"><LocalTime value={r.decidedAt} /></p>
                    </TD>
                  </TR>
                ))}
              </TBody>
            </Table>
          </div>
          {history.length > HISTORY_PREVIEW && (
            <div className="px-5 py-3">
              <Button size="sm" variant="ghost" onClick={() => setShowAllHistory((v) => !v)}>
                {showAllHistory ? "Show fewer" : `Show all ${history.length}`}
              </Button>
            </div>
          )}
        </div>
      )}
    </Card>
  );
}
