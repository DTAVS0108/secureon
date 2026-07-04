"use client";

import { useCallback, useRef, useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { Card, CardHeader } from "@/components/ui/Card";
import { FieldRow, Input } from "@/components/ui/Field";
import { Toggle } from "@/components/ui/Toggle";
import { useToast } from "@/components/ui/Toast";
import { ensureApplicationPolicyScope } from "@/lib/api/ensure-application-policy";
import {
  fetchApplicationMfaPolicy,
  resetApplicationMfaPolicy,
  saveApplicationMfaPolicy,
} from "@/lib/api/application-settings";
import {
  fetchPlatformMfaPolicy,
  savePlatformMfaPolicy,
  type MfaPolicy,
} from "@/lib/api/mfa-policy";

const DEFAULTS: MfaPolicy = {
  stepUpAfterPassword: true,
  requirePasskeyWhenEnrolled: false,
  recoveryCodesPerEnrollment: 10,
  challengeTtlSeconds: 300,
  maxChallengeAttempts: 5,
  rememberDeviceDays: 0,
};

export function MfaPolicySettingsPanel({ applicationId }: { applicationId?: string }) {
  const { toast } = useToast();
  const [policy, setPolicy] = useState<MfaPolicy>(DEFAULTS);
  const [loaded, setLoaded] = useState(false);
  const saveTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const reload = useCallback(async () => {
    setLoaded(false);
    try {
      if (applicationId) {
        await ensureApplicationPolicyScope(applicationId, "auth-methods");
        setPolicy({ ...DEFAULTS, ...(await fetchApplicationMfaPolicy(applicationId)) });
      } else {
        setPolicy({ ...DEFAULTS, ...(await fetchPlatformMfaPolicy()) });
      }
    } catch (e) {
      toast(e instanceof Error ? e.message : "Failed to load MFA policy", "error");
    } finally {
      setLoaded(true);
    }
  }, [applicationId, toast]);

  useDeferredEffect(() => {
    reload();
  }, [reload]);

  const scheduleSave = (next: MfaPolicy) => {
    if (!loaded) return;
    if (saveTimer.current) clearTimeout(saveTimer.current);
    saveTimer.current = setTimeout(async () => {
      try {
        const { scope: _s, inheritsPlatformDefaults: _i, ...body } = next;
        const saved = applicationId
          ? await saveApplicationMfaPolicy(applicationId, body)
          : await savePlatformMfaPolicy(body);
        setPolicy({ ...DEFAULTS, ...saved });
        toast("MFA policy saved", "success");
      } catch (e) {
        toast(e instanceof Error ? e.message : "Save failed", "error");
      }
    }, 700);
  };

  const patch = (partial: Partial<MfaPolicy>) => {
    const next = { ...policy, ...partial };
    setPolicy(next);
    scheduleSave(next);
  };

  async function resetOverride() {
    if (!applicationId) return;
    try {
      await resetApplicationMfaPolicy(applicationId);
      await reload();
      toast("MFA policy reset to platform defaults", "success");
    } catch (e) {
      toast(e instanceof Error ? e.message : "Reset failed", "error");
    }
  }

  return (
    <Card padded={false} className={!loaded ? "pointer-events-none opacity-60" : ""}>
      <CardHeader
        title="MFA policy"
        description="Step-up behavior, challenge TTL, and remembered-device settings. Login MFA requirement is set under Verification → Login."
      />
      <ul className="divide-y divide-ui">
        <li className="flex items-center justify-between gap-4 px-5 py-4">
          <div>
            <p className="text-sm font-medium text-ui">Step-up after password</p>
            <p className="text-xs text-muted">Require TOTP/recovery after password login when user has MFA enrolled.</p>
          </div>
          <Toggle
            checked={Boolean(policy.stepUpAfterPassword)}
            onChange={(v) => patch({ stepUpAfterPassword: v })}
            aria-label="Step-up after password"
          />
        </li>
        <li className="flex items-center justify-between gap-4 px-5 py-4">
          <div>
            <p className="text-sm font-medium text-ui">Prefer passkey when enrolled</p>
            <p className="text-xs text-muted">When enabled, users with passkeys must use passkey step-up instead of TOTP.</p>
          </div>
          <Toggle
            checked={Boolean(policy.requirePasskeyWhenEnrolled)}
            onChange={(v) => patch({ requirePasskeyWhenEnrolled: v })}
            aria-label="Require passkey when enrolled"
          />
        </li>
        <li className="px-5 py-4">
          <FieldRow label="Challenge TTL (seconds)">
            <Input
              type="number"
              min={60}
              max={900}
              value={policy.challengeTtlSeconds ?? 300}
              onChange={(e) => patch({ challengeTtlSeconds: Number(e.target.value) })}
            />
          </FieldRow>
        </li>
        <li className="px-5 py-4">
          <FieldRow label="Max challenge attempts">
            <Input
              type="number"
              min={1}
              max={10}
              value={policy.maxChallengeAttempts ?? 5}
              onChange={(e) => patch({ maxChallengeAttempts: Number(e.target.value) })}
            />
          </FieldRow>
        </li>
        <li className="px-5 py-4">
          <FieldRow label="Remember device (days)" hint="0 = disabled. Trusted-device cookies are a future enhancement.">
            <Input
              type="number"
              min={0}
              max={90}
              value={policy.rememberDeviceDays ?? 0}
              onChange={(e) => patch({ rememberDeviceDays: Number(e.target.value) })}
            />
          </FieldRow>
        </li>
      </ul>
      {applicationId && policy.inheritsPlatformDefaults === false && (
        <div className="border-t border-ui px-5 py-4">
          <button type="button" className="text-sm text-brand hover:underline" onClick={() => void resetOverride()}>
            Reset to platform defaults
          </button>
        </div>
      )}
    </Card>
  );
}
