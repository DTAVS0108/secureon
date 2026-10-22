"use client";

import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { Button } from "@/components/ui/Button";
import { browserApiFetch } from "@/lib/api/browser-client";
import { signWithPasskey } from "@/lib/webauthn";

type StepUpStatus = { verifiedRecently: boolean; passkey: boolean; totp: boolean };

/**
 * "Verify it's you" for sensitive console actions. When the server answers 403 with `freshStepRequired`, the
 * API client asks this dialog (window.__secureoneStepUp); after a code, recovery code or passkey it retries the
 * call once.
 */
export function StepUpProvider() {
  const [open, setOpen] = useState(false);
  const [status, setStatus] = useState<StepUpStatus | null>(null);
  const [code, setCode] = useState("");
  const [useRecovery, setUseRecovery] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const resolver = useRef<((ok: boolean) => void) | null>(null);

  useEffect(() => {
    const w = window as unknown as { __secureoneStepUp?: () => Promise<boolean> };
    w.__secureoneStepUp = () =>
      new Promise<boolean>((resolve) => {
        resolver.current = resolve;
        setCode("");
        setError(null);
        setUseRecovery(false);
        setStatus(null);
        setOpen(true);
        browserApiFetch<StepUpStatus>("/api/admin/v1/me/step-up")
          .then(setStatus)
          .catch(() => setStatus({ verifiedRecently: false, passkey: false, totp: true }));
      });
    return () => {
      delete w.__secureoneStepUp;
    };
  }, []);

  function close(ok: boolean) {
    setOpen(false);
    resolver.current?.(ok);
    resolver.current = null;
  }

  async function verifyCode() {
    setBusy(true);
    setError(null);
    try {
      await browserApiFetch("/api/admin/v1/me/step-up", {
        method: "POST",
        body: JSON.stringify(useRecovery ? { recoveryCode: code.trim() } : { code: code.trim() }),
      });
      close(true);
    } catch (e) {
      setError(e instanceof Error ? e.message : "That code is not valid.");
    } finally {
      setBusy(false);
    }
  }

  async function verifyPasskey() {
    setBusy(true);
    setError(null);
    try {
      const options = await browserApiFetch<{ requestId: string; publicKey: string }>(
        "/api/admin/v1/me/step-up/passkey/options",
        { method: "POST", body: "{}" },
      );
      const credential = await signWithPasskey(options.publicKey);
      await browserApiFetch("/api/admin/v1/me/step-up/passkey/verify", {
        method: "POST",
        body: JSON.stringify({ requestId: options.requestId, credential }),
      });
      close(true);
    } catch (e) {
      setError(e instanceof Error ? e.message : "The passkey was not accepted.");
    } finally {
      setBusy(false);
    }
  }

  if (!open) return null;
  return createPortal(
    <div className="fixed inset-0 z-[110] flex items-center justify-center p-4">
      <div className="fixed inset-0 bg-black/40 backdrop-blur-sm" onClick={() => !busy && close(false)} />
      <div
        role="dialog"
        aria-modal="true"
        data-testid="step-up-dialog"
        className="relative z-10 w-full max-w-md rounded-2xl border border-black/10 bg-[var(--background)] p-6 shadow-2xl dark:border-white/10"
      >
        <h2 className="text-base font-semibold">Verify it&apos;s you</h2>
        <p className="mt-1 text-sm text-soft">This action needs a fresh check with your second step.</p>
        {status?.passkey && (
          <Button className="mt-4 w-full" variant="secondary" onClick={() => void verifyPasskey()} disabled={busy}>
            Use your passkey or security key
          </Button>
        )}
        <label className="mt-4 block text-sm">
          <span className="text-soft">{useRecovery ? "Recovery code" : "Code from your authenticator app"}</span>
          <input
            autoFocus
            data-testid="step-up-code"
            value={code}
            onChange={(e) => setCode(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && code.trim() && void verifyCode()}
            inputMode={useRecovery ? "text" : "numeric"}
            autoComplete="one-time-code"
            className="mt-1 block w-full rounded-md border border-ui bg-ui-elevated px-3 py-2 text-sm text-ui outline-none focus:border-ui-primary"
          />
        </label>
        <button type="button" className="mt-2 text-xs text-brand hover:underline" onClick={() => setUseRecovery((v) => !v)}>
          {useRecovery ? "Use an authenticator code" : "Use a recovery code"}
        </button>
        {error && (
          <p role="alert" className="mt-3 rounded-lg bg-red-500/10 px-3 py-2 text-sm text-red-600 dark:text-red-300">
            {error}
          </p>
        )}
        <div className="mt-6 flex justify-end gap-2">
          <Button variant="secondary" onClick={() => close(false)} disabled={busy}>
            Cancel
          </Button>
          <Button onClick={() => void verifyCode()} disabled={busy || !code.trim()} data-testid="step-up-verify">
            {busy ? "Checking…" : "Verify"}
          </Button>
        </div>
      </div>
    </div>,
    document.body,
  );
}
