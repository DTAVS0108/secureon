"use client";

import { useCallback, useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { CopyValue } from "@/components/ui/CopyValue";
import { FieldRow, Input } from "@/components/ui/Field";
import { useToast } from "@/components/ui/Toast";
import {
  createWebhook,
  deleteWebhook,
  listWebhooks,
  type WebhookEndpoint,
} from "@/lib/api/webhooks";

const DEFAULT_EVENTS = [
  "user.login.succeeded",
  "user.created",
  "access_request.created",
  "access_request.approved",
  "access_request.denied",
];

export function WebhooksPanel({ tenantId }: { tenantId: string }) {
  const { toast } = useToast();
  const [rows, setRows] = useState<WebhookEndpoint[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [url, setUrl] = useState("");
  const [creating, setCreating] = useState(false);
  const [newSecret, setNewSecret] = useState<string | null>(null);

  const reload = useCallback(async () => {
    setLoaded(false);
    try {
      setRows(await listWebhooks(tenantId));
    } catch (e) {
      toast(e instanceof Error ? e.message : "Failed to load webhooks", "error");
    } finally {
      setLoaded(true);
    }
  }, [tenantId, toast]);

  useDeferredEffect(() => {
    reload();
  }, [reload]);

  async function handleCreate() {
    if (!url.trim()) return;
    setCreating(true);
    try {
      const created = await createWebhook(tenantId, { url: url.trim(), events: DEFAULT_EVENTS, enabled: true });
      if (created.secret) setNewSecret(created.secret);
      setUrl("");
      toast("Webhook created", "success");
      await reload();
    } catch (e) {
      toast(e instanceof Error ? e.message : "Create failed", "error");
    } finally {
      setCreating(false);
    }
  }

  return (
    <Card padded={false} className={!loaded ? "pointer-events-none opacity-60" : ""}>
      <CardHeader
        title="Webhooks"
        description="Outbound HTTPS notifications for login and lifecycle events (HMAC-signed)."
      />
      <div className="space-y-4 px-5 pb-5">
        <FieldRow label="Endpoint URL">
          <Input value={url} onChange={(e) => setUrl(e.target.value)} placeholder="https://example.com/hooks/secureone" />
        </FieldRow>
        <p className="text-xs text-muted">Events: {DEFAULT_EVENTS.join(", ")}</p>
        <Button onClick={() => void handleCreate()} disabled={creating || !url.trim()}>
          {creating ? "Creating…" : "Add webhook"}
        </Button>

        {newSecret && (
          <div className="rounded-xl border border-amber-500/30 bg-amber-500/5 p-4 space-y-2">
            <p className="text-sm font-medium text-ui">Signing secret (copy now)</p>
            <CopyValue value={newSecret} />
            <Button variant="secondary" size="sm" onClick={() => setNewSecret(null)}>
              Done
            </Button>
          </div>
        )}

        {rows.length === 0 ? (
          <p className="text-sm text-muted">No webhooks configured.</p>
        ) : (
          <ul className="divide-y divide-ui rounded-xl border border-ui">
            {rows.map((row) => (
              <li key={row.id} className="flex items-center justify-between gap-3 px-4 py-3">
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium text-ui">{row.url}</p>
                  <Badge tone={row.enabled ? "success" : "neutral"} className="mt-1">
                    {row.enabled ? "enabled" : "disabled"}
                  </Badge>
                </div>
                <Button variant="ghost" size="sm" onClick={() => void deleteWebhook(tenantId, row.id).then(reload)}>
                  Delete
                </Button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </Card>
  );
}
