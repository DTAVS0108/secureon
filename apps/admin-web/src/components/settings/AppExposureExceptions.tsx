"use client";

import { useCallback, useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { browserApiFetch as apiFetch } from "@/lib/api/browser-client";
import { listApplications } from "@/lib/api/applications";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { FieldRow, Select } from "@/components/ui/Field";
import { useToast } from "@/components/ui/Toast";
import type { ApplicationProduct } from "@/lib/types";

const SECTIONS: { key: string; label: string }[] = [
  { key: "notifications", label: "Notifications" },
  { key: "email", label: "Email sender" },
  { key: "auth-methods", label: "Sign-in methods" },
  { key: "password-policy", label: "Password policy" },
  { key: "feature-flags", label: "Feature flags" },
  { key: "token-policy", label: "OAuth tokens tab" },
  { key: "verification-policy", label: "Verification & OTP" },
  { key: "sms", label: "SMS provider" },
  { key: "appearance", label: "Appearance" },
  { key: "user-directory", label: "User directory" },
  { key: "public-manifest", label: "Public API" },
];

type Exceptions = Record<string, Record<string, boolean>>;

/**
 * Platform settings › For applications: sections open or closed for single applications, different from the
 * defaults above (for example Password policy closed to every app except one). Two Factor Authentication has no
 * exceptions: it has its own per-application switch.
 */
export function AppExposureExceptions({ defaults }: { defaults: Record<string, boolean> }) {
  const { toast } = useToast();
  const [apps, setApps] = useState<ApplicationProduct[]>([]);
  const [all, setAll] = useState<Exceptions>({});
  const [appId, setAppId] = useState("");
  const [draft, setDraft] = useState<Record<string, boolean>>({});
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    try {
      const [a, e] = await Promise.all([
        listApplications(),
        apiFetch<Exceptions>("/api/admin/v1/settings/app-exposure/exceptions"),
      ]);
      setApps(a);
      setAll(e ?? {});
    } catch (err) {
      toast(err instanceof Error ? err.message : "Could not load exceptions", "error");
    }
  }, [toast]);

  useDeferredEffect(() => {
    load();
  }, [load]);

  function choose(id: string) {
    setAppId(id);
    setDraft({ ...(all[id] ?? {}) });
  }

  async function save() {
    if (!appId) return;
    setSaving(true);
    try {
      const saved = await apiFetch<Exceptions>(`/api/admin/v1/settings/app-exposure/exceptions/${appId}`, {
        method: "PUT",
        body: JSON.stringify(draft),
      });
      setAll(saved ?? {});
      toast("Exceptions saved", "success");
    } catch (err) {
      toast(err instanceof Error ? err.message : "Save failed", "error");
    } finally {
      setSaving(false);
    }
  }

  const withExceptions = apps.filter((a) => all[a.id] && Object.keys(all[a.id]).length > 0);

  return (
    <Card padded={false} className="mt-6">
      <CardHeader
        title="Exceptions for single applications"
        description="Open or close a section for one application, different from the defaults above. Two Factor Authentication is set per application with its own switch."
      />
      <div className="space-y-4 px-5 pb-5">
        {withExceptions.length > 0 && (
          <p className="text-xs text-muted">
            With exceptions: {withExceptions.map((a) => `${a.name} (${Object.keys(all[a.id]).length})`).join(", ")}
          </p>
        )}
        <FieldRow label="Application">
          <Select value={appId} onChange={(e) => choose(e.target.value)}>
            <option value="">Choose an application…</option>
            {apps.map((a) => (
              <option key={a.id} value={a.id}>
                {a.name}
              </option>
            ))}
          </Select>
        </FieldRow>
        {appId && (
          <>
            <ul className="divide-y divide-ui rounded-xl border border-ui">
              {SECTIONS.map((s) => {
                const value = draft[s.key];
                const current = value === undefined ? "default" : value ? "open" : "closed";
                return (
                  <li key={s.key} className="flex items-center justify-between gap-4 px-4 py-2 text-sm">
                    <span className="text-ui">{s.label}</span>
                    <Select
                      className="max-w-56"
                      value={current}
                      onChange={(e) => {
                        const next = { ...draft };
                        if (e.target.value === "default") delete next[s.key];
                        else next[s.key] = e.target.value === "open";
                        setDraft(next);
                      }}
                      aria-label={s.label}
                    >
                      <option value="default">Default ({defaults[s.key] ? "open" : "closed"})</option>
                      <option value="open">Open for this app</option>
                      <option value="closed">Closed for this app</option>
                    </Select>
                  </li>
                );
              })}
            </ul>
            <div className="flex justify-end">
              <Button onClick={save} disabled={saving}>
                {saving ? "Saving…" : "Save exceptions"}
              </Button>
            </div>
          </>
        )}
      </div>
    </Card>
  );
}
