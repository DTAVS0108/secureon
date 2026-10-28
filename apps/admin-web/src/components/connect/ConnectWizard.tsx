"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { CopyValue } from "@/components/ui/CopyValue";
import { FieldRow, Input, Select } from "@/components/ui/Field";
import { Toggle } from "@/components/ui/Toggle";
import { connectAppAction, type ConnectResult } from "@/lib/actions/connect";
import {
  KINDS,
  defaultAppScheme,
  frameworksFor,
  joinUrl,
  kindInfo,
  slugify,
  testRedirectUri,
  type ConnectKind,
} from "@/lib/connect/catalog";
import { setupFor, type SnippetValues } from "@/lib/connect/snippets";
import { buildSetupMarkdown, downloadTextFile } from "@/lib/connect/setup-file";
import { CodeBlock } from "./CodeBlock";
import { publicAuthServerUrl } from "@/lib/config";
import { cn } from "@/lib/cn";
import type { ApplicationProduct, Tenant } from "@/lib/types";

type Created = Extract<ConnectResult, { ok: true }>;

const KIND_SUFFIX: Record<ConnectKind, string> = {
  web: "web",
  spa: "spa",
  native: "mobile",
  desktop: "desktop",
  service: "service",
  device: "device",
  api: "api",
};

export function ConnectWizard({
  tenants,
  applications,
  initialApplicationId,
  canCreateApplications = true,
}: {
  tenants: Tenant[];
  applications: ApplicationProduct[];
  initialApplicationId?: string;
  /** Platform operators can register a new application; tenant operators pick one they manage. */
  canCreateApplications?: boolean;
}) {
  const issuer = publicAuthServerUrl();
  const initialApp =
    applications.find((a) => a.id === initialApplicationId) ?? (canCreateApplications ? undefined : applications[0]);

  const [kind, setKind] = useState<ConnectKind | null>(null);
  const [framework, setFramework] = useState("");
  const [tenantId, setTenantId] = useState(initialApp?.tenantId ?? tenants.find((t) => !t.system)?.id ?? "");
  const [applicationId, setApplicationId] = useState(initialApp?.id ?? "");
  const [applicationName, setApplicationName] = useState("");
  const [clientId, setClientId] = useState("");
  const [clientIdEdited, setClientIdEdited] = useState(false);
  const [appUrl, setAppUrl] = useState("");
  const [redirectUri, setRedirectUri] = useState("");
  const [postLogoutUri, setPostLogoutUri] = useState("");
  const [urlsEdited, setUrlsEdited] = useState(false);
  const [appScheme, setAppScheme] = useState("");
  const [audiences, setAudiences] = useState("");
  const [includeTest, setIncludeTest] = useState(true);
  // The secret goes into the downloaded setup file only when asked for (it is shown only once).
  const [secretInFile, setSecretInFile] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [created, setCreated] = useState<Created | null>(null);

  const info = kind ? kindInfo(kind) : null;
  const frameworkLabel = kind ? (frameworksFor(kind).find((f) => f.id === framework)?.label ?? framework) : framework;
  const tenantApps = applications.filter((a) => a.tenantId === tenantId);
  const appLabel = applicationId ? tenantApps.find((a) => a.id === applicationId)?.name ?? "" : applicationName;
  const fw = kind ? frameworksFor(kind).find((f) => f.id === framework) : undefined;

  function chooseKind(next: ConnectKind) {
    setKind(next);
    setError(null);
    const first = frameworksFor(next)[0];
    chooseFramework(next, first.id, true);
  }

  function chooseFramework(k: ConnectKind, id: string, force = false) {
    setFramework(id);
    const f = frameworksFor(k).find((x) => x.id === id);
    if (k === "native") {
      const scheme = appScheme || defaultAppScheme(appLabel || "app");
      setAppScheme(scheme);
      if (force || !urlsEdited) {
        setRedirectUri(`${scheme}:/oauthredirect`);
        setPostLogoutUri(`${scheme}:/signout`);
      }
    } else if (f?.appUrl && (force || !urlsEdited)) {
      setAppUrl(f.appUrl);
      setRedirectUri(joinUrl(f.appUrl, f.callbackPath));
      setPostLogoutUri(joinUrl(f.appUrl, f.logoutPath));
      setUrlsEdited(false);
    }
    if (!clientIdEdited) setClientId(suggestClientId(appLabel, k));
  }

  function changeAppUrl(value: string) {
    setAppUrl(value);
    if (!urlsEdited && fw) {
      setRedirectUri(joinUrl(value, fw.callbackPath));
      setPostLogoutUri(joinUrl(value, fw.logoutPath));
    }
  }

  function changeAppName(value: string) {
    setApplicationName(value);
    if (!clientIdEdited && kind) setClientId(suggestClientId(value, kind));
    if (kind === "native" && !urlsEdited) {
      const scheme = defaultAppScheme(value || "app");
      setAppScheme(scheme);
      setRedirectUri(`${scheme}:/oauthredirect`);
      setPostLogoutUri(`${scheme}:/signout`);
    }
  }

  function changeExistingApp(id: string) {
    setApplicationId(id);
    const name = tenantApps.find((a) => a.id === id)?.name ?? applicationName;
    if (!clientIdEdited && kind) setClientId(suggestClientId(name, kind));
  }

  async function create() {
    if (!kind) return;
    setBusy(true);
    setError(null);
    const redirects = info?.redirects ? [redirectUri.trim()] : [];
    if (info?.redirects && includeTest) redirects.push(testRedirectUri(issuer));
    const result = await connectAppAction({
      kind,
      tenantId,
      applicationId: applicationId || undefined,
      applicationName,
      clientId,
      redirectUris: redirects,
      postLogoutRedirectUris: info?.redirects ? [postLogoutUri.trim()] : [],
      framework,
    });
    setBusy(false);
    if (result.ok) setCreated(result);
    else setError(result.error);
  }

  const values: SnippetValues = useMemo(
    () => ({
      issuer,
      clientId: created?.clientId ?? (clientId || "<client id>"),
      appUrl: appUrl || "http://localhost:3000",
      redirectUri: redirectUri || "http://localhost:3000/callback",
      postLogoutUri: postLogoutUri || "http://localhost:3000/",
      appScheme: appScheme || "com.example.app",
      scopes: info?.client?.scopes ?? [],
      audiences: audiences.split(/[\s,]+/).filter(Boolean),
    }),
    [issuer, created, clientId, appUrl, redirectUri, postLogoutUri, appScheme, info, audiences],
  );

  // ---------------------------------------------------------------- step 1: what are you connecting?
  if (!kind) {
    return (
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
        {KINDS.map((k) => (
          <button
            key={k.id}
            type="button"
            onClick={() => chooseKind(k.id)}
            className="rounded-(--ui-radius) border border-ui bg-ui-surface p-5 text-left transition-colors hover:border-ui-primary focus:outline-none focus:ring-2 focus:ring-brand"
          >
            <p className="text-sm font-semibold text-ui">{k.title}</p>
            <p className="mt-1 text-sm text-muted">{k.description}</p>
            <p className="mt-3 text-xs text-faint">{k.examples}</p>
          </button>
        ))}
      </div>
    );
  }

  const isApi = kind === "api";
  const done = isApi || created !== null;

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center gap-3 text-sm">
        <button type="button" className="link-brand" onClick={() => { setKind(null); setCreated(null); setError(null); }}>
          ← Change
        </button>
        <span className="font-medium text-ui">{info?.title}</span>
        <span className="text-faint">·</span>
        <span className="text-muted">{fw?.label}</span>
      </div>

      {/* ---------------------------------------------------------------- step 2: details */}
      {!created && (
        <Card padded={false}>
          <CardHeader
            title={isApi ? "Your API" : "Register the app"}
            description={
              isApi
                ? "APIs need no registration: they check the tokens your apps send. Pick your framework for the setup."
                : "SecureOne creates an OAuth client with secure settings for this kind of app."
            }
          />
          <div className="grid gap-4 p-5 md:grid-cols-2">
            <FieldRow label="Framework">
              <Select value={framework} onChange={(e) => chooseFramework(kind, e.target.value)}>
                {frameworksFor(kind).map((f) => (
                  <option key={f.id} value={f.id}>{f.label}</option>
                ))}
              </Select>
            </FieldRow>

            {isApi ? (
              <FieldRow label="Apps allowed to call it" hint="client IDs, comma-separated" info="Checked against the token's audience (aud), which is the client ID of the app that got the token.">
                <Input value={audiences} onChange={(e) => setAudiences(e.target.value)} placeholder="Audiences, comma-separated" />
              </FieldRow>
            ) : (
              <>
                <FieldRow label="Tenant" info="Users of this tenant can sign in to the app.">
                  <Select
                    value={tenantId}
                    disabled={!canCreateApplications}
                    onChange={(e) => { setTenantId(e.target.value); setApplicationId(""); }}
                  >
                    {tenants.filter((t) => !t.system).map((t) => (
                      <option key={t.id} value={t.id}>{t.name} ({t.slug})</option>
                    ))}
                  </Select>
                </FieldRow>
                <FieldRow label="Application" info={canCreateApplications ? undefined : "The applications you manage. Ask a platform operator to create a new one."}>
                  <Select value={applicationId} onChange={(e) => changeExistingApp(e.target.value)}>
                    {canCreateApplications && <option value="">New application…</option>}
                    {tenantApps.map((a) => (
                      <option key={a.id} value={a.id}>{a.name}</option>
                    ))}
                  </Select>
                </FieldRow>
                {!applicationId && (
                  <FieldRow label="Application name" hint="what users see when signing in">
                    <Input value={applicationName} onChange={(e) => changeAppName(e.target.value)} placeholder="Application name" />
                  </FieldRow>
                )}
                <FieldRow label="Client ID" hint="your app sends this; lowercase and dashes">
                  <Input
                    value={clientId}
                    onChange={(e) => { setClientId(e.target.value); setClientIdEdited(true); }}
                    placeholder="Client ID"
                  />
                </FieldRow>

                {kind === "native" && (
                  <FieldRow label="App scheme" hint="reverse domain of your app id" info="Sign-in returns to the app through this URL scheme (RFC 8252).">
                    <Input
                      value={appScheme}
                      onChange={(e) => {
                        setAppScheme(e.target.value);
                        if (!urlsEdited) {
                          setRedirectUri(`${e.target.value}:/oauthredirect`);
                          setPostLogoutUri(`${e.target.value}:/signout`);
                        }
                      }}
                    />
                  </FieldRow>
                )}
                {info?.redirects && kind !== "native" && (
                  <FieldRow label="App URL" hint="where the app runs">
                    <Input value={appUrl} onChange={(e) => changeAppUrl(e.target.value)} />
                  </FieldRow>
                )}
                {info?.redirects && (
                  <>
                    <FieldRow label="Redirect URL" hint="after sign-in" info="Must match what the app sends exactly. Add production URLs later on the client page.">
                      <Input value={redirectUri} onChange={(e) => { setRedirectUri(e.target.value); setUrlsEdited(true); }} />
                    </FieldRow>
                    <FieldRow label="After sign-out URL">
                      <Input value={postLogoutUri} onChange={(e) => { setPostLogoutUri(e.target.value); setUrlsEdited(true); }} />
                    </FieldRow>
                    <div className="flex items-start justify-between gap-4 rounded-lg border border-ui px-4 py-3 md:col-span-2">
                      <div>
                        <p className="text-sm font-medium text-ui">Allow a test sign-in from the console</p>
                        <p className="text-xs text-muted">
                          Also registers <code className="font-mono">{testRedirectUri(issuer)}</code>, so you can prove sign-in works before writing code. Remove it later on the client page.
                        </p>
                      </div>
                      <Toggle checked={includeTest} onChange={setIncludeTest} aria-label="Allow a test sign-in" />
                    </div>
                  </>
                )}
              </>
            )}
          </div>
          {!isApi && (
            <div className="flex items-center justify-between gap-4 border-t border-ui px-5 py-4">
              <p className="text-xs text-muted">
                {describeTemplate(kind)}
              </p>
              <Button onClick={create} disabled={busy}>{busy ? "Creating…" : "Create client"}</Button>
            </div>
          )}
          {error && (
            <p role="alert" className="border-t border-ui bg-red-500/10 px-5 py-3 text-sm text-red-700 dark:text-red-300">{error}</p>
          )}
        </Card>
      )}

      {/* ---------------------------------------------------------------- step 3: credentials + code */}
      {created && (
        <Card padded={false}>
          <CardHeader title="Client created" description={`Users of the selected tenant can now sign in to ${appLabel || "the app"}.`} />
          <div className="space-y-4 p-5">
            <dl className="grid gap-3 text-sm md:grid-cols-2">
              <Value label="Issuer (authority)" value={issuer} />
              <Value label="Client ID" value={created.clientId} />
              {info?.redirects && <Value label="Redirect URL" value={redirectUri} />}
              <Value label="Discovery" value={`${issuer}/.well-known/openid-configuration`} />
            </dl>
            {created.clientSecret && (
              <div className="rounded-lg border border-amber-500/40 bg-amber-500/10 p-4">
                <p className="text-sm font-semibold text-ui">Client secret, shown only now</p>
                <p className="mt-1 text-xs text-muted">Store it as SECUREONE_CLIENT_SECRET on your server. Lost it? Rotate it on the client page.</p>
                <div className="mt-2 flex items-center gap-2">
                  <code className="min-w-0 flex-1 truncate rounded bg-black/5 px-2 py-1 font-mono text-xs dark:bg-white/10">{created.clientSecret}</code>
                  <CopyValue value={created.clientSecret} />
                </div>
              </div>
            )}
            <div className="flex flex-wrap gap-2">
              {info?.redirects && includeTest && (
                <a
                  className="inline-flex items-center rounded-(--ui-radius) bg-ui-primary px-3 py-1.5 text-sm font-medium text-white"
                  href={`${testRedirectUri(issuer)}?${new URLSearchParams({
                    client_id: created.clientId,
                    public: info.client?.confidential ? "0" : "1",
                    scope: (info.client?.scopes ?? []).join(" "),
                  })}`}
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  Test sign-in ↗
                </a>
              )}
              <Link className="inline-flex items-center rounded-(--ui-radius) border border-ui px-3 py-1.5 text-sm text-ui" href={`/applications/${created.clientRecordId}`}>
                Client settings
              </Link>
              <Link className="inline-flex items-center rounded-(--ui-radius) border border-ui px-3 py-1.5 text-sm text-ui" href={`/app/${created.applicationId}/users`}>
                Users & roles
              </Link>
            </div>
            <div className="flex flex-wrap items-center gap-x-4 gap-y-2 rounded-lg border border-ui bg-ui-elevated/40 px-4 py-3">
              <div className="min-w-0 flex-1">
                <p className="text-sm font-medium text-ui">Keep a copy for later</p>
                <p className="text-xs text-muted">
                  Downloads a Markdown file with these values, the endpoints and the setup code for {frameworkLabel}.
                </p>
              </div>
              {created.clientSecret && (
                <label className="flex items-center gap-2 text-xs text-muted">
                  <input
                    type="checkbox"
                    checked={secretInFile}
                    onChange={(e) => setSecretInFile(e.target.checked)}
                    className="h-4 w-4 accent-[var(--ui-primary)]"
                  />
                  Include the client secret
                </label>
              )}
              <Button
                type="button"
                variant="secondary"
                size="sm"
                onClick={() =>
                  downloadTextFile(
                    `${created.clientId}-secureone-setup.md`,
                    buildSetupMarkdown({
                      appName: appLabel || created.clientId,
                      kindTitle: info?.title ?? "",
                      frameworkId: framework,
                      frameworkLabel,
                      values,
                      clientSecret: secretInFile ? created.clientSecret : null,
                      confidential: Boolean(info?.client?.confidential),
                      grantTypes: info?.client?.grantTypes ?? [],
                      showRedirects: Boolean(info?.redirects),
                      createdAt: new Date(),
                    }),
                  )
                }
              >
                Download setup (.md)
              </Button>
            </div>
            {secretInFile && (
              <p className="text-xs text-amber-700 dark:text-amber-300">
                The file will contain the client secret. Keep it in a password manager or secret store, not in Git or chat.
              </p>
            )}
          </div>
        </Card>
      )}

      {done && <SetupGuide framework={framework} kind={kind} values={values} onFramework={setFramework} lockFramework={!!info?.redirects} />}
    </div>
  );
}

