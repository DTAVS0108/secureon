"use client";

import { FormEvent, type ReactNode, Suspense, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { APP_NAME } from "@/lib/config";

const INPUT_CLASS =
  "h-10 w-full rounded-[var(--ui-radius)] border border-ui bg-ui-elevated px-3 text-sm text-ui outline-none focus:border-[var(--ui-primary)] focus:ring-2 focus:ring-brand";
const BUTTON_CLASS =
  "h-10 w-full rounded-[var(--ui-radius)] bg-brand text-sm font-medium text-on-brand transition-opacity hover:opacity-90 disabled:opacity-60";

/** Reserved tenant that holds named platform operator accounts. */
const PLATFORM_TENANT = "platform";
const REMEMBER_KEY = "secureone.login";

/**
 * Who is signing in:
 * - organization: tenant operators (tenant slug + email)
 * - platform: named platform operators (email; the tenant is always "platform")
 * - emergency: the configured break-glass account (username, no tenant)
 */
type LoginMode = "organization" | "platform" | "emergency";

type Remembered = { mode?: LoginMode; tenantSlug?: string };

function readRemembered(): Remembered {
  try {
    return JSON.parse(window.localStorage.getItem(REMEMBER_KEY) ?? "{}") as Remembered;
  } catch {
    return {};
  }
}

function remember(value: Remembered) {
  try {
    window.localStorage.setItem(REMEMBER_KEY, JSON.stringify(value));
  } catch {
    // Storage unavailable (private window); remembering is only a convenience.
  }
}

type MfaStep =
  | { kind: "mfa"; mfaToken: string; identity: string }
  | { kind: "enroll"; mfaToken: string; identity: string; factorId: string; secret: string; qrCodeDataUrl: string };

const REASONS: Record<string, string> = {
  expired: "Your session ended (inactivity or maximum session length). Sign in again to continue.",
  "signed-out": "You signed out.",
};

function LoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const next = searchParams.get("next") ?? "/";
  const reason = REASONS[searchParams.get("reason") ?? ""] ?? null;
  const [step, setStep] = useState<MfaStep | null>(null);
  const [recoveryCodes, setRecoveryCodes] = useState<string[] | null>(null);

  function finish() {
    router.replace(next.startsWith("/") && !next.startsWith("//") ? next : "/");
    router.refresh();
  }

  if (recoveryCodes) {
    return <RecoveryCodesNotice codes={recoveryCodes} onDone={finish} />;
  }
  if (step) {
    return (
      <SecondFactorForm
        step={step}
        onRestart={() => setStep(null)}
        onSignedIn={(codes) => (codes?.length ? setRecoveryCodes(codes) : finish())}
      />
    );
  }
  return <PasswordForm reason={reason} onStep={setStep} onSignedIn={finish} />;
}

function Card({ subtitle, children }: { subtitle: string; children: ReactNode }) {
  return (
    <div className="w-full max-w-md rounded-2xl border border-ui bg-ui-surface p-8 shadow-lg">
      <div className="mb-6 text-center">
        <div className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-xl bg-brand text-on-brand text-lg font-bold">
          S1
        </div>
        <h1 className="text-xl font-semibold text-ui">{APP_NAME} Admin</h1>
        <p className="mt-1 text-sm text-muted">{subtitle}</p>
      </div>
      {children}
    </div>
  );
}

function ErrorText({ error }: { error: string | null }) {
  if (!error) return null;
  return (
    <p role="alert" data-testid="login-error" className="rounded-lg bg-red-500/10 px-3 py-2 text-sm text-red-600 dark:text-red-300">
      {error}
    </p>
  );
}

function ModeTabs({ mode, onChange }: { mode: LoginMode; onChange: (mode: LoginMode) => void }) {
  const tabs: { id: LoginMode; label: string }[] = [
    { id: "organization", label: "Organization" },
    { id: "platform", label: "Platform operator" },
  ];
  return (
    <div role="tablist" className="mb-5 grid grid-cols-2 rounded-[var(--ui-radius)] border border-ui bg-ui-elevated p-1">
      {tabs.map((tab) => (
        <button
          key={tab.id}
          type="button"
          role="tab"
          aria-selected={mode === tab.id}
          onClick={() => onChange(tab.id)}
          className={`rounded-[calc(var(--ui-radius)-2px)] px-3 py-1.5 text-sm transition-colors ${
            mode === tab.id ? "bg-ui-surface font-medium text-ui shadow-sm" : "text-muted hover:text-soft"
          }`}
        >
          {tab.label}
        </button>
      ))}
    </div>
  );
}

