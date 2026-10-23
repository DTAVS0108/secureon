"use client";

import { useCallback, useRef, useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { LiveApiResponse } from "@/components/settings/LiveApiResponse";
import { Card, CardHeader } from "@/components/ui/Card";
import { Toggle } from "@/components/ui/Toggle";
import { useToast } from "@/components/ui/Toast";
import { ensureApplicationPolicyScope } from "@/lib/api/ensure-application-policy";
import { publicAuthServerUrl } from "@/lib/config";
import {
  fetchPublicManifestConfig,
  savePublicManifestConfig,
  type PublicManifestConfig,
  type PublicManifestSections,
} from "@/lib/api/public-manifest";

const DEFAULTS: PublicManifestConfig = {
  enabled: false,
  authMethodsOnlyEnabled: true,
  sections: {
    application: true,
    authMethods: true,
    featureFlags: true,
    passwordPolicy: true,
    verificationPolicy: true,
    appearance: false,
    federation: true,
  },
};

const SECTION_META: { key: keyof PublicManifestSections; label: string; description: string }[] = [
  {
    key: "application",
    label: "Application info",
    description: "Id, name, slug, and status (no secrets).",
  },
  {
    key: "authMethods",
    label: "Authentication methods",
    description: "Enabled/disabled sign-in and MFA options for login UI.",
  },
  {
    key: "featureFlags",
    label: "Feature flags",
    description: "Public capability toggles (key, name, enabled).",
  },
  {
    key: "passwordPolicy",
    label: "Password policy",
    description: "Rules clients can use for password forms (length, complexity).",
  },
  {
    key: "verificationPolicy",
    label: "Verification & OTP",
    description: "Signup, login, and password recovery modes (link vs OTP), channels, and OTP parameters.",
  },
  {
    key: "appearance",
    label: "Appearance",
    description: "Full client theme (colors, widgets, branding) from the application Appearance tab.",
  },
  {
    key: "federation",
    label: "Federation providers",
    description: "Enabled social/OIDC sign-in buttons and the public providers list endpoint.",
  },
];

export function PublicManifestSettings({ applicationId }: { applicationId: string }) {
  const { toast } = useToast();
  const [config, setConfig] = useState<PublicManifestConfig>(DEFAULTS);
  const [loaded, setLoaded] = useState(false);
  const saveTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const reload = useCallback(async () => {
    setLoaded(false);
    try {
      const data = await fetchPublicManifestConfig(applicationId).catch(() => DEFAULTS);
      setConfig({
        ...DEFAULTS,
        ...data,
        sections: { ...DEFAULTS.sections, ...data.sections },
      });
    } catch (e) {
      toast(e instanceof Error ? e.message : "Failed to load public API settings", "error");
    } finally {
      setLoaded(true);
    }
  }, [applicationId, toast]);

  useDeferredEffect(() => {
    reload();
  }, [reload]);

  const scheduleSave = useCallback(
    (next: PublicManifestConfig) => {
      if (!loaded) return;
      if (saveTimer.current) clearTimeout(saveTimer.current);
      saveTimer.current = setTimeout(async () => {
        try {
          await ensureApplicationPolicyScope(applicationId, "public-manifest");
          const payload: PublicManifestConfig = {
            enabled: next.enabled,
            authMethodsOnlyEnabled: next.authMethodsOnlyEnabled,
            sections: next.sections,
          };
          const saved = await savePublicManifestConfig(applicationId, payload);
          setConfig({
            ...DEFAULTS,
            ...saved,
            sections: { ...DEFAULTS.sections, ...saved.sections },
          });
          toast("Settings saved", "success");
        } catch (e) {
          toast(e instanceof Error ? e.message : "Save failed", "error");
        }
      }, 600);
    },
    [applicationId, loaded, toast],
  );

  function patch(partial: Partial<PublicManifestConfig>) {
    const next = { ...config, ...partial };
    setConfig(next);
    scheduleSave(next);
  }

  function patchSection(key: keyof PublicManifestSections, enabled: boolean) {
    const next = {
      ...config,
      sections: { ...config.sections, [key]: enabled },
    };
    setConfig(next);
    scheduleSave(next);
  }

  const publicUrl = `${publicAuthServerUrl()}/api/v1/applications/${applicationId}`;

  return (
    <div className={`space-y-5 ${!loaded ? "pointer-events-none opacity-60" : ""}`}>
      <Card padded={false}>
        <CardHeader
          title="Public application API"
          description="Unauthenticated endpoint for client apps to discover sign-in options and flags. No login required."
        />
        <ul className="divide-y divide-ui">
          <li className="flex items-center justify-between gap-4 px-5 py-4">
            <div>
              <p className="text-sm font-medium text-ui">Enable public manifest</p>
              <p className="text-xs text-muted">
                When off, GET returns 404. When on, clients can fetch configured sections.
              </p>
            </div>
            <Toggle
              checked={config.enabled}
              onChange={(v) => patch({ enabled: v })}
              aria-label="Enable public manifest"
            />
          </li>
          <li className="flex items-center justify-between gap-4 px-5 py-4">
            <div>
              <p className="text-sm font-medium text-ui">Auth methods: enabled only</p>
              <p className="text-xs text-muted">
                Omit disabled methods from the response (recommended for login pages).
              </p>
            </div>
            <Toggle
              checked={config.authMethodsOnlyEnabled}
              onChange={(v) => patch({ authMethodsOnlyEnabled: v })}
              aria-label="Only enabled auth methods"
            />
          </li>
        </ul>
      </Card>

      <Card padded={false}>
        <CardHeader
          title="Include in response"
          description="Choose which blocks are returned. Sensitive admin settings (SMTP, LDAP passwords) are never exposed."
        />
        <ul className="divide-y divide-ui">
          {SECTION_META.map((section) => (
            <li key={section.key} className="flex items-center justify-between gap-4 px-5 py-4">
              <div>
                <p className="text-sm font-medium text-ui">{section.label}</p>
                <p className="text-xs text-muted">{section.description}</p>
              </div>
              <Toggle
                checked={Boolean(config.sections[section.key])}
                onChange={(v) => patchSection(section.key, v)}
                aria-label={`Include ${section.label}`}
              />
            </li>
          ))}
        </ul>
      </Card>

      <Card className="px-5 py-4">
        <p className="text-xs font-semibold uppercase tracking-wide text-muted">Endpoint</p>
        <code className="mt-2 block break-all rounded-lg border border-ui bg-ui-elevated/50 px-3 py-2 text-xs text-ui">
          GET {publicUrl}
        </code>
        <p className="mt-3 text-xs text-faint">
          Example: fetch from your SPA before rendering the login screen. Response shape depends on
          the toggles above.
        </p>
        <LiveApiResponse
          url={publicUrl}
          disabled={!config.enabled}
          disabledHint="Enable the public manifest above to fetch a live response."
        />
      </Card>
    </div>
  );
}
