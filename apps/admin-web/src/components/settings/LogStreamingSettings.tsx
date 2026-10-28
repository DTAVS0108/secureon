"use client";

import { useCallback, useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { browserApiFetch as apiFetch } from "@/lib/api/browser-client";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { FieldRow, Input, Select } from "@/components/ui/Field";
import { LocalTime } from "@/components/ui/LocalTime";
import { Toggle } from "@/components/ui/Toggle";
import { useToast } from "@/components/ui/Toast";

type Stream = {
  id?: string;
  name: string;
  url: string;
  format: "json" | "splunk";
  enabled: boolean;
  headerName: string;
  /** Write-only; blank keeps the saved value. */
  headerValue?: string;
  headerValueSet?: boolean;
  lastDelivery?: { at: string; ok: boolean; status: number; error: string | null } | null;
};

type Config = { auditRetentionDays: number; signInHistoryRetentionDays: number; streams: Stream[] };

const PATH = "/api/admin/v1/settings/log-streams";
const EXAMPLES = "Use the collector's HTTPS intake URL and the header it expects for its API key or token (for example Authorization).";

/** Platform settings › Logs & streaming: retention and streaming the audit log to a SIEM. */
export function LogStreamingSettings() {
  const { toast } = useToast();
  const [config, setConfig] = useState<Config | null>(null);
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    try {
      setConfig(await apiFetch<Config>(PATH));
    } catch (e) {
      toast(e instanceof Error ? e.message : "Could not load log settings", "error");
    }
  }, [toast]);

  useDeferredEffect(() => {
    load();
  }, [load]);

  if (!config) return <p className="text-sm text-muted">Loading…</p>;

  const patchStream = (i: number, patch: Partial<Stream>) =>
    setConfig({ ...config, streams: config.streams.map((s, j) => (j === i ? { ...s, ...patch } : s)) });

  async function save() {
    if (!config) return;
    setSaving(true);
    try {
      setConfig(await apiFetch<Config>(PATH, { method: "PUT", body: JSON.stringify(config) }));
      toast("Log settings saved", "success");
    } catch (e) {
      toast(e instanceof Error ? e.message : "Save failed", "error");
    } finally {
      setSaving(false);
    }
  }

  async function test(id: string) {
    try {
      const r = await apiFetch<{ ok: boolean; status: number; error: string | null }>(`${PATH}/${id}/test`, { method: "POST" });
      toast(r.ok ? `Delivered (HTTP ${r.status})` : `Not delivered: ${r.error ?? "unknown error"}`, r.ok ? "success" : "error");
      await load();
    } catch (e) {
      toast(e instanceof Error ? e.message : "Test failed", "error");
    }
  }

  return (
    <div className="space-y-6">
      <Card padded={false}>
        <CardHeader title="Retention" description="Older entries are deleted every night (30 to 3650 days)." />
        <div className="grid gap-5 p-5 md:grid-cols-2">
          <FieldRow label="Keep the audit log for (days)">
            <Input
              type="number"
              min={30}
              max={3650}
              value={config.auditRetentionDays}
              onChange={(e) => setConfig({ ...config, auditRetentionDays: Number(e.target.value) })}
            />
          </FieldRow>
          <FieldRow label="Keep sign-in history for (days)">
            <Input
              type="number"
              min={30}
              max={3650}
              value={config.signInHistoryRetentionDays}
              onChange={(e) => setConfig({ ...config, signInHistoryRetentionDays: Number(e.target.value) })}
            />
          </FieldRow>
        </div>
      </Card>

      <Card padded={false}>
        <CardHeader
          title="Log streams"
          description="Send every audit entry, from every tenant, to your SIEM or log service as it happens (HTTPS, JSON). Up to 5."
        />
        <p className="px-5 text-xs text-muted">{EXAMPLES}</p>
        <div className="space-y-4 p-5">
          {config.streams.length === 0 && <p className="text-sm text-muted">No log streams.</p>}
          {config.streams.map((s, i) => (
            <div key={s.id ?? `new-${i}`} className="space-y-3 rounded-xl border border-ui p-4">
              <div className="flex items-center justify-between gap-3">
                <Input value={s.name} onChange={(e) => patchStream(i, { name: e.target.value })} placeholder="Name" />
                <Toggle checked={s.enabled} onChange={(v) => patchStream(i, { enabled: v })} aria-label="Stream enabled" />
              </div>
              <div className="grid gap-3 md:grid-cols-[2fr_1fr]">
                <Input value={s.url} onChange={(e) => patchStream(i, { url: e.target.value })} placeholder="https://…" />
                <Select value={s.format} onChange={(e) => patchStream(i, { format: e.target.value as Stream["format"] })}>
                  <option value="json">JSON (one entry per request)</option>
                  <option value="splunk">HTTP Event Collector (HEC)</option>
                </Select>
              </div>
              <div className="grid gap-3 md:grid-cols-2">
                <Input value={s.headerName} onChange={(e) => patchStream(i, { headerName: e.target.value })} placeholder="Header name" />
                <Input
                  type="password"
                  autoComplete="off"
                  value={s.headerValue ?? ""}
                  onChange={(e) => patchStream(i, { headerValue: e.target.value })}
                  placeholder={s.headerValueSet ? "Saved. Leave blank to keep." : "Header value (stored encrypted)"}
                />
              </div>
              <div className="flex flex-wrap items-center justify-between gap-2 text-xs">
                <span className="text-muted">
                  {s.lastDelivery ? (
                    <>
                      Last delivery <LocalTime value={s.lastDelivery.at} format="datetime" />:{" "}
                      <span className={s.lastDelivery.ok ? "text-emerald-600" : "text-red-600"}>
                        {s.lastDelivery.ok ? `OK (HTTP ${s.lastDelivery.status})` : s.lastDelivery.error ?? "failed"}
                      </span>
                    </>
                  ) : (
                    "No deliveries yet on this server."
                  )}
                </span>
                <span className="flex gap-2">
                  {s.id && (
                    <Button variant="secondary" size="sm" onClick={() => test(s.id!)}>
                      Send test entry
                    </Button>
                  )}
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => setConfig({ ...config, streams: config.streams.filter((_, j) => j !== i) })}
                  >
                    Remove
                  </Button>
                </span>
              </div>
            </div>
          ))}
          {config.streams.length < 5 && (
            <Button
              variant="secondary"
              onClick={() =>
                setConfig({
                  ...config,
                  streams: [...config.streams, { name: "", url: "", format: "json", enabled: true, headerName: "" }],
                })
              }
            >
              Add log stream
            </Button>
          )}
        </div>
      </Card>

      <div className="flex justify-end">
        <Button onClick={save} disabled={saving}>
          {saving ? "Saving…" : "Save log settings"}
        </Button>
      </div>
    </div>
  );
}
