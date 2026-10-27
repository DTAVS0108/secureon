import { appScopeHeaders } from "./http";
import { browserApiFetch as apiFetch } from "./browser-client";

export type SamlAttribute = { name: string; source: string; value?: string | null };

/** A SAML service provider signing users in through this tenant's SAML identity provider. */
export type SamlApp = {
  id: string;
  applicationId: string;
  name: string;
  entityId: string;
  acsUrl: string;
  sloUrl: string | null;
  nameIdFormat: "email" | "persistent" | "unspecified";
  signResponse: boolean;
  signAssertion: boolean;
  spCertificate: string | null;
  requireSignedRequests: boolean;
  attributes: SamlAttribute[];
  effectiveAttributes: SamlAttribute[];
  assertionLifetimeSeconds: number;
  status: "active" | "disabled";
};

export type SamlIdentityProvider = {
  entityId: string;
  metadataUrl: string;
  ssoUrl: string;
  sloUrl: string;
  initUrl: string;
  certificatePem: string;
  bindings: string[];
};

export type SamlApps = {
  identityProvider: SamlIdentityProvider;
  serviceProviders: SamlApp[];
  attributeSources: string[];
  defaultAttributes: SamlAttribute[];
};

export type SamlAppInput = Partial<Omit<SamlApp, "id" | "applicationId" | "effectiveAttributes">>;

const base = (applicationId: string) => `/api/admin/v1/applications/${applicationId}/settings/saml-apps`;

export async function fetchSamlApps(applicationId: string): Promise<SamlApps> {
  return apiFetch<SamlApps>(base(applicationId), { headers: appScopeHeaders(applicationId) });
}

export async function createSamlApp(applicationId: string, input: SamlAppInput): Promise<SamlApp> {
  return apiFetch<SamlApp>(base(applicationId), {
    method: "POST",
    headers: appScopeHeaders(applicationId),
    body: JSON.stringify(input),
  });
}

export async function updateSamlApp(applicationId: string, id: string, input: SamlAppInput): Promise<SamlApp> {
  return apiFetch<SamlApp>(`${base(applicationId)}/${id}`, {
    method: "PUT",
    headers: appScopeHeaders(applicationId),
    body: JSON.stringify(input),
  });
}

export async function deleteSamlApp(applicationId: string, id: string): Promise<void> {
  await apiFetch<void>(`${base(applicationId)}/${id}`, { method: "DELETE", headers: appScopeHeaders(applicationId) });
}
