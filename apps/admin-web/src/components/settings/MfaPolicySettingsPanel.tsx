"use client";

import { useCallback, useRef, useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { Badge } from "@/components/ui/Badge";
import { Card, CardHeader } from "@/components/ui/Card";
import { Input, Select } from "@/components/ui/Field";
import { Toggle } from "@/components/ui/Toggle";
import { useToast } from "@/components/ui/Toast";
import {
  fetchApplicationMfaPolicy,
  resetApplicationMfaPolicy,
  saveApplicationMfaPolicy,
} from "@/lib/api/application-settings";
import {
  fetchPlatformMfaPolicy,
  fetchTenantMfaPolicy,
  resetTenantMfaPolicy,
  savePlatformMfaPolicy,
  saveTenantMfaPolicy,
  TWO_STEP_METHODS,
  type LockMode,
  type MfaPolicy,
  type TwoStepMethodId,
} from "@/lib/api/mfa-policy";
import { cn } from "@/lib/cn";

const DEFAULTS: MfaPolicy = {
  mode: "optional",
  minimumStrength: "strong",
  methods: ["passkey", "security_key", "totp"],
  defaultMethod: "passkey",
  promptOptional: true,
  promptIntervalDays: 7,
  allowDontAskAgain: false,
  graceDays: 14,
  graceSkips: 3,
  enforceFrom: "",
  reminderDays: [7, 1],
  rememberDeviceDays: 0,
  challengeTtlSeconds: 300,
  maxChallengeAttempts: 5,
  recoveryCodesPerEnrollment: 10,
  freshStepMinutes: 5,
  trustIdpMfa: false,
  stepUpAfterPassword: true,
  locks: {},
};

const META_KEYS = ["scope", "inheritsPlatformDefaults", "overrides", "inherited", "platformLocks", "ownSettings"] as const;

type Level = "platform" | "tenant" | "application";

/**
 * Two-step verification settings for the platform, a tenant (defaults for its applications) or one
 * application. Platform and tenant also set lock modes for the levels below: open, stricter only, locked.
 */
export function MfaPolicySettingsPanel({ applicationId, tenantId }: { applicationId?: string; tenantId?: string }) {
  const level: Level = applicationId ? "application" : tenantId ? "tenant" : "platform";
  const { toast } = useToast();
  const [policy, setPolicy] = useState<MfaPolicy>(DEFAULTS);
  const [loaded, setLoaded] = useState(false);
  const saveTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const reload = useCallback(async () => {
    setLoaded(false);
    try {
      if (applicationId) {
        // Two-factor overrides are stored as differences from the inherited settings: no policy-source switch.
        setPolicy({ ...DEFAULTS, ...(await fetchApplicationMfaPolicy(applicationId)) });
      } else if (tenantId) {
        setPolicy({ ...DEFAULTS, ...(await fetchTenantMfaPolicy(tenantId)) });
      } else {
        setPolicy({ ...DEFAULTS, ...(await fetchPlatformMfaPolicy()) });
      }
    } catch (e) {
      toast(e instanceof Error ? e.message : "Failed to load Two Factor Authentication settings", "error");
    } finally {
      setLoaded(true);
    }
  }, [applicationId, tenantId, toast]);

  useDeferredEffect(() => {
    reload();
  }, [reload]);

  // Locks from the levels above: the application sees the combined platform + tenant locks, the tenant the
  // platform's. The platform has none.
  // Backup codes are one set for every application, so their number is never an application setting.
  const aboveLocks: Partial<Record<string, LockMode>> =
    level === "application"
      ? { ...(policy.locks ?? {}), recoveryCodesPerEnrollment: "locked" }
      : level === "tenant"
        ? policy.platformLocks ?? {}
        : {};
  const ownLocks = level === "application" ? {} : policy.locks ?? {};

  const scheduleSave = (next: MfaPolicy) => {
    if (!loaded) return;
    if (saveTimer.current) clearTimeout(saveTimer.current);
    saveTimer.current = setTimeout(async () => {
      try {
        const body: MfaPolicy = { ...next };
        for (const k of META_KEYS) delete (body as Record<string, unknown>)[k];
        if (level === "application") delete body.locks;
        const saved = applicationId
          ? await saveApplicationMfaPolicy(applicationId, body)
          : tenantId
            ? await saveTenantMfaPolicy(tenantId, body)
            : await savePlatformMfaPolicy(body);
        setPolicy({ ...DEFAULTS, ...saved });
        toast("Two Factor Authentication saved", "success");
      } catch (e) {
        toast(e instanceof Error ? e.message : "Save failed", "error");
        await reload();
      }
    }, 700);
  };

  const patch = (partial: Partial<MfaPolicy>) => {
    const next = { ...policy, ...partial };
    setPolicy(next);
    scheduleSave(next);
  };

  const setLock = (key: string, mode: LockMode) => {
    const locks = { ...(policy.locks ?? {}) };
    if (mode === "open") delete locks[key];
    else locks[key] = mode;
    patch({ locks });
  };

  async function resetToInherited() {
    try {
      if (applicationId) await resetApplicationMfaPolicy(applicationId);
      else if (tenantId) await resetTenantMfaPolicy(tenantId);
      await reload();
      toast(level === "application" ? "Back to the inherited settings" : "Back to the platform settings", "success");
    } catch (e) {
      toast(e instanceof Error ? e.message : "Reset failed", "error");
    }
  }

  const ctx: RowContext = { level, aboveLocks, ownLocks, overrides: policy.overrides ?? [], setLock };

  const methods = policy.methods ?? [];
  const toggleMethod = (id: TwoStepMethodId, on: boolean) => {
    const next = on ? [...methods, id] : methods.filter((m) => m !== id);
    const ordered = TWO_STEP_METHODS.map((m) => m.id).filter((m) => next.includes(m));
    patch({
      methods: ordered,
      defaultMethod: ordered.includes(policy.defaultMethod as TwoStepMethodId) ? policy.defaultMethod : ordered[0],
    });
  };

  const levelText =
    level === "platform"
      ? "The default for every tenant and application. Use the menus on the right to decide what tenants and applications may change."
      : level === "tenant"
        ? "Defaults for every application of this tenant, within what the platform allows. Lock settings for the tenant's applications on the right."
        : "This application's settings, within what the platform and the tenant allow.";

  return (
    <div className={cn("space-y-6", !loaded && "pointer-events-none opacity-60")} data-testid="two-step-settings">
      <Card padded={false}>
        <CardHeader title="Two Factor Authentication" description={levelText} />
        {level === "application" && policy.ownSettings === false && (
          <p
            role="status"
            data-testid="two-factor-not-applied"
            className="border-t border-ui bg-amber-500/10 px-5 py-3 text-sm text-ui"
          >
            Not applied: <strong>Two Factor Authentication</strong> is off under this application&apos;s Feature
            flags, so sign-in uses the platform and tenant settings shown here.
          </p>
        )}
        <ul className="divide-y divide-ui">
          <SettingRow ctx={ctx} k="mode" label="Two Factor Authentication" hint="Off: never asked. Optional: users choose (and are reminded). Required: everyone must set it up.">
            <div className="flex flex-wrap gap-2" role="radiogroup">
              {(["off", "optional", "required"] as const).map((m) => (
                <button
                  key={m}
                  type="button"
                  role="radio"
                  aria-checked={policy.mode === m}
                  data-testid={`mode-${m}`}
                  onClick={() => patch({ mode: m })}
                  className={cn(
                    "rounded-lg border px-3 py-1.5 text-sm",
                    policy.mode === m ? "border-transparent bg-brand text-on-brand" : "border-ui text-soft hover:bg-ui-elevated",
                  )}
                >
                  {m === "off" ? "Off" : m === "optional" ? "Optional" : "Required"}
                </button>
              ))}
            </div>
          </SettingRow>
          <SettingRow ctx={ctx} k="minimumStrength" label="Required strength" hint="What counts when Two Factor Authentication is required.">
            <Select
              data-testid="minimum-strength"
              value={policy.minimumStrength}
              onChange={(e) => patch({ minimumStrength: e.target.value as MfaPolicy["minimumStrength"] })}
            >
              <option value="any">Any option (text message and email codes count)</option>
              <option value="strong">Strong: passkey, security key or authenticator app</option>
              <option value="phishing_resistant">Phishing-resistant: passkey or security key only</option>
            </Select>
          </SettingRow>
          <SettingRow ctx={ctx} k="methods" label="Options users can use" hint="Users choose among these; the first one they have is asked first.">
            <ul className="space-y-2">
              {TWO_STEP_METHODS.map((m) => (
                <li key={m.id} className="flex items-start justify-between gap-3">
                  <div>
                    <p className="flex items-center gap-2 text-sm">
                      {m.label}
                      <Badge tone={m.strength === "phishing_resistant" ? "success" : m.strength === "strong" ? "info" : "warning"}>
                        {m.strength === "phishing_resistant" ? "Phishing-resistant" : m.strength === "strong" ? "Strong" : "Basic"}
                      </Badge>
                    </p>
                    <p className="text-xs text-muted">{m.hint}</p>
                  </div>
                  <Toggle
                    checked={methods.includes(m.id)}
                    onChange={(v) => toggleMethod(m.id, v)}
                    aria-label={`Allow ${m.label}`}
                    data-testid={`method-${m.id}`}
                  />
                </li>
              ))}
            </ul>
          </SettingRow>
          <SettingRow ctx={ctx} k="defaultMethod" label="Suggested first" hint="Shown first to users who have it, and recommended at setup.">
            <Select value={policy.defaultMethod} onChange={(e) => patch({ defaultMethod: e.target.value as TwoStepMethodId })}>
              {TWO_STEP_METHODS.filter((m) => methods.includes(m.id)).map((m) => (
                <option key={m.id} value={m.id}>{m.label}</option>
              ))}
            </Select>
          </SettingRow>
        </ul>
      </Card>

      <Card padded={false}>
        <CardHeader
          title="Users who haven't set it up"
          description="Reminders when it is optional; a grace period, then setup during sign-in, when it is required."
        />
        <ul className="divide-y divide-ui">
          <SettingRow ctx={ctx} k="promptOptional" label="Suggest setup at sign-in (optional mode)">
            <Toggle checked={Boolean(policy.promptOptional)} onChange={(v) => patch({ promptOptional: v })} aria-label="Suggest setup" />
          </SettingRow>
          <SettingRow ctx={ctx} k="promptIntervalDays" label="Ask again after (days)">
            <Input type="number" min={1} max={90} value={policy.promptIntervalDays ?? 7}
              onChange={(e) => patch({ promptIntervalDays: Number(e.target.value) })} />
          </SettingRow>
          <SettingRow ctx={ctx} k="allowDontAskAgain" label="Offer “Don't ask again”" hint="Lets users stop the reminders for good.">
            <Toggle checked={Boolean(policy.allowDontAskAgain)} onChange={(v) => patch({ allowDontAskAgain: v })} aria-label="Allow don't ask again" />
          </SettingRow>
          <SettingRow ctx={ctx} k="graceDays" label="Grace period (days, required mode)" hint="From the first prompt. 0 = set up at the next sign-in.">
            <Input type="number" min={0} max={180} data-testid="grace-days" value={policy.graceDays ?? 14}
              onChange={(e) => patch({ graceDays: Number(e.target.value) })} />
          </SettingRow>
          <SettingRow ctx={ctx} k="graceSkips" label="“Later” allowed during the grace period">
            <Input type="number" min={0} max={10} data-testid="grace-skips" value={policy.graceSkips ?? 3}
              onChange={(e) => patch({ graceSkips: Number(e.target.value) })} />
          </SettingRow>
          <SettingRow ctx={ctx} k="enforceFrom" label="Enforce from" hint="Existing users' grace periods start no earlier than this date. Empty = now.">
            <Input type="date" value={policy.enforceFrom ?? ""} onChange={(e) => patch({ enforceFrom: e.target.value })} />
          </SettingRow>
          <SettingRow ctx={ctx} k="reminderDays" label="Reminder emails (days before the deadline)" hint="Comma-separated, e.g. 7, 1.">
            <Input
              defaultValue={(policy.reminderDays ?? []).join(", ")}
              key={(policy.reminderDays ?? []).join(",")}
              onBlur={(e) => patch({ reminderDays: e.target.value.split(",").map((x) => Number(x.trim())).filter((n) => n > 0) })}
            />
          </SettingRow>
        </ul>
      </Card>

      <Card padded={false}>
        <CardHeader title="Sign-in details" description="How the second step behaves." />
        <ul className="divide-y divide-ui">
          <SettingRow ctx={ctx} k="rememberDeviceDays" label="“Don't ask again on this device” (days)" hint="0 = never offered. Users can forget devices on their account page.">
            <Input type="number" min={0} max={90} value={policy.rememberDeviceDays ?? 0}
              onChange={(e) => patch({ rememberDeviceDays: Number(e.target.value) })} />
          </SettingRow>
          <SettingRow ctx={ctx} k="freshStepMinutes" label="“Verify it's you” window (minutes)" hint="How recent a second step must be before changing sign-in options, email or backup codes.">
            <Input type="number" min={1} max={60} value={policy.freshStepMinutes ?? 5}
              onChange={(e) => patch({ freshStepMinutes: Number(e.target.value) })} />
          </SettingRow>
          <SettingRow ctx={ctx} k="stepUpAfterPassword" label="Ask users who have an option (optional mode)" hint="Off: users with an option are not asked unless it is required.">
            <Toggle checked={Boolean(policy.stepUpAfterPassword)} onChange={(v) => patch({ stepUpAfterPassword: v })} aria-label="Ask users who have an option" />
          </SettingRow>
          <SettingRow ctx={ctx} k="trustIdpMfa" label="Trust the identity provider's own MFA" hint="Google, Microsoft, OIDC or SAML sign-ins that report MFA are not asked again.">
            <Toggle checked={Boolean(policy.trustIdpMfa)} onChange={(v) => patch({ trustIdpMfa: v })} aria-label="Trust IdP MFA" />
          </SettingRow>
          <SettingRow ctx={ctx} k="challengeTtlSeconds" label="Time to finish the second step (seconds)">
            <Input type="number" min={60} max={900} value={policy.challengeTtlSeconds ?? 300}
              onChange={(e) => patch({ challengeTtlSeconds: Number(e.target.value) })} />
          </SettingRow>
          <SettingRow ctx={ctx} k="maxChallengeAttempts" label="Wrong codes per sign-in" hint="Wrong codes also count toward the account lockout.">
            <Input type="number" min={1} max={10} value={policy.maxChallengeAttempts ?? 5}
              onChange={(e) => patch({ maxChallengeAttempts: Number(e.target.value) })} />
          </SettingRow>
          <SettingRow ctx={ctx} k="recoveryCodesPerEnrollment" label="Backup codes per user">
            <Input type="number" min={1} max={20} value={policy.recoveryCodesPerEnrollment ?? 10}
              onChange={(e) => patch({ recoveryCodesPerEnrollment: Number(e.target.value) })} />
          </SettingRow>
        </ul>
        {level !== "platform" && policy.inheritsPlatformDefaults === false && (
          <div className="border-t border-ui px-5 py-4">
            <button type="button" className="text-sm text-brand hover:underline" onClick={() => void resetToInherited()}>
              {level === "application" ? "Use the inherited settings again" : "Use the platform settings again"}
            </button>
          </div>
        )}
      </Card>
    </div>
  );
}

type RowContext = {
  level: Level;
  aboveLocks: Partial<Record<string, LockMode>>;
  ownLocks: Partial<Record<string, LockMode>>;
  overrides: string[];
  setLock: (key: string, mode: LockMode) => void;
};

/** A setting row: the control, what the level above allows, and (platform/tenant) the lock for levels below. */
function SettingRow({
  ctx,
  k,
  label,
  hint,
  children,
}: {
  ctx: RowContext;
  k: string;
  label: string;
  hint?: string;
  children: React.ReactNode;
}) {
  const above = ctx.aboveLocks[k];
  const own = ctx.ownLocks[k] ?? "open";
  const overridden = ctx.overrides.includes(k);
  return (
    <li className="px-5 py-4" data-testid={`setting-${k}`}>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0 flex-1">
          <p className="flex flex-wrap items-center gap-2 text-sm font-medium text-ui">
            {label}
            {above === "locked" && <Badge tone="neutral">Set by {ctx.level === "application" ? "platform or tenant" : "platform"}</Badge>}
            {above === "stricter" && <Badge tone="info">Can only be made stricter</Badge>}
            {ctx.level !== "platform" && overridden && <Badge tone="indigo">Changed here</Badge>}
          </p>
          {hint && <p className="text-xs text-muted">{hint}</p>}
        </div>
        {ctx.level !== "application" && (
          <Select
            aria-label={`Lower levels for ${label}`}
            data-testid={`lock-${k}`}
            className="w-auto text-xs"
            value={own}
            onChange={(e) => ctx.setLock(k, e.target.value as LockMode)}
          >
            <option value="open">{ctx.level === "platform" ? "Tenants & apps may change" : "Apps may change"}</option>
            <option value="stricter">Only stricter</option>
            <option value="locked">Locked</option>
          </Select>
        )}
      </div>
      <fieldset disabled={above === "locked"} className={cn("mt-3", above === "locked" && "opacity-60")}>
        {children}
      </fieldset>
    </li>
  );
}
