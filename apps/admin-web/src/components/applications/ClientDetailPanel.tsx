"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { OAuthClientFormModal } from "@/components/forms/OAuthClientFormModal";
import { ClientSetupCode } from "@/components/applications/ClientSetupCode";
import { ClientDeleteRequest } from "@/components/approvals/ClientDeleteRequest";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { CopyValue } from "@/components/ui/CopyValue";
import { useToast } from "@/components/ui/Toast";
import { rotateOAuthClientSecretApi } from "@/lib/api/oauth-clients";
import { publicAuthServerUrl } from "@/lib/config";
import { statusTone } from "@/lib/status";
import type { OAuthClient } from "@/lib/types";
import { LocalTime } from "@/components/ui/LocalTime";

const TYPE_LABEL: Record<OAuthClient["type"], string> = {
  web: "Web application (confidential)",
  spa: "Single-page application (public)",
  native: "Native / mobile (public)",
  m2m: "Machine-to-machine (confidential)",
};

export function ClientDetailPanel({
  client,
  applicationName,
}: {
  client: OAuthClient;
  applicationName: string;
}) {
  const { toast } = useToast();
  const router = useRouter();
  const [revealedSecret, setRevealedSecret] = useState<string | null>(null);
  const [rotating, setRotating] = useState(false);
  const discoveryUrl = `${publicAuthServerUrl()}/.well-known/openid-configuration`;

  async function rotateSecret() {
    setRotating(true);
    try {
      const secret = await rotateOAuthClientSecretApi(client.id);
      setRevealedSecret(secret);
      toast("New client secret generated — copy it now; it will not be shown again.", "success");
      router.refresh();
    } catch (e) {
      toast(e instanceof Error ? e.message : "Rotate failed", "error");
    } finally {
      setRotating(false);
    }
  }

  return (
    <div className="space-y-6">
      <p className="text-sm text-muted">
        OAuth client for <strong>{applicationName}</strong>. User, role, and policy operations live in
        the{" "}
        <Link href={`/app/${client.applicationId}/users`} className="text-brand hover:underline">
          application console
        </Link>
        .
      </p>

      {revealedSecret && (
        <div className="rounded-lg border border-amber-500/40 bg-amber-500/10 px-4 py-3">
          <p className="text-sm font-medium text-ui">Client secret (shown once)</p>
          <div className="mt-2 flex flex-wrap items-center gap-2">
            <code className="break-all rounded bg-black/10 px-2 py-1 font-mono text-sm dark:bg-white/10">
              {revealedSecret}
            </code>
            <CopyValue value={revealedSecret} />
          </div>
          <p className="mt-2 text-xs text-muted">Store this in your vault. It cannot be retrieved later.</p>
        </div>
      )}

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <Card padded={false}>
          <CardHeader title="Client credentials" />
          <div className="space-y-4 p-5 text-sm">
            <Field label="Client ID">
              <div className="flex flex-wrap items-center gap-2">
                <code className="font-mono text-sm">{client.clientId}</code>
                <CopyValue value={client.clientId} />
              </div>
            </Field>
            <Field label="Application" value={applicationName} />
            <Field label="Client type" value={<Badge tone="indigo">{TYPE_LABEL[client.type]}</Badge>} />
            <Field
              label="Client authentication"
              value={
                client.confidential
                  ? `${client.tokenEndpointAuthMethod} · secret ${client.clientSecretConfigured ? "configured" : "missing"}`
                  : "Public client (no secret)"
              }
            />
            {client.confidential && (
              <div>
                <Button variant="secondary" size="sm" onClick={rotateSecret} disabled={rotating}>
                  {rotating ? "Rotating…" : "Rotate client secret"}
                </Button>
              </div>
            )}
            <Field label="Last updated" value={<LocalTime value={client.updatedAt} />} />
          </div>
        </Card>

        <Card padded={false}>
          <CardHeader title="Security policy" />
          <div className="space-y-3 p-5 text-sm">
            <Field label="PKCE required" value={client.pkceRequired ? "Yes (OAuth 2.1)" : "No"} />
            <Field
              label="Token subject (sub)"
              value={client.subjectType === "user_id" ? "User ID (stable)" : "Legacy (tenant:email)"}
            />
            <Field label="Grant types" value={client.grantTypes.join(", ") || "—"} />
            <Field label="Scopes" value={client.scopes.join(", ") || "—"} />
            <Field
              label="Status"
              value={
                <Badge tone={statusTone(client.status)} dot className="capitalize">
                  {client.status}
                </Badge>
              }
            />
          </div>
        </Card>

        <Card padded={false} className="lg:col-span-2">
          <CardHeader
            title="OAuth 2.1 / OIDC endpoints"
            description="Share with application teams integrating with SecureOne"
          />
          <div className="grid gap-4 p-5 sm:grid-cols-2">
            <EndpointRow label="Issuer" value={client.oAuthEndpoints.issuer} />
            <EndpointRow label="Authorization" value={client.oAuthEndpoints.authorizationEndpoint} />
            <EndpointRow label="Token" value={client.oAuthEndpoints.tokenEndpoint} />
            <EndpointRow label="JWKS" value={client.oAuthEndpoints.jwksUri} />
            <EndpointRow label="Discovery" value={discoveryUrl} />
          </div>
        </Card>

        <div className="lg:col-span-2">
          <ClientSetupCode client={client} />
        </div>

        <Card padded={false}>
          <CardHeader title="Redirect URIs" />
          <UriList uris={client.redirectUris} empty="No redirect URIs configured." />
        </Card>

        <Card padded={false}>
          <CardHeader title="Post-logout redirect URIs" />
          <UriList uris={client.postLogoutRedirectUris} empty="None (optional for OIDC logout)." />
        </Card>
      </div>

      <Card className="border-red-500/20" padded={false}>
        <CardHeader title="Danger zone" />
        <ClientDeleteRequest clientDbId={client.id} clientId={client.clientId} tenantId={client.tenantId} />
      </Card>
    </div>
  );
}

function Field({ label, value, children }: { label: string; value?: React.ReactNode; children?: React.ReactNode }) {
  return (
    <div>
      <p className="mb-1 text-xs font-medium uppercase tracking-wide text-muted">{label}</p>
      {children ?? value}
    </div>
  );
}

function EndpointRow({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="text-xs font-medium uppercase tracking-wide text-muted">{label}</p>
      <div className="mt-1 flex flex-wrap items-center gap-2">
        <code className="break-all font-mono text-xs text-ui">{value}</code>
        <CopyValue value={value} label="Copy" />
      </div>
    </div>
  );
}

function UriList({ uris, empty }: { uris: string[]; empty: string }) {
  if (uris.length === 0) {
    return <p className="p-5 text-sm text-muted">{empty}</p>;
  }
  return (
    <ul className="divide-y divide-ui p-5">
      {uris.map((uri) => (
        <li key={uri} className="flex items-center justify-between gap-2 py-2 font-mono text-xs">
          <span className="break-all">{uri}</span>
          <CopyValue value={uri} />
        </li>
      ))}
    </ul>
  );
}
