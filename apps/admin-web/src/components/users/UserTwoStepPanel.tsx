"use client";

import { useState } from "react";
import { useDialog } from "@/components/ui/Dialogs";
import { Badge, type BadgeTone } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Field";
import { LocalTime } from "@/components/ui/LocalTime";
import { useToast } from "@/components/ui/Toast";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import {
  endUserTwoStepExemption,
  exemptUserTwoStep,
  extendUserTwoStep,
  fetchUserTwoStep,
  resetUserTwoStep,
  TWO_STEP_STATUS_LABELS,
  type TwoStepMethodId,
  type TwoStepUserStatus,
  type TwoStepUserView,
} from "@/lib/api/mfa-policy";

const STATUS_TONE: Record<TwoStepUserStatus, BadgeTone> = {
  enabled: "success",
  off: "neutral",
  in_grace: "warning",
  overdue: "danger",
  not_set_up: "warning",
  exempt: "info",
};

const METHOD_LABEL: Record<TwoStepMethodId, string> = {
  passkey: "Passkey",
  security_key: "Security key",
  totp: "Authenticator app",
  sms: "Text message",
  email: "Email code",
};

/**
 * A user's two-step verification for admins: status, options (never secrets), backup codes, deadline, and
 * reset / extend / exempt. Reset and exempt ask the admin to verify it's them first (step-up dialog).
 * The deadline and exemptions are per application; the user's options and backup codes are one set for every
 * application of the tenant, so a reset applies everywhere.
 */
