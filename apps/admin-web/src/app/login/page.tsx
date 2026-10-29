"use client";

import { FormEvent, type ReactNode, Suspense, useEffect, useState } from "react";
import { signWithPasskey } from "@/lib/webauthn";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { APP_NAME, withBasePath } from "@/lib/config";
import { BrandMark } from "@/components/BrandMark";

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
  | { kind: "mfa"; mfaToken: string; identity: string; methods: string[]; tryAnotherWay: boolean }
  | { kind: "enroll"; mfaToken: string; identity: string; factorId: string; secret: string; qrCodeDataUrl: string };

const REASONS: Record<string, string> = {
  expired: "Your session ended (inactivity or maximum session length). Sign in again to continue.",
  "signed-out": "You signed out.",
  "setup-done": "Your operator account is ready. Sign in under Platform operator.",
};

type SetupStatus = { setupRequired: boolean; emergencyAccess: boolean; adminMfaRequired: boolean };

/**
 * Whether first-run setup is still open (no platform operator yet), whether this server has the
 * emergency (break-glass) account turned on, and whether console sign-in asks for an authenticator app.
 */
function useSetupStatus(): SetupStatus {
  const [status, setStatus] = useState<SetupStatus>({
    setupRequired: false,
    emergencyAccess: false,
    adminMfaRequired: false,
  });
  useEffect(() => {
    let cancelled = false;
    fetch(withBasePath("/api/setup/status"), { cache: "no-store" })
      .then((r) => (r.ok ? r.json() : null))
      .then(
        (s) =>
          !cancelled &&
          setStatus({
            setupRequired: !!s?.setupRequired,
            emergencyAccess: !!s?.emergencyAccess,
            adminMfaRequired: !!s?.adminMfaRequired,
          }),
      )
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, []);
  return status;
}

function LoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const next = searchParams.get("next") ?? "/";
  const reason = REASONS[searchParams.get("reason") ?? ""] ?? null;
  const [step, setStep] = useState<MfaStep | null>(null);
  const [changeToken, setChangeToken] = useState<string | null>(null);
  const [passwordChanged, setPasswordChanged] = useState(false);
  const [recoveryCodes, setRecoveryCodes] = useState<string[] | null>(null);

  function finish() {
    router.replace(next.startsWith("/") && !next.startsWith("//") ? next : "/");
    router.refresh();
  }

  if (recoveryCodes) {
    return <RecoveryCodesNotice codes={recoveryCodes} onDone={finish} />;
  }
  if (changeToken) {
    return (
      <NewPasswordForm
        changeToken={changeToken}
        onRestart={() => setChangeToken(null)}
        onChanged={() => {
          setChangeToken(null);
          setPasswordChanged(true);
        }}
      />
    );
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
  return (
    <PasswordForm
      reason={passwordChanged ? "Your new password is saved. Sign in with it to continue." : reason}
      onStep={setStep}
      onNewPassword={setChangeToken}
      onSignedIn={finish}
    />
  );
}

/** A temporary password was accepted: the operator chooses their own before signing in. */
function NewPasswordForm({
  changeToken,
  onRestart,
  onChanged,
}: {
  changeToken: string;
  onRestart: () => void;
  onChanged: () => void;
}) {
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    if (password !== confirm) {
      setError("The two passwords do not match.");
      return;
    }
    setLoading(true);
    try {
      const res = await fetch(withBasePath("/api/auth/login/new-password"), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ changeToken, password }),
      });
      if (!res.ok) {
        const body = (await res.json().catch(() => ({}))) as { detail?: string };
        setError(body.detail ?? "Could not set the new password.");
        return;
      }
      onChanged();
    } catch {
      setError("Could not reach the login service.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <Card subtitle="Your password is temporary. Choose your own to continue.">
      <form onSubmit={onSubmit} className="space-y-4">
        <div>
          <label htmlFor="newPassword" className="mb-1 block text-xs font-medium text-soft">
            New password
          </label>
          <input
            id="newPassword"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="new-password"
            required
            minLength={8}
            className={INPUT_CLASS}
          />
        </div>
        <div>
          <label htmlFor="confirmPassword" className="mb-1 block text-xs font-medium text-soft">
            Confirm new password
          </label>
          <input
            id="confirmPassword"
            type="password"
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
            autoComplete="new-password"
            required
            className={INPUT_CLASS}
          />
        </div>
        <ErrorText error={error} />
        <button type="submit" disabled={loading} className={BUTTON_CLASS}>
          {loading ? "Saving…" : "Save password"}
        </button>
      </form>
      <div className="mt-6 text-center text-xs text-faint">
        <button type="button" className="underline-offset-2 hover:text-soft hover:underline" onClick={onRestart}>
          Back to sign-in
        </button>
      </div>
    </Card>
  );
}

