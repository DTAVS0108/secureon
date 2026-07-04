"use client";

import { useCallback, useRef, useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { Card, CardHeader } from "@/components/ui/Card";
import { Input } from "@/components/ui/Field";
import { Toggle } from "@/components/ui/Toggle";
import { useToast } from "@/components/ui/Toast";
import { ensureApplicationPolicyScope } from "@/lib/api/ensure-application-policy";
import { fetchApplicationFeatureFlags } from "@/lib/api/application-settings";
import {
  fetchUserDirectorySettings,
  saveUserDirectorySettings,
  type UserDirectorySettings,
} from "@/lib/api/user-directory";
import { normalizeFeatureFlags } from "@/lib/auth-settings-normalize";
import { isFeatureEnabled } from "@/lib/feature-flags";
import type { FeatureFlag } from "@/lib/types";

const DEFAULTS: UserDirectorySettings = {
  importEnabled: false,
  exportEnabled: false,
  sources: {
    csv: { enabled: true },
    excel: { enabled: true },
    ldap: { enabled: false },
  },
  ldap: {
    host: "",
    port: 389,
    baseDn: "",
    bindDn: "",
    bindPassword: "",
    userFilter: "(mail={0})",
    useTls: true,
  },
};

export function UserDirectorySettings({ applicationId }: { applicationId: string }) {
  const { toast } = useToast();
  const [config, setConfig] = useState<UserDirectorySettings>(DEFAULTS);
  const [featureFlags, setFeatureFlags] = useState<FeatureFlag[]>([]);
  const [loaded, setLoaded] = useState(false);
  const ldapFeatureEnabled = isFeatureEnabled(featureFlags, "ldap");
  const saveTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const reload = useCallback(async () => {
    setLoaded(false);
    try {
      await ensureApplicationPolicyScope(applicationId, "user-directory");
      const [data, flagsRaw] = await Promise.all([
        fetchUserDirectorySettings(applicationId).catch(() => DEFAULTS),
        fetchApplicationFeatureFlags(applicationId).catch(() => []),
      ]);
      setFeatureFlags(normalizeFeatureFlags(flagsRaw));
      setConfig({
        ...DEFAULTS,
        ...data,
        sources: { ...DEFAULTS.sources, ...data.sources },
        ldap: { ...DEFAULTS.ldap, ...data.ldap },
      });
    } catch (e) {
      toast(e instanceof Error ? e.message : "Failed to load user directory settings", "error");
    } finally {
      setLoaded(true);
    }
  }, [applicationId, toast]);

  useDeferredEffect(() => {
    reload();
  }, [reload]);

  const scheduleSave = useCallback(
    (next: UserDirectorySettings) => {
      if (!loaded) return;
      if (saveTimer.current) clearTimeout(saveTimer.current);
      saveTimer.current = setTimeout(async () => {
        try {
          const saved = await saveUserDirectorySettings(applicationId, next);
          setConfig({
            ...DEFAULTS,
            ...saved,
            sources: { ...DEFAULTS.sources, ...saved.sources },
            ldap: { ...DEFAULTS.ldap, ...saved.ldap },
          });
          toast("Settings saved", "success");
        } catch (e) {
          toast(e instanceof Error ? e.message : "Save failed", "error");
        }
      }, 600);
    },
    [applicationId, loaded, toast],
  );

  function patch(partial: Partial<UserDirectorySettings>) {
    const next = { ...config, ...partial };
    setConfig(next);
    scheduleSave(next);
  }

  function patchSource(key: keyof UserDirectorySettings["sources"], enabled: boolean) {
    if (key === "ldap" && enabled && !ldapFeatureEnabled) {
      toast("Enable the LDAP / AD feature flag under Authentication → Feature flags first.", "error");
      return;
    }
    const next = {
      ...config,
      sources: { ...config.sources, [key]: { enabled } },
    };
    setConfig(next);
    scheduleSave(next);
  }

  function patchLdap(field: keyof UserDirectorySettings["ldap"], value: string | number | boolean) {
    const next = { ...config, ldap: { ...config.ldap, [field]: value } };
    setConfig(next);
    scheduleSave(next);
  }

  const importSources = (["csv", "excel", "ldap"] as const).filter(
    (key) => key !== "ldap" || ldapFeatureEnabled,
  );

  return (
    <div className={`space-y-5 ${!loaded ? "pointer-events-none opacity-60" : ""}`}>
      <Card padded={false}>
        <CardHeader
          title="Import & export"
          description="Control whether admins can bulk import or export users for this application. Disabled features are hidden on the Users page."
        />
        <ul className="divide-y divide-ui">
          <li className="flex items-center justify-between gap-4 px-5 py-4">
            <div>
              <p className="text-sm font-medium text-ui">Allow user import</p>
              <p className="text-xs text-muted">CSV, Excel (CSV format), and LDAP sources.</p>
            </div>
            <Toggle
              checked={config.importEnabled}
              onChange={(v) => patch({ importEnabled: v })}
              aria-label="Allow user import"
            />
          </li>
          <li className="flex items-center justify-between gap-4 px-5 py-4">
            <div>
              <p className="text-sm font-medium text-ui">Allow user export</p>
              <p className="text-xs text-muted">Download CSV of application members.</p>
            </div>
            <Toggle
              checked={config.exportEnabled}
              onChange={(v) => patch({ exportEnabled: v })}
              aria-label="Allow user export"
            />
          </li>
        </ul>
      </Card>

      <Card padded={false}>
        <CardHeader title="Import sources" description="Which formats appear in the import menu." />
        <ul className="divide-y divide-ui">
          {importSources.map((key) => (
            <li key={key} className="flex items-center justify-between gap-4 px-5 py-4">
              <p className="text-sm font-medium uppercase text-ui">{key}</p>
              <Toggle
                checked={config.sources[key].enabled}
                onChange={(v) => patchSource(key, v)}
                aria-label={`Enable ${key} import`}
              />
            </li>
          ))}
        </ul>
        {!ldapFeatureEnabled && (
          <p className="border-t border-ui px-5 py-3 text-xs text-muted">
            LDAP import is hidden because the <strong>LDAP / AD</strong> feature flag is off. Enable it
            under Authentication → Feature flags.
          </p>
        )}
      </Card>

      {ldapFeatureEnabled && config.sources.ldap?.enabled && (
        <Card padded={false}>
          <CardHeader title="LDAP connection" description="Used when LDAP import is enabled." />
          <div className="grid gap-4 p-5 md:grid-cols-2">
            <label className="block text-sm">
              <span className="text-muted">Host</span>
              <Input
                className="mt-1"
                value={config.ldap.host}
                onChange={(e) => patchLdap("host", e.target.value)}
              />
            </label>
            <label className="block text-sm">
              <span className="text-muted">Port</span>
              <Input
                className="mt-1"
                type="number"
                value={config.ldap.port}
                onChange={(e) => patchLdap("port", Number(e.target.value))}
              />
            </label>
            <label className="block text-sm md:col-span-2">
              <span className="text-muted">Base DN</span>
              <Input
                className="mt-1"
                value={config.ldap.baseDn}
                onChange={(e) => patchLdap("baseDn", e.target.value)}
              />
            </label>
            <label className="block text-sm">
              <span className="text-muted">Bind DN</span>
              <Input
                className="mt-1"
                value={config.ldap.bindDn}
                onChange={(e) => patchLdap("bindDn", e.target.value)}
              />
            </label>
            <label className="block text-sm">
              <span className="text-muted">Bind password</span>
              <Input
                className="mt-1"
                type="password"
                value={config.ldap.bindPassword}
                onChange={(e) => patchLdap("bindPassword", e.target.value)}
              />
            </label>
            <label className="block text-sm md:col-span-2">
              <span className="text-muted">User filter</span>
              <Input
                className="mt-1"
                value={config.ldap.userFilter}
                onChange={(e) => patchLdap("userFilter", e.target.value)}
              />
            </label>
            <div className="flex items-center justify-between md:col-span-2">
              <span className="text-sm">Use TLS</span>
              <Toggle
                checked={config.ldap.useTls}
                onChange={(v) => patchLdap("useTls", v)}
                aria-label="LDAP TLS"
              />
            </div>
          </div>
        </Card>
      )}
    </div>
  );
}
