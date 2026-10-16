"use client";

import { useCallback, useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { FieldRow, Input } from "@/components/ui/Field";
import { useToast } from "@/components/ui/Toast";
import {
  beginOperatorPasskeyRegistration,
  confirmOperatorTotp,
  deleteOperatorPasskey,
  deleteOperatorTotp,
  enrollOperatorTotp,
  finishOperatorPasskeyRegistration,
  getOperatorMfaStatus,
  listOperatorMfaFactors,
  regenerateOperatorRecoveryCodes,
  type MfaFactor,
  type OperatorMfaStatus,
} from "@/lib/api/operator-mfa";
import { createPasskey, passkeysSupported } from "@/lib/webauthn";
import { LocalTime } from "@/components/ui/LocalTime";

const TYPE_LABELS: Record<string, string> = {
  totp: "Authenticator app",
  passkey: "Passkey",
  recovery: "Recovery codes",
};

function typeLabel(type: string): string {
  return TYPE_LABELS[type] ?? type;
}

/** Few enough recovery codes left that the operator should make new ones. */
const LOW_RECOVERY_CODES = 3;

export function OperatorSecurityPanel({
  breakGlass,
  variant = "page",
}: {
  /** The configured emergency account has no user record and no MFA to manage. */
  breakGlass: boolean;
  variant?: "page" | "modal";
}) {
  const { toast } = useToast();
  const [factors, setFactors] = useState<MfaFactor[]>([]);
  const [status, setStatus] = useState<OperatorMfaStatus | null>(null);
  const [loaded, setLoaded] = useState(false);
  const [totpEnroll, setTotpEnroll] = useState<{
    factorId: string;
    qrCodeDataUrl: string;
    secret: string;
  } | null>(null);
  const [totpCode, setTotpCode] = useState("");
  const [recoveryCodes, setRecoveryCodes] = useState<string[] | null>(null);
  const [confirmRemove, setConfirmRemove] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);

  const reload = useCallback(async () => {
    if (breakGlass) {
      setLoaded(true);
      return;
    }
    setLoaded(false);
    try {
      const [list, st] = await Promise.all([listOperatorMfaFactors(), getOperatorMfaStatus()]);
      setFactors(list);
      setStatus(st);
    } catch (e) {
      toast(e instanceof Error ? e.message : "Could not load MFA factors", "error");
    } finally {
      setLoaded(true);
    }
  }, [breakGlass, toast]);

  useDeferredEffect(() => {
    void reload();
  }, [reload]);

  async function startTotpEnroll() {
    setBusy("totp-enroll");
    try {
      const res = await enrollOperatorTotp("Admin console");
      setTotpEnroll({
        factorId: res.factorId,
        qrCodeDataUrl: res.qrCodeDataUrl,
        secret: res.secret,
      });
      setTotpCode("");
      setRecoveryCodes(null);
    } catch (e) {
      toast(e instanceof Error ? e.message : "TOTP enrollment failed", "error");
    } finally {
      setBusy(null);
    }
  }

  async function confirmTotp() {
    if (!totpEnroll) return;
    setBusy("totp-confirm");
    try {
      const res = await confirmOperatorTotp(totpEnroll.factorId, totpCode.trim());
      setRecoveryCodes(res.recoveryCodes);
      setTotpEnroll(null);
      setTotpCode("");
      toast("Authenticator app enrolled", "success");
      await reload();
    } catch (e) {
      toast(e instanceof Error ? e.message : "Invalid code", "error");
    } finally {
      setBusy(null);
    }
  }

  async function removeFactor(factor: MfaFactor) {
    if (factor.type === "recovery") return;
    setBusy(factor.id);
    try {
      if (factor.type === "passkey") {
        await deleteOperatorPasskey(factor.id);
      } else if (factor.type === "totp") {
        await deleteOperatorTotp(factor.id);
      }
      toast(
        factor.type === "totp"
          ? "Authenticator removed. You will set up a new one at your next sign-in."
          : "Passkey removed",
        "success",
      );
      setConfirmRemove(null);
      await reload();
    } catch (e) {
      toast(e instanceof Error ? e.message : "Could not remove factor", "error");
    } finally {
      setBusy(null);
    }
  }

  async function regenerateCodes() {
    setBusy("recovery");
    try {
      const res = await regenerateOperatorRecoveryCodes();
      setRecoveryCodes(res.recoveryCodes);
      toast("New recovery codes created. The old ones no longer work.", "success");
      await reload();
    } catch (e) {
      toast(e instanceof Error ? e.message : "Could not create recovery codes", "error");
    } finally {
      setBusy(null);
    }
  }

  async function addPasskey() {
    if (!passkeysSupported()) {
      toast("Passkeys are not supported in this browser", "error");
      return;
    }
    setBusy("passkey");
    try {
      const options = await beginOperatorPasskeyRegistration("Admin console");
      const credential = await createPasskey(options.publicKey);
      await finishOperatorPasskeyRegistration(options.requestId, credential);
      toast("Passkey added", "success");
      await reload();
    } catch (e) {
      toast(e instanceof Error ? e.message : "Passkey registration failed", "error");
    } finally {
      setBusy(null);
    }
  }

  const contentPadding = variant === "modal" ? "" : "px-5 pb-5";

  if (breakGlass) {
    return (
      <p className={`text-sm text-soft ${contentPadding}`}>
        You are signed in with the emergency account, which has no MFA settings. Sign in with your
        named account (tenant slug <code>platform</code> for platform operators) to manage your
        authenticator and recovery codes.
      </p>
    );
  }

  if (!loaded) {
    return <p className={`text-sm text-muted ${contentPadding}`}>Loading security settings…</p>;
  }

  const visibleFactors = factors.filter((f) => f.type !== "recovery");
  const totpEnrolled = status?.totpEnrolled ?? visibleFactors.some((f) => f.type === "totp" && f.verified);
  const remaining = status?.recoveryCodesRemaining ?? 0;

  return (
    <div className={`space-y-5 ${contentPadding}`}>
      <div className="flex flex-wrap items-center gap-2">
        <Badge tone={totpEnrolled ? "success" : "danger"}>
          {totpEnrolled ? "Authenticator app set up" : "No authenticator app"}
        </Badge>
        {totpEnrolled && (
          <Badge tone={remaining <= LOW_RECOVERY_CODES ? "warning" : "neutral"}>
            {remaining} recovery {remaining === 1 ? "code" : "codes"} left
          </Badge>
        )}
        {status?.mfaRequiredForConsole && (
          <span className="text-xs text-muted">Required for admin console sign-in.</span>
        )}
      </div>

      {visibleFactors.length > 0 && (
        <ul className="divide-y divide-ui rounded-xl border border-ui">
          {visibleFactors.map((factor) => (
            <li key={factor.id} className="flex flex-wrap items-center justify-between gap-3 px-4 py-3">
              <div>
                <p className="text-sm font-medium text-ui">{factor.label || typeLabel(factor.type)}</p>
                <div className="mt-1 flex flex-wrap gap-2">
                  <Badge tone="neutral">{typeLabel(factor.type)}</Badge>
                  <Badge tone={factor.verified ? "success" : "warning"}>
                    {factor.verified ? "verified" : "pending"}
                  </Badge>
                  {factor.lastUsedAt && (
                    <span className="text-xs text-faint">
                      Last used <LocalTime value={factor.lastUsedAt} format="datetime" />
                    </span>
                  )}
                </div>
              </div>
              {confirmRemove === factor.id ? (
                <div className="flex flex-wrap items-center gap-2">
                  {factor.type === "totp" && status?.mfaRequiredForConsole && (
                    <span className="text-xs text-muted">You will set up a new one at next sign-in.</span>
                  )}
                  <Button
                    variant="ghost"
                    size="sm"
                    disabled={busy === factor.id}
                    onClick={() => void removeFactor(factor)}
                  >
                    {busy === factor.id ? "Removing…" : "Confirm remove"}
                  </Button>
                  <Button variant="ghost" size="sm" onClick={() => setConfirmRemove(null)}>
                    Cancel
                  </Button>
                </div>
              ) : (
                <Button variant="ghost" size="sm" onClick={() => setConfirmRemove(factor.id)}>
                  Remove
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}

      {totpEnroll && (
        <div className="space-y-4 rounded-xl border border-ui bg-ui-elevated/30 p-4">
          <p className="text-sm font-medium text-ui">Scan QR code with your authenticator app</p>
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src={totpEnroll.qrCodeDataUrl} alt="TOTP QR code" className="h-40 w-40 rounded-lg bg-white p-2" />
          <p className="break-all text-xs text-muted">Manual key: {totpEnroll.secret}</p>
          <FieldRow label="Verification code">
            <Input
              value={totpCode}
              onChange={(e) => setTotpCode(e.target.value)}
              inputMode="numeric"
              autoComplete="one-time-code"
              placeholder="6-digit code"
            />
          </FieldRow>
          <div className="flex gap-2">
            <Button onClick={() => void confirmTotp()} disabled={busy === "totp-confirm" || !totpCode.trim()}>
              {busy === "totp-confirm" ? "Confirming…" : "Confirm"}
            </Button>
            <Button variant="ghost" onClick={() => setTotpEnroll(null)}>
              Cancel
            </Button>
          </div>
        </div>
      )}

      {recoveryCodes && (
        <div className="space-y-3 rounded-xl border border-amber-500/30 bg-amber-500/5 p-4">
          <p className="text-sm font-medium text-ui">Save these recovery codes</p>
          <p className="text-xs text-muted">
            Each code signs you in once if you lose your authenticator. They will not be shown again.
          </p>
          <ul className="grid grid-cols-2 gap-2 font-mono text-xs text-ui">
            {recoveryCodes.map((code) => (
              <li key={code} className="rounded bg-ui-elevated px-2 py-1">
                {code}
              </li>
            ))}
          </ul>
          <div className="flex gap-2">
            <Button
              variant="secondary"
              size="sm"
              onClick={() => void navigator.clipboard?.writeText(recoveryCodes.join("\n"))}
            >
              Copy
            </Button>
            <Button variant="secondary" size="sm" onClick={() => setRecoveryCodes(null)}>
              I saved these
            </Button>
          </div>
        </div>
      )}

      {!totpEnroll && (
        <div className="flex flex-wrap gap-2">
          {!totpEnrolled && (
            <Button
              variant="secondary"
              disabled={busy === "totp-enroll"}
              onClick={() => void startTotpEnroll()}
            >
              {busy === "totp-enroll" ? "Starting…" : "Add authenticator app"}
            </Button>
          )}
          {totpEnrolled && !recoveryCodes && (
            <Button variant="secondary" disabled={busy === "recovery"} onClick={() => void regenerateCodes()}>
              {busy === "recovery" ? "Creating…" : "Generate new recovery codes"}
            </Button>
          )}
          <Button variant="secondary" disabled={busy === "passkey" || !passkeysSupported()} onClick={() => void addPasskey()}>
            {busy === "passkey" ? "Waiting for device…" : "Add passkey"}
          </Button>
        </div>
      )}
    </div>
  );
}