export function UserTwoStepPanel({ userId, applicationId }: { userId: string; applicationId?: string }) {
  const { toast } = useToast();
  const [view, setView] = useState<TwoStepUserView | null>(null);
  const dialog = useDialog();
  const [busy, setBusy] = useState(false);
  const [days, setDays] = useState(7);
  const [exemptUntil, setExemptUntil] = useState("");
  const [exemptReason, setExemptReason] = useState("");
  // Exempt in this application only (default when there is one) or in every application of the tenant.
  const [exemptEverywhere, setExemptEverywhere] = useState(!applicationId);
  const everywhereNote = "This applies to every application of this tenant: the user's options are shared.";

  useDeferredEffect(() => {
    let cancelled = false;
    fetchUserTwoStep(userId, applicationId)
      .then((v) => !cancelled && setView(v))
      .catch((e: unknown) => !cancelled && toast(e instanceof Error ? e.message : "Could not load two-step status", "error"));
    return () => {
      cancelled = true;
    };
  }, [userId, applicationId, toast]);

  async function run(work: () => Promise<TwoStepUserView>, done: string | ((v: TwoStepUserView) => string)) {
    setBusy(true);
    try {
      const next = await work();
      setView(next);
      toast(typeof done === "function" ? done(next) : done, "success");
    } catch (e) {
      toast(e instanceof Error ? e.message : "That didn't work", "error");
    } finally {
      setBusy(false);
    }
  }

  if (!view) return <p className="text-sm text-muted">Loading…</p>;

  const deadlineText = view.deadline ? <LocalTime value={view.deadline} /> : null;

  return (
    <div className="flex flex-col gap-6" data-testid="user-two-step">
      <section className="rounded-xl border border-ui p-4">
        <div className="flex flex-wrap items-center gap-2">
          <Badge tone={STATUS_TONE[view.status]} data-testid="user-two-step-status">
            {TWO_STEP_STATUS_LABELS[view.status]}
          </Badge>
          <span className="text-sm text-muted">
            {view.mode === "required" ? "Required" : view.mode === "optional" ? "Optional" : "Off"} for this
            application · {view.backupCodesRemaining} backup codes left · {view.rememberedDevices} remembered
            device{view.rememberedDevices === 1 ? "" : "s"}
          </span>
        </div>
        {view.status === "in_grace" && <p className="mt-2 text-sm">Must set it up by {deadlineText}.</p>}
        {view.status === "overdue" && (
          <p className="mt-2 text-sm">The deadline passed{deadlineText && <> ({deadlineText})</>}: setup is asked at the next sign-in.</p>
        )}
        {view.status === "exempt" && (
          <p className="mt-2 text-sm">
            Exempt {view.exemptScope === "all" ? "in every application" : "in this application"} until{" "}
            <LocalTime value={view.exemptUntil} /> by {view.exemptedBy}: “{view.exemptReason}”.
          </p>
        )}
      </section>

      <section>
        <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
          <div>
            <h3 className="text-sm font-semibold text-ui">Options</h3>
            <p className="text-xs text-muted" data-testid="two-step-shared-note">
              Shared by every application of this tenant; removing one removes it everywhere.
            </p>
          </div>
          {view.methods.length > 0 && (
            <Button
              variant="danger"
              size="sm"
              disabled={busy}
              data-testid="reset-all-two-step"
              onClick={async () => {
                if (
                  !(await dialog.confirm({
                    title: "Reset two-step verification?",
                    message: `Every option and backup code is removed and the user is signed out everywhere. ${everywhereNote}`,
                    confirmLabel: "Reset all",
                  }))
                )
                  return;
                void run(() => resetUserTwoStep(userId, null, applicationId), "Two-step verification reset");
              }}
            >
              Reset all
            </Button>
          )}
        </div>
        {view.methods.length > 0 ? (
          <ul className="divide-y divide-ui rounded-xl border border-ui">
            {view.methods.map((m) => (
              <li key={m.id} className="flex flex-wrap items-center justify-between gap-3 px-4 py-3">
                <div>
                  <p className="text-sm font-medium text-ui">
                    {METHOD_LABEL[m.type] ?? m.type}
                    {m.isDefault && <span className="ml-2 text-xs text-muted">asked first</span>}
                  </p>
                  <p className="text-xs text-muted">
                    {m.label}
                    {m.masked ? ` · ${m.masked}` : ""} · added <LocalTime value={m.createdAt ?? null} />
                    {m.lastUsedAt && (
                      <>
                        {" "}· last used <LocalTime value={m.lastUsedAt} />
                      </>
                    )}
                  </p>
                </div>
                <Button
                  variant="ghost"
                  size="sm"
                  disabled={busy}
                  onClick={async () => {
                    if (
                      !(await dialog.confirm({
                        title: `Remove ${METHOD_LABEL[m.type]}?`,
                        message: `The user is signed out everywhere. ${everywhereNote}`,
                        confirmLabel: "Remove",
                      }))
                    )
                      return;
                    void run(() => resetUserTwoStep(userId, m.type, applicationId), `${METHOD_LABEL[m.type]} removed`);
                  }}
                >
                  Remove
                </Button>
              </li>
            ))}
          </ul>
        ) : (
          <p className="rounded-xl border border-dashed border-ui px-6 py-6 text-center text-sm text-muted">
            No options set up.
          </p>
        )}
      </section>

      {view.mode === "required" && view.status !== "enabled" && (
        <section className="rounded-xl border border-ui p-4">
          <h3 className="text-sm font-semibold text-ui">More time to set it up</h3>
          <p className="text-xs text-muted">
            Moves the deadline {applicationId ? "in this application" : "in every application"} (up to{" "}
            {view.maxExtendDays} days).
          </p>
          <div className="mt-2 flex flex-wrap items-center gap-2">
            <Input
              type="number"
              min={1}
              max={view.maxExtendDays}
              value={days}
              onChange={(e) => setDays(Number(e.target.value))}
              className="w-24"
              data-testid="extend-days"
            />
            <Button
              size="sm"
              variant="secondary"
              disabled={busy}
              data-testid="extend-two-step"
              onClick={() => void run(() => extendUserTwoStep(userId, days, applicationId), "Deadline extended")}
            >
              Extend
            </Button>
          </div>
        </section>
      )}

      {view.canExempt && (
        <section className="rounded-xl border border-ui p-4">
          <h3 className="text-sm font-semibold text-ui">Exemption</h3>
          {view.status === "exempt" ? (
            <Button
              size="sm"
              variant="secondary"
              className="mt-2"
              disabled={busy}
              onClick={() =>
                void run(
                  () => endUserTwoStepExemption(userId, view.exemptScope === "all" ? null : applicationId),
                  "Exemption ended",
                )
              }
            >
              End exemption
            </Button>
          ) : (
            <>
              <p className="text-xs text-muted">
                Signs in without two-step verification until the date. Needs another admin&apos;s approval; audited;
                the user is emailed.
              </p>
              {view.pendingChange && (
                <p className="mt-2 rounded-lg bg-sky-500/10 px-3 py-2 text-sm" data-testid="exemption-pending">
                  <strong>Waiting for approval</strong> (
                  {view.pendingChange.mine ? "your request" : `asked by ${view.pendingChange.requestedBy}`}):{" "}
                  {view.pendingChange.details}
                </p>
              )}
              {applicationId && (
                <label className="mt-2 flex items-center gap-2 text-sm text-soft">
                  <input
                    type="checkbox"
                    checked={exemptEverywhere}
                    onChange={(e) => setExemptEverywhere(e.target.checked)}
                    data-testid="exempt-everywhere"
                  />
                  Every application of this tenant (otherwise this application only)
                </label>
              )}
              <div className="mt-2 grid gap-2 sm:grid-cols-[10rem_1fr_auto]">
                <Input type="date" value={exemptUntil} onChange={(e) => setExemptUntil(e.target.value)} data-testid="exempt-until" />
                <Input
                  placeholder="Reason, e.g. lost phone, travelling"
                  value={exemptReason}
                  maxLength={500}
                  onChange={(e) => setExemptReason(e.target.value)}
                  data-testid="exempt-reason"
                />
                <Button
                  size="sm"
                  variant="secondary"
                  disabled={busy || !exemptUntil || !exemptReason.trim()}
                  data-testid="exempt-two-step"
                  onClick={() =>
                    void run(
                      () =>
                        exemptUserTwoStep(
                          userId,
                          exemptUntil,
                          exemptReason.trim(),
                          exemptEverywhere ? null : applicationId,
                        ),
                      (v) =>
                        v.pendingChange
                          ? "Sent for approval: another admin decides on Access review"
                          : "User exempted",
                    )
                  }
                >
                  Exempt
                </Button>
              </div>
            </>
          )}
        </section>
      )}
    </div>
  );
}
