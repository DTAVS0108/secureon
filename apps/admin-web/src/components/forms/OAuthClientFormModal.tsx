"use client";

import { useActionState, useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { Modal } from "@/components/ui/Modal";
import { Button } from "@/components/ui/Button";
import { CopyValue } from "@/components/ui/CopyValue";
import { FieldRow, Input, Select, Textarea } from "@/components/ui/Field";
import { oauthClientCreateAction, oauthClientUpdateAction, type FormState } from "@/lib/actions";
import { CheckboxGroup, FormActions, FormError, useCloseOnSuccess } from "./form-utils";
import type { ApplicationProduct, AppType, OAuthClient, Tenant } from "@/lib/types";

const initial: FormState = { ok: false };

const GRANT_OPTIONS = [
  { value: "authorization_code", label: "authorization_code" },
  { value: "refresh_token", label: "refresh_token" },
  { value: "client_credentials", label: "client_credentials" },
  { value: "urn:ietf:params:oauth:grant-type:device_code", label: "device_code (TVs, CLIs)" },
  { value: "urn:ietf:params:oauth:grant-type:token-exchange", label: "token-exchange" },
];

const TEMPLATE_BY_TYPE: Record<
  AppType,
  { grantTypes: string[]; scopes: string; pkceRequired: boolean; tokenEndpointAuthMethod: string }
> = {
  web: {
    grantTypes: ["authorization_code", "refresh_token"],
    scopes: "openid, profile, email",
    pkceRequired: true,
    tokenEndpointAuthMethod: "client_secret_basic",
  },
  spa: {
    grantTypes: ["authorization_code", "refresh_token"],
    scopes: "openid, profile, email",
    pkceRequired: true,
    tokenEndpointAuthMethod: "none",
  },
  native: {
    grantTypes: ["authorization_code", "refresh_token"],
    scopes: "openid, profile, email",
    pkceRequired: true,
    tokenEndpointAuthMethod: "none",
  },
  m2m: {
    grantTypes: ["client_credentials"],
    scopes: "",
    pkceRequired: false,
    tokenEndpointAuthMethod: "client_secret_basic",
  },
};

export function OAuthClientFormModal({
  client,
  applications,
  tenants,
  triggerLabel,
  triggerVariant = "primary",
  triggerSize = "md",
}: {
  client?: OAuthClient;
  applications: ApplicationProduct[];
  tenants?: Tenant[];
  triggerLabel: React.ReactNode;
  triggerVariant?: "primary" | "secondary" | "ghost" | "danger";
  triggerSize?: "sm" | "md";
}) {
  const editing = Boolean(client);
  return (
    <Modal
      triggerLabel={triggerLabel}
      triggerVariant={triggerVariant}
      triggerSize={triggerSize}
      width="lg"
      title={editing ? "Edit OAuth client" : "Register OAuth client"}
      description={
        editing
          ? "Update client metadata, grants, and redirect URIs."
          : "Register OAuth credentials. Leave application empty to auto-create a product and schema."
      }
    >
      {(close) => (
        <OAuthClientForm
          client={client}
          applications={applications}
          tenants={tenants ?? []}
          close={close}
        />
      )}
    </Modal>
  );
}

function OAuthClientForm({
  client,
  applications,
  tenants,
  close,
}: {
  client?: OAuthClient;
  applications: ApplicationProduct[];
  tenants: Tenant[];
  close: () => void;
}) {
  const action = client ? oauthClientUpdateAction : oauthClientCreateAction;
  const [state, formAction, pending] = useActionState(action, initial);
  const [clientType, setClientType] = useState<AppType>(client?.type ?? "web");
  const [grantTypes, setGrantTypes] = useState<string[]>(
    client?.grantTypes ?? TEMPLATE_BY_TYPE.web.grantTypes,
  );
  const [scopes, setScopes] = useState(client?.scopes.join(", ") ?? TEMPLATE_BY_TYPE.web.scopes);
  const [pkceRequired, setPkceRequired] = useState(client?.pkceRequired ?? true);
  const [authMethod, setAuthMethod] = useState(
    client?.tokenEndpointAuthMethod ?? TEMPLATE_BY_TYPE.web.tokenEndpointAuthMethod,
  );
  const [newSecret, setNewSecret] = useState<string | null>(null);
  const [autoCreate, setAutoCreate] = useState(!client && applications.length === 0);

  useCloseOnSuccess(state, close, (s) => {
    if (s.createdClientSecret) {
      setNewSecret(s.createdClientSecret);
    }
  });

  useDeferredEffect(() => {
    if (client) return;
    const t = TEMPLATE_BY_TYPE[clientType];
    setGrantTypes(t.grantTypes);
    setScopes(t.scopes);
    setPkceRequired(t.pkceRequired);
    setAuthMethod(t.tokenEndpointAuthMethod);
  }, [clientType, client]);

  if (newSecret) {
    return (
      <div className="space-y-4">
        <p className="text-sm text-ui">Client registered. Copy the secret now — it will not be shown again.</p>
        <div className="rounded-lg border border-amber-500/40 bg-amber-500/10 p-4">
          <code className="block break-all font-mono text-sm">{newSecret}</code>
          <div className="mt-2">
            <CopyValue value={newSecret} />
          </div>
        </div>
        <div className="flex justify-end">
          <Button type="button" onClick={close}>
            Done
          </Button>
        </div>
      </div>
    );
  }

  const confidential = clientType === "web" || clientType === "m2m";

  return (
    <form action={formAction} className="space-y-4">
      {client && <input type="hidden" name="id" value={client.id} />}
      <input type="hidden" name="tokenEndpointAuthMethod" value={authMethod} />
      <FormError state={state} />
      {!client && (
        <>
          <FieldRow label="Application product" hint="Optional — auto-creates application + schema when empty">
            <Select
              name="applicationId"
              defaultValue=""
              onChange={(e) => setAutoCreate(!e.target.value)}
            >
              <option value="">Auto-create from client</option>
              {applications.map((a) => (
                <option key={a.id} value={a.id}>
                  {a.name} ({a.slug})
                </option>
              ))}
            </Select>
          </FieldRow>
          {autoCreate && (
            <>
              <FieldRow label="Tenant" hint="Required when auto-creating application">
                <Select name="tenantId" defaultValue={tenants[0]?.id} required={autoCreate}>
                  {tenants.length === 0 && <option value="">No tenants available</option>}
                  {tenants.map((t) => (
                    <option key={t.id} value={t.id}>
                      {t.name}
                    </option>
                  ))}
                </Select>
              </FieldRow>
              <FieldRow label="Application name" hint="Used for auto-created product and schema">
                <Input name="applicationName" placeholder="Demo Store" />
              </FieldRow>
            </>
          )}
        </>
      )}
      <div className="grid grid-cols-2 gap-4">
        <FieldRow label="Client template">
          <Select
            name="type"
            value={clientType}
            onChange={(e) => setClientType(e.target.value as AppType)}
          >
            <option value="web">Web app (confidential)</option>
            <option value="spa">SPA (public + PKCE)</option>
            <option value="native">Native (public + PKCE)</option>
            <option value="m2m">Machine-to-machine</option>
          </Select>
        </FieldRow>
        <FieldRow label="Status">
          <Select name="status" defaultValue={client?.status ?? "active"}>
            <option value="active">Active</option>
            <option value="disabled">Disabled</option>
            <option value="suspended">Suspended</option>
          </Select>
        </FieldRow>
      </div>
      {!client && (
        <FieldRow label="Client ID" hint="Optional — defaults to application slug">
          <Input name="clientId" placeholder="demostore-web" />
        </FieldRow>
      )}
      <FieldRow label="Grant types">
        <CheckboxGroup
          key={grantTypes.join(",")}
          name="grantTypes"
          options={GRANT_OPTIONS}
          selected={grantTypes}
        />
      </FieldRow>
      <FieldRow label="Scopes" hint="Comma-separated OIDC scopes">
        <Input name="scopes" value={scopes} onChange={(e) => setScopes(e.target.value)} />
      </FieldRow>
      <FieldRow label="Redirect URIs" hint="One per line">
        <Textarea
          name="redirectUris"
          defaultValue={client?.redirectUris.join("\n")}
          placeholder="https://app.example.com/callback"
        />
      </FieldRow>
      <FieldRow label="Post-logout redirect URIs" hint="Optional">
        <Textarea
          name="postLogoutRedirectUris"
          defaultValue={client?.postLogoutRedirectUris.join("\n")}
        />
      </FieldRow>
      <div className="flex flex-wrap items-center gap-6 rounded-lg border border-ui bg-ui-elevated/50 px-4 py-3">
        <label className="flex items-center gap-2 text-sm">
          <input
            type="checkbox"
            name="pkceRequired"
            checked={pkceRequired}
            onChange={(e) => setPkceRequired(e.target.checked)}
            className="rounded border-ui"
          />
          Require PKCE
        </label>
        <label className="flex items-center gap-2 text-sm">
          Token subject (sub)
          <select
            name="subjectType"
            defaultValue={client?.subjectType ?? "user_id"}
            className="rounded border-ui bg-ui-elevated px-2 py-1 text-sm"
            title="user_id: the account ID, which never changes. Legacy: tenant:email, which changes if the user changes email."
          >
            <option value="user_id">User ID (stable)</option>
            <option value="legacy">Legacy (tenant:email)</option>
          </select>
        </label>
        <span className="text-xs text-muted">
          Auth at token endpoint:{" "}
          <strong>{confidential ? authMethod : "none (public client)"}</strong>
        </span>
      </div>
      <FormActions
        pending={pending}
        close={close}
        submitLabel={client ? "Save changes" : "Register client"}
        submitDisabled={!client && autoCreate && tenants.length === 0}
      />
    </form>
  );
}
