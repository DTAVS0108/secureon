"use client";

import { useCallback, useRef, useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { Card, CardHeader } from "@/components/ui/Card";
import { Toggle } from "@/components/ui/Toggle";
import { useToast } from "@/components/ui/Toast";
import { ensureApplicationPolicyScope } from "@/lib/api/ensure-application-policy";
import {
  fetchUserDirectorySettings,
  saveUserDirectorySettings,
  type UserDirectorySettings,
} from "@/lib/api/user-directory";

/** Import sources offered in the Users page import menu. Directory sync (LDAP / AD) is not part of SecureOne. */
const IMPORT_SOURCES = ["csv", "excel"] as const;

const DEFAULTS: UserDirectorySettings = {
  importEnabled: false,
  exportEnabled: false,
  sources: {
    csv: { enabled: true },
    excel: { enabled: true },
  },
};

export function UserDirectorySettings({ applicationId }: { applicationId: string }) {
  const { toast } = useToast();
  const [config, setConfig] = useState<UserDirectorySettings>(DEFAULTS);
  const [loaded, setLoaded] = useState(false);
  const saveTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const reload = useCallback(async () => {
    setLoaded(false);
    try {
      const data = await fetchUserDirectorySettings(applicationId).catch(() => DEFAULTS);
      setConfig({ ...DEFAULTS, ...data, sources: { ...DEFAULTS.sources, ...data.sources } });
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
          await ensureApplicationPolicyScope(applicationId, "user-directory");
          const saved = await saveUserDirectorySettings(applicationId, next);
          setConfig({ ...DEFAULTS, ...saved, sources: { ...DEFAULTS.sources, ...saved.sources } });
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

  function patchSource(key: (typeof IMPORT_SOURCES)[number], enabled: boolean) {
    patch({ sources: { ...config.sources, [key]: { enabled } } });
  }

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
              <p className="text-xs text-muted">CSV and Excel (CSV format) files.</p>
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
          {IMPORT_SOURCES.map((key) => (
            <li key={key} className="flex items-center justify-between gap-4 px-5 py-4">
              <p className="text-sm font-medium uppercase text-ui">{key}</p>
              <Toggle
                checked={config.sources[key]?.enabled !== false}
                onChange={(v) => patchSource(key, v)}
                aria-label={`Enable ${key} import`}
              />
            </li>
          ))}
        </ul>
      </Card>
    </div>
  );
}
