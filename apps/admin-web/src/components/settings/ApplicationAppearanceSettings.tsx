"use client";

import { useCallback, useRef, useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { AppearanceThemeEditor } from "@/components/settings/appearance/AppearanceThemeEditor";
import { ClientThemePreview, WidgetColorFields } from "@/components/settings/appearance/appearance-shared";
import { Card, CardHeader } from "@/components/ui/Card";
import { FieldRow, Input, Select } from "@/components/ui/Field";
import { HOSTED_PAGE_LANGUAGES } from "@/lib/theme/types";
import { useToast } from "@/components/ui/Toast";
import { ensureApplicationPolicyScope } from "@/lib/api/ensure-application-policy";
import {
  fetchApplicationAppearance,
  saveApplicationAppearance,
} from "@/lib/api/application-settings";
import {
  mergeClientAppearance,
  normalizeClientAppearance,
  stripAppearanceMeta,
  toClientAppearancePayload,
} from "@/lib/theme/client-appearance";
import { DEFAULT_CLIENT_APPEARANCE, type ClientAppearance } from "@/lib/theme/types";

export function ApplicationAppearanceSettings({ applicationId }: { applicationId: string }) {
  const { toast } = useToast();
  const [appearance, setAppearance] = useState<ClientAppearance>(DEFAULT_CLIENT_APPEARANCE);
  const [loaded, setLoaded] = useState(false);
  const saveTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  // What the server holds now. A change equal to it (the theme editor normalising freshly loaded
  // values) is not saved; any real edit, including the very first, is.
  const lastSaved = useRef<string | null>(null);

  const reload = useCallback(async () => {
    setLoaded(false);
    try {
      const data = await fetchApplicationAppearance(applicationId).catch(() => ({}));
      const current = mergeClientAppearance(stripAppearanceMeta(data as Record<string, unknown>));
      lastSaved.current = JSON.stringify(toClientAppearancePayload(normalizeClientAppearance(current)));
      setAppearance(current);
    } catch (e) {
      toast(e instanceof Error ? e.message : "Failed to load appearance", "error");
    } finally {
      setLoaded(true);
    }
  }, [applicationId, toast]);

  useDeferredEffect(() => {
    reload();
  }, [reload]);

  const scheduleSave = useCallback(
    (next: ClientAppearance) => {
      if (!loaded) return;
      const payload = toClientAppearancePayload(next);
      if (saveTimer.current) clearTimeout(saveTimer.current);
      if (JSON.stringify(payload) === lastSaved.current) return;
      saveTimer.current = setTimeout(async () => {
        try {
          await ensureApplicationPolicyScope(applicationId, "appearance");
          const saved = await saveApplicationAppearance(applicationId, payload);
          const stored = mergeClientAppearance(stripAppearanceMeta(saved as Record<string, unknown>));
          lastSaved.current = JSON.stringify(toClientAppearancePayload(normalizeClientAppearance(stored)));
          setAppearance(stored);
          toast("Settings saved", "success");
        } catch (e) {
          toast(e instanceof Error ? e.message : "Save failed", "error");
        }
      }, 700);
    },
    [applicationId, loaded, toast],
  );

  const patch = useCallback(
    (partial: Partial<ClientAppearance>) => {
      setAppearance((prev) => {
        const next = normalizeClientAppearance({ ...prev, ...partial });
        scheduleSave(next);
        return next;
      });
    },
    [scheduleSave],
  );

  useDeferredEffect(() => {
    return () => {
      if (saveTimer.current) clearTimeout(saveTimer.current);
    };
  }, []);

  return (
    <div className={`space-y-6 ${!loaded ? "pointer-events-none opacity-60" : ""}`}>
      <div className="rounded-lg border border-ui bg-surface px-4 py-3 text-sm text-muted">
        <strong className="text-ui">Client application theme</strong> — published to embedded and mobile apps
        through the <strong>Public API</strong> when the Appearance section is enabled. This does not change the
        SecureOne admin console (configure that under Platform → Appearance).
      </div>

      <Card padded={false}>
        <CardHeader
          title="Application branding"
          description="Name, logo and language of SecureOne's sign-in pages for this application (also in the public manifest)."
        />
        <div className="grid grid-cols-1 gap-5 p-5 md:grid-cols-2">
          <FieldRow label="App display name">
            <Input
              value={appearance.appName}
              onChange={(e) => patch({ appName: e.target.value })}
            />
          </FieldRow>
          <FieldRow label="Logo URL">
            <Input
              value={appearance.logoUrl}
              onChange={(e) => patch({ logoUrl: e.target.value })}
              placeholder="https://…"
            />
          </FieldRow>
          <FieldRow label="Sign-in page language" hint="People can still pick another language on the page">
            <Select value={appearance.language} onChange={(e) => patch({ language: e.target.value })}>
              {HOSTED_PAGE_LANGUAGES.map((l) => (
                <option key={l.code} value={l.code}>
                  {l.name}
                </option>
              ))}
            </Select>
          </FieldRow>
        </div>
      </Card>

      <Card padded={false}>
        <CardHeader
          title="Theme & colors"
          description="Same structure as platform appearance — scoped to this application for client UIs."
        />
        <AppearanceThemeEditor
          prefs={appearance}
          setPrefs={(next) => {
            setAppearance((prev) => {
              const resolved =
                typeof next === "function"
                  ? next(prev)
                  : { ...prev, ...next };
              const normalized = normalizeClientAppearance(resolved);
              scheduleSave(normalized);
              return normalized;
            });
          }}
          ready={loaded}
          showNotifyPreviews={false}
        />
      </Card>

      <Card padded={false}>
        <CardHeader
          title="Dialogs, inputs & common widgets"
          description="Colors for modals, forms, and secondary UI in client applications."
        />
        <div className="grid grid-cols-1 gap-5 p-5 md:grid-cols-2">
          <WidgetColorFields
            widgets={appearance}
            onChange={(widgetPatch) => patch(widgetPatch)}
          />
        </div>
        <ClientThemePreview appearance={appearance} />
      </Card>
    </div>
  );
}
