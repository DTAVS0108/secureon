"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { Toggle } from "@/components/ui/Toggle";
import { InfoTip } from "@/components/ui/InfoTip";
import { useToast } from "@/components/ui/Toast";
import { loadAppExposureAction, saveAppExposureAction } from "@/lib/actions/app-exposure";
import type { AppSettingsExposure } from "@/lib/api/app-exposure";
import { coerceAppSettingsExposure, normalizeAppSettingsExposure } from "@/lib/settings-exposure";
import { EXPOSURE_HELP } from "@/lib/verification-settings-help";

const GROUPS: { title: string; keys: string[] }[] = [
  { title: "Messaging", keys: ["notifications", "email"] },
  { title: "Authentication", keys: ["auth-methods", "two-factor", "password-policy", "feature-flags", "token-policy", "verification-policy", "sms"] },
  { title: "Experience & directory", keys: ["appearance", "user-directory"] },
  { title: "Client integration", keys: ["public-manifest"] },
];

const SECTIONS: { key: string; label: string; description: string; info?: string }[] = [
  {
    key: "notifications",
    label: "Notifications",
    description: "Application email alerts, user mail, SMTP, and templates (independent from platform alerts).",
  },
  {
    key: "email",
    label: "Email sender",
    description: "From name, address, and reply-to for this application's outbound mail.",
  },
  {
    key: "auth-methods",
    label: "Sign-in methods",
    description: "Password, magic link, passkeys and identity providers for users of this application.",
  },
  {
    key: "two-factor",
    label: "Two Factor Authentication",
    description:
      "Lets each application have its own two-factor settings (its Feature flags → Two Factor Authentication switch). Platform and tenant locks still apply.",
  },
  {
    key: "password-policy",
    label: "Password policy",
    description: "Length, complexity, and history rules for this application.",
  },
  {
    key: "feature-flags",
    label: "Feature flags",
    description:
      "Lets each application turn off application features (self-registration, recovery, LDAP) that are on here. Console controls are never shown to applications.",
  },
  {
    key: "token-policy",
    label: "OAuth tokens tab",
    description:
      "When on, each application can enable an OAuth tokens tab to manage access/refresh lifetimes and rotation.",
  },
  {
    key: "verification-policy",
    label: "Verification & OTP",
    description: "Signup verification mode (link vs OTP), OTP TTL, and channel policy.",
    info: EXPOSURE_HELP.verificationPolicy,
  },
  {
    key: "sms",
    label: "SMS provider",
    description: "Per-application SMS provider (Twilio, AWS SNS, MSG91, or log) for OTP delivery.",
    info: EXPOSURE_HELP.sms,
  },
  {
    key: "appearance",
    label: "Appearance",
    description: "Per-application branding (admin console theme stays platform-wide unless you enable this).",
  },
  {
    key: "user-directory",
    label: "User directory",
    description: "Bulk import and export (CSV, Excel, LDAP) and per-app enablement of those features.",
  },
  {
    key: "public-manifest",
    label: "Public API",
    description: "Unauthenticated application manifest for client login UIs (auth methods, flags).",
  },
];

export function AppExposureSettings() {
  const { toast } = useToast();
  const toastRef = useRef(toast);
  useEffect(() => {
    toastRef.current = toast;
  }, [toast]);
  const [exposure, setExposure] = useState<AppSettingsExposure>({});
  const [loaded, setLoaded] = useState(false);
  const saveTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const saveSeq = useRef(0);
  const exposureRef = useRef<AppSettingsExposure>({});

  useEffect(() => {
    exposureRef.current = exposure;
  }, [exposure]);

  useEffect(() => {
    let cancelled = false;
    loadAppExposureAction().then(({ exposure: data, error }) => {
      if (cancelled) return;
      const resolved = coerceAppSettingsExposure(data);
      setExposure(resolved);
      exposureRef.current = resolved;
      setLoaded(true);
      if (error) toastRef.current(error, "error");
    });
    return () => {
      cancelled = true;
    };
  }, []);

  const scheduleSave = useCallback(
    (next: AppSettingsExposure) => {
      if (!loaded) return;
      const normalized = normalizeAppSettingsExposure(next);
      setExposure(normalized);
      exposureRef.current = normalized;
      if (saveTimer.current) clearTimeout(saveTimer.current);
      const seq = ++saveSeq.current;
      saveTimer.current = setTimeout(async () => {
        const { exposure: saved, error } = await saveAppExposureAction(normalized);
        if (seq !== saveSeq.current) return;
        const applied = normalizeAppSettingsExposure(saved);
        setExposure(applied);
        exposureRef.current = applied;
        if (error) toastRef.current(error, "error");
        else toastRef.current("Settings saved", "success");
      }, 600);
    },
    [loaded],
  );

  useEffect(() => {
    return () => {
      if (saveTimer.current) clearTimeout(saveTimer.current);
    };
  }, []);

  const setAll = (enabled: boolean) => {
    const next = normalizeAppSettingsExposure(
      Object.fromEntries(SECTIONS.map((s) => [s.key, enabled])),
    );
    setExposure(next);
    exposureRef.current = next;
    scheduleSave(next);
  };

  const sectionByKey = Object.fromEntries(SECTIONS.map((s) => [s.key, s]));

  return (
    <Card padded={false}>
      <CardHeader
        title="For applications"
        description="Choose which platform setting sections appear under each application's Settings page. Disabled sections stay platform-only."
        action={
          <div className="flex gap-2">
            <Button type="button" variant="secondary" size="sm" onClick={() => setAll(true)} disabled={!loaded}>
              Enable all
            </Button>
            <Button type="button" variant="secondary" size="sm" onClick={() => setAll(false)} disabled={!loaded}>
              Disable all
            </Button>
          </div>
        }
      />
      <div className={!loaded ? "pointer-events-none opacity-60" : ""}>
        {GROUPS.map((group) => (
          <section key={group.title} className="border-t border-ui first:border-t-0">
            <p className="px-5 pt-4 text-xs font-semibold uppercase tracking-wide text-faint">{group.title}</p>
            <ul className="divide-y divide-ui">
              {group.keys.map((key) => {
                const section = sectionByKey[key];
                if (!section) return null;
                return (
                  <li key={section.key} className="flex items-start justify-between gap-4 px-5 py-4">
                    <div>
                      <p className="flex items-center text-sm font-medium text-ui">
                        {section.label}
                        {section.info && <InfoTip content={section.info} />}
                      </p>
                      <p className="text-xs text-muted">{section.description}</p>
                    </div>
                    <Toggle
                      checked={Boolean(exposure[section.key])}
                      onChange={(enabled) => {
                        const next = normalizeAppSettingsExposure({
                          ...exposureRef.current,
                          [section.key]: enabled,
                        });
                        scheduleSave(next);
                      }}
                      aria-label={`Enable ${section.label} for applications`}
                    />
                  </li>
                );
              })}
            </ul>
          </section>
        ))}
      </div>
    </Card>
  );
}
