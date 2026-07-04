import { browserApiFetch as apiFetch } from "./browser-client";

export type WebhookEndpoint = {
  id: string;
  tenantId: string;
  applicationId: string | null;
  url: string;
  events: string[];
  enabled: boolean;
  secretConfigured?: boolean;
};

export type WebhookDelivery = {
  id: string;
  eventType: string;
  status: string;
  responseCode: number | null;
  createdAt: string;
};

export async function listWebhooks(tenantId: string): Promise<WebhookEndpoint[]> {
  return apiFetch<WebhookEndpoint[]>(`/api/admin/v1/tenants/${tenantId}/webhooks`);
}

export async function createWebhook(
  tenantId: string,
  body: { url: string; events: string[]; enabled?: boolean; applicationId?: string },
): Promise<WebhookEndpoint & { secret?: string }> {
  return apiFetch(`/api/admin/v1/tenants/${tenantId}/webhooks`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function deleteWebhook(tenantId: string, id: string): Promise<void> {
  await apiFetch<void>(`/api/admin/v1/tenants/${tenantId}/webhooks/${id}`, { method: "DELETE" });
}

export async function listWebhookDeliveries(tenantId: string, webhookId: string): Promise<WebhookDelivery[]> {
  return apiFetch<WebhookDelivery[]>(`/api/admin/v1/tenants/${tenantId}/webhooks/${webhookId}/deliveries`);
}
