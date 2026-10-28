"use client";

import { useAppFlags } from "@/hooks/useAppFlags";
import { useDialog } from "@/components/ui/Dialogs";
import { APP_FLAG } from "@/lib/app-feature-flags";
import { useCallback, useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { CopyValue } from "@/components/ui/CopyValue";
import { FieldRow, Input, Select, Textarea } from "@/components/ui/Field";
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
  { value: "SAML", label: "SAML 2.0 (enterprise SSO)" },
];

function defaultAlias(type: FederationProviderType): string {
  return type === "GOOGLE" ? "google" : type === "GITHUB" ? "github" : type === "SAML" ? "sso" : "oidc";
}

const EMPTY_DRAFT: FederationProviderInput = {
  alias: "google",
  type: "GOOGLE",
  enabled: false,
  clientId: "",
  clientSecret: "",
  config: {},
};

function configText(draft: FederationProviderInput, key: string): string {
  const value = draft.config?.[key];
  return typeof value === "string" ? value : "";
}

/** What is missing before this draft can be saved, or null. */
function draftProblem(draft: FederationProviderInput): string | null {
  if (!draft.alias.trim()) return "Alias is required.";
  if (draft.type === "SAML") {
    const manual = ["idpEntityId", "ssoUrl", "idpCertificate"].every((k) => configText(draft, k).trim());
    return configText(draft, "metadataUrl").trim() || manual
      ? null
      : "Enter the IdP metadata URL, or its entity ID, SSO URL and signing certificate.";
  }
  if (!draft.clientId?.trim()) return "Client ID is required.";
  if (draft.type === "OIDC") {
    const missing = ["authorizationUri", "tokenUri", "userInfoUri"].some((k) => !configText(draft, k).trim());
    if (missing) return "Authorization, token and user info URLs are required for an OIDC provider.";
  }
  return null;
}

