"use client";

import { useCallback, useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { FieldRow, Input } from "@/components/ui/Field";
import { Toggle } from "@/components/ui/Toggle";
import { useToast } from "@/components/ui/Toast";
import { publicAuthServerUrl } from "@/lib/config";
import { fetchScimSettings, saveScimSettings, type ScimSettings } from "@/lib/api/scim-settings";

export function ScimSettingsPanel() {
  const { toast } = useToast();
  const [settings, setSettings] = useState<ScimSettings | null>(null);
  const [bearerToken, setBearerToken] = useState("");
  const [saving, setSaving] = useState(false);

  const reload = useCallback(async () => {
    try {
      setSettings(await fetchScimSettings());
    } catch (e) {
      toast(e instanceof Error ? e.message : "Failed to load SCIM settings", "error");
    }
  }, [toast]);

  useDeferredEffect(() => {
    reload();
  }, [reload]);

  async function save() {
    if (!settings) return;
    setSaving(true);
    try {
      const next = await saveScimSettings({
        enabled: settings.enabled,
        userNameDomain: settings.userNameDomain,
        bearerToken: bearerToken.trim() || undefined,
      });
      setSettings(next);
      setBearerToken("");
      toast("SCIM settings saved", "success");
    } catch (e) {
      toast(e instanceof Error ? e.message : "Save failed", "error");
    } finally {
      setSaving(false);
    }
  }

  if (!settings) {
    return <p className="text-sm text-muted">Loading SCIM settings…</p>;
  }

  return (
    <Card padded={false}>
      <CardHeader
        title="SCIM 2.0 provisioning"
        description="Bearer-token API for user provisioning from your company's identity provider. Pair it with SAML or OIDC sign-in under Authentication → Identity federation."
      />
      <div className="space-y-4 p-4">
        <div className="flex items-center justify-between gap-4 rounded-lg border border-ui px-4 py-3">
          <div>
            <p className="text-sm font-medium text-ui">Enable SCIM</p>
            <p className="text-xs text-muted">When disabled, /scim/v2 endpoints return 404.</p>
          </div>
          <Toggle
            checked={settings.enabled}
            onChange={(enabled) => setSettings((prev) => (prev ? { ...prev, enabled } : prev))}
            aria-label="Enable SCIM"
          />
        </div>
        <FieldRow label="Bearer token" hint="Leave blank to keep the current token. Shown only on save.">
          <Input
            type="password"
            value={bearerToken}
            onChange={(e) => setBearerToken(e.target.value)}
            placeholder={settings.bearerTokenConfigured ? "•••••••• (configured)" : "Generate a long random secret"}
            autoComplete="off"
          />
        </FieldRow>
        <FieldRow label="Username domain suffix" hint="Optional hint for provisioned usernames">
          <Input
            value={settings.userNameDomain ?? ""}
            onChange={(e) =>
              setSettings((prev) => (prev ? { ...prev, userNameDomain: e.target.value } : prev))
            }
            placeholder="secureone.local"
          />
        </FieldRow>
        <div className="rounded-lg border border-ui bg-black/5 px-3 py-2 text-xs dark:bg-white/5">
          <p className="text-muted">Base URL</p>
          <code className="mt-1 block font-mono">{publicAuthServerUrl()}/scim/v2</code>
          <p className="mt-2 text-muted">Discovery</p>
          <code className="mt-1 block font-mono">{publicAuthServerUrl()}/scim/v2/ServiceProviderConfig</code>
        </div>
        <Button onClick={save} disabled={saving}>
          {saving ? "Saving…" : "Save SCIM settings"}
        </Button>
      </div>
    </Card>
  );
}