function SetupGuide({
  framework,
  kind,
  values,
  onFramework,
  lockFramework,
}: {
  framework: string;
  kind: ConnectKind;
  values: SnippetValues;
  onFramework: (id: string) => void;
  /** Redirect URLs are framework-specific, so the framework is fixed once the client exists. */
  lockFramework: boolean;
}) {
  let setup: ReturnType<typeof setupFor>;
  try {
    setup = setupFor(framework, values);
  } catch {
    // A URL field holds something that is not a URL yet.
    setup = { snippets: [], notes: ["Enter valid URLs above to see the setup code."] };
  }
  const options = frameworksFor(kind);
  return (
    <Card padded={false}>
      <CardHeader
        title="Add it to your app"
        description="Standard OpenID Connect libraries; values are filled in. Secrets come from the environment, never from code."
      />
      {!lockFramework && options.length > 1 && (
        <div className="flex flex-wrap gap-1 border-b border-ui px-5 py-3">
          {options.map((f) => (
            <button
              key={f.id}
              type="button"
              onClick={() => onFramework(f.id)}
              className={cn(
                "rounded-md px-3 py-1 text-sm",
                f.id === framework ? "bg-ui-primary text-white" : "text-muted hover:bg-ui-muted",
              )}
            >
              {f.label}
            </button>
          ))}
        </div>
      )}
      <div className="space-y-4 p-5">
        {setup.install && <CodeBlock title="Install" language="bash" code={setup.install} />}
        {setup.snippets.map((s) => (
          <CodeBlock key={s.title} title={s.title} language={s.language} code={s.code} />
        ))}
        {setup.notes.length > 0 && (
          <ul className="list-disc space-y-1 pl-5 text-sm text-muted">
            {setup.notes.map((n) => (
              <li key={n}>{n}</li>
            ))}
          </ul>
        )}
      </div>
    </Card>
  );
}

function Value({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs text-muted">{label}</dt>
      <dd className="mt-0.5 flex items-center gap-2">
        <code className="min-w-0 flex-1 truncate font-mono text-xs text-ui">{value}</code>
        <CopyValue value={value} />
      </dd>
    </div>
  );
}

function suggestClientId(appName: string, kind: ConnectKind): string {
  const base = slugify(appName) || "my-app";
  return `${base}-${KIND_SUFFIX[kind]}`.slice(0, 64);
}

function describeTemplate(kind: ConnectKind): string {
  switch (kind) {
    case "web":
      return "Confidential client: authorization code with PKCE, refresh tokens, secret for the server.";
    case "spa":
    case "native":
      return "Public client: authorization code with PKCE, refresh tokens, no secret.";
    case "desktop":
      return "Public client: authorization code with PKCE through the system browser and a loopback address, refresh tokens, no secret.";
    case "service":
      return "Confidential client: client credentials (no user), secret for the service.";
    case "device":
      return "Public client: device authorization grant with refresh tokens, no redirect, no secret.";
    default:
      return "";
  }
}
