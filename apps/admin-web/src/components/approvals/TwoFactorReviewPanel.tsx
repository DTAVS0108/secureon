"use client";

import Link from "next/link";
import { useState } from "react";
import { Badge, type BadgeTone } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { LocalTime } from "@/components/ui/LocalTime";
import { Table, TBody, TD, TH, THead, TR } from "@/components/ui/Table";
import { useToast } from "@/components/ui/Toast";
import { loadTwoFactorReview, type TwoFactorReview } from "@/lib/api/change-requests";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";

const MODE: Record<string, { label: string; tone: BadgeTone }> = {
  required: { label: "Required", tone: "success" },
  optional: { label: "Optional", tone: "warning" },
  off: { label: "Off", tone: "danger" },
};

/**
 * Two Factor Authentication on Access review: where it is off or only optional, who is exempt, which admins
 * sign in without a second step, and whether weakening changes wait for a second admin. Platform operators see
 * every tenant; with `tenantId`, the tenant super admin's view of their tenant.
 */
export function TwoFactorReviewPanel({ tenantId }: { tenantId?: string }) {
  const { toast } = useToast();
  const [review, setReview] = useState<TwoFactorReview | null>(null);
  const [reload, setReload] = useState(0);

  useDeferredEffect(() => {
    let cancelled = false;
    loadTwoFactorReview(tenantId)
      .then((r) => !cancelled && setReview(r))
      .catch((e: unknown) => !cancelled && toast(e instanceof Error ? e.message : "Could not load the review", "error"));
    return () => {
      cancelled = true;
    };
  }, [tenantId, reload, toast]);

  const weakApps = (review?.applications ?? []).filter((a) => a.mode !== "required");

  return (
    <Card padded={false} className="mt-6" data-testid="two-factor-review">
      <CardHeader
        title="Two Factor Authentication"
        description="Where it is off or optional, who is exempt, and admins without a second step. Changes that weaken it wait for a second admin."
        action={
          <Button size="sm" variant="secondary" onClick={() => setReload((n) => n + 1)}>
            Refresh
          </Button>
        }
      />
      {review === null ? (
        <p className="px-5 py-4 text-sm text-muted">Loading…</p>
      ) : (
        <div className="space-y-5 px-5 pb-5">
          <p
            role="status"
            data-testid="approvals-state"
            className={
              review.approvalsActive
                ? "rounded-lg bg-emerald-500/10 px-3 py-2 text-sm text-ui"
                : "rounded-lg bg-amber-500/10 px-3 py-2 text-sm text-ui"
            }
          >
            {review.approvalsActive ? (
              <>
                <strong>Approvals active:</strong> {review.approvers} people can approve. Turning Two Factor
                Authentication off, weakening it, or exempting someone waits for another admin.
              </>
            ) : (
              <>
                <strong>Approvals not active yet:</strong> only {review.approvers} person can approve, so weakening
                changes apply at once (after a fresh second step) and are recorded as &ldquo;applied without
                approval&rdquo;. Add a second platform operator{tenantId ? " or tenant super admin" : ""} to activate
                them.
              </>
            )}
          </p>

          <section>
            <h3 className="text-sm font-semibold text-ui">Applications</h3>
            <p className="mb-2 text-xs text-muted">
              {weakApps.length === 0
                ? "Every application requires a second step."
                : `${weakApps.length} of ${review.applications.length} don't require a second step.`}
            </p>
            {review.applications.length > 0 && (
              <div className="overflow-x-auto rounded-xl border border-ui">
                <Table>
                  <THead>
                    <tr>
                      <TH>Application</TH>
                      {!tenantId && <TH>Tenant</TH>}
                      <TH>Two Factor Authentication</TH>
                      <TH>Decided by</TH>
                    </tr>
                  </THead>
                  <TBody>
                    {review.applications.map((a) => (
                      <TR key={a.id}>
                        <TD>
                          <Link href={`/app/${a.id}/settings`} className="text-brand underline-offset-2 hover:underline">
                            {a.name}
                          </Link>
                        </TD>
                        {!tenantId && <TD className="text-sm text-soft">{a.tenantName}</TD>}
                        <TD>
                          <Badge tone={(MODE[a.mode] ?? MODE.off).tone}>{(MODE[a.mode] ?? MODE.off).label}</Badge>
                        </TD>
                        <TD className="text-xs text-muted">
                          {a.lockedAbove
                            ? "Locked by the platform or tenant"
                            : a.managed
                              ? "The application's own settings"
                              : a.switchedOff
                                ? "Switched off by the application"
                                : "Platform and tenant settings"}
                        </TD>
                      </TR>
                    ))}
                  </TBody>
                </Table>
              </div>
            )}
          </section>

          <section>
            <h3 className="text-sm font-semibold text-ui">Exempt users</h3>
            {review.exemptions.length === 0 ? (
              <p className="text-xs text-muted">Nobody is exempt.</p>
            ) : (
              <div className="mt-2 overflow-x-auto rounded-xl border border-ui">
                <Table>
                  <THead>
                    <tr>
                      <TH>User</TH>
                      <TH>Where</TH>
                      <TH>Until</TH>
                      <TH>Reason</TH>
                    </tr>
                  </THead>
                  <TBody>
                    {review.exemptions.map((e) => (
                      <TR key={`${e.userId}-${e.applicationId ?? "all"}`}>
                        <TD className="text-sm">{e.email}</TD>
                        <TD className="text-sm text-soft">{e.applicationName ?? "Every application"}</TD>
                        <TD className="text-sm text-soft">
                          <LocalTime value={e.until} />
                        </TD>
                        <TD className="text-xs text-muted">
                          {e.reason}
                          {e.exemptedBy && <span className="block text-faint">by {e.exemptedBy}</span>}
                        </TD>
                      </TR>
                    ))}
                  </TBody>
                </Table>
              </div>
            )}
          </section>

          <section>
            <h3 className="text-sm font-semibold text-ui">Admins without a second step</h3>
            {review.adminsWithoutTwoFactor.length === 0 ? (
              <p className="text-xs text-muted">Every admin has an authenticator or passkey.</p>
            ) : (
              <ul className="mt-2 divide-y divide-ui rounded-xl border border-ui">
                {review.adminsWithoutTwoFactor.map((a) => (
                  <li key={`${a.userId}-${a.role}`} className="flex justify-between gap-3 px-4 py-2 text-sm">
                    <span>{a.email}</span>
                    <span className="text-xs text-muted">{a.role}</span>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>
      )}
    </Card>
  );
}
