"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/Button";
import { FieldRow, Input, Select } from "@/components/ui/Field";
import { CopyValue } from "@/components/ui/CopyValue";
import { APP_NAME, withBasePath } from "@/lib/config";
import { BrandMark } from "@/components/BrandMark";

type Action = "verify" | "installation" | "email" | "complete";
type Result = { ok: boolean; status: number; detail?: string; body: Record<string, unknown> };

async function post(action: Action, body: unknown): Promise<Result> {
  const res = await fetch(withBasePath(`/api/setup/${action}`), {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  const json = (await res.json().catch(() => ({}))) as Record<string, unknown>;
  const detail = (json.detail ?? json.title) as string | undefined;
  return { ok: res.ok, status: res.status, detail, body: json };
}

/** What the server reports after the code (POST /api/v1/setup/installation). */
type Installation = {
  publicUrl: string;
  https: boolean;
  encryptionKeySource: "environment" | "file" | "generated" | "none";
  encryptionKeyFile?: string;
  encryptionKey?: string;
  emailConfigured: boolean;
};

type Step = "code" | "installation" | "email" | "key" | "account";

const KEY_SOURCE: Record<Installation["encryptionKeySource"], string> = {
  environment: "From SECUREONE_ENCRYPTION_KEY",
  file: "Key file in the data volume",
  generated: "Created on this first start, in the data volume",
  none: "Kept in the database (development only)",
};

export function SetupForm({ codeFromEnvironment }: { codeFromEnvironment: boolean }) {
  const router = useRouter();
  const [step, setStep] = useState<Step>("code");
  const [code, setCode] = useState("");
  const [installation, setInstallation] = useState<Installation | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  // Email (optional)
  const [smtpHost, setSmtpHost] = useState("");
  const [smtpPort, setSmtpPort] = useState("587");
  const [smtpSecurity, setSmtpSecurity] = useState("starttls");
  const [smtpUser, setSmtpUser] = useState("");
  const [smtpPassword, setSmtpPassword] = useState("");
  const [testTo, setTestTo] = useState("");

  // Key backup
  const [keySaved, setKeySaved] = useState(false);

  // First operator
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");

  const steps: Step[] = ["code", "installation", "email", ...(installation?.encryptionKey ? (["key"] as Step[]) : []), "account"];
  const titles: Record<Step, string> = {
    code: "Prove you run this server",
    installation: "Check your installation",
    email: "Email (optional)",
    key: "Back up the encryption key",
    account: "Create the first platform operator",
  };
  const next = (from: Step) => steps[steps.indexOf(from) + 1];

  function fail(result: Result) {
    if (result.status === 410) {
      router.replace("/login");
      return;
    }
    setError(result.detail ?? "Something went wrong. Try again.");
  }

  async function run(work: () => Promise<void>) {
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      await work();
    } finally {
      setBusy(false);
    }
  }

  function submitCode(e: FormEvent) {
    e.preventDefault();
    void run(async () => {
      const result = await post("installation", { code });
      if (!result.ok) return fail(result);
      setInstallation(result.body as unknown as Installation);
      setStep("installation");
    });
  }

  function submitEmail(e: FormEvent, sendTest: boolean) {
    e.preventDefault();
    void run(async () => {
      const result = await post("email", {
        code,
        host: smtpHost,
        port: Number(smtpPort) || 587,
        security: smtpSecurity,
        username: smtpUser,
        password: smtpPassword,
        testTo: sendTest ? testTo : "",
      });
      if (!result.ok) return fail(result);
      setInstallation((current) => (current ? { ...current, emailConfigured: true } : current));
      if (sendTest) {
        setNotice(`Saved. A test email was sent to ${testTo}: check the inbox, then continue.`);
      } else {
        setStep(next("email"));
      }
    });
  }

  function submitAccount(e: FormEvent) {
    e.preventDefault();
    if (password !== confirm) {
      setError("The passwords do not match.");
      return;
    }
    void run(async () => {
      const result = await post("complete", { code, email, firstName, lastName, password });
      if (result.ok) router.replace("/login?reason=setup-done");
      else fail(result);
    });
  }

  function downloadKey() {
    if (!installation?.encryptionKey) return;
    const blob = new Blob([`${installation.encryptionKey}\n`], { type: "text/plain" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = "secureone-encryption.key";
    link.click();
    URL.revokeObjectURL(url);
  }

  const position = steps.indexOf(step) + 1;

  return (
    <div className="w-full max-w-lg rounded-2xl border border-ui bg-ui-surface p-8 shadow-lg">
      <div className="mb-6 text-center">
        <BrandMark size={48} className="mx-auto mb-4" />
        <h1 className="text-xl font-semibold text-ui">Set up {APP_NAME}</h1>
        <p className="mt-1 text-sm text-muted" data-testid="setup-step">
          {/* The number of steps is known once the server has said whether a key backup is needed. */}
          {installation ? `Step ${position} of ${steps.length}` : "Step 1"} · {titles[step]}
        </p>
        {installation && (
          <div className="mx-auto mt-4 flex max-w-xs gap-1.5" aria-hidden="true">
            {steps.map((s, i) => (
              <span key={s} className={`h-1.5 flex-1 rounded-full ${i < position ? "bg-indigo-500" : "bg-ui-elevated"}`} />
            ))}
          </div>
        )}
      </div>

      {error && (
        <p role="alert" data-testid="setup-error" className="mb-4 rounded-lg bg-red-500/10 px-3 py-2 text-sm text-red-600 dark:text-red-300">
          {error}
        </p>
      )}
      {notice && (
        <p role="status" className="mb-4 rounded-lg bg-emerald-500/10 px-3 py-2 text-sm text-emerald-700 dark:text-emerald-300">
          {notice}
        </p>
      )}

      {step === "code" && (
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
      )}

      {step === "installation" && installation && (
        <div className="space-y-4">
          <dl className="divide-y divide-ui rounded-xl border border-ui text-sm">
            <div className="flex justify-between gap-4 px-4 py-3">
              <dt className="text-muted">Public address</dt>
              <dd className="text-right font-mono text-ui">{installation.publicUrl || "not set"}</dd>
            </div>
            <div className="flex justify-between gap-4 px-4 py-3">
              <dt className="text-muted">Encryption key</dt>
              <dd className="text-right text-ui">{KEY_SOURCE[installation.encryptionKeySource]}</dd>
            </div>
            <div className="flex justify-between gap-4 px-4 py-3">
              <dt className="text-muted">Database and cache</dt>
              <dd className="text-right text-ui">Connected</dd>
            </div>
            <div className="flex justify-between gap-4 px-4 py-3">
              <dt className="text-muted">Email</dt>
              <dd className="text-right text-ui">{installation.emailConfigured ? "Set up" : "Not set up yet"}</dd>
            </div>
          </dl>
          {!installation.https && (
            <p className="rounded-lg bg-amber-500/10 px-3 py-2 text-sm text-amber-700 dark:text-amber-300">
              The public address is not https. That is fine for a trial; for real use, put SecureOne behind HTTPS and set
              SECUREONE_PUBLIC_BASE_URL and SECUREONE_ISSUER_URL to the https address before connecting applications:
              tokens and sign-in links carry it.
            </p>
          )}
          <Button className="w-full" onClick={() => setStep(next("installation"))}>Continue</Button>
        </div>
      )}

      {step === "email" && (
        <form onSubmit={(e) => submitEmail(e, false)} className="space-y-4">
          <p className="text-sm text-muted">
            SecureOne sends verification codes, password resets and invitations by email. You can also do this later under
            Settings › Notifications.
          </p>
          <div className="grid grid-cols-3 gap-3">
            <div className="col-span-2">
              <FieldRow label="SMTP server">
                <Input value={smtpHost} onChange={(e) => setSmtpHost(e.target.value)} placeholder="smtp.example.com" required />
              </FieldRow>
            </div>
            <FieldRow label="Port">
              <Input value={smtpPort} onChange={(e) => setSmtpPort(e.target.value)} inputMode="numeric" required />
            </FieldRow>
          </div>
          <FieldRow label="Security">
            <Select value={smtpSecurity} onChange={(e) => setSmtpSecurity(e.target.value)}>
              <option value="starttls">STARTTLS (usually port 587)</option>
              <option value="ssl">SSL/TLS (usually port 465)</option>
              <option value="none">None (mail server on this machine only)</option>
            </Select>
          </FieldRow>
          <div className="grid grid-cols-2 gap-3">
            <FieldRow label="Username">
              <Input value={smtpUser} onChange={(e) => setSmtpUser(e.target.value)} autoComplete="off" />
            </FieldRow>
            <FieldRow label="Password">
              <Input type="password" value={smtpPassword} onChange={(e) => setSmtpPassword(e.target.value)} autoComplete="new-password" />
            </FieldRow>
          </div>
          <FieldRow label="Send a test email to" hint="optional">
            <Input type="email" value={testTo} onChange={(e) => setTestTo(e.target.value)} placeholder="you@example.com" />
          </FieldRow>
          <div className="flex flex-wrap gap-2">
            <Button
              type="button"
              variant="secondary"
              disabled={busy || !smtpHost || !testTo}
              onClick={(e) => submitEmail(e as unknown as FormEvent, true)}
            >
              Save and send test
            </Button>
            <Button type="submit" disabled={busy}>{busy ? "Saving…" : "Save and continue"}</Button>
            <Button type="button" variant="ghost" onClick={() => setStep(next("email"))}>
              {installation?.emailConfigured ? "Continue" : "Skip for now"}
            </Button>
          </div>
        </form>
      )}

      {step === "key" && installation?.encryptionKey && (
        <div className="space-y-4">
          <p className="text-sm text-muted">
            This key encrypts the secrets SecureOne stores (email and SMS passwords, signing keys). It is kept in{" "}
            <span className="font-mono text-ui">{installation.encryptionKeyFile}</span>, outside the database.{" "}
            <b className="text-ui">Without it, a database backup cannot be restored.</b> Store a copy in your password
            manager or secret store, apart from your database backups. It is shown only during setup.
          </p>
          <div className="flex items-center gap-2 rounded-xl border border-ui bg-ui-elevated px-3 py-2">
            <code className="min-w-0 flex-1 break-all font-mono text-xs text-ui" data-testid="setup-key">
              {installation.encryptionKey}
            </code>
            <CopyValue value={installation.encryptionKey} />
          </div>
          <Button type="button" variant="secondary" onClick={downloadKey}>Download as a file</Button>
          <label className="flex items-start gap-2 text-sm text-ui">
            <input type="checkbox" className="mt-1" checked={keySaved} onChange={(e) => setKeySaved(e.target.checked)} />
            I have stored the encryption key somewhere safe, apart from the database backups.
          </label>
          <Button className="w-full" disabled={!keySaved} onClick={() => setStep(next("key"))}>Continue</Button>
        </div>
      )}

      {step === "account" && (
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
          <Button type="submit" className="w-full" disabled={busy}>{busy ? "Creating…" : "Create operator account and finish"}</Button>
        </form>
      )}
    </div>
  );
}
