"use client";

import { Card, CardHeader } from "@/components/ui/Card";
import { FieldRow, Input } from "@/components/ui/Field";
import { Toggle } from "@/components/ui/Toggle";
import {
  LOGIN_HELP,
  RECOVERY_HELP,
  SIGNUP_HELP,
} from "@/lib/verification-settings-help";
import type { VerificationPolicy, VerificationPolicySection } from "@/lib/api/verification-settings";

type OtpFieldsProps = {
  section: VerificationPolicySection;
  sectionHelp: {
    otpLength: string;
    otpTtlSeconds: string;
    maxAttempts: string;
    resendCooldownSeconds: string;
  };
  onChange: (next: VerificationPolicySection) => void;
};

function OtpFields({ section, sectionHelp, onChange }: OtpFieldsProps) {
  return (
    <>
      <FieldRow label="OTP length" info={sectionHelp.otpLength}>
        <Input
          type="number"
          min={6}
          max={8}
          value={section.otpLength ?? 6}
          onChange={(e) => onChange({ ...section, otpLength: Number(e.target.value) })}
        />
      </FieldRow>
      <FieldRow label="OTP TTL (seconds)" info={sectionHelp.otpTtlSeconds}>
        <Input
          type="number"
          min={60}
          max={600}
          value={section.otpTtlSeconds ?? 600}
          onChange={(e) => onChange({ ...section, otpTtlSeconds: Number(e.target.value) })}
        />
      </FieldRow>
      <FieldRow label="Max attempts" info={sectionHelp.maxAttempts}>
        <Input
          type="number"
          min={3}
          max={10}
          value={section.maxAttempts ?? 5}
          onChange={(e) => onChange({ ...section, maxAttempts: Number(e.target.value) })}
        />
      </FieldRow>
      <FieldRow label="Resend cooldown (seconds)" info={sectionHelp.resendCooldownSeconds}>
        <Input
          type="number"
          min={30}
          max={600}
          value={section.resendCooldownSeconds ?? 60}
          onChange={(e) => onChange({ ...section, resendCooldownSeconds: Number(e.target.value) })}
        />
      </FieldRow>
    </>
  );
}

function ModeSelect({
  value,
  allowed,
  onChange,
}: {
  value: string;
  allowed: string[];
  onChange: (mode: string) => void;
}) {
  return (
    <select
      className="w-full rounded-md border border-ui bg-surface px-3 py-2 text-sm"
      value={value}
      onChange={(e) => onChange(e.target.value)}
    >
      {allowed.includes("link") && <option value="link">Email link</option>}
      {allowed.includes("otp") && <option value="otp">OTP code</option>}
    </select>
  );
}

