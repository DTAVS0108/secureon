import { browserApiFetch as apiFetch } from "./browser-client";

export type WebhookEndpoint = {
  id: string;
  tenantId: string;
  applicationId: string | null;
  url: string;
  events: string[];
  enabled: boolean;
  secretConfigured?: boolean;
  /** After a rotation: until when the old secret also signs. */
  previousSecretValidUntil?: string | null;
};

export type WebhookDelivery = {
  id: string;
  eventType: string;
  /** PENDING, RETRYING, DELIVERED or FAILED. */
  status: string;
  responseCode: number | null;
  createdAt: string;
  attempts?: number;
  lastError?: string | null;
  nextAttemptAt?: string | null;
  deliveredAt?: string | null;
};

/**
 * Whose webhooks: a tenant id (the tenant's endpoints, receiving every application's events), or one
 * application (its own endpoints, receiving only its events; managed by its administrators).
 */
export type WebhookScope = string | { applicationId: string };

function base(scope: WebhookScope): string {
  return typeof scope === "string"
    ? `/api/admin/v1/tenants/${scope}/webhooks`
    : `/api/admin/v1/applications/${scope.applicationId}/settings/webhooks`;
}

function init(scope: WebhookScope, extra?: RequestInit): RequestInit | undefined {
  if (typeof scope === "string") return extra;
  return { ...extra, headers: { ...(extra?.headers ?? {}), "X-Application-Id": scope.applicationId } };
}

export async function rotateWebhookSecret(
  scope: WebhookScope,
  id: string,
  keepOldHours: number,
): Promise<WebhookEndpoint & { secret: string }> {
  return apiFetch(`${base(scope)}/${id}/rotate-secret?keepOldHours=${keepOldHours}`, init(scope, { method: "POST" }));
}

export async function testWebhook(
  scope: WebhookScope,
  id: string,
): Promise<{ ok: boolean; responseCode: number | null; error: string | null }> {
  return apiFetch(`${base(scope)}/${id}/test`, init(scope, { method: "POST" }));
}

export async function redeliverWebhook(scope: WebhookScope, id: string, deliveryId: string): Promise<WebhookDelivery> {
  return apiFetch(`${base(scope)}/${id}/deliveries/${deliveryId}/redeliver`, init(scope, { method: "POST" }));
}

export type WebhookEventInfo = { event: string; description: string };

export async function listWebhookEvents(scope: WebhookScope): Promise<WebhookEventInfo[]> {
  return apiFetch<WebhookEventInfo[]>(`${base(scope)}/events`, init(scope));
}

export async function listWebhooks(scope: WebhookScope): Promise<WebhookEndpoint[]> {
  return apiFetch<WebhookEndpoint[]>(base(scope), init(scope));
}

export async function createWebhook(
  scope: WebhookScope,
  body: { url: string; events: string[]; enabled?: boolean; applicationId?: string },
): Promise<WebhookEndpoint & { secret?: string }> {
  return apiFetch(base(scope), init(scope, { method: "POST", body: JSON.stringify(body) }));
}

export async function deleteWebhook(scope: WebhookScope, id: string): Promise<void> {
  await apiFetch<void>(`${base(scope)}/${id}`, init(scope, { method: "DELETE" }));
}

export async function listWebhookDeliveries(scope: WebhookScope, webhookId: string): Promise<WebhookDelivery[]> {
  return apiFetch<WebhookDelivery[]>(`${base(scope)}/${webhookId}/deliveries`, init(scope));
}
