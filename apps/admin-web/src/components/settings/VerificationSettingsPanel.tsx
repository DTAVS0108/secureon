"use client";

import { useCallback, useRef, useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { Card, CardHeader } from "@/components/ui/Card";
import { useToast } from "@/components/ui/Toast";
import { SmsProviderSettingsCard } from "@/components/settings/SmsProviderSettingsCard";
import { VerificationPolicyCards } from "@/components/settings/VerificationPolicyCards";
import { ensureApplicationPolicyScope } from "@/lib/api/ensure-application-policy";
import {
  fetchSmsSettings,
  fetchVerificationPolicy,
  saveVerificationPolicy,
  type SmsSettings,
  type VerificationPolicy,
} from "@/lib/api/verification-settings";
import { VERIFICATION_SETUP_GUIDE } from "@/lib/verification-settings-help";

export function VerificationSettingsPanel({ applicationId }: { applicationId?: string }) {
  const { toast } = useToast();
  const [policy, setPolicy] = useState<VerificationPolicy | null>(null);
  const [sms, setSms] = useState<SmsSettings | null>(null);
  const [loaded, setLoaded] = useState(false);
  const saveTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const reload = useCallback(async () => {
    setLoaded(false);
    try {
      const [p, s] = await Promise.all([
        fetchVerificationPolicy(applicationId),
        fetchSmsSettings(applicationId),
      ]);
      setPolicy(p);
      setSms(s);
    } catch (e) {
      toast(e instanceof Error ? e.message : "Failed to load verification settings", "error");
    } finally {
      setLoaded(true);
    }
  }, [applicationId, toast]);

  useDeferredEffect(() => {
    reload();
  }, [reload]);

  const scheduleSavePolicy = useCallback(
    (next: VerificationPolicy) => {
      if (!loaded) return;
      setPolicy(next);
      if (saveTimer.current) clearTimeout(saveTimer.current);
      saveTimer.current = setTimeout(async () => {
        try {
          if (applicationId) {
            await ensureApplicationPolicyScope(applicationId, "verification-policy");
          }
          await saveVerificationPolicy(next, applicationId);
          toast("Verification policy saved", "success");
        } catch (e) {
          toast(e instanceof Error ? e.message : "Save failed", "error");
        }
      }, 700);
    },
    [applicationId, loaded, toast],
  );

  if (!loaded || !policy) {
    return <p className="text-sm text-muted">Loading verification settings…</p>;
  }

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader
          title={VERIFICATION_SETUP_GUIDE.title}
          description="Hover the info icons on each field for details. Changes auto-save after a short delay."
        />
        <ol className="list-decimal space-y-2 px-5 pb-5 pl-9 text-sm text-muted">
          {VERIFICATION_SETUP_GUIDE.steps.map((step) => (
            <li key={step}>{step.replace(/\*\*(.*?)\*\*/g, "$1")}</li>
          ))}
        </ol>
      </Card>

      <VerificationPolicyCards policy={policy} onChange={scheduleSavePolicy} />

      {sms &&
        (applicationId ? (
          <SmsProviderSettingsCard
            scope="application"
            applicationId={applicationId}
            sms={sms}
            onSaved={setSms}
          />
        ) : (
          <SmsProviderSettingsCard scope="platform" sms={sms} onSaved={setSms} />
        ))}
    </div>
  );
}