function PasswordForm({
  reason,
  onStep,
  onSignedIn,
}: {
  reason: string | null;
  onStep: (step: MfaStep) => void;
  onSignedIn: () => void;
}) {
  const [mode, setMode] = useState<LoginMode>(() =>
    typeof window === "undefined" ? "organization" : (readRemembered().mode ?? "organization"),
  );
  const [tenantSlug, setTenantSlug] = useState(() =>
    typeof window === "undefined" ? "" : (readRemembered().tenantSlug ?? ""),
  );
  const [emailOrUsername, setEmailOrUsername] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  function switchMode(nextMode: LoginMode) {
    setMode(nextMode);
    setError(null);
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    const identity = emailOrUsername.trim();
    const slug =
      mode === "platform" ? PLATFORM_TENANT : mode === "emergency" ? "" : tenantSlug.trim().toLowerCase();
    if (mode === "organization" && !slug) {
      setError("Enter your organization's tenant slug.");
      return;
    }
    if (mode === "organization" && slug === PLATFORM_TENANT) {
      setError("Platform operators sign in on the Platform operator tab.");
      return;
    }
    setLoading(true);
    try {
      const res = await fetch("/api/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ tenantSlug: slug, emailOrUsername: identity, password }),
      });
      const body = (await res.json().catch(() => ({}))) as {
        detail?: string;
        step?: "mfa" | "enroll";
        mfaToken?: string;
        factorId?: string;
        secret?: string;
        qrCodeDataUrl?: string;
      };
      if (!res.ok) {
        setError(body.detail ?? "Sign-in failed.");
        return;
      }
      if (mode !== "emergency") {
        remember({ mode, tenantSlug: mode === "organization" ? slug : undefined });
      }
      if (body.step === "mfa" && body.mfaToken) {
        onStep({ kind: "mfa", mfaToken: body.mfaToken, identity });
        return;
      }
      if (body.step === "enroll" && body.mfaToken && body.factorId) {
        onStep({
          kind: "enroll",
          mfaToken: body.mfaToken,
          identity,
          factorId: body.factorId,
          secret: body.secret ?? "",
          qrCodeDataUrl: body.qrCodeDataUrl ?? "",
        });
        return;
      }
      onSignedIn();
    } catch {
      setError("Could not reach the login service.");
    } finally {
      setLoading(false);
    }
  }

  const emergency = mode === "emergency";

  return (
    <Card
      subtitle={
        emergency
          ? "Emergency access with the configured platform account."
          : "Sign in to manage your platform and applications."
      }
    >
      {reason && (
        <p role="status" className="mb-4 rounded-lg bg-sky-500/10 px-3 py-2 text-sm text-sky-700 dark:text-sky-300">
          {reason}
        </p>
      )}
      {emergency ? (
        <p className="mb-4 rounded-lg bg-red-500/10 px-3 py-2 text-xs text-red-700 dark:text-red-300">
          Use only when no named operator can sign in. This sign-in is audited, platform admins are
          emailed, and the session lasts at most 1 hour. It may be disabled in production.
        </p>
      ) : (
        <ModeTabs mode={mode} onChange={switchMode} />
      )}

      <form onSubmit={onSubmit} className="space-y-4">
        {mode === "organization" && (
          <div>
            <label htmlFor="tenantSlug" className="mb-1 block text-xs font-medium text-soft">
              Tenant slug
            </label>
            <input
              id="tenantSlug"
              value={tenantSlug}
              onChange={(e) => setTenantSlug(e.target.value)}
              placeholder="your-organization"
              autoComplete="organization"
              required
              className={INPUT_CLASS}
            />
          </div>
        )}

        <div>
          <label htmlFor="emailOrUsername" className="mb-1 block text-xs font-medium text-soft">
            {emergency ? "Username" : "Email"}
          </label>
          <input
            id="emailOrUsername"
            type={emergency ? "text" : "email"}
            value={emailOrUsername}
            onChange={(e) => setEmailOrUsername(e.target.value)}
            placeholder={emergency ? "admin" : "you@company.com"}
            autoComplete="username"
            required
            className={INPUT_CLASS}
          />
        </div>

        <div>
          <label htmlFor="password" className="mb-1 block text-xs font-medium text-soft">
            Password
          </label>
          <div className="relative">
            <input
              id="password"
              type={showPassword ? "text" : "password"}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="current-password"
              required
              className={`${INPUT_CLASS} pr-16`}
            />
            <button
              type="button"
              onClick={() => setShowPassword((v) => !v)}
              className="absolute inset-y-0 right-2 px-2 text-xs text-faint hover:text-soft"
            >
              {showPassword ? "Hide" : "Show"}
            </button>
          </div>
        </div>

        <ErrorText error={error} />

        <button type="submit" disabled={loading} className={BUTTON_CLASS}>
          {loading ? "Signing in…" : emergency ? "Use emergency access" : "Continue"}
        </button>
      </form>

      <div className="mt-6 space-y-2 text-center text-xs text-faint">
        {!emergency && <p>After your password you will enter a code from your authenticator app.</p>}
        <button
          type="button"
          className="underline-offset-2 hover:text-soft hover:underline"
          onClick={() => switchMode(emergency ? (readRemembered().mode ?? "organization") : "emergency")}
        >
          {emergency ? "Back to normal sign-in" : "Emergency access"}
        </button>
      </div>
    </Card>
  );
}

