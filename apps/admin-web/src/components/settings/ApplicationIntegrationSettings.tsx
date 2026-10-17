"use client";

import { useAppFlags } from "@/hooks/useAppFlags";
import { APP_FLAG } from "@/lib/app-feature-flags";
import Link from "next/link";
import { useCallback, useRef, useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { CopyValue } from "@/components/ui/CopyValue";
import { FieldRow, Input, Textarea } from "@/components/ui/Field";
import { useToast } from "@/components/ui/Toast";
import { getApplication } from "@/lib/api/applications";
import { listOAuthClients } from "@/lib/api/oauth-clients";
import {
  fetchClientIntegration,
  saveClientIntegration,
  type ClientIntegrationConfig,
  type IntegrationChecklist,
} from "@/lib/api/application-settings";
import { runIntegrationHealthChecks, type IntegrationHealthResult } from "@/lib/api/integration-health";
import { ApiReferenceList } from "@/components/settings/ApiReferenceList";
import { IsolateApplicationButton } from "@/components/applications/IsolateApplicationButton";
import { useAdminContext } from "@/components/AdminContextProvider";
import { publicAuthServerUrl } from "@/lib/config";
import { buildIntegrationApiCatalog } from "@/lib/integration-api-catalog";
import type { ApplicationProduct, OAuthClient } from "@/lib/types";
import type { SettingsTabId, AuthSubTabId } from "@/components/settings/SettingsTabs";

const DEFAULT_CONFIG: ClientIntegrationConfig = {
  authUiMode: "hosted",
  checklist: {
    redirectUris: false,
    publicManifest: false,
    smtpConfigured: false,
    smsConfigured: false,
    authMethodsReviewed: false,
    signupReviewed: false,
    passwordResetTested: false,
    recoveryOtpTested: false,
    loginFlowTested: false,
    oauthPkceTested: false,
    mfaStepUpTested: false,
    sessionRevokeTested: false,
    magicLinkTested: false,
    federationTested: false,
    passkeyTested: false,
  },
  notes: "",
};

const CHECKLIST_META: {
  key: keyof IntegrationChecklist;
  label: string;
  hint: string;
  tab?: SettingsTabId;
}[] = [
  {
    key: "redirectUris",
    label: "Redirect URIs configured",
    hint: "OAuth client redirect and post-logout URIs match your app URLs.",
  },
  {
    key: "publicManifest",
    label: "Public API manifest enabled",
    hint: "Expose auth methods and branding to your client without admin credentials.",
    tab: "public-api",
  },
  {
    key: "smtpConfigured",
    label: "SMTP / notifications ready",
    hint: "Email verification, password reset, and invites require outbound mail.",
    tab: "notifications",
  },
  {
    key: "smsConfigured",
    label: "SMS provider configured",
    hint: "SMS OTP login and signup require a provider (or log mode in dev).",
    tab: "verification",
  },
  {
    key: "authMethodsReviewed",
    label: "Authentication methods reviewed",
    hint: "Password, MFA, and social providers match your product requirements.",
    tab: "auth",
  },
  {
    key: "signupReviewed",
    label: "Signup flow reviewed",
    hint: "Self-registration fields, default role, and email verification.",
    tab: "users",
  },
  {
    key: "passwordResetTested",
    label: "Password reset tested",
    hint: "Forgot-password email delivers and reset link works end-to-end.",
    tab: "verification",
  },
  {
    key: "recoveryOtpTested",
    label: "Recovery OTP tested",
    hint: "Forgot-password OTP via email or SMS completes with new password.",
    tab: "verification",
  },
  {
    key: "loginFlowTested",
    label: "Login flow tested",
    hint: "Hosted redirect or native session login succeeds with a test user.",
  },
  {
    key: "oauthPkceTested",
    label: "OAuth PKCE tested",
    hint: "Authorization code + PKCE token exchange returns access and refresh tokens.",
  },
  {
    key: "mfaStepUpTested",
    label: "MFA step-up tested",
    hint: "Password login triggers TOTP step-up when policy requires MFA.",
    tab: "auth",
  },
  {
    key: "sessionRevokeTested",
    label: "Session revoke tested",
    hint: "GET/DELETE /api/v1/account/sessions lists and revokes browser sessions.",
  },
  {
    key: "magicLinkTested",
    label: "Magic link tested",
    hint: "Magic link email delivers and completes sign-in.",
    tab: "auth",
  },
  {
    key: "federationTested",
    label: "Federation sign-in tested",
    hint: "Google, GitHub, or OIDC provider completes hosted login.",
    tab: "auth",
  },
  {
    key: "passkeyTested",
    label: "Passkey sign-in tested",
    hint: "WebAuthn registration and passwordless login succeed in a supported browser.",
    tab: "auth",
  },
];

export function ApplicationIntegrationSettings({
  applicationId,
  onOpenTab,
}: {
  applicationId: string;
  onOpenTab?: (tab: SettingsTabId, authSubTab?: AuthSubTabId) => void;
}) {
  const { toast } = useToast();
  const adminContext = useAdminContext();
  // Checklist, API reference and health check each have a feature flag (off by default).
  const flags = useAppFlags();
  const [config, setConfig] = useState<ClientIntegrationConfig>(DEFAULT_CONFIG);
  const [app, setApp] = useState<ApplicationProduct | null>(null);
  const [oauthClients, setOauthClients] = useState<OAuthClient[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [health, setHealth] = useState<IntegrationHealthResult[] | null>(null);
  const [checking, setChecking] = useState(false);
  const saveTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const scheduleSave = useCallback(
    (next: ClientIntegrationConfig) => {
      if (saveTimer.current) clearTimeout(saveTimer.current);
      saveTimer.current = setTimeout(async () => {
        try {
          await saveClientIntegration(applicationId, next);
        } catch (e) {
          toast(e instanceof Error ? e.message : "Failed to save integration settings", "error");
        }
      }, 600);
    },
    [applicationId, toast],
  );

  const reload = useCallback(async () => {
    setLoaded(false);
    try {
      const [integration, application, clients] = await Promise.all([
        fetchClientIntegration(applicationId),
        getApplication(applicationId),
        listOAuthClients({ applicationId }),
      ]);
      setConfig({
        ...DEFAULT_CONFIG,
        ...integration,
        clientUrls: { ...DEFAULT_CONFIG.clientUrls, ...integration.clientUrls },
        checklist: { ...DEFAULT_CONFIG.checklist, ...integration.checklist },
      });
      setApp(application);
      setOauthClients(clients);
    } catch (e) {
      toast(e instanceof Error ? e.message : "Failed to load integration settings", "error");
    } finally {
      setLoaded(true);
    }
  }, [applicationId, toast]);

  useDeferredEffect(() => {
    reload();
    return () => {
      if (saveTimer.current) clearTimeout(saveTimer.current);
    };
  }, [reload]);

  function updateConfig(patch: Partial<ClientIntegrationConfig>) {
    setConfig((prev) => {
      const next = { ...prev, ...patch };
      scheduleSave(next);
      return next;
    });
  }

  function toggleChecklist(key: keyof IntegrationChecklist) {
    setConfig((prev) => {
      const next = {
        ...prev,
        checklist: { ...prev.checklist, [key]: !prev.checklist[key] },
      };
      scheduleSave(next);
      return next;
    });
  }

  async function runHealthCheck() {
    setChecking(true);
    setHealth(null);
    try {
      const results = await runIntegrationHealthChecks(applicationId);
      setHealth(results);
      const failed = results.filter((r) => !r.ok);
      if (failed.length === 0) {
        toast("All API probes succeeded.", "success");
      } else {
        toast(`${failed.length} probe(s) failed — see details below.`, "error");
      }
    } catch (e) {
      toast(e instanceof Error ? e.message : "Health check failed", "error");
    } finally {
      setChecking(false);
    }
  }

  const catalog = buildIntegrationApiCatalog({
    applicationId,
    clientId: oauthClients[0]?.clientId,
  });
  const filteredCatalog = catalog.filter(
    (e) =>
      !e.authUiMode ||
      e.authUiMode === "both" ||
      e.authUiMode === config.authUiMode,
  );

  if (!loaded) {
    return <p className="text-sm text-muted">Loading integration guide…</p>;
  }

  return (
    <div className="space-y-6">
      <Card padded={false}>
        <CardHeader
          title="Database schema"
          action={
            adminContext.platformSuperAdmin && app ? (
              <IsolateApplicationButton application={app} />
            ) : undefined
          }
        />
        <div className="p-4">
          <p className="text-sm text-muted">
            {app?.schemaName
              ? `IAM data for this application lives in the dedicated PostgreSQL schema ${app.schemaName}.`
              : "Legacy layout: IAM data still shares the platform schema. Platform super-admins can isolate it into a dedicated schema."}
          </p>
        </div>
      </Card>

      <Card padded={false}>
        <CardHeader title="Authentication UI" />
        <div className="space-y-4 p-4">
          <p className="text-sm text-muted">
            Choose how end users sign in. The choice is published in the public manifest as{" "}
            <code className="text-xs">authUiMode</code>, so your app can read it instead of a build flag
            (e.g. <code className="text-xs">VITE_AUTH_MODE</code>). Both flows stay available; this
            only sets the recommended one and filters the API reference below.
          </p>
          <div className="grid gap-3 sm:grid-cols-2">
            {(
              [
                {
                  id: "hosted" as const,
                  title: "Hosted SecureOne UI",
                  description:
                    "Redirect to /oauth2/authorize. SecureOne renders login, MFA, and consent. Best for quick integration.",
                },
                {
                  id: "native" as const,
                  title: "Native / custom UI",
                  description:
                    "Build your own login screens. Use session login + PKCE token exchange, or embed forms that call account APIs.",
                },
              ] as const
            ).map((mode) => (
              <button
                key={mode.id}
                type="button"
                onClick={() => updateConfig({ authUiMode: mode.id })}
                className={`rounded-lg border p-4 text-left transition-colors ${
                  config.authUiMode === mode.id
                    ? "border-brand bg-brand/5"
                    : "border-ui bg-surface hover:border-ui-strong"
                }`}
              >
                <p className="font-medium text-ui">{mode.title}</p>
                <p className="mt-1 text-xs text-muted">{mode.description}</p>
              </button>
            ))}
          </div>
          {app && (
            <div className="rounded-lg border border-ui bg-black/5 px-3 py-2 dark:bg-white/5">
              <p className="text-xs text-muted">OAuth client ID</p>
              <div className="mt-1 flex flex-wrap items-center gap-2">
                {oauthClients[0] ? (
                  <>
                    <code className="font-mono text-sm">{oauthClients[0].clientId}</code>
                    <CopyValue value={oauthClients[0].clientId} />
                  </>
                ) : (
                  <span className="text-sm text-muted">
                    No OAuth client registered —{" "}
                    <Link href="/applications" className="text-brand hover:underline">
                      register one
                    </Link>
                  </span>
                )}
              </div>
              <p className="mt-2 text-xs text-muted">Application UUID</p>
              <div className="mt-1 flex flex-wrap items-center gap-2">
                <code className="font-mono text-xs">{app.id}</code>
                <CopyValue value={app.id} />
              </div>
            </div>
          )}
        </div>
      </Card>

      {config.authUiMode === "native" && (
        <Card padded={false}>
          <CardHeader title="Native app URLs" />
          <div className="space-y-4 p-4">
            <p className="text-sm text-muted">
              Password reset emails include a one-time token in the link. Point these URLs at your
              storefront pages so users land on your UI (not SecureOne&apos;s hosted HTML).
            </p>
            <FieldRow
              label="Forgot password page"
              hint="Where users request a reset email (your app calls POST /api/v1/account/password/forgot)"
            >
              <Input
                value={config.clientUrls?.forgotPassword ?? ""}
                onChange={(e) =>
                  updateConfig({
                    clientUrls: { ...config.clientUrls, forgotPassword: e.target.value },
                  })
                }
                placeholder="http://localhost:5173/forgot-password"
              />
            </FieldRow>
            <FieldRow
              label="Reset password page"
              hint="Base URL for the email link; SecureOne appends ?token=… automatically"
            >
              <Input
                value={config.clientUrls?.passwordReset ?? ""}
                onChange={(e) =>
                  updateConfig({
                    clientUrls: { ...config.clientUrls, passwordReset: e.target.value },
                  })
                }
                placeholder="http://localhost:5173/reset-password"
              />
            </FieldRow>
            <FieldRow
              label="Magic link landing page"
              hint="Base URL for magic-link emails; SecureOne appends ?token=…&applicationId=…"
            >
              <Input
                value={config.clientUrls?.magicLink ?? ""}
                onChange={(e) =>
                  updateConfig({
                    clientUrls: { ...config.clientUrls, magicLink: e.target.value },
                  })
                }
                placeholder="http://localhost:5173/magic-link"
              />
            </FieldRow>
          </div>
        </Card>
      )}

      {flags.on(APP_FLAG.integrationChecklist) && (
      <Card padded={false}>
        <CardHeader
          title="Integration checklist"
          action={
            <span className="text-xs text-muted">
              {Object.values(config.checklist).filter(Boolean).length} / {CHECKLIST_META.length} done
            </span>
          }
        />
        <ul className="divide-y divide-ui">
          {CHECKLIST_META.map((item) => (
            <li key={item.key} className="flex items-start gap-3 px-4 py-3">
              <input
                type="checkbox"
                checked={config.checklist[item.key]}
                onChange={() => toggleChecklist(item.key)}
                className="mt-1"
              />
              <div className="min-w-0 flex-1">
                <p className="text-sm font-medium text-ui">{item.label}</p>
                <p className="text-xs text-muted">{item.hint}</p>
                {item.tab && onOpenTab && (
                  <button
                    type="button"
                    onClick={() => onOpenTab(item.tab!)}
                    className="mt-1 text-xs text-brand hover:underline"
                  >
                    Open settings →
                  </button>
                )}
              </div>
            </li>
          ))}
        </ul>
        <div className="border-t border-ui p-4">
          <label className="mb-1 block text-xs font-medium text-muted">Notes</label>
          <Textarea
            rows={3}
            value={config.notes}
            onChange={(e) => updateConfig({ notes: e.target.value })}
            placeholder="Environment URLs, test accounts, rollout notes…"
          />
        </div>
      </Card>
      )}

      {flags.on(APP_FLAG.apiReference) && (
      <Card padded={false}>
        <CardHeader
          title="API reference"
          action={
            <Link
              href={`${publicAuthServerUrl()}/docs`}
              target="_blank"
              rel="noopener noreferrer"
              className="text-xs text-brand hover:underline"
            >
              OpenAPI docs →
            </Link>
          }
        />
        <p className="border-b border-ui px-4 py-2 text-xs text-muted">
          Click an endpoint to expand request fields, types, examples, and response shapes.
        </p>
        <ApiReferenceList endpoints={filteredCatalog} applicationId={applicationId} />
      </Card>
      )}

      {flags.on(APP_FLAG.apiHealthCheck) && (
      <Card padded={false}>
        <CardHeader
          title="API health check"
          action={
            <Button variant="secondary" size="sm" onClick={runHealthCheck} disabled={checking}>
              {checking ? "Checking…" : "Run checks"}
            </Button>
          }
        />
        <div className="space-y-3 p-4">
          <p className="text-sm text-muted">
            Probes public application-scoped endpoints (no tenant slug required). Forgot-password expects a
            generic success even for unknown emails when self-service recovery is enabled.
          </p>
          {health && (
            <ul className="space-y-2">
              {health.map((r) => (
                <li
                  key={r.id}
                  className={`rounded-lg border px-3 py-2 text-sm ${
                    r.ok
                      ? "border-emerald-500/30 bg-emerald-500/10"
                      : "border-red-500/30 bg-red-500/10"
                  }`}
                >
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <span className="font-medium">{r.label}</span>
                    <span className="font-mono text-xs">
                      {r.status > 0 ? `HTTP ${r.status}` : "Network error"}
                    </span>
                  </div>
                  <p className="mt-0.5 text-xs text-muted">{r.detail}</p>
                </li>
              ))}
            </ul>
          )}
        </div>
      </Card>
      )}
    </div>
  );
}
