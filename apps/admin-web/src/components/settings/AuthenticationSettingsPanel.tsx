"use client";

import { cn } from "@/lib/cn";
import { useDialog } from "@/components/ui/Dialogs";
import { useCallback, useRef, useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { ComingSoonBadge } from "@/components/ui/Badge";
import { Card, CardHeader } from "@/components/ui/Card";
import { FieldRow, Input } from "@/components/ui/Field";
import { Toggle } from "@/components/ui/Toggle";
import { useToast } from "@/components/ui/Toast";
import {
  loadAuthSettingsAction,
  saveAuthMethodsAction,
  saveFeatureFlagsAction,
  savePasswordPolicyAction,
} from "@/lib/actions/auth-settings";
import { ensureApplicationPolicyScope } from "@/lib/api/ensure-application-policy";
import type { SettingsExposureKey } from "@/lib/api/application-policy";
import {
  fetchApplicationMfaTabState,
  setApplicationMfaTabEnabled,
  setApplicationTokenTabEnabled,
} from "@/lib/api/application-settings";
import type { ChangeRequest } from "@/lib/api/change-requests";
import { ApiError } from "@/lib/api/http";
import { isAuthMethodComingSoon, isFeatureFlagComingSoon } from "@/lib/coming-soon";
import type { AuthMethod, FeatureFlag, PasswordPolicy } from "@/lib/types";
import { FederationProvidersPanel } from "@/components/settings/FederationProvidersPanel";
import { MfaPolicySettingsPanel } from "@/components/settings/MfaPolicySettingsPanel";
import { useAdminContext } from "@/components/AdminContextProvider";

type Tab = "auth" | "mfa" | "password" | "flags";

const TAB_EXPOSURE: Record<Tab, SettingsExposureKey> = {
  auth: "auth-methods",
  mfa: "two-factor",
  password: "password-policy",
  flags: "feature-flags",
};

export function AuthenticationSettingsPanel({
  tab,
  applicationId,
  mfaTabEnabled = false,
  onMfaTabEnabledChange,
  platformExposesOAuthTokens = false,
  oauthTokensTabEnabled = false,
  onOAuthTokensTabEnabledChange,
  platformExposesFeatureFlags = true,
  platformExposesTwoFactor = false,
}: {
  tab: Tab;
  applicationId?: string;
  /** Application: the "Two Factor Authentication" switch (Feature flags) is on. */
  mfaTabEnabled?: boolean;
  onMfaTabEnabledChange?: (enabled: boolean) => void;
  /** Platform enabled Feature flags under For applications (always true on the platform). */
  platformExposesFeatureFlags?: boolean;
  /** Platform enabled Two Factor Authentication under For applications. */
  platformExposesTwoFactor?: boolean;
  /** Platform enabled OAuth tokens under For applications. */
  platformExposesOAuthTokens?: boolean;
  oauthTokensTabEnabled?: boolean;
  onOAuthTokensTabEnabledChange?: (enabled: boolean) => void;
}) {
  const { toast } = useToast();
  const adminContext = useAdminContext();
  const [authMethods, setAuthMethods] = useState<AuthMethod[]>([]);
  const [featureFlags, setFeatureFlags] = useState<FeatureFlag[]>([]);
  const [passwordPolicy, setPasswordPolicy] = useState<PasswordPolicy | null>(null);
  const [loaded, setLoaded] = useState(false);
  const saveTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const exposureKey = TAB_EXPOSURE[tab];
  // Application switches for its own settings tabs, shown on the Feature flags tab.
  const hasTabSwitches = Boolean(applicationId) && (platformExposesTwoFactor || platformExposesOAuthTokens);

  const reload = useCallback(async () => {
    setLoaded(false);
    try {
      const { authMethods: a, featureFlags: f, passwordPolicy: p, error } =
        await loadAuthSettingsAction(applicationId);
      setAuthMethods(a);
      setFeatureFlags(f);
      setPasswordPolicy(p);
      if (error) toast(error, "error");
    } catch (e) {
      toast(e instanceof Error ? e.message : "Failed to load settings", "error");
    } finally {
      setLoaded(true);
    }
  }, [applicationId, toast]);

  useDeferredEffect(() => {
    reload();
  }, [reload]);

  const scheduleSave = useCallback(
    (
      fn: () => Promise<{
        ok: boolean;
        error?: string;
        passwordPolicy?: PasswordPolicy;
        featureFlags?: FeatureFlag[];
      }>,
      exposure?: SettingsExposureKey,
    ) => {
      if (!loaded) return;
      if (saveTimer.current) clearTimeout(saveTimer.current);
      saveTimer.current = setTimeout(async () => {
        try {
          if (applicationId && exposure) {
            await ensureApplicationPolicyScope(applicationId, exposure);
          }
          const result = await fn();
          if (result.ok) {
            if (result.passwordPolicy) setPasswordPolicy(result.passwordPolicy);
            if (result.featureFlags) setFeatureFlags(result.featureFlags);
            toast("Settings saved", "success");
          } else {
            toast(result.error ?? "Save failed", "error");
          }
        } catch (e) {
          toast(e instanceof Error ? e.message : "Save failed", "error");
        }
      }, 700);
    },
    [applicationId, loaded, toast],
  );

  const primary = authMethods.filter((m) => m.category === "primary");
  const mfa = authMethods.filter((m) => m.category === "mfa");
  const federation = authMethods.filter((m) => m.category === "federation");
  const federationImplemented = federation.some(
    (m) => m.enabled && !isAuthMethodComingSoon(m),
  );

  return (
    <div className={!loaded ? "pointer-events-none opacity-60" : ""}>
      {!applicationId && (
        <p className="mb-4 text-sm text-muted">
          Platform-wide policy. Enable sections under <strong>For applications</strong> so each app can
          override them.
        </p>
      )}

      {tab === "auth" && (
        <div className="space-y-6">
          {loaded && primary.length > 0 && !primary.some((m) => m.enabled && !isAuthMethodComingSoon(m)) && (
            <p
              role="alert"
              className="rounded-lg border border-amber-500/40 bg-amber-500/10 px-4 py-3 text-sm text-amber-800 dark:text-amber-200"
            >
              No primary sign-in method is on, so nobody can sign in{applicationId ? " to this application" : ""}.
              Turn on at least one, for example <strong>Password</strong>.
            </p>
          )}
          <MethodSection
            title="Primary login"
            description="How users authenticate first"
            methods={primary}
            onToggle={(next) => {
              const merged = [...next, ...mfa, ...federation];
              setAuthMethods(merged);
              scheduleSave(() => saveAuthMethodsAction(merged, applicationId), "auth-methods");
            }}
          />
          <MethodSection
            title="Identity federation"
            description="Google, GitHub, external OIDC and SAML 2.0 sign-in. Configure the providers below when enabled."
            methods={federation}
            onToggle={(next) => {
              const merged = [...primary, ...mfa, ...next];
              setAuthMethods(merged);
              scheduleSave(() => saveAuthMethodsAction(merged, applicationId), "auth-methods");
            }}
          />
          {federationImplemented && (
            <FederationProvidersPanel
              applicationId={applicationId}
              tenantId={adminContext.tenantId}
            />
          )}
        </div>
      )}

      {tab === "mfa" && (
        <div className="space-y-6">
          {/* One place for second-step options, requirement, setup reminders and grace period. */}
          <MfaPolicySettingsPanel applicationId={applicationId} />
        </div>
      )}

      {tab === "password" && !applicationId && (
        <p className="mb-4 rounded-lg border border-ui bg-surface px-4 py-3 text-sm text-muted">
          Platform defaults for password complexity, expiry, and history. Applications inherit these
          values until an app admin saves an override.
        </p>
      )}

      {tab === "password" && passwordPolicy && (
        <PasswordPolicyEditor
          policy={passwordPolicy}
          onChange={(next) => {
            setPasswordPolicy(next);
            scheduleSave(() => savePasswordPolicyAction(next, applicationId), "password-policy");
          }}
        />
      )}

      {tab === "password" && !passwordPolicy && loaded && (
        <p className="rounded-lg border border-ui bg-surface px-4 py-6 text-sm text-muted">
          Could not load password policy. Ensure <strong>Password policy</strong> is enabled under Platform →
          For applications and auth-server is running.
        </p>
      )}

      {tab === "flags" && featureFlags.length === 0 && !hasTabSwitches && loaded && (
        <p className="rounded-lg border border-ui bg-surface px-4 py-6 text-sm text-muted">
          No feature flags loaded. Enable <strong>Feature flags</strong> under Platform → For applications
          and restart auth-server if the list stays empty.
        </p>
      )}

      {tab === "flags" && (featureFlags.length > 0 || hasTabSwitches) && (
        <FeatureFlagsPanel
          flags={platformExposesFeatureFlags ? featureFlags : []}
          applicationId={applicationId}
          platformExposesFeatureFlags={platformExposesFeatureFlags}
          platformExposesTwoFactor={platformExposesTwoFactor}
          twoFactorEnabled={mfaTabEnabled}
          onTwoFactorEnabledChange={onMfaTabEnabledChange}
          platformExposesOAuthTokens={platformExposesOAuthTokens}
          oauthTokensTabEnabled={oauthTokensTabEnabled}
          onOAuthTokensTabEnabledChange={onOAuthTokensTabEnabledChange}
          onChange={(next) => {
            setFeatureFlags(next);
            scheduleSave(() => saveFeatureFlagsAction(next, applicationId), "feature-flags");
          }}
        />
      )}
    </div>
  );
}

const FLAG_GROUPS: { title: string; categories: string[] }[] = [
  { title: "Identity & sign-in", categories: ["identity"] },
  { title: "Notifications", categories: ["notifications"] },
  { title: "Integration tab", categories: ["integration"] },
  { title: "Application console", categories: ["console"] },
];

function flagCategory(flag: FeatureFlag): string {
  return flag.category ?? "identity";
}

function FeatureFlagsPanel({
  flags,
  onChange,
  applicationId,
  platformExposesFeatureFlags = true,
  platformExposesTwoFactor,
  twoFactorEnabled,
  onTwoFactorEnabledChange,
  platformExposesOAuthTokens,
  oauthTokensTabEnabled,
  onOAuthTokensTabEnabledChange,
}: {
  flags: FeatureFlag[];
  onChange: (next: FeatureFlag[]) => void;
  applicationId?: string;
  platformExposesFeatureFlags?: boolean;
  platformExposesTwoFactor?: boolean;
  twoFactorEnabled?: boolean;
  onTwoFactorEnabledChange?: (enabled: boolean) => void;
  platformExposesOAuthTokens?: boolean;
  oauthTokensTabEnabled?: boolean;
  onOAuthTokensTabEnabledChange?: (enabled: boolean) => void;
}) {
  const { toast } = useToast();
  const [tabTogglePending, setTabTogglePending] = useState(false);
  const dialog = useDialog();
  // Turning Two Factor Authentication off waits for another admin; this is that request while it waits.
  const [offRequest, setOffRequest] = useState<ChangeRequest | null>(null);

  useDeferredEffect(() => {
    if (!applicationId || !platformExposesTwoFactor) return;
    let cancelled = false;
    fetchApplicationMfaTabState(applicationId)
      .then((s) => !cancelled && setOffRequest(s.pendingChange ?? null))
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [applicationId, platformExposesTwoFactor]);

  /** On: this application has two-factor with its own settings. Off (after approval): no second step. */
  async function toggleTwoFactor(enabled: boolean) {
    if (!applicationId) return;
    setTabTogglePending(true);
    try {
      const state = await setApplicationMfaTabEnabled(applicationId, enabled);
      setOffRequest(state.pendingChange ?? null);
      // Unchanged while turning off waits for approval: don't treat it as just turned on.
      if (state.tabEnabled !== Boolean(twoFactorEnabled)) onTwoFactorEnabledChange?.(state.tabEnabled);
      if (!enabled && state.pendingChange) {
        toast("Sent for approval: another admin decides on Access review", "success");
      } else if (state.appliedWithoutApproval) {
        toast("Turned off without approval (nobody else can approve yet); recorded in the audit log", "success");
      } else {
        toast("Settings saved", "success");
      }
    } catch (e) {
      const msg =
        e instanceof ApiError && e.status === 404
          ? "Two-factor API not found — restart auth-server (port 9000) and try again."
          : e instanceof Error
            ? e.message
            : "Update failed";
      toast(msg, "error");
    } finally {
      setTabTogglePending(false);
    }
  }

  async function toggleOAuthTokensTab(enabled: boolean) {
    if (!applicationId) return;
    if (
      !enabled &&
      !(await dialog.confirm({
        title: "Use the platform's token policy?",
        message: "This application's own token lifetimes are removed.",
        confirmLabel: "Use platform policy",
      }))
    ) {
      return;
    }
    setTabTogglePending(true);
    try {
      const state = await setApplicationTokenTabEnabled(applicationId, enabled);
      onOAuthTokensTabEnabledChange?.(state.tabEnabled);
      toast("Settings saved", "success");
    } catch (e) {
      toast(e instanceof Error ? e.message : "Update failed", "error");
    } finally {
      setTabTogglePending(false);
    }
  }

  const isApplication = Boolean(applicationId);
  // Applications see only main and advanced features the platform has on; console controls never.
  const sections: { title: string | null; note: string | null; flags: FeatureFlag[] }[] = isApplication
    ? [{ title: null, note: null, flags: flags.filter((f) => f.scope !== "platform" && f.platformEnabled !== false) }]
    : [
        {
          title: "Application features",
          note: "Main and advanced features. While one is on here, each application's admin may turn it off for their application; while it is off here, applications don't see it.",
          flags: flags.filter((f) => f.scope !== "platform"),
        },
        {
          title: "Console controls",
          note: "Parts of the admin console, decided here for every application. Applications don't see or change these.",
          flags: flags.filter((f) => f.scope === "platform"),
        },
      ];
  const hasVisible = sections.some((section) => section.flags.length > 0);

  return (
    <Card padded={false}>
      <CardHeader
        title="Feature flags"
        description={
          isApplication
            ? "Features the platform allows for this application; turn off what this application should not offer."
            : "Defaults for every application, and switches for parts of the admin console."
        }
      />
      {applicationId && (platformExposesTwoFactor || platformExposesOAuthTokens) && (
        <section className="border-t border-ui">
          <p className="px-5 pt-4 text-xs font-semibold uppercase tracking-wide text-faint">
            Settings tabs
          </p>
          <ul className="divide-y divide-ui">
            {platformExposesTwoFactor && (
              <li className="flex items-center justify-between gap-4 px-5 py-4" data-testid="flag-two-factor">
                <div>
                  <div className="flex items-center gap-2">
                    <p className="text-sm font-medium text-ui">Two Factor Authentication</p>
                    <code className="font-mono text-[10px] text-faint">two_factor_authentication</code>
                  </div>
                  <p className="text-xs text-muted">
                    On: this application has two-factor, with its own settings (Authentication → Two Factor
                    Authentication). Off: no second step at sign-in and no two-factor in this application&apos;s
                    console, unless the platform or tenant locks it as required. Turning it off needs another
                    admin&apos;s approval.
                  </p>
                  {offRequest && (
                    <p className="mt-1 text-xs text-amber-700 dark:text-amber-300" data-testid="two-factor-off-pending">
                      Turning off is waiting for approval ({offRequest.mine ? "your request" : `asked by ${offRequest.requestedBy}`}) on
                      Access review.
                    </p>
                  )}
                </div>
                <Toggle
                  checked={Boolean(twoFactorEnabled)}
                  disabled={tabTogglePending || Boolean(offRequest)}
                  onChange={toggleTwoFactor}
                  aria-label="Two Factor Authentication"
                />
              </li>
            )}
            {platformExposesOAuthTokens && (
            <li className="flex items-center justify-between gap-4 px-5 py-4">
              <div>
                <div className="flex items-center gap-2">
                  <p className="text-sm font-medium text-ui">Own OAuth token policy</p>
                  <code className="font-mono text-[10px] text-faint">token-policy</code>
                </div>
                <p className="text-xs text-muted">
                  On: this application has its own token lifetimes (OAuth tokens tab), starting from the
                  platform&apos;s and never longer than them. Off: it uses the platform&apos;s token policy, and its
                  own values are removed.
                </p>
              </div>
              <Toggle
                checked={oauthTokensTabEnabled}
                disabled={tabTogglePending}
                onChange={toggleOAuthTokensTab}
                aria-label="Own OAuth token policy"
              />
            </li>
            )}
          </ul>
        </section>
      )}
      {isApplication && platformExposesFeatureFlags && !hasVisible && (
        <p className="border-t border-ui px-5 py-6 text-sm text-muted">
          No features are open to this application. A platform operator turns them on in Platform settings →
          Feature flags → Application features.
        </p>
      )}
      {sections
        .filter((section) => section.flags.length > 0)
        .map((section) => {
          const grouped = FLAG_GROUPS.map((group) => ({
            ...group,
            items: section.flags.filter((f) => group.categories.includes(flagCategory(f))),
          })).filter((g) => g.items.length > 0);
          const uncategorized = section.flags.filter(
            (f) => !FLAG_GROUPS.some((g) => g.categories.includes(flagCategory(f))),
          );
          return (
            <div key={section.title ?? "flags"} className="border-t border-ui first:border-t-0">
              {section.title && (
                <div className="bg-ui-elevated/40 px-5 py-3">
                  <h3 className="text-sm font-semibold text-ui">{section.title}</h3>
                  {section.note && <p className="mt-0.5 text-xs text-muted">{section.note}</p>}
                </div>
              )}
              {grouped.map((group) => (
                <section key={group.title} className="border-t border-ui">
                  <p className="px-5 pt-4 text-xs font-semibold uppercase tracking-wide text-faint">{group.title}</p>
                  <FlagList flags={flags} keys={group.items.map((f) => f.key)} onChange={onChange} />
                </section>
              ))}
              {uncategorized.length > 0 && (
                <section className="border-t border-ui">
                  <p className="px-5 pt-4 text-xs font-semibold uppercase tracking-wide text-faint">Other</p>
                  <FlagList flags={flags} keys={uncategorized.map((f) => f.key)} onChange={onChange} />
                </section>
              )}
            </div>
          );
        })}
    </Card>
  );
}

function FlagList({
  flags,
  keys,
  onChange,
}: {
  flags: FeatureFlag[];
  keys: string[];
  onChange: (next: FeatureFlag[]) => void;
}) {
  return (
    <ul className="divide-y divide-ui">
      {flags
        .filter((f) => keys.includes(f.key))
        .map((f) => {
          const idx = flags.findIndex((x) => x.key === f.key);
          const comingSoon = isFeatureFlagComingSoon(f.key);
          return (
            <li key={f.key} className="flex items-center justify-between gap-4 px-5 py-4">
              <div>
                <div className="flex items-center gap-2">
                  <p className={`text-sm font-medium ${comingSoon ? "text-muted" : "text-ui"}`}>{f.name}</p>
                  <code className="font-mono text-[10px] text-faint">{f.key}</code>
                  {comingSoon && <ComingSoonBadge />}
                </div>
                <p className="text-xs text-muted">
                  {f.description}
                  {comingSoon && " — not available yet."}
                </p>
                {!comingSoon && f.platformEnabled === false && (
                  <p className="mt-1 text-xs text-faint">
                    Enable this capability under Platform → Settings → Feature flags before turning it on
                    for this application.
                  </p>
                )}
              </div>
              <Toggle
                checked={f.enabled}
                disabled={f.platformEnabled === false || (comingSoon && !f.enabled)}
                onChange={(enabled) => {
                  if (f.platformEnabled === false) return;
                  if (comingSoon && enabled) return;
                  const next = flags.map((x, i) => (i === idx ? { ...x, enabled } : x));
                  onChange(next);
                }}
                aria-label={f.name}
              />
            </li>
          );
        })}
    </ul>
  );
}

function MethodSection({
  title,
  description,
  methods,
  readOnly,
  onToggle,
}: {
  title: string;
  description: string;
  methods: AuthMethod[];
  readOnly?: boolean;
  onToggle: (next: AuthMethod[]) => void;
}) {
  // Available methods first; "coming soon" ones last and dimmed.
  const ordered = [...methods].sort(
    (a, b) => Number(isAuthMethodComingSoon(a)) - Number(isAuthMethodComingSoon(b)),
  );
  const enabledCount = methods.filter((m) => m.enabled && !isAuthMethodComingSoon(m)).length;
  return (
    <Card padded={false} className={readOnly ? "opacity-90" : undefined}>
      <CardHeader
        title={title}
        description={description}
        action={<span className="whitespace-nowrap text-xs text-muted">{enabledCount} on</span>}
      />
      <ul className="grid gap-3 p-4 sm:grid-cols-2">
        {ordered.map((m) => {
          const comingSoon = isAuthMethodComingSoon(m);
          // Off on the platform: off for every application.
          const platformOff = m.platformEnabled === false;
          return (
            <li
              key={m.id}
              className={cn(
                "flex items-start justify-between gap-3 rounded-xl border px-4 py-3 transition-colors",
                m.enabled && !comingSoon ? "border-ui-primary bg-brand-muted/30" : "border-ui",
                comingSoon && "opacity-60",
              )}
            >
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2">
                  <p className={`text-sm font-medium ${comingSoon ? "text-muted" : "text-ui"}`}>{m.name}</p>
                  {comingSoon && <ComingSoonBadge />}
                </div>
                <p className="mt-0.5 text-xs leading-snug text-muted">{m.description}</p>
                {platformOff && !comingSoon && (
                  <p className="mt-1 text-xs text-faint">Off on the platform, so off for every application.</p>
                )}
              </div>
              <Toggle
                checked={m.enabled}
                disabled={readOnly || platformOff || (comingSoon && !m.enabled)}
                onChange={(enabled) => {
                  const next = methods.map((x) => (x.id === m.id ? { ...x, enabled } : x));
                  onToggle(next);
                }}
                aria-label={m.name}
              />
            </li>
          );
        })}
      </ul>
    </Card>
  );
}

function PolicyToggle({
  label,
  on,
  disabled,
  onChange,
}: {
  label: string;
  on: boolean;
  disabled?: boolean;
  onChange: (v: boolean) => void;
}) {
  return (
    <div className="flex items-center justify-between rounded-lg border border-ui px-4 py-2.5">
      <span className="text-sm text-ui">{label}</span>
      <Toggle checked={on} disabled={disabled} onChange={onChange} aria-label={label} />
    </div>
  );
}

/** "Passwords need at least 12 characters, an upper-case letter, a number and a symbol." */
function describePolicy(p: PasswordPolicy): string {
  const parts = [`at least ${p.minLength} characters`];
  if (p.requireUppercase) parts.push("an upper-case letter");
  if (p.requireNumber) parts.push("a number");
  if (p.requireSymbol) parts.push("a symbol");
  const list = parts.length > 1 ? `${parts.slice(0, -1).join(", ")} and ${parts[parts.length - 1]}` : parts[0];
  const extras: string[] = [];
  if (p.breachCheck) extras.push("passwords found in public breaches are refused");
  if (p.historyCount > 0) extras.push(`the last ${p.historyCount} passwords cannot be reused`);
  if (p.expiryDays > 0) extras.push(`passwords expire after ${p.expiryDays} days`);
  extras.push(
    `${p.maxFailedAttempts ?? 5} wrong attempts in a row lock the account for ${p.lockoutDurationMinutes ?? 15} minutes`,
  );
  return `Passwords need ${list}. ${extras.map((e) => e.charAt(0).toUpperCase() + e.slice(1)).join(". ")}.`;
}

function PasswordPolicyEditor({
  policy,
  onChange,
}: {
  policy: PasswordPolicy;
  onChange: (next: PasswordPolicy) => void;
}) {
  const set = (patch: Partial<PasswordPolicy>) => onChange({ ...policy, ...patch });
  return (
    <Card padded={false}>
      <CardHeader
        title="Password policy"
        description="Enforced on sign-up, set-password, reset and admin set-password."
      />
      <div className="border-b border-ui bg-ui-elevated/40 px-5 py-3 text-sm text-ui">{describePolicy(policy)}</div>
      {policy.twoFactorMode === "off" && policy.minLength < 15 && (
        <p className="mx-5 mt-4 rounded-lg border border-amber-500/40 bg-amber-500/10 px-3 py-2 text-xs text-amber-800 dark:text-amber-200">
          Two Factor Authentication is off for this application, so the password is the only thing protecting each
          account. NIST SP 800-63B then asks for at least 15 characters.
        </p>
      )}
      {(policy.requireUppercase || policy.requireNumber || policy.requireSymbol) && (
        <p className="mx-5 mt-4 rounded-lg border border-ui bg-surface px-3 py-2 text-xs text-muted">
          Character-type rules (upper-case, number, symbol) are not recommended by NIST SP 800-63B: they lead to
          predictable passwords. Length and blocking leaked passwords protect better. Keep them only if your own policy
          requires them.
        </p>
      )}
      <div className="grid gap-6 p-5 lg:grid-cols-3">
        <PolicyGroup title="Complexity">
          <FieldRow label="Minimum length">
            <Input type="number" min={1} value={policy.minLength} onChange={(e) => set({ minLength: Number(e.target.value) })} />
          </FieldRow>
          <PolicyToggle label="Require upper-case letter" on={policy.requireUppercase} onChange={(v) => set({ requireUppercase: v })} />
          <PolicyToggle label="Require number" on={policy.requireNumber} onChange={(v) => set({ requireNumber: v })} />
          <PolicyToggle label="Require symbol" on={policy.requireSymbol} onChange={(v) => set({ requireSymbol: v })} />
          <PolicyToggle
            label="Block leaked passwords (Have I Been Pwned)"
            on={policy.breachCheck ?? false}
            onChange={(v) => set({ breachCheck: v })}
          />
        </PolicyGroup>
        <PolicyGroup title="Expiry & reuse">
          <FieldRow label="Password expiry (days)" hint="0 = never expires">
            <Input type="number" min={0} value={policy.expiryDays} onChange={(e) => set({ expiryDays: Number(e.target.value) })} />
          </FieldRow>
          <FieldRow label="Password history" hint="block reuse of the last N (0 = off)">
            <Input type="number" min={0} value={policy.historyCount} onChange={(e) => set({ historyCount: Number(e.target.value) })} />
          </FieldRow>
        </PolicyGroup>
        <PolicyGroup title="Lockout">
          <FieldRow label="Lock after failed sign-ins" hint="wrong passwords or codes in a row">
            <Input
              type="number"
              min={1}
              value={policy.maxFailedAttempts ?? 5}
              onChange={(e) => set({ maxFailedAttempts: Number(e.target.value) })}
            />
          </FieldRow>
          <FieldRow label="Lockout duration (minutes)" hint="admins can unlock sooner">
            <Input
              type="number"
              min={1}
              value={policy.lockoutDurationMinutes ?? 15}
              onChange={(e) => set({ lockoutDurationMinutes: Number(e.target.value) })}
            />
          </FieldRow>
        </PolicyGroup>
      </div>
    </Card>
  );
}

function PolicyGroup({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="space-y-3">
      <h3 className="text-xs font-semibold uppercase tracking-wide text-faint">{title}</h3>
      {children}
    </section>
  );
}
