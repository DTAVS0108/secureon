"use client";

import { useCallback, useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { CopyValue } from "@/components/ui/CopyValue";
import { FieldRow, Input, Select } from "@/components/ui/Field";
import { Toggle } from "@/components/ui/Toggle";
import { useToast } from "@/components/ui/Toast";
import { getApplication } from "@/lib/api/applications";
import {
  createApplicationFederationProvider,
  createTenantFederationProvider,
  deleteApplicationFederationProvider,
  deleteTenantFederationProvider,
  listApplicationFederationProviders,
  listTenantFederationProviders,
  updateApplicationFederationProvider,
  updateTenantFederationProvider,
  type FederationProvider,
  type FederationProviderInput,
  type FederationProviderType,
} from "@/lib/api/federation";

const TYPE_OPTIONS: { value: FederationProviderType; label: string }[] = [
  { value: "GOOGLE", label: "Google OIDC" },
  { value: "GITHUB", label: "GitHub OAuth" },
  { value: "OIDC", label: "External OIDC" },
];

function defaultAlias(type: FederationProviderType): string {
  return type === "GOOGLE" ? "google" : type === "GITHUB" ? "github" : "oidc";
}

export function FederationProvidersPanel({
  applicationId,
  tenantId,
}: {
  applicationId?: string;
  tenantId?: string | null;
}) {
  const { toast } = useToast();
  const [providers, setProviders] = useState<FederationProvider[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [resolvedTenantId, setResolvedTenantId] = useState<string | null>(tenantId ?? null);
  const [creating, setCreating] = useState(false);
  const [draft, setDraft] = useState<FederationProviderInput>({
    alias: "google",
    type: "GOOGLE",
    enabled: false,
    clientId: "",
    clientSecret: "",
  });

  const reload = useCallback(async () => {
    setLoaded(false);
    try {
      let tid = tenantId ?? null;
      if (applicationId) {
        const app = await getApplication(applicationId);
        tid = app?.tenantId ?? tid;
        setResolvedTenantId(tid);
        const rows = await listApplicationFederationProviders(applicationId);
        setProviders(rows);
      } else if (tid) {
        setResolvedTenantId(tid);
        setProviders(await listTenantFederationProviders(tid));
      } else {
        setProviders([]);
      }
    } catch (e) {
      toast(e instanceof Error ? e.message : "Failed to load federation providers", "error");
    } finally {
      setLoaded(true);
    }
  }, [applicationId, tenantId, toast]);

  useDeferredEffect(() => {
    reload();
  }, [reload]);

  async function createProvider() {
    if (!draft.alias.trim() || !draft.clientId?.trim()) {
      toast("Alias and client ID are required.", "error");
      return;
    }
    setCreating(true);
    try {
      if (applicationId) {
        await createApplicationFederationProvider(applicationId, draft);
      } else if (resolvedTenantId) {
        await createTenantFederationProvider(resolvedTenantId, draft);
      } else {
        toast("Tenant context is required to configure federation.", "error");
        return;
      }
      toast("Provider created", "success");
      setDraft({
        alias: "google",
        type: "GOOGLE",
        enabled: false,
        clientId: "",
        clientSecret: "",
      });
      await reload();
    } catch (e) {
      toast(e instanceof Error ? e.message : "Create failed", "error");
    } finally {
      setCreating(false);
    }
  }

  async function toggleEnabled(provider: FederationProvider, enabled: boolean) {
    try {
      const body = { enabled };
      if (applicationId) {
        await updateApplicationFederationProvider(applicationId, provider.id, body);
      } else if (resolvedTenantId) {
        await updateTenantFederationProvider(resolvedTenantId, provider.id, body);
      }
      setProviders((prev) => prev.map((p) => (p.id === provider.id ? { ...p, enabled } : p)));
    } catch (e) {
      toast(e instanceof Error ? e.message : "Update failed", "error");
    }
  }

  async function removeProvider(provider: FederationProvider) {
    if (!confirm(`Remove federation provider "${provider.alias}"?`)) return;
    try {
      if (applicationId) {
        await deleteApplicationFederationProvider(applicationId, provider.id);
      } else if (resolvedTenantId) {
        await deleteTenantFederationProvider(resolvedTenantId, provider.id);
      }
      toast("Provider removed", "success");
      await reload();
    } catch (e) {
      toast(e instanceof Error ? e.message : "Delete failed", "error");
    }
  }

  if (!applicationId && !resolvedTenantId) {
    return (
      <p className="text-sm text-muted">
        Federation providers are configured per tenant or per application. Open an application&apos;s
        settings to add Google, GitHub, or OIDC sign-in.
      </p>
    );
  }

  return (
    <div className="space-y-6">
      <Card padded={false}>
        <CardHeader
          title="Configured providers"
          description={
            applicationId
              ? "Application-scoped IdPs override tenant defaults for this app."
              : "Tenant-wide IdPs available to all applications in this tenant."
          }
        />
        {!loaded ? (
          <p className="p-4 text-sm text-muted">Loading…</p>
        ) : providers.length === 0 ? (
          <p className="p-4 text-sm text-muted">No federation providers configured yet.</p>
        ) : (
          <ul className="divide-y divide-ui">
            {providers.map((p) => (
              <li key={p.id} className="space-y-2 px-4 py-4">
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <div>
                    <p className="text-sm font-medium text-ui">
                      {p.displayName || p.alias}{" "}
                      <span className="font-mono text-xs text-faint">({p.type})</span>
                    </p>
                    <p className="text-xs text-muted">
                      Alias <code className="font-mono">{p.alias}</code> · Client ID{" "}
                      {p.config.clientId || "—"}
                      {p.config.clientSecretConfigured ? " · secret configured" : " · no secret"}
                    </p>
                  </div>
                  <div className="flex items-center gap-3">
                    <Toggle
                      checked={p.enabled}
                      onChange={(enabled) => toggleEnabled(p, enabled)}
                      aria-label={`Enable ${p.alias}`}
                    />
                    <Button variant="ghost" size="sm" onClick={() => removeProvider(p)}>
                      Remove
                    </Button>
                  </div>
                </div>
                <div className="rounded-lg border border-ui bg-black/5 px-3 py-2 text-xs dark:bg-white/5">
                  <span className="text-muted">Callback URL</span>
                  <div className="mt-1 flex flex-wrap items-center gap-2">
                    <code className="break-all font-mono text-[11px]">{p.callbackUrl}</code>
                    <CopyValue value={p.callbackUrl} />
                  </div>
                </div>
              </li>
            ))}
          </ul>
        )}
      </Card>

      <Card padded={false}>
        <CardHeader title="Add provider" description="Register OAuth client credentials from Google Cloud or GitHub." />
        <div className="grid gap-4 p-4 md:grid-cols-2">
          <FieldRow label="Provider type">
            <Select
              value={draft.type}
              onChange={(e) => {
                const type = e.target.value as FederationProviderType;
                setDraft((prev) => ({ ...prev, type, alias: defaultAlias(type) }));
              }}
            >
              {TYPE_OPTIONS.map((opt) => (
                <option key={opt.value} value={opt.value}>
                  {opt.label}
                </option>
              ))}
            </Select>
          </FieldRow>
          <FieldRow label="Alias" hint="Used in login URLs, e.g. /federation/google/start">
            <Input
              value={draft.alias}
              onChange={(e) => setDraft((prev) => ({ ...prev, alias: e.target.value }))}
            />
          </FieldRow>
          <FieldRow label="Display name">
            <Input
              value={draft.displayName ?? ""}
              onChange={(e) => setDraft((prev) => ({ ...prev, displayName: e.target.value }))}
              placeholder="Continue with Google"
            />
          </FieldRow>
          <FieldRow label="OAuth client ID">
            <Input
              value={draft.clientId ?? ""}
              onChange={(e) => setDraft((prev) => ({ ...prev, clientId: e.target.value }))}
            />
          </FieldRow>
          <FieldRow label="OAuth client secret">
            <Input
              type="password"
              value={draft.clientSecret ?? ""}
              onChange={(e) => setDraft((prev) => ({ ...prev, clientSecret: e.target.value }))}
              autoComplete="off"
            />
          </FieldRow>
        </div>
        <div className="flex items-center justify-between border-t border-ui px-4 py-3">
          <label className="flex items-center gap-2 text-sm text-ui">
            <input
              type="checkbox"
              checked={Boolean(draft.enabled)}
              onChange={(e) => setDraft((prev) => ({ ...prev, enabled: e.target.checked }))}
            />
            Enable immediately
          </label>
          <Button onClick={createProvider} disabled={creating}>
            {creating ? "Adding…" : "Add provider"}
          </Button>
        </div>
      </Card>
    </div>
  );
}