export function VerificationPolicyCards({
  policy,
  onChange,
}: {
  policy: VerificationPolicy;
  onChange: (next: VerificationPolicy) => void;
}) {
  const signup = policy.signup ?? {};
  const login = policy.login ?? {};
  const recovery = policy.recovery ?? {};

  const updateSignup = (next: VerificationPolicySection) => onChange({ ...policy, signup: next });
  const updateLogin = (next: VerificationPolicySection) => onChange({ ...policy, login: next });
  const updateRecovery = (next: VerificationPolicySection) => onChange({ ...policy, recovery: next });

  const signupAllowed = signup.allowedModes ?? ["link", "otp"];
  const recoveryAllowed = recovery.allowedModes ?? ["link", "otp"];

  return (
    <>
      <Card>
        <CardHeader
          title="Signup verification"
          description="How new accounts confirm email or phone after registration."
          info={SIGNUP_HELP.section}
        />
        <div className="space-y-4 p-4">
          <FieldRow label="Default mode" info={SIGNUP_HELP.defaultMode}>
            <ModeSelect
              value={signup.defaultMode ?? "link"}
              allowed={signupAllowed}
              onChange={(defaultMode) => updateSignup({ ...signup, defaultMode })}
            />
          </FieldRow>
          <FieldRow label="Allowed modes" info={SIGNUP_HELP.allowedModes}>
            <div className="flex flex-wrap gap-4">
              {(["link", "otp"] as const).map((mode) => (
                <label key={mode} className="flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={signupAllowed.includes(mode)}
                    onChange={(e) => {
                      const next = e.target.checked
                        ? [...new Set([...signupAllowed, mode])]
                        : signupAllowed.filter((m) => m !== mode);
                      updateSignup({ ...signup, allowedModes: next.length ? next : ["link"] });
                    }}
                  />
                  {mode === "link" ? "Link" : "OTP"}
                </label>
              ))}
            </div>
          </FieldRow>
          <FieldRow label="Required channels" info={SIGNUP_HELP.requiredChannels}>
            <div className="flex gap-4">
              {(["email", "phone"] as const).map((ch) => (
                <label key={ch} className="flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={(signup.requiredChannels ?? ["email"]).includes(ch)}
                    onChange={(e) => {
                      const current = signup.requiredChannels ?? ["email"];
                      const next = e.target.checked
                        ? [...new Set([...current, ch])]
                        : current.filter((c) => c !== ch);
                      updateSignup({ ...signup, requiredChannels: next });
                    }}
                  />
                  {ch}
                </label>
              ))}
            </div>
          </FieldRow>
          <FieldRow label="Allow user channel choice" info={SIGNUP_HELP.allowUserChannelChoice}>
            <Toggle
              checked={Boolean(signup.allowUserChannelChoice)}
              onChange={(checked) => updateSignup({ ...signup, allowUserChannelChoice: checked })}
            />
          </FieldRow>
          <OtpFields section={signup} sectionHelp={SIGNUP_HELP} onChange={updateSignup} />
        </div>
      </Card>

      <Card>
        <CardHeader
          title="Login & passwordless"
          description="Email/phone identifiers and OTP or magic-link sign-in."
          info={LOGIN_HELP.section}
        />
        <div className="space-y-4 p-4">
          <FieldRow label="Identifier types" info={LOGIN_HELP.identifierTypes}>
            <div className="flex gap-4">
              {(["email", "phone"] as const).map((t) => (
                <label key={t} className="flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={(login.identifierTypes ?? ["email", "phone"]).includes(t)}
                    onChange={(e) => {
                      const current = login.identifierTypes ?? ["email", "phone"];
                      const next = e.target.checked
                        ? [...new Set([...current, t])]
                        : current.filter((c) => c !== t);
                      updateLogin({ ...login, identifierTypes: next });
                    }}
                  />
                  {t}
                </label>
              ))}
            </div>
          </FieldRow>
          <FieldRow label="Passwordless modes" info={LOGIN_HELP.passwordlessModes}>
            <div className="flex flex-col gap-2 text-sm">
              {(
                [
                  ["email_otp", "Email OTP"],
                  ["phone_otp", "SMS OTP"],
                  ["magic_link", "Magic link"],
                ] as const
              ).map(([key, label]) => (
                <label key={key} className="flex items-center gap-2">
                  <input
                    type="checkbox"
                    checked={(login.passwordlessModes ?? []).includes(key)}
                    onChange={(e) => {
                      const current = login.passwordlessModes ?? ["email_otp", "phone_otp", "magic_link"];
                      const next = e.target.checked
                        ? [...new Set([...current, key])]
                        : current.filter((m) => m !== key);
                      updateLogin({ ...login, passwordlessModes: next });
                    }}
                  />
                  {label}
                </label>
              ))}
            </div>
          </FieldRow>
          <p className="text-xs text-muted">
            Whether a second step is required is set under <strong>Two Factor Authentication</strong>.
          </p>
          <OtpFields section={login} sectionHelp={LOGIN_HELP} onChange={updateLogin} />
        </div>
      </Card>

      <Card>
        <CardHeader
          title="Password recovery"
          description="Forgot-password via email link or OTP (email/SMS)."
          info={RECOVERY_HELP.section}
        />
        <div className="space-y-4 p-4">
          <FieldRow label="Default mode" info={RECOVERY_HELP.defaultMode}>
            <ModeSelect
              value={recovery.passwordResetMode ?? "link"}
              allowed={recoveryAllowed}
              onChange={(passwordResetMode) => updateRecovery({ ...recovery, passwordResetMode })}
            />
          </FieldRow>
          <FieldRow label="Allowed modes" info={RECOVERY_HELP.allowedModes}>
            <div className="flex flex-wrap gap-4">
              {(["link", "otp"] as const).map((mode) => (
                <label key={mode} className="flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={recoveryAllowed.includes(mode)}
                    onChange={(e) => {
                      const next = e.target.checked
                        ? [...new Set([...recoveryAllowed, mode])]
                        : recoveryAllowed.filter((m) => m !== mode);
                      updateRecovery({ ...recovery, allowedModes: next.length ? next : ["link"] });
                    }}
                  />
                  {mode === "link" ? "Link" : "OTP"}
                </label>
              ))}
            </div>
          </FieldRow>
          <FieldRow label="Allowed channels" info={RECOVERY_HELP.allowedChannels}>
            <div className="flex gap-4">
              {(["email", "phone"] as const).map((ch) => (
                <label key={ch} className="flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={(recovery.allowedChannels ?? ["email"]).includes(ch)}
                    onChange={(e) => {
                      const current = recovery.allowedChannels ?? ["email"];
                      const next = e.target.checked
                        ? [...new Set([...current, ch])]
                        : current.filter((c) => c !== ch);
                      updateRecovery({ ...recovery, allowedChannels: next });
                    }}
                  />
                  {ch}
                </label>
              ))}
            </div>
          </FieldRow>
          <FieldRow label="Allow user channel choice" info={RECOVERY_HELP.allowUserChannelChoice}>
            <Toggle
              checked={recovery.allowUserChannelChoice !== false}
              onChange={(checked) => updateRecovery({ ...recovery, allowUserChannelChoice: checked })}
            />
          </FieldRow>
          <FieldRow label="Require verified email/phone" info={RECOVERY_HELP.requireVerifiedChannel}>
            <Toggle
              checked={recovery.requireVerifiedChannel !== false}
              onChange={(checked) => updateRecovery({ ...recovery, requireVerifiedChannel: checked })}
            />
          </FieldRow>
          <FieldRow label="Fallback to link if OTP unavailable" info={RECOVERY_HELP.fallbackToLink}>
            <Toggle
              checked={recovery.fallbackToLink !== false}
              onChange={(checked) => updateRecovery({ ...recovery, fallbackToLink: checked })}
            />
          </FieldRow>
          <OtpFields section={recovery} sectionHelp={RECOVERY_HELP} onChange={updateRecovery} />
        </div>
      </Card>
    </>
  );
}
