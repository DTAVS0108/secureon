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
  listOperatorMfaFactors,
  type MfaFactor,
} from "@/lib/api/operator-mfa";
import { createPasskey, passkeysSupported } from "@/lib/webauthn";

const TYPE_LABELS: Record<string, string> = {
  totp: "Authenticator app",
  passkey: "Passkey",
  recovery: "Recovery codes",
};

function typeLabel(type: string): string {
  return TYPE_LABELS[type] ?? type;
}

export function OperatorSecurityPanel({
  platformSuperAdmin,
  variant = "page",
}: {
  platformSuperAdmin: boolean;
  variant?: "page" | "modal";
}) {
  const { toast } = useToast();
  const [factors, setFactors] = useState<MfaFactor[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [totpEnroll, setTotpEnroll] = useState<{
    factorId: string;
    qrCodeDataUrl: string;
    secret: string;
  } | null>(null);
  const [totpCode, setTotpCode] = useState("");
  const [recoveryCodes, setRecoveryCodes] = useState<string[] | null>(null);
  const [busy, setBusy] = useState<string | null>(null);

  const reload = useCallback(async () => {
    if (platformSuperAdmin) {
      setLoaded(true);
      return;
    }
    setLoaded(false);
    try {
      setFactors(await listOperatorMfaFactors());
    } catch (e) {
      toast(e instanceof Error ? e.message : "Could not load MFA factors", "error");
    } finally {
      setLoaded(true);
    }
  }, [platformSuperAdmin, toast]);

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
      toast("Factor removed", "success");
      await reload();
    } catch (e) {
      toast(e instanceof Error ? e.message : "Could not remove factor", "error");
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

  if (platformSuperAdmin) {
    return (
      <p className={`text-sm text-soft ${contentPadding}`}>
        Platform super-admin accounts use server-configured credentials. Sign in as a tenant operator
        to enroll TOTP or passkeys for your user account.
      </p>
    );
  }

  if (!loaded) {
    return <p className={`text-sm text-muted ${contentPadding}`}>Loading security settings…</p>;
  }

  const visibleFactors = factors.filter((f) => f.type !== "recovery");

  return (
    <div className={`space-y-5 ${contentPadding}`}>
      <div>
        <p className="text-xs text-muted">
          Protect sign-in to integrated applications with an authenticator app or passkey. These factors
          apply to your tenant user account, not the dev admin login.
        </p>
      </div>

      {visibleFactors.length > 0 && (
        <ul className="divide-y divide-ui rounded-xl border border-ui">
          {visibleFactors.map((factor) => (
            <li key={factor.id} className="flex items-center justify-between gap-3 px-4 py-3">
              <div>
                <p className="text-sm font-medium text-ui">{factor.label || typeLabel(factor.type)}</p>
                <div className="mt-1 flex flex-wrap gap-2">
                  <Badge tone="neutral">{typeLabel(factor.type)}</Badge>
                  <Badge tone={factor.verified ? "success" : "warning"}>
                    {factor.verified ? "verified" : "pending"}
                  </Badge>
                </div>
              </div>
              {factor.type !== "recovery" && (
                <Button
                  variant="ghost"
                  size="sm"
                  disabled={busy === factor.id}
                  onClick={() => void removeFactor(factor)}
                >
                  Remove
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}

      {totpEnroll && (
        <div className="rounded-xl border border-ui bg-ui-elevated/30 p-4 space-y-4">
          <p className="text-sm font-medium text-ui">Scan QR code with your authenticator app</p>
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src={totpEnroll.qrCodeDataUrl} alt="TOTP QR code" className="h-40 w-40 rounded-lg bg-white p-2" />
          <p className="text-xs text-muted break-all">Manual key: {totpEnroll.secret}</p>
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
        <div className="rounded-xl border border-amber-500/30 bg-amber-500/5 p-4 space-y-3">
          <p className="text-sm font-medium text-ui">Save these recovery codes</p>
          <p className="text-xs text-muted">Each code works once if you lose access to your authenticator.</p>
          <ul className="grid grid-cols-2 gap-2 font-mono text-xs text-ui">
            {recoveryCodes.map((code) => (
              <li key={code} className="rounded bg-ui-elevated px-2 py-1">
                {code}
              </li>
            ))}
          </ul>
          <Button variant="secondary" size="sm" onClick={() => setRecoveryCodes(null)}>
            I saved these
          </Button>
        </div>
      )}

      {!totpEnroll && (
        <div className="flex flex-wrap gap-2">
          <Button
            variant="secondary"
            disabled={busy === "totp-enroll" || visibleFactors.some((f) => f.type === "totp" && f.verified)}
            onClick={() => void startTotpEnroll()}
          >
            {busy === "totp-enroll" ? "Starting…" : "Add authenticator app"}
          </Button>
          <Button variant="secondary" disabled={busy === "passkey" || !passkeysSupported()} onClick={() => void addPasskey()}>
            {busy === "passkey" ? "Waiting for device…" : "Add passkey"}
          </Button>
        </div>
      )}
    </div>
  );
}
