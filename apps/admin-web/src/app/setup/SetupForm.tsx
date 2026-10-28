"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/Button";
import { FieldRow, Input } from "@/components/ui/Field";
import { APP_NAME, withBasePath } from "@/lib/config";

async function post(action: "verify" | "complete", body: unknown) {
  const res = await fetch(withBasePath(`/api/setup/${action}`), {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  const json = (await res.json().catch(() => ({}))) as { detail?: string; title?: string };
  return { ok: res.ok, status: res.status, detail: json.detail ?? json.title };
}

export function SetupForm({ codeFromEnvironment }: { codeFromEnvironment: boolean }) {
  const router = useRouter();
  const [step, setStep] = useState<"code" | "account">("code");
  const [code, setCode] = useState("");
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function fail(result: { status: number; detail?: string }) {
    if (result.status === 410) {
      router.replace("/login");
      return;
    }
    setError(result.detail ?? "Something went wrong. Try again.");
  }

  async function submitCode(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    const result = await post("verify", { code });
    setBusy(false);
    if (result.ok) setStep("account");
    else fail(result);
  }

  async function submitAccount(e: FormEvent) {
    e.preventDefault();
    if (password !== confirm) {
      setError("The passwords do not match.");
      return;
    }
    setBusy(true);
    setError(null);
    const result = await post("complete", { code, email, firstName, lastName, password });
    setBusy(false);
    if (result.ok) router.replace("/login?reason=setup-done");
    else fail(result);
  }

  return (
    <div className="w-full max-w-md rounded-2xl border border-ui bg-ui-surface p-8 shadow-lg">
      <div className="mb-6 text-center">
        <div className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-xl bg-brand text-lg font-bold text-on-brand">S1</div>
        <h1 className="text-xl font-semibold text-ui">Set up {APP_NAME}</h1>
        <p className="mt-1 text-sm text-muted">
          {step === "code" ? "Step 1 of 2 · Prove you run this server" : "Step 2 of 2 · Create the first platform operator"}
        </p>
      </div>

      {error && (
        <p role="alert" data-testid="setup-error" className="mb-4 rounded-lg bg-red-500/10 px-3 py-2 text-sm text-red-600 dark:text-red-300">
          {error}
        </p>
      )}

      {step === "code" ? (
        <form onSubmit={submitCode} className="space-y-4">
          <p className="text-sm text-muted">
            {codeFromEnvironment
              ? "Enter the setup code configured in SECUREONE_SETUP_CODE."
              : "The server printed a one-time setup code in its log when it started (look for “SecureOne is not set up yet”). It is valid for 24 hours."}
          </p>
          <FieldRow label="Setup code">
            <Input
              value={code}
              onChange={(e) => setCode(e.target.value)}
              placeholder="Setup code"
              autoComplete="off"
              spellCheck={false}
              required
              className="font-mono uppercase tracking-wider"
            />
          </FieldRow>
          <Button type="submit" className="w-full" disabled={busy}>{busy ? "Checking…" : "Continue"}</Button>
        </form>
      ) : (
        <form onSubmit={submitAccount} className="space-y-4">
          <p className="text-sm text-muted">
            This account administers the whole platform. After you sign in you will add an authenticator app (required for operators).
          </p>
          <div className="grid grid-cols-2 gap-3">
            <FieldRow label="First name">
              <Input value={firstName} onChange={(e) => setFirstName(e.target.value)} autoComplete="given-name" required />
            </FieldRow>
            <FieldRow label="Last name">
              <Input value={lastName} onChange={(e) => setLastName(e.target.value)} autoComplete="family-name" />
            </FieldRow>
          </div>
          <FieldRow label="Work email">
            <Input type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" required />
          </FieldRow>
          <FieldRow label="Password" hint="at least 12 characters, with upper case, a number and a symbol">
            <Input type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="new-password" required />
          </FieldRow>
          <FieldRow label="Confirm password">
            <Input type="password" value={confirm} onChange={(e) => setConfirm(e.target.value)} autoComplete="new-password" required />
          </FieldRow>
          <Button type="submit" className="w-full" disabled={busy}>{busy ? "Creating…" : "Create operator account"}</Button>
        </form>
      )}
    </div>
  );
}
