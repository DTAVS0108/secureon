"use client";

import { useCallback, useMemo, useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { CopyValue } from "@/components/ui/CopyValue";
import { FieldRow, Input } from "@/components/ui/Field";
import { LocalTime } from "@/components/ui/LocalTime";
import { useToast } from "@/components/ui/Toast";
import {
  createWebhook,
  deleteWebhook,
  listWebhookDeliveries,
  listWebhookEvents,
  listWebhooks,
  redeliverWebhook,
  rotateWebhookSecret,
  testWebhook,
  type WebhookDelivery,
  type WebhookEndpoint,
  type WebhookEventInfo,
  type WebhookScope,
} from "@/lib/api/webhooks";

/**
 * A tenant's webhooks (every application's events), or with `applicationId` one application's own (its events
 * only, managed by its administrators).
 */
export function WebhooksPanel({ tenantId, applicationId }: { tenantId?: string; applicationId?: string }) {
  const scope: WebhookScope = useMemo(
    () => (applicationId ? { applicationId } : (tenantId ?? "")),
    [tenantId, applicationId],
  );
  const { toast } = useToast();
  const [rows, setRows] = useState<WebhookEndpoint[]>([]);
  const [catalog, setCatalog] = useState<WebhookEventInfo[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [url, setUrl] = useState("");
  const [allEvents, setAllEvents] = useState(true);
  const [chosen, setChosen] = useState<string[]>([]);
  const [creating, setCreating] = useState(false);
  const [newSecret, setNewSecret] = useState<string | null>(null);
  const [openId, setOpenId] = useState<string | null>(null);
  const [deliveries, setDeliveries] = useState<WebhookDelivery[]>([]);

  const reload = useCallback(async () => {
    setLoaded(false);
    try {
      const [hooks, events] = await Promise.all([listWebhooks(scope), listWebhookEvents(scope).catch(() => [])]);
      setRows(hooks);
      setCatalog(events);
    } catch (e) {
      toast(e instanceof Error ? e.message : "Failed to load webhooks", "error");
    } finally {
      setLoaded(true);
    }
  }, [scope, toast]);

  useDeferredEffect(() => {
    reload();
  }, [reload]);

  async function handleCreate() {
    if (!url.trim()) return;
    if (!allEvents && chosen.length === 0) {
      toast("Choose at least one event, or All events.", "error");
      return;
    }
    setCreating(true);
    try {
      const created = await createWebhook(scope, {
        url: url.trim(),
        events: allEvents ? ["*"] : chosen,
        enabled: true,
      });
      if (created.secret) setNewSecret(created.secret);
      setUrl("");
      setChosen([]);
      setAllEvents(true);
      toast("Webhook created", "success");
      await reload();
    } catch (e) {
      toast(e instanceof Error ? e.message : "Create failed", "error");
    } finally {
      setCreating(false);
    }
  }

  async function toggleDeliveries(id: string) {
    if (openId === id) {
      setOpenId(null);
      return;
    }
    setOpenId(id);
    await loadDeliveries(id);
  }

  async function loadDeliveries(id: string) {
    try {
      setDeliveries((await listWebhookDeliveries(scope, id)).slice(0, 20));
    } catch {
      setDeliveries([]);
    }
  }

  async function handleTest(id: string) {
    try {
      const r = await testWebhook(scope, id);
      toast(
        r.ok ? `Test delivered (${r.responseCode})` : `Test not accepted: ${r.error ?? "no answer"}`,
        r.ok ? "success" : "error",
      );
      if (openId === id) await loadDeliveries(id);
    } catch (e) {
      toast(e instanceof Error ? e.message : "Test failed", "error");
    }
  }

  async function handleRotate(id: string) {
    const hours = window.prompt(
      "New signing secret. For how many hours should the old secret keep signing too, so your receiver can switch over? (0 to 168)",
      "24",
    );
    if (hours === null) return;
    const keep = Number(hours);
    if (!Number.isInteger(keep) || keep < 0 || keep > 168) {
      toast("Enter a whole number of hours from 0 to 168.", "error");
      return;
    }
    try {
      const rotated = await rotateWebhookSecret(scope, id, keep);
      setNewSecret(rotated.secret);
      toast("Signing secret rotated", "success");
      await reload();
    } catch (e) {
      toast(e instanceof Error ? e.message : "Rotation failed", "error");
    }
  }

  async function handleRedeliver(id: string, deliveryId: string) {
    try {
      await redeliverWebhook(scope, id, deliveryId);
      toast("Queued to send again", "success");
      await loadDeliveries(id);
    } catch (e) {
      toast(e instanceof Error ? e.message : "Could not send again", "error");
    }
  }

  return (
    <Card padded={false} className={!loaded ? "pointer-events-none opacity-60" : ""}>
      <CardHeader
        title="Webhooks"
        description={
          (applicationId
            ? "HTTPS notifications when this application's users, roles and sign-ins change (the tenant's webhooks also receive them). "
            : "HTTPS notifications when users, roles and sign-ins change in any of the tenant's applications. ") +
          "Signed with HMAC-SHA256 and a timestamp (X-SecureOne-Signature-256); a delivery that is not accepted is retried for about two days."
        }
      />
      <div className="space-y-4 px-5 pb-5">
        <FieldRow label="Endpoint URL">
          <Input value={url} onChange={(e) => setUrl(e.target.value)} placeholder="https://example.com/hooks/secureone" />
        </FieldRow>
        <fieldset className="space-y-2">
          <legend className="text-sm font-medium text-ui">Events</legend>
          <label className="flex items-center gap-2 text-sm text-ui">
            <input type="radio" checked={allEvents} onChange={() => setAllEvents(true)} /> All events
          </label>
          <label className="flex items-center gap-2 text-sm text-ui">
            <input type="radio" checked={!allEvents} onChange={() => setAllEvents(false)} /> Only these
          </label>
          {!allEvents && (
            <ul className="grid gap-1 rounded-xl border border-ui p-3 sm:grid-cols-2">
              {catalog.map((e) => (
                <li key={e.event}>
                  <label className="flex items-start gap-2 text-xs">
                    <input
                      type="checkbox"
                      className="mt-0.5"
                      checked={chosen.includes(e.event)}
                      onChange={(ev) =>
                        setChosen(ev.target.checked ? [...chosen, e.event] : chosen.filter((x) => x !== e.event))
                      }
                    />
                    <span>
                      <code className="font-mono text-[11px] text-ui">{e.event}</code>
                      <span className="block text-muted">{e.description}</span>
                    </span>
                  </label>
                </li>
              ))}
            </ul>
          )}
        </fieldset>
        <Button onClick={() => void handleCreate()} disabled={creating || !url.trim()}>
          {creating ? "Creating…" : "Add webhook"}
        </Button>

        {newSecret && (
          <div className="space-y-2 rounded-xl border border-amber-500/30 bg-amber-500/5 p-4">
            <p className="text-sm font-medium text-ui">Signing secret (copy now; it is not shown again)</p>
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
              <li key={row.id} className="px-4 py-3">
                <div className="flex items-center justify-between gap-3">
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium text-ui">{row.url}</p>
                    <p className="mt-0.5 text-xs text-muted">
                      {row.events.includes("*") || row.events.length === 0
                        ? "All events"
                        : `${row.events.length} event(s): ${row.events.join(", ")}`}
                    </p>
                    <Badge tone={row.enabled ? "success" : "neutral"} className="mt-1">
                      {row.enabled ? "enabled" : "disabled"}
                    </Badge>
                    {!applicationId && row.applicationId && (
                      <Badge tone="info" className="ml-1 mt-1">
                        one application&apos;s events
                      </Badge>
                    )}
                    {row.previousSecretValidUntil && (
                      <p className="mt-1 text-xs text-amber-700 dark:text-amber-300">
                        The old secret also signs until <LocalTime value={row.previousSecretValidUntil} format="datetime" />.
                      </p>
                    )}
                  </div>
                  <div className="flex shrink-0 flex-wrap justify-end gap-1">
                    <Button variant="ghost" size="sm" onClick={() => void handleTest(row.id)}>
                      Send test
                    </Button>
                    <Button variant="ghost" size="sm" onClick={() => void handleRotate(row.id)}>
                      Rotate secret
                    </Button>
                    <Button variant="ghost" size="sm" onClick={() => void toggleDeliveries(row.id)}>
                      {openId === row.id ? "Hide deliveries" : "Deliveries"}
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => {
                        if (!window.confirm(`Delete the webhook to ${row.url}? It stops receiving events.`)) return;
                        void deleteWebhook(scope, row.id)
                          .then(reload)
                          .catch((e) => toast(e instanceof Error ? e.message : "Delete failed", "error"));
                      }}
                    >
                      Delete
                    </Button>
                  </div>
                </div>
                {openId === row.id && (
                  <ul className="mt-3 space-y-1 text-xs">
                    {deliveries.length === 0 && <li className="text-muted">No deliveries yet.</li>}
                    {deliveries.map((d) => (
                      <li key={d.id} className="flex flex-wrap items-center justify-between gap-2">
                        <span>
                          <code className="font-mono">{d.eventType}</code> ·{" "}
                          <span className={d.status === "DELIVERED" ? "text-emerald-600" : "text-amber-600"}>
                            {d.status}
                            {d.responseCode ? ` (${d.responseCode})` : ""}
                          </span>
                          {(d.attempts ?? 0) > 1 && <span className="text-muted"> · {d.attempts} tries</span>}
                          {d.lastError && d.status !== "DELIVERED" && (
                            <span className="block text-muted">{d.lastError}</span>
                          )}
                          {d.status === "RETRYING" && d.nextAttemptAt && (
                            <span className="block text-muted">
                              Next try <LocalTime value={d.nextAttemptAt} format="datetime" />
                            </span>
                          )}
                        </span>
                        <span className="flex items-center gap-2 text-faint">
                          <LocalTime value={d.createdAt} format="datetime" />
                          {d.status === "FAILED" && (
                            <Button variant="ghost" size="sm" onClick={() => void handleRedeliver(row.id, d.id)}>
                              Send again
                            </Button>
                          )}
                        </span>
                      </li>
                    ))}
                  </ul>
                )}
              </li>
            ))}
          </ul>
        )}
      </div>
    </Card>
  );
}