function SecondFactorForm({
  step,
  onRestart,
  onSignedIn,
}: {
  step: MfaStep;
  onRestart: () => void;
  onSignedIn: (recoveryCodes?: string[]) => void;
}) {
  const [code, setCode] = useState("");
  const [useRecovery, setUseRecovery] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const enrolling = step.kind === "enroll";

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    const payload =
      step.kind === "enroll"
        ? { action: "enroll", mfaToken: step.mfaToken, factorId: step.factorId, code }
        : useRecovery
          ? { action: "verify", mfaToken: step.mfaToken, recoveryCode: code }
          : { action: "verify", mfaToken: step.mfaToken, code };
    try {
      const res = await fetch("/api/auth/login/mfa", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      });
      const body = (await res.json().catch(() => ({}))) as { detail?: string; recoveryCodes?: string[] };
      if (!res.ok) {
        setError(body.detail ?? "That code is not valid.");
        setCode("");
        // The challenge is gone after too many tries or a timeout: go back to the password step.
        if (/password again/i.test(body.detail ?? "")) {
          setTimeout(onRestart, 2500);
        }
        return;
      }
      onSignedIn(body.recoveryCodes);
    } catch {
      setError("Could not reach the login service.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <Card
      subtitle={
        enrolling
          ? "Set up an authenticator app. The admin console requires one."
          : "Enter the code from your authenticator app."
      }
    >
      <p className="mb-4 text-center text-xs text-muted">
        Signing in as <span className="font-medium text-soft">{step.identity}</span>
      </p>
      <form onSubmit={onSubmit} className="space-y-4">
        {step.kind === "enroll" && (
          <div className="space-y-3 text-sm text-soft">
            <p>1. Scan this code with an authenticator app (Google Authenticator, 1Password, Authy…).</p>
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img
              src={step.qrCodeDataUrl}
              alt="QR code for your authenticator app"
              className="mx-auto h-44 w-44 rounded-lg bg-white p-2"
            />
            <p className="text-xs text-faint">
              Can&apos;t scan? Enter this key:{" "}
              <code className="break-all font-mono text-soft" data-testid="totp-secret">
                {step.secret}
              </code>
            </p>
            <p>2. Enter the 6-digit code the app shows.</p>
          </div>
        )}
        <div>
          <label htmlFor="mfaCode" className="mb-1 block text-xs font-medium text-soft">
            {useRecovery ? "Recovery code" : "Authenticator code"}
          </label>
          <input
            id="mfaCode"
            value={code}
            onChange={(e) => setCode(e.target.value)}
            inputMode={useRecovery ? "text" : "numeric"}
            autoComplete="one-time-code"
            autoFocus
            required
            maxLength={useRecovery ? 20 : 6}
            placeholder={useRecovery ? "Recovery code" : "123456"}
            className={`${INPUT_CLASS} ${useRecovery ? "" : "text-center font-mono tracking-[0.4em]"}`}
          />
        </div>
        <ErrorText error={error} />
        <button type="submit" disabled={loading} className={BUTTON_CLASS}>
          {loading ? "Checking…" : enrolling ? "Confirm and sign in" : "Verify"}
        </button>
        <div className="flex justify-between text-xs text-faint">
          {!enrolling ? (
            <button
              type="button"
              className="hover:text-soft"
              onClick={() => {
                setUseRecovery((v) => !v);
                setCode("");
                setError(null);
              }}
            >
              {useRecovery ? "Use authenticator code" : "Lost your device? Use a recovery code"}
            </button>
          ) : (
            <span />
          )}
          <button type="button" className="hover:text-soft" onClick={onRestart}>
            Start over
          </button>
        </div>
        {!enrolling && useRecovery && (
          <p className="text-xs text-faint">
            No recovery codes either? Ask an administrator to reset your MFA, then sign in again to set
            up a new authenticator.
          </p>
        )}
      </form>
    </Card>
  );
}

function RecoveryCodesNotice({ codes, onDone }: { codes: string[]; onDone: () => void }) {
  const [copied, setCopied] = useState(false);
  return (
    <Card subtitle="Save your recovery codes">
      <div className="space-y-4 text-sm text-soft">
        <p>
          Each code signs you in once if you lose your authenticator. Store them somewhere safe; they
          will not be shown again. You can create new ones later under My profile → Security.
        </p>
        <ul
          className="grid grid-cols-2 gap-2 rounded-lg border border-ui bg-ui-elevated p-3 font-mono text-ui"
          data-testid="recovery-codes"
        >
          {codes.map((c) => (
            <li key={c}>{c}</li>
          ))}
        </ul>
        <button
          type="button"
          className="h-9 w-full rounded-[var(--ui-radius)] border border-ui text-sm text-soft hover:bg-ui-elevated"
          onClick={() => {
            void navigator.clipboard?.writeText(codes.join("\n")).then(() => setCopied(true));
          }}
        >
          {copied ? "Copied" : "Copy codes"}
        </button>
        <button type="button" onClick={onDone} className={BUTTON_CLASS}>
          I have saved these codes
        </button>
      </div>
    </Card>
  );
}

export default function LoginPage() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-ui px-4">
      <Suspense fallback={<div className="text-sm text-muted">Loading…</div>}>
        <LoginForm />
      </Suspense>
    </div>
  );
}
