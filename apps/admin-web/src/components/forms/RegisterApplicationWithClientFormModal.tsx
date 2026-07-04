"use client";

import { useActionState, useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { Modal } from "@/components/ui/Modal";
import { Button } from "@/components/ui/Button";
import { CopyValue } from "@/components/ui/CopyValue";
import { FieldRow, Input, Select, Textarea } from "@/components/ui/Field";
import { applicationWithClientCreateAction, type FormState } from "@/lib/actions";
import { CheckboxGroup, FormActions, FormError, useCloseOnSuccess } from "./form-utils";
import type { AppType, Tenant } from "@/lib/types";

const initial: FormState = { ok: false };

const GRANT_OPTIONS = [
  { value: "authorization_code", label: "authorization_code" },
  { value: "refresh_token", label: "refresh_token" },
  { value: "client_credentials", label: "client_credentials" },
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

export function RegisterApplicationWithClientFormModal({
  tenants,
  triggerLabel = "+ Register application & OAuth client",
  triggerVariant = "primary",
  triggerSize = "md",
}: {
  tenants: Tenant[];
  triggerLabel?: React.ReactNode;
  triggerVariant?: "primary" | "secondary" | "ghost" | "danger";
  triggerSize?: "sm" | "md";
}) {
  return (
    <Modal
      triggerLabel={triggerLabel}
      triggerVariant={triggerVariant}
      triggerSize={triggerSize}
      width="lg"
      title="Register application & OAuth client"
      description="Creates the application product (with isolated IAM schema), then attaches the first OAuth client in one step."
    >
      {(close) => <CombinedForm tenants={tenants} close={close} />}
    </Modal>
  );
}

function CombinedForm({ tenants, close }: { tenants: Tenant[]; close: () => void }) {
  const [state, formAction, pending] = useActionState(applicationWithClientCreateAction, initial);
  const [clientType, setClientType] = useState<AppType>("web");
  const [grantTypes, setGrantTypes] = useState<string[]>(TEMPLATE_BY_TYPE.web.grantTypes);
  const [scopes, setScopes] = useState(TEMPLATE_BY_TYPE.web.scopes);
  const [pkceRequired, setPkceRequired] = useState(true);
  const [authMethod, setAuthMethod] = useState(TEMPLATE_BY_TYPE.web.tokenEndpointAuthMethod);
  const [newSecret, setNewSecret] = useState<string | null>(null);

  useCloseOnSuccess(state, close, (s) => {
    if (s.createdClientSecret) setNewSecret(s.createdClientSecret);
  });

  useDeferredEffect(() => {
    const t = TEMPLATE_BY_TYPE[clientType];
    setGrantTypes(t.grantTypes);
    setScopes(t.scopes);
    setPkceRequired(t.pkceRequired);
    setAuthMethod(t.tokenEndpointAuthMethod);
  }, [clientType]);

  if (newSecret) {
    return (
      <div className="space-y-4">
        <p className="text-sm text-ui">
          Application and OAuth client registered. Copy the client secret now — it will not be shown again.
        </p>
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
    <form action={formAction} className="space-y-6">
      <input type="hidden" name="tokenEndpointAuthMethod" value={authMethod} />
      <FormError state={state} />

      <section className="space-y-4">
        <h3 className="text-sm font-semibold text-ui">Application product</h3>
        <div className="grid grid-cols-2 gap-4">
          <FieldRow label="Display name">
            <Input name="name" placeholder="Flipkart" required />
          </FieldRow>
          <FieldRow label="Tenant">
            <Select name="tenantId" defaultValue={tenants[0]?.id} disabled={tenants.length === 0} required>
              {tenants.length === 0 && <option value="">No tenants available</option>}
              {tenants.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name}
                </option>
              ))}
            </Select>
          </FieldRow>
        </div>
        <FieldRow label="Slug" hint="Schema name + default client ID (e.g. flipkart)">
          <Input name="slug" placeholder="flipkart" />
        </FieldRow>
        <FieldRow label="Description" hint="Optional">
          <Textarea name="description" rows={2} placeholder="Customer-facing product" />
        </FieldRow>
        <FieldRow label="Status">
          <Select name="status" defaultValue="active">
            <option value="active">Active</option>
            <option value="disabled">Disabled</option>
            <option value="suspended">Suspended</option>
          </Select>
        </FieldRow>
      </section>

      <section className="space-y-4 border-t border-ui pt-6">
        <h3 className="text-sm font-semibold text-ui">OAuth client</h3>
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
          <FieldRow label="Client ID" hint="Optional — defaults to application slug">
            <Input name="clientId" placeholder="flipkart-web" />
          </FieldRow>
        </div>
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
          <Textarea name="redirectUris" placeholder="https://app.example.com/callback" />
        </FieldRow>
        <FieldRow label="Post-logout redirect URIs" hint="Optional">
          <Textarea name="postLogoutRedirectUris" />
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
          <span className="text-xs text-muted">
            Auth at token endpoint:{" "}
            <strong>{confidential ? authMethod : "none (public client)"}</strong>
          </span>
        </div>
      </section>

      <FormActions
        pending={pending}
        close={close}
        submitLabel="Register application & client"
        submitDisabled={tenants.length === 0}
      />
    </form>
  );
}
