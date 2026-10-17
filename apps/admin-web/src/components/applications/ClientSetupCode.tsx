"use client";

import { useState } from "react";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { CodeBlock } from "@/components/connect/CodeBlock";
import { useAdminContext } from "@/components/AdminContextProvider";
import { frameworksFor, kindInfo, testRedirectUri, type ConnectKind, type FrameworkInfo } from "@/lib/connect/catalog";
import { buildSetupMarkdown, downloadTextFile } from "@/lib/connect/setup-file";
import { saveOAuthClientSetupFrameworkApi } from "@/lib/api/oauth-clients";
import { setupFor, type SnippetValues } from "@/lib/connect/snippets";
import { cn } from "@/lib/cn";
import type { OAuthClient } from "@/lib/types";

const DEVICE_GRANT = "urn:ietf:params:oauth:grant-type:device_code";

/** The kind of app a stored client is for (Connect an app's categories). */
function kindOf(client: OAuthClient): ConnectKind {
  if (client.grantTypes.some((g) => g === DEVICE_GRANT || g === "device_code")) return "device";
  switch (client.type) {
    case "m2m":
      return "service";
    case "spa":
      return "spa";
    case "native":
      // Desktop apps register a loopback redirect (RFC 8252 7.3); mobile apps an app scheme.
      return client.redirectUris.some((u) => /^http:\/\/(127\.0\.0\.1|localhost|\[::1\])(:\d+)?\//.test(u))
        ? "desktop"
        : "native";
    default:
      return "web";
  }
}

/**
 * The framework whose default callback path best matches a registered redirect: the longest matching
 * path wins ("/auth/callback" over "/callback"), and the framework's default port breaks ties
 * (Flask on 5000 vs. a Python backend behind a frontend dev server on 5173).
 */
function guessFramework(options: FrameworkInfo[], redirects: string[]): string | null {
  let best: { id: string; score: number } | null = null;
  for (const f of options) {
    if (!f.callbackPath) continue;
    for (const uri of redirects) {
      if (!uri.endsWith(f.callbackPath)) continue;
      let score = f.callbackPath.length;
      try {
        if (f.appUrl && new URL(uri).port === new URL(f.appUrl).port) score += 1000;
      } catch {
        // not an http(s) URL
      }
      if (!best || score > best.score) best = { id: f.id, score };
    }
  }
  return best?.id ?? null;
}

function originOf(url: string | undefined): string | null {
  if (!url) return null;
  try {
    const u = new URL(url);
    return u.protocol === "http:" || u.protocol === "https:" ? u.origin : null;
  } catch {
    return null;
  }
}

/**
 * Setup code for an existing OAuth client, rebuilt from its stored settings at any time (the client
 * secret is never shown here). Behind the platform feature "Client setup code".
 */
export function ClientSetupCode({ client }: { client: OAuthClient }) {
  // Platform settings → Feature flags → Platform features → Client setup code.
  const enabled = useAdminContext().clientSetupCodeEnabled !== false;
  const kind = kindOf(client);
  const options = frameworksFor(kind);
  const issuer = client.oAuthEndpoints.issuer.replace(/\/+$/, "");
  // The console's test page is not the app's own redirect URL.
  const appRedirects = client.redirectUris.filter((u) => u !== testRedirectUri(issuer));
  // The framework chosen in Connect an app (or last picked here); otherwise a guess from the redirect URL.
  const saved = options.some((f) => f.id === client.setupFramework) ? client.setupFramework! : null;
  const guessed = saved ?? guessFramework(options, appRedirects) ?? options[0]?.id ?? "";
  const [framework, setFrameworkState] = useState(guessed);
  const setFramework = (id: string) => {
    setFrameworkState(id);
    // Remember it for next time; a failure only means the guess is shown again.
    void saveOAuthClientSetupFrameworkApi(client.id, id).catch(() => {});
  };

  const redirectUri = appRedirects[0] ?? "";
  const appUrl = originOf(redirectUri) ?? "http://localhost:3000";
  const values: SnippetValues = {
    issuer,
    clientId: client.clientId,
    appUrl,
    redirectUri: redirectUri || `${appUrl}/callback`,
    postLogoutUri: client.postLogoutRedirectUris[0] ?? `${appUrl}/`,
    // Mobile redirects look like com.example.shop:/oauthredirect.
    appScheme: redirectUri && !originOf(redirectUri) ? redirectUri.split(":")[0] : "com.example.app",
    scopes: client.scopes,
    audiences: [],
  };

  if (!enabled || options.length === 0) return null;

  let setup: ReturnType<typeof setupFor> | null = null;
  try {
    setup = setupFor(framework, values);
  } catch {
    setup = null;
  }
  const frameworkLabel = options.find((f) => f.id === framework)?.label ?? framework;

  return (
    <Card padded={false}>
      <CardHeader
        title="Setup code"
        description={`Ready-to-use code for ${kindInfo(kind).title.toLowerCase()} libraries, built from this client's settings.${
          client.confidential ? " The client secret is not shown: use the value you stored, or rotate it above." : ""
        }`}
        action={
          <Button
            type="button"
            variant="secondary"
            size="sm"
            onClick={() =>
              downloadTextFile(
                `${client.clientId}-secureone-setup.md`,
                buildSetupMarkdown({
                  appName: client.applicationName,
                  kindTitle: kindInfo(kind).title,
                  frameworkId: framework,
                  frameworkLabel,
                  values,
                  clientSecret: null,
                  confidential: client.confidential,
                  grantTypes: client.grantTypes,
                  showRedirects: Boolean(kindInfo(kind).redirects),
                  createdAt: new Date(),
                }),
              )
            }
          >
            Download (.md)
          </Button>
        }
      />
      {options.length > 1 && (
        <div className="flex flex-wrap gap-1 border-b border-ui px-5 py-3">
          {options.map((f) => (
            <button
              key={f.id}
              type="button"
              onClick={() => setFramework(f.id)}
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
        {!setup ? (
          <p className="text-sm text-muted">Setup code is not available for this framework.</p>
        ) : (
          <>
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
          </>
        )}
      </div>
    </Card>
  );
}
