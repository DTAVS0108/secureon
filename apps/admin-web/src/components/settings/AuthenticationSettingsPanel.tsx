"use client";

import { useCallback, useRef, useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { Badge } from "@/components/ui/Badge";
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
  setApplicationMfaTabEnabled,
  setApplicationTokenTabEnabled,
} from "@/lib/api/application-settings";
import { ApiError } from "@/lib/api/http";
import { IS_PROD } from "@/lib/config";
import type { AuthMethod, FeatureFlag, PasswordPolicy } from "@/lib/types";
import { FederationProvidersPanel } from "@/components/settings/FederationProvidersPanel";
import { MfaPolicySettingsPanel } from "@/components/settings/MfaPolicySettingsPanel";
import { useAdminContext } from "@/components/AdminContextProvider";

type Tab = "auth" | "mfa" | "password" | "flags";

const TAB_EXPOSURE: Record<Tab, SettingsExposureKey> = {
  auth: "auth-methods",
  mfa: "auth-methods",
  password: "password-policy",
  flags: "feature-flags",
};

export function AuthenticationSettingsPanel({
  tab,
  applicationId,
  platformExposesAuthMethods = false,
  mfaTabEnabled = false,
  onMfaTabEnabledChange,
  platformExposesOAuthTokens = false,
  oauthTokensTabEnabled = false,
  onOAuthTokensTabEnabledChange,
}: {
  tab: Tab;
  applicationId?: string;
  /** Platform enabled Authentication & MFA under For applications. */
  platformExposesAuthMethods?: boolean;
  mfaTabEnabled?: boolean;
  onMfaTabEnabledChange?: (enabled: boolean) => void;
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
    (m) => m.enabled && (m as AuthMethod & { implemented?: boolean }).implemented !== false,
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
          {applicationId && platformExposesAuthMethods && (
            <Card padded={false}>
              <CardHeader
                title="Multi-factor authentication"
                description="When enabled, the MFA sub-tab appears so you can configure multi-factor methods for this application."
              />
              <MfaTabToggle
                applicationId={applicationId}
                mfaTabEnabled={mfaTabEnabled}
                onMfaTabEnabledChange={onMfaTabEnabledChange}
              />
            </Card>
          )}
          <MethodSection
            title="Primary login"
            description="How users authenticate first"
            methods={primary}
            onToggle={(next) => {
              const merged = [...next, ...mfa, ...federation];
              setAuthMethods(merged);
              scheduleSave(() => saveAuthMethodsAction(merged, applicationId));
            }}
          />
          <MethodSection
            title="Identity federation"
            description="Google, GitHub, and external OIDC sign-in. Configure OAuth credentials below when enabled."
            methods={federation}
            onToggle={(next) => {
              const merged = [...primary, ...mfa, ...next];
              setAuthMethods(merged);
              scheduleSave(() => saveAuthMethodsAction(merged, applicationId));
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
          <MethodSection
            title="Multi-factor methods"
            description="TOTP, recovery codes, and SMS/email OTP at login when policy requires MFA."
            methods={mfa}
            onToggle={(next) => {
              const merged = [...primary, ...next, ...federation];
              setAuthMethods(merged);
              scheduleSave(() => saveAuthMethodsAction(merged, applicationId));
            }}
          />
          <MfaPolicySettingsPanel applicationId={applicationId} />
        </div>
      )}

      {tab === "password" && applicationId && passwordPolicy && (
        <p className="mb-4 rounded-lg border border-ui bg-surface px-4 py-3 text-sm text-muted">
          {passwordPolicy.inheritsPlatformDefaults !== false ? (
            <>
              This application inherits the <strong>platform password policy</strong>. Saving any field
              below creates an application-only override; platform changes will no longer apply until you
              reset this section.
            </>
          ) : (
            <>
              This application uses its <strong>own password policy override</strong>. These rules apply
              when users set or reset passwords for this application and can be exposed on the public API.
            </>
          )}
        </p>
      )}

      {tab === "password" && !applicationId && (
        <p className="mb-4 rounded-lg border border-ui bg-surface px-4 py-3 text-sm text-muted">
          Platform defaults for password complexity, expiry, and history. Applications inherit these
          values until an app admin saves an override.
        </p>
      )}

      {tab === "password" && passwordPolicy && (
        <Card padded={false}>
          <CardHeader
            title="Password policy"
            description="Enforced on signup, set-password, reset, and admin set-password flows"
          />
          <div className="grid grid-cols-1 gap-5 p-5 md:grid-cols-2">
            <FieldRow label="Minimum length">
              <Input
                type="number"
                value={passwordPolicy.minLength}
                onChange={(e) => {
                  const next = { ...passwordPolicy, minLength: Number(e.target.value) };
                  setPasswordPolicy(next);
                  scheduleSave(() => savePasswordPolicyAction(next, applicationId), "password-policy");
                }}
              />
            </FieldRow>
            <FieldRow label="Password expiry (days)" hint="0 = never expires">
              <Input
                type="number"
                value={passwordPolicy.expiryDays}
                onChange={(e) => {
                  const next = { ...passwordPolicy, expiryDays: Number(e.target.value) };
                  setPasswordPolicy(next);
                  scheduleSave(() => savePasswordPolicyAction(next, applicationId), "password-policy");
                }}
              />
            </FieldRow>
            <FieldRow label="Password history" hint="Block reuse of the last N passwords (0 = off)">
              <Input
                type="number"
                value={passwordPolicy.historyCount}
                onChange={(e) => {
                  const next = { ...passwordPolicy, historyCount: Number(e.target.value) };
                  setPasswordPolicy(next);
                  scheduleSave(() => savePasswordPolicyAction(next, applicationId), "password-policy");
                }}
              />
            </FieldRow>
            <FieldRow label="Hash algorithm" hint="bcrypt only (stored on each credential)">
              <Input value={passwordPolicy.hashAlgorithm} disabled />
            </FieldRow>
            <div className="md:col-span-2 space-y-2">
              <PolicyToggle
                label="Require uppercase"
                on={passwordPolicy.requireUppercase}
                onChange={(v) => {
                  const next = { ...passwordPolicy, requireUppercase: v };
                  setPasswordPolicy(next);
                  scheduleSave(() => savePasswordPolicyAction(next, applicationId), "password-policy");
                }}
              />
              <PolicyToggle
                label="Require number"
                on={passwordPolicy.requireNumber}
                onChange={(v) => {
                  const next = { ...passwordPolicy, requireNumber: v };
                  setPasswordPolicy(next);
                  scheduleSave(() => savePasswordPolicyAction(next, applicationId), "password-policy");
                }}
              />
              <PolicyToggle
                label="Require symbol"
                on={passwordPolicy.requireSymbol}
                onChange={(v) => {
                  const next = { ...passwordPolicy, requireSymbol: v };
                  setPasswordPolicy(next);
                  scheduleSave(() => savePasswordPolicyAction(next, applicationId), "password-policy");
                }}
              />
              <PolicyToggle
                label="Block leaked passwords (Have I Been Pwned)"
                on={passwordPolicy.breachCheck ?? false}
                onChange={(v) => {
                  const next = { ...passwordPolicy, breachCheck: v };
                  setPasswordPolicy(next);
                  scheduleSave(() => savePasswordPolicyAction(next, applicationId), "password-policy");
                }}
              />
            </div>
          </div>
        </Card>
      )}

      {tab === "password" && !passwordPolicy && loaded && (
        <p className="rounded-lg border border-ui bg-surface px-4 py-6 text-sm text-muted">
          Could not load password policy. Ensure <strong>Password policy</strong> is enabled under Platform →
          For applications and auth-server is running.
        </p>
      )}

      {tab === "flags" && featureFlags.length === 0 && loaded && (
        <p className="rounded-lg border border-ui bg-surface px-4 py-6 text-sm text-muted">
          No feature flags loaded. Enable <strong>Feature flags</strong> under Platform → For applications
          and restart auth-server if the list stays empty.
        </p>
      )}

      {tab === "flags" && featureFlags.length > 0 && (
        <FeatureFlagsPanel
          flags={featureFlags}
          applicationId={applicationId}
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

function MfaTabToggle({
  applicationId,
  mfaTabEnabled,
  onMfaTabEnabledChange,
}: {
  applicationId: string;
  mfaTabEnabled: boolean;
  onMfaTabEnabledChange?: (enabled: boolean) => void;
}) {
  const { toast } = useToast();
  const [pending, setPending] = useState(false);

  async function toggle(enabled: boolean) {
    setPending(true);
    try {
      const state = await setApplicationMfaTabEnabled(applicationId, enabled);
      onMfaTabEnabledChange?.(state.tabEnabled);
      toast("Settings saved", "success");
    } catch (e) {
      const msg =
        e instanceof ApiError && e.status === 404
          ? "MFA API not found — restart auth-server (port 9000) and try again."
          : e instanceof Error
            ? e.message
            : "Update failed";
      toast(msg, "error");
    } finally {
      setPending(false);
    }
  }

  return (
    <div className="flex items-center justify-between gap-4 px-5 py-4">
      <div>
        <p className="text-sm font-medium text-ui">Enable MFA</p>
        <p className="text-xs text-muted">
          Show the <strong>MFA</strong> sub-tab under Authentication to configure TOTP, passkeys, and other factors.
        </p>
      </div>
      <Toggle
        checked={mfaTabEnabled}
        disabled={pending}
        onChange={toggle}
        aria-label="Enable MFA"
      />
    </div>
  );
}

const FLAG_GROUPS: { title: string; categories: string[] }[] = [
  { title: "Identity & sign-in", categories: ["identity"] },
  { title: "Notifications", categories: ["notifications"] },
];

function flagCategory(flag: FeatureFlag): string {
  return flag.category ?? "identity";
}

function FeatureFlagsPanel({
  flags,
  onChange,
  applicationId,
  platformExposesOAuthTokens,
  oauthTokensTabEnabled,
  onOAuthTokensTabEnabledChange,
}: {
  flags: FeatureFlag[];
  onChange: (next: FeatureFlag[]) => void;
  applicationId?: string;
  platformExposesOAuthTokens?: boolean;
  oauthTokensTabEnabled?: boolean;
  onOAuthTokensTabEnabledChange?: (enabled: boolean) => void;
}) {
  const { toast } = useToast();
  const [tabTogglePending, setTabTogglePending] = useState(false);

  async function toggleOAuthTokensTab(enabled: boolean) {
    if (!applicationId) return;
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

  const grouped = FLAG_GROUPS.map((group) => ({
    ...group,
    items: flags.filter((f) => group.categories.includes(flagCategory(f))),
  })).filter((g) => g.items.length > 0);
  const uncategorized = flags.filter(
    (f) => !FLAG_GROUPS.some((g) => g.categories.includes(flagCategory(f))),
  );

  return (
    <Card padded={false}>
      <CardHeader
        title="Feature flags"
        description="Capability toggles and which settings tabs appear for this application"
      />
      {applicationId && platformExposesOAuthTokens && (
        <section className="border-t border-ui">
          <p className="px-5 pt-4 text-xs font-semibold uppercase tracking-wide text-faint">
            Settings tabs
          </p>
          <ul className="divide-y divide-ui">
            <li className="flex items-center justify-between gap-4 px-5 py-4">
              <div>
                <div className="flex items-center gap-2">
                  <p className="text-sm font-medium text-ui">OAuth tokens tab</p>
                  <code className="font-mono text-[10px] text-faint">token-policy</code>
                </div>
                <p className="text-xs text-muted">
                  Show the OAuth tokens settings tab for this application (requires platform to allow OAuth
                  tokens under For applications).
                </p>
              </div>
              <Toggle
                checked={oauthTokensTabEnabled}
                disabled={tabTogglePending}
                onChange={toggleOAuthTokensTab}
                aria-label="Show OAuth tokens tab"
              />
            </li>
          </ul>
        </section>
      )}
      {grouped.map((group) => (
        <section key={group.title} className="border-t border-ui first:border-t-0">
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
          return (
            <li key={f.key} className="flex items-center justify-between gap-4 px-5 py-4">
              <div>
                <div className="flex items-center gap-2">
                  <p className="text-sm font-medium text-ui">{f.name}</p>
                  <code className="font-mono text-[10px] text-faint">{f.key}</code>
                </div>
                <p className="text-xs text-muted">{f.description}</p>
                {f.platformEnabled === false && (
                  <p className="mt-1 text-xs text-faint">
                    Enable this capability under Platform → Settings → Feature flags before turning it on
                    for this application.
                  </p>
                )}
              </div>
              <Toggle
                checked={f.enabled}
                disabled={f.platformEnabled === false}
                onChange={(enabled) => {
                  if (f.platformEnabled === false) return;
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
  return (
    <Card padded={false} className={readOnly ? "opacity-90" : undefined}>
      <CardHeader title={title} description={description} />
      <ul className="divide-y divide-ui">
        {methods.map((m, idx) => {
          const extended = m as AuthMethod & { implemented?: boolean };
          const implemented = extended.implemented !== false;
          return (
            <li key={m.id} className="flex items-center justify-between gap-4 px-5 py-4">
              <div>
                <div className="flex items-center gap-2">
                  <p className="text-sm font-medium text-ui">{m.name}</p>
                  {!implemented && !IS_PROD && <Badge tone="warning">Coming soon</Badge>}
                </div>
                <p className="text-xs text-muted">{m.description}</p>
              </div>
              <Toggle
                checked={m.enabled}
                disabled={readOnly || (IS_PROD && !implemented)}
                onChange={(enabled) => {
                  const next = methods.map((x, i) => (i === idx ? { ...x, enabled } : x));
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