function Card({ subtitle, children }: { subtitle: string; children: ReactNode }) {
  return (
    <div className="w-full max-w-md rounded-2xl border border-ui bg-ui-surface p-8 shadow-lg">
      <div className="mb-6 text-center">
        <BrandMark size={48} className="mx-auto mb-4" />
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
  onNewPassword,
  onSignedIn,
}: {
  reason: string | null;
  onStep: (step: MfaStep) => void;
  onNewPassword: (changeToken: string) => void;
  onSignedIn: () => void;
}) {
  // Just finished first-run setup: the new account is a platform operator. Search params are the same
  // on the server and in the browser, so this initial tab does not change during hydration.
  const justSetUp = useSearchParams().get("reason") === "setup-done";
  const [mode, setMode] = useState<LoginMode>(() =>
    justSetUp
      ? "platform"
      : typeof window === "undefined"
        ? "organization"
        : (readRemembered().mode ?? "organization"),
  );
  const { setupRequired, emergencyAccess, adminMfaRequired } = useSetupStatus();
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
      const res = await fetch(withBasePath("/api/auth/login"), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ tenantSlug: slug, emailOrUsername: identity, password }),
      });
      const body = (await res.json().catch(() => ({}))) as {
        detail?: string;
        step?: "mfa" | "enroll" | "new-password";
        changeToken?: string;
        mfaToken?: string;
        methods?: string[];
        tryAnotherWay?: boolean;
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
      if (body.step === "new-password" && body.changeToken) {
        setPassword("");
        onNewPassword(body.changeToken);
        return;
      }
      if (body.step === "mfa" && body.mfaToken) {
        onStep({
          kind: "mfa",
          mfaToken: body.mfaToken,
          identity,
          methods: body.methods ?? ["totp"],
          tryAnotherWay: body.tryAnotherWay !== false,
        });
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
      {setupRequired && (
        <p role="status" data-testid="setup-banner" className="mb-4 rounded-lg bg-amber-500/10 px-3 py-2 text-sm text-ui">
          SecureOne is not set up yet: no platform operator exists.{" "}
          <Link href="/setup" className="font-medium text-brand hover:underline">Set it up</Link> with the code from the server log.
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
              placeholder="Organization slug"
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
            placeholder={emergency ? "Username" : "Email address"}
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
        {!emergency && adminMfaRequired && <p>After your password you will enter a code from your authenticator app.</p>}
        {/* Offered only when this server has the break-glass account on (SECUREONE_BREAK_GLASS_ENABLED). */}
        {(emergency || emergencyAccess) && (
          <button
            type="button"
            className="underline-offset-2 hover:text-soft hover:underline"
            onClick={() => switchMode(emergency ? (readRemembered().mode ?? "organization") : "emergency")}
          >
            {emergency ? "Back to normal sign-in" : "Emergency access"}
          </button>
        )}
      </div>
    </Card>
  );
}

type MfaMethod = "totp" | "passkey" | "recovery";

/** Second-step options, in the order offered; {@code server} is the id the login API lists. */
const MFA_METHODS: { id: MfaMethod; server: string; label: string; hint: string; subtitle: string }[] = [
  {
    id: "totp",
    server: "totp",
    label: "Authenticator app",
    hint: "A 6-digit code from Google Authenticator, 1Password, Authy…",
    subtitle: "Enter the code from your authenticator app.",
  },
  {
    id: "passkey",
    server: "passkey",
    label: "Passkey or security key",
    hint: "Your fingerprint, face or device PIN, or a USB/NFC key.",
    subtitle: "Use your passkey or security key.",
  },
  {
    id: "recovery",
    server: "recovery_code",
    label: "Recovery code",
    hint: "One of the codes you saved when you set up your authenticator.",
    subtitle: "Enter one of your recovery codes.",
  },
];

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
  const [method, setMethod] = useState<MfaMethod>("totp");
  const [choosing, setChoosing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const enrolling = step.kind === "enroll";
  const useRecovery = method === "recovery";
  // Every option enabled for this operator; the authenticator app is always one of them. Choosing another
  // option needs the "2FA: Try another way" feature flag (Platform settings → Feature flags).
  const available =
    step.kind === "mfa" && step.tryAnotherWay
      ? MFA_METHODS.filter((m) => m.id === "totp" || step.methods.includes(m.server))
      : MFA_METHODS.filter((m) => m.id === "totp");
  const current = MFA_METHODS.find((m) => m.id === method) ?? MFA_METHODS[0];

  function pick(next: MfaMethod) {
    setMethod(next);
    setChoosing(false);
    setCode("");
    setError(null);
  }

  async function onPasskey() {
    setError(null);
    setLoading(true);
    try {
      const call = (payload: Record<string, unknown>) =>
        fetch(withBasePath("/api/auth/login/mfa"), {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ mfaToken: step.mfaToken, ...payload }),
        });
      const optionsRes = await call({ action: "passkey-options" });
      const options = (await optionsRes.json().catch(() => ({}))) as {
        detail?: string;
        requestId?: string;
        publicKey?: string;
      };
      if (!optionsRes.ok || !options.requestId || !options.publicKey) {
        setError(options.detail ?? "Passkeys are not available right now.");
        return;
      }
      const credential = await signWithPasskey(options.publicKey);
      const res = await call({ action: "passkey-verify", requestId: options.requestId, credential });
      const body = (await res.json().catch(() => ({}))) as { detail?: string };
      if (!res.ok) {
        setError(body.detail ?? "The passkey was not accepted.");
        return;
      }
      onSignedIn();
    } catch {
      setError("The passkey prompt was cancelled or failed. Try again or use a code.");
    } finally {
      setLoading(false);
    }
  }

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
      const res = await fetch(withBasePath("/api/auth/login/mfa"), {
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

  const footer = (
    <div className="flex justify-between gap-3 text-xs text-faint">
      {!enrolling && available.length > 1 ? (
        <button
          type="button"
          className="text-left hover:text-soft"
          data-testid="try-another-way"
          onClick={() => {
            setChoosing(true);
            setError(null);
          }}
        >
          {method === "recovery" ? "Try another way" : `Can't use your ${current.label.toLowerCase()}? Try another way`}
        </button>
      ) : (
        <span />
      )}
      <button type="button" className="shrink-0 hover:text-soft" onClick={onRestart}>
        Start over
      </button>
    </div>
  );

  return (
    <Card
      subtitle={
        enrolling
          ? "Set up an authenticator app. The admin console requires one."
          : choosing
            ? "Choose another way to verify it's you."
            : current.subtitle
      }
    >
      <p className="mb-4 text-center text-xs text-muted">
        Signing in as <span className="font-medium text-soft">{step.identity}</span>
      </p>
      {choosing ? (
        <div className="space-y-2" data-testid="mfa-method-list">
          {available.map((m) => (
            <button
              key={m.id}
              type="button"
              data-testid={`mfa-option-${m.id}`}
              onClick={() => pick(m.id)}
              className="w-full rounded-[var(--ui-radius)] border border-ui bg-ui-elevated px-3 py-2 text-left transition-colors hover:border-[var(--ui-primary)]"
            >
              <span className="block text-sm font-medium text-ui">
                {m.label}
                {m.id === method && <span className="font-normal text-faint"> · current</span>}
              </span>
              <span className="block text-xs text-faint">{m.hint}</span>
            </button>
          ))}
          <p className="pt-2 text-xs text-faint">
            None of these work? Ask an administrator to reset your MFA, then sign in again to set up a new
            authenticator.
          </p>
          <div className="flex justify-between text-xs text-faint">
            <button type="button" className="hover:text-soft" onClick={() => setChoosing(false)}>
              Back
            </button>
            <button type="button" className="hover:text-soft" onClick={onRestart}>
              Start over
            </button>
          </div>
        </div>
      ) : method === "passkey" ? (
        <div className="space-y-4">
          <button
            type="button"
            onClick={() => void onPasskey()}
            disabled={loading}
            data-testid="console-use-passkey"
            className={BUTTON_CLASS}
          >
            {loading ? "Waiting for your passkey…" : "Use your passkey or security key"}
          </button>
          <ErrorText error={error} />
          {footer}
        </div>
      ) : (
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
            placeholder={useRecovery ? "Recovery code" : "Verification code"}
            className={`${INPUT_CLASS} ${useRecovery ? "" : "text-center font-mono tracking-[0.4em]"}`}
          />
        </div>
        <ErrorText error={error} />
        <button type="submit" disabled={loading} className={BUTTON_CLASS}>
          {loading ? "Checking…" : enrolling ? "Confirm and sign in" : "Verify"}
        </button>
        {footer}
      </form>
      )}
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
