"use client";

import { useCallback, useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { Input, Select } from "@/components/ui/Field";
import { LocalTime } from "@/components/ui/LocalTime";
import { Table, TBody, TD, TH, THead, TR } from "@/components/ui/Table";
import { useToast } from "@/components/ui/Toast";
import { downloadAuditCsv, searchAudit, type AuditFilter, type AuditRow } from "@/lib/api/audit-search";
import { listTenants } from "@/lib/api/tenants";

/** "2026-10-24" (date input) → start of that day in the viewer's time zone, as ISO. */
function dayStart(value: string): string | undefined {
  return value ? new Date(`${value}T00:00:00`).toISOString() : undefined;
}
function dayEnd(value: string): string | undefined {
  if (!value) return undefined;
  const d = new Date(`${value}T00:00:00`);
  d.setDate(d.getDate() + 1);
  return d.toISOString();
}

/**
 * Audit log search with filters and CSV export. Without {@code applicationId}: every tenant (platform super admins),
 * with a tenant filter.
 */
export function AuditSearchPanel({ applicationId }: { applicationId?: string }) {
  const { toast } = useToast();
  const [rows, setRows] = useState<AuditRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [tenants, setTenants] = useState<{ id: string; name: string }[]>([]);
  const [tenantId, setTenantId] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [action, setAction] = useState("");
  const [actor, setActor] = useState("");
  const [result, setResult] = useState<"" | "success" | "failure">("");
  const [exporting, setExporting] = useState(false);

  const filter = useCallback(
    (): AuditFilter => ({
      applicationId,
      tenantId: applicationId ? undefined : tenantId || undefined,
      from: dayStart(from),
      to: dayEnd(to),
      action: action.trim() || undefined,
      actor: actor.trim() || undefined,
      result,
    }),
    [applicationId, tenantId, from, to, action, actor, result],
  );

  const search = useCallback(async () => {
    setLoading(true);
    try {
      setRows(await searchAudit(filter()));
    } catch (e) {
      toast(e instanceof Error ? e.message : "Could not load the audit log", "error");
    } finally {
      setLoading(false);
    }
  }, [filter, toast]);

  useDeferredEffect(() => {
    search();
    if (!applicationId) {
      listTenants()
        .then((t) => setTenants(t.map((x) => ({ id: x.id, name: x.name }))))
        .catch(() => setTenants([]));
    }
    // Load once; later searches run from the Search button.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function exportCsv() {
    setExporting(true);
    try {
      await downloadAuditCsv(filter());
    } catch (e) {
      toast(e instanceof Error ? e.message : "Export failed", "error");
    } finally {
      setExporting(false);
    }
  }

  return (
    <div className="space-y-4">
      <Card>
        <form
          className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4"
          onSubmit={(e) => {
            e.preventDefault();
            search();
          }}
        >
          {!applicationId && (
            <Select value={tenantId} onChange={(e) => setTenantId(e.target.value)} aria-label="Tenant">
              <option value="">All tenants</option>
              {tenants.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name}
                </option>
              ))}
            </Select>
          )}
          <Input type="date" value={from} onChange={(e) => setFrom(e.target.value)} aria-label="From" title="From" />
          <Input type="date" value={to} onChange={(e) => setTo(e.target.value)} aria-label="To" title="To" />
          <Select value={result} onChange={(e) => setResult(e.target.value as "" | "success" | "failure")} aria-label="Result">
            <option value="">Success and failure</option>
            <option value="success">Success</option>
            <option value="failure">Failure</option>
          </Select>
          <Input value={action} onChange={(e) => setAction(e.target.value)} placeholder="Action contains (user.locked…)" />
          <Input value={actor} onChange={(e) => setActor(e.target.value)} placeholder="Actor contains (email)" />
          <div className="flex gap-2 sm:col-span-2 lg:col-span-2 lg:justify-end">
            <Button type="submit" disabled={loading}>
              {loading ? "Searching…" : "Search"}
            </Button>
            <Button type="button" variant="secondary" onClick={exportCsv} disabled={exporting}>
              {exporting ? "Exporting…" : "Export CSV"}
            </Button>
          </div>
        </form>
      </Card>

      <p className="text-xs text-muted">
        Newest {rows.length} entries shown (up to 500). Export CSV includes up to 100,000 matching entries.
      </p>
      <Card padded={false}>
        <Table>
          <THead>
            <TR>
              <TH>Time</TH>
              <TH>Action</TH>
              <TH>Actor</TH>
              <TH>Target</TH>
              {!applicationId && <TH>Tenant / application</TH>}
              <TH>Result</TH>
              <TH>IP</TH>
            </TR>
          </THead>
          <TBody>
            {rows.map((r) => (
              <TR key={r.id}>
                <TD className="whitespace-nowrap text-xs">
                  <LocalTime value={r.timestamp} format="datetime" />
                </TD>
                <TD>
                  <code className="font-mono text-xs">{r.action}</code>
                </TD>
                <TD className="text-xs">{r.actor}</TD>
                <TD className="text-xs">{r.target}</TD>
                {!applicationId && (
                  <TD className="text-xs text-muted">{[r.tenant, r.application].filter(Boolean).join(" · ") || "—"}</TD>
                )}
                <TD>
                  <Badge tone={r.result === "failure" ? "danger" : "success"}>{r.result}</Badge>
                </TD>
                <TD className="font-mono text-xs text-muted">{r.ip ?? "—"}</TD>
              </TR>
            ))}
          </TBody>
        </Table>
        {!loading && rows.length === 0 && <p className="px-5 py-6 text-sm text-muted">No matching entries.</p>}
      </Card>
    </div>
  );
}
