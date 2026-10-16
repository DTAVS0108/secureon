"use server";

// Shared API helpers called here run as the signed-in user (registers the lookup).
import "@/lib/api/server-auth";

import { revalidatePath } from "next/cache";
import { createApplicationApi } from "@/lib/api/applications";
import { createOAuthClientApi } from "@/lib/api/oauth-clients";
import { kindInfo, slugify, type ConnectKind } from "@/lib/connect/catalog";

export type ConnectInput = {
  kind: ConnectKind;
  tenantId: string;
  /** Existing application, or empty to create one named applicationName. */
  applicationId?: string;
  applicationName?: string;
  clientId: string;
  redirectUris: string[];
  postLogoutRedirectUris: string[];
};

export type ConnectResult =
  | { ok: true; applicationId: string; clientRecordId: string; clientId: string; clientSecret?: string }
  | { ok: false; error: string };

/** Creates the application (if new) and an OAuth client with the kind's secure defaults. */
export async function connectAppAction(input: ConnectInput): Promise<ConnectResult> {
  const kind = kindInfo(input.kind);
  if (!kind?.client) return { ok: false, error: "Nothing to register for this kind of app." };
  if (!input.tenantId) return { ok: false, error: "Choose the tenant whose users will sign in." };
  const clientId = slugify(input.clientId);
  if (!clientId) return { ok: false, error: "Enter a client ID (letters, digits and dashes)." };
  if (kind.redirects && input.redirectUris.filter(Boolean).length === 0) {
    return { ok: false, error: "Add at least one redirect URL." };
  }

  try {
    let applicationId = input.applicationId;
    if (!applicationId) {
      const name = input.applicationName?.trim();
      if (!name) return { ok: false, error: "Name the application." };
      const created = await createApplicationApi({ name, tenantId: input.tenantId, slug: slugify(name), status: "active" });
      applicationId = created.id;
    }
    const t = kind.client;
    const { client, clientSecret } = await createOAuthClientApi({
      applicationId,
      clientId,
      type: t.type,
      status: "active",
      grantTypes: t.grantTypes,
      scopes: t.scopes,
      redirectUris: kind.redirects ? input.redirectUris.filter(Boolean) : [],
      postLogoutRedirectUris: kind.redirects ? input.postLogoutRedirectUris.filter(Boolean) : [],
      pkceRequired: t.pkceRequired,
      tokenEndpointAuthMethod: t.tokenEndpointAuthMethod,
    });
    revalidatePath("/applications");
    revalidatePath("/app");
    return { ok: true, applicationId, clientRecordId: client.id, clientId: client.clientId, clientSecret };
  } catch (e) {
    return { ok: false, error: e instanceof Error ? e.message : "Could not register the app." };
  }
}