export function FederationProvidersPanel({
  applicationId,
  tenantId,
}: {
  applicationId?: string;
  tenantId?: string | null;
}) {
  // Applications can hide "Add provider" with the federation_add_provider feature flag.
  const flags = useAppFlags();
  const canAdd = !applicationId || flags.on(APP_FLAG.federationAddProvider);
  const { toast } = useToast();
  const [providers, setProviders] = useState<FederationProvider[]>([]);
  const dialog = useDialog();
  const [loaded, setLoaded] = useState(false);
  const [resolvedTenantId, setResolvedTenantId] = useState<string | null>(tenantId ?? null);
  const [creating, setCreating] = useState(false);
  const [draft, setDraft] = useState<FederationProviderInput>(EMPTY_DRAFT);
  const setConfig = (key: string, value: string) =>
    setDraft((prev) => ({ ...prev, config: { ...prev.config, [key]: value } }));

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
    const problem = draftProblem(draft);
    if (problem) {
      toast(problem, "error");
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
      setDraft(EMPTY_DRAFT);
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
    if (
      !(await dialog.confirm({
        title: `Remove the "${provider.alias}" sign-in provider?`,
        message: "Users can no longer sign in with it.",
        confirmLabel: "Remove provider",
      }))
    )
      return;
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
        settings to add Google, GitHub, OIDC or SAML sign-in.
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
                      Alias <code className="font-mono">{p.alias}</code> ·{" "}
                      {p.type === "SAML" ? (
                        <>IdP {p.config.metadataUrl || p.config.idpEntityId || "—"}</>
                      ) : (
                        <>
                          Client ID {p.config.clientId || "—"}
                          {p.config.clientSecretConfigured ? " · secret configured" : " · no secret"}
                        </>
                      )}
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
                <div className="space-y-2 rounded-lg border border-ui bg-black/5 px-3 py-2 text-xs dark:bg-white/5">
                  {(p.serviceProvider
                    ? [
                        ["SP entity ID (audience)", p.serviceProvider.entityId],
                        ["ACS URL (HTTP-POST)", p.serviceProvider.acsUrl],
                        ...(p.serviceProvider.sloUrl ? [["Single logout URL", p.serviceProvider.sloUrl]] : []),
                        ["SP metadata", p.serviceProvider.metadataUrl],
                      ]
                    : [["Callback URL", p.callbackUrl ?? ""]]
                  ).map(([label, value]) => (
                    <div key={label}>
                      <span className="text-muted">{label}</span>
                      <div className="mt-1 flex flex-wrap items-center gap-2">
                        <code className="break-all font-mono text-[11px]">{value}</code>
                        <CopyValue value={value} />
                      </div>
                    </div>
                  ))}
                </div>
              </li>
            ))}
          </ul>
        )}
      </Card>

      {canAdd && (
      <Card padded={false}>
        <CardHeader
          title="Add provider"
          description={
            draft.type === "SAML"
              ? "Point at your company's SAML 2.0 identity provider. After saving, give the IdP the SP entity ID and ACS URL shown above."
              : "Register OAuth client credentials from Google Cloud, GitHub or your OIDC provider."
          }
        />
        <div className="grid gap-4 p-4 md:grid-cols-2">
          <FieldRow label="Provider type">
            <Select
              value={draft.type}
              onChange={(e) => {
                const type = e.target.value as FederationProviderType;
                setDraft((prev) => ({ ...prev, type, alias: defaultAlias(type), config: {} }));
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
              placeholder="Button label"
            />
          </FieldRow>
          {draft.type === "SAML" ? (
            <>
              <FieldRow label="IdP metadata URL" hint="Preferred: keeps certificates current. Or fill the fields below.">
                <Input
                  value={configText(draft, "metadataUrl")}
                  onChange={(e) => setConfig("metadataUrl", e.target.value)}
                  placeholder="Metadata URL"
                />
              </FieldRow>
              <FieldRow label="IdP entity ID">
                <Input value={configText(draft, "idpEntityId")} onChange={(e) => setConfig("idpEntityId", e.target.value)} />
              </FieldRow>
              <FieldRow label="IdP SSO URL (HTTP-Redirect)">
                <Input value={configText(draft, "ssoUrl")} onChange={(e) => setConfig("ssoUrl", e.target.value)} />
              </FieldRow>
              <FieldRow label="IdP single logout URL" hint="Optional with manual setup; read from metadata otherwise.">
                <Input value={configText(draft, "sloUrl")} onChange={(e) => setConfig("sloUrl", e.target.value)} />
              </FieldRow>
              <FieldRow
                label="Landing URL for IdP-initiated sign-in"
                hint="Where users go when they start from the IdP's app portal. Leave empty to only allow sign-in started from the app."
              >
                <Input
                  value={configText(draft, "landingUrl")}
                  onChange={(e) => {
                    const value = e.target.value;
                    setDraft((prev) => ({
                      ...prev,
                      config: { ...prev.config, landingUrl: value, allowIdpInitiated: value.trim().length > 0 },
                    }));
                  }}
                  placeholder="Landing URL"
                />
              </FieldRow>
              <FieldRow label="Email attribute" hint="Optional. Defaults to email/mail attributes, then an email NameID.">
                <Input
                  value={configText(draft, "emailAttribute")}
                  onChange={(e) => setConfig("emailAttribute", e.target.value)}
                />
              </FieldRow>
              <div className="md:col-span-2">
                <FieldRow label="IdP signing certificate (PEM)">
                  <Textarea
                    rows={4}
                    value={configText(draft, "idpCertificate")}
                    onChange={(e) => setConfig("idpCertificate", e.target.value)}
                    placeholder="-----BEGIN CERTIFICATE-----"
                  />
                </FieldRow>
              </div>
            </>
          ) : (
            <>
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
              {draft.type === "OIDC" &&
                (
                  [
                    ["authorizationUri", "Authorization URL"],
                    ["tokenUri", "Token URL"],
                    ["userInfoUri", "User info URL"],
                  ] as const
                ).map(([key, label]) => (
                  <FieldRow key={key} label={label}>
                    <Input value={configText(draft, key)} onChange={(e) => setConfig(key, e.target.value)} />
                  </FieldRow>
                ))}
            </>
          )}
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
      )}
    </div>
  );
}
