"use client";

import { Fragment, useCallback, useMemo, useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Input, Label } from "@/components/ui/Field";
import { Table, TBody, TD, TH, THead, TR } from "@/components/ui/Table";
import { cn } from "@/lib/cn";
import type { ApplicationLogEntry } from "@/lib/types";
import { withBasePath } from "@/lib/config";
import { LocalTime } from "@/components/ui/LocalTime";

type LevelFilter = "ALL" | "ERROR" | "WARN" | "INFO" | "DEBUG";
type RangeFilter = "1h" | "6h" | "24h" | "7d" | "all";

function formatDateInput(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

function localDayStart(dateStr: string): Date {
  const [y, m, d] = dateStr.split("-").map(Number);
  return new Date(y, m - 1, d, 0, 0, 0, 0);
}

function dayRange(dateStr: string): { since: string; until: string } {
  const start = localDayStart(dateStr);
  const end = new Date(start);
  end.setDate(end.getDate() + 1);
  return { since: start.toISOString(), until: end.toISOString() };
}

function todayRange() {
  return dayRange(formatDateInput(new Date()));
}

function yesterdayRange() {
  const d = new Date();
  d.setDate(d.getDate() - 1);
  return dayRange(formatDateInput(d));
}

function betweenRange(from: string, to: string): { since: string; until: string } | null {
  if (!from || !to) return null;
  const start = localDayStart(from);
  const end = localDayStart(to);
  if (end < start) return null;
  const until = new Date(end);
  until.setDate(until.getDate() + 1);
  return { since: start.toISOString(), until: until.toISOString() };
}

type Props = {
  applicationId?: string;
  tenantId?: string;
  initialSessionId?: string;
  initialRequestId?: string;
};

function sinceForRange(range: RangeFilter): string | undefined {
  if (range === "all") return undefined;
  const hours = range === "1h" ? 1 : range === "6h" ? 6 : range === "24h" ? 24 : 168;
  return new Date(Date.now() - hours * 60 * 60 * 1000).toISOString();
}

function levelTone(level: string): "success" | "danger" | "warning" | "neutral" {
  if (level === "ERROR") return "danger";
  if (level === "WARN") return "warning";
  if (level === "INFO") return "success";
  return "neutral";
}

function shortId(value: string | null, size = 8) {
  if (!value) return "—";
  return value.length <= size ? value : `${value.slice(0, size)}…`;
}

export function ApplicationLogViewer({ applicationId, tenantId, initialSessionId, initialRequestId }: Props) {
  const [logs, setLogs] = useState<ApplicationLogEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [sessionId, setSessionId] = useState(initialSessionId ?? "");
  const [requestId, setRequestId] = useState(initialRequestId ?? "");
  // Platform-wide rows (no app or tenant) are opt-in, and the server only returns them to platform operators.
  const [includePlatform, setIncludePlatform] = useState(false);
  const [search, setSearch] = useState("");
  const [level, setLevel] = useState<LevelFilter>("ALL");
  const [range, setRange] = useState<RangeFilter>("24h");
  const [autoRefresh, setAutoRefresh] = useState(false);
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [deleteOnDate, setDeleteOnDate] = useState(() => formatDateInput(new Date()));
  const [deleteFrom, setDeleteFrom] = useState(() => formatDateInput(new Date()));
  const [deleteTo, setDeleteTo] = useState(() => formatDateInput(new Date()));
  const [applyDeleteFilters, setApplyDeleteFilters] = useState(true);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [deleteStatus, setDeleteStatus] = useState<string | null>(null);

  const queryString = useMemo(() => {
    const params = new URLSearchParams();
    if (sessionId.trim()) params.set("sessionId", sessionId.trim());
    if (requestId.trim()) params.set("requestId", requestId.trim());
    if (level !== "ALL") params.set("level", level);
    if (applicationId) params.set("applicationId", applicationId);
    if (tenantId) params.set("tenantId", tenantId);
    params.set("includeUnscoped", includePlatform ? "true" : "false");
    const since = (sessionId.trim() || requestId.trim()) && range !== "all" ? undefined : sinceForRange(range);
    if (since) params.set("since", since);
    if (search.trim()) params.set("search", search.trim());
    params.set("limit", "300");
    return params.toString();
  }, [sessionId, requestId, level, applicationId, tenantId, range, search, includePlatform]);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await fetch(withBasePath(`/api/logs?${queryString}`), { cache: "no-store" });
      if (!res.ok) {
        const body = (await res.json().catch(() => null)) as { error?: string } | null;
        throw new Error(body?.error ?? `HTTP ${res.status}`);
      }
      const rows = (await res.json()) as ApplicationLogEntry[];
      setLogs(rows);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load logs");
      setLogs([]);
    } finally {
      setLoading(false);
    }
  }, [queryString]);

  useDeferredEffect(() => {
    void load();
  }, [load]);

  useDeferredEffect(() => {
    if (!autoRefresh) return;
    const id = window.setInterval(() => void load(), 10_000);
    return () => window.clearInterval(id);
  }, [autoRefresh, load]);

  const buildDeleteParams = useCallback(
    (since: string, until: string) => {
      const params = new URLSearchParams();
      params.set("since", since);
      params.set("until", until);
      // Deleting from an app's page removes only that app's rows, never platform-wide ones.
      params.set("includeUnscoped", "false");
      if (applicationId) params.set("applicationId", applicationId);
      if (tenantId) params.set("tenantId", tenantId);
      if (applyDeleteFilters) {
        if (sessionId.trim()) params.set("sessionId", sessionId.trim());
        if (requestId.trim()) params.set("requestId", requestId.trim());
        if (level !== "ALL") params.set("level", level);
        if (search.trim()) params.set("search", search.trim());
      }
      return params;
    },
    [applicationId, tenantId, applyDeleteFilters, sessionId, requestId, level, search],
  );

  const runDelete = useCallback(
    async (since: string, until: string, label: string) => {
      const filterNote = applyDeleteFilters
        ? " Only logs matching the current session/request/level/search filters will be removed."
        : "";
      if (
        !window.confirm(
          `Delete application logs for ${label}?${filterNote} This cannot be undone.`,
        )
      ) {
        return;
      }
      setDeleting(true);
      setDeleteStatus(null);
      setError(null);
      try {
        const res = await fetch(withBasePath(`/api/logs?${buildDeleteParams(since, until)}`), {
          method: "DELETE",
          cache: "no-store",
        });
        const body = (await res.json().catch(() => null)) as
          | { deleted?: number; matched?: number; error?: string }
          | null;
        if (!res.ok) {
          throw new Error(body?.error ?? `HTTP ${res.status}`);
        }
        setDeleteStatus(`Deleted ${body?.deleted ?? 0} log event(s) for ${label}.`);
        await load();
      } catch (e) {
        setError(e instanceof Error ? e.message : "Failed to delete logs");
      } finally {
        setDeleting(false);
      }
    },
    [applyDeleteFilters, buildDeleteParams, load],
  );

  const levelTabs: LevelFilter[] = ["ALL", "ERROR", "WARN", "INFO", "DEBUG"];
  const rangeTabs: { id: RangeFilter; label: string }[] = [
    { id: "1h", label: "1h" },
    { id: "6h", label: "6h" },
    { id: "24h", label: "24h" },
    { id: "7d", label: "7d" },
    { id: "all", label: "All" },
  ];

  return (
    <div className="space-y-4">
      <div className="rounded-xl border border-black/10 bg-black/[0.02] p-4 dark:border-white/10 dark:bg-white/[0.03]">
        <p className="mb-3 text-sm text-muted">
          Runtime log events for this application. Filter by session ID (from the Sessions page) or request ID, or
          search the message text. A session or request ID searches all time.
        </p>
        <div className="grid gap-3 md:grid-cols-2 lg:grid-cols-4">
          <div>
            <Label htmlFor="log-session">Session ID</Label>
            <Input
              id="log-session"
              placeholder="JSESSIONID or full value"
              value={sessionId}
              onChange={(e) => setSessionId(e.target.value)}
            />
          </div>
          <div>
            <Label htmlFor="log-request">Request ID</Label>
            <Input
              id="log-request"
              placeholder="X-Request-Id"
              value={requestId}
              onChange={(e) => setRequestId(e.target.value)}
            />
          </div>
          <div>
            <Label htmlFor="log-search">Search message</Label>
            <Input
              id="log-search"
              placeholder="error, login, oauth…"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>
          <div className="flex items-end gap-2">
            <Button type="button" className="flex-1" onClick={() => void load()} disabled={loading}>
              {loading ? "Loading…" : "Refresh"}
            </Button>
            <label className="flex items-center gap-2 text-xs text-muted">
              <input
                type="checkbox"
                checked={autoRefresh}
                onChange={(e) => setAutoRefresh(e.target.checked)}
              />
              Auto 10s
            </label>
          </div>
        </div>
        <div className="mt-3 flex flex-wrap items-center gap-3">
          <div className="flex gap-1 rounded-lg border border-black/10 p-1 dark:border-white/10">
            {levelTabs.map((tab) => (
              <button
                key={tab}
                type="button"
                onClick={() => setLevel(tab)}
                className={cn(
                  "rounded-md px-2.5 py-1 text-xs font-medium",
                  level === tab
                    ? "bg-brand text-on-brand"
                    : "text-muted hover:bg-black/5 dark:hover:bg-white/10",
                )}
              >
                {tab}
              </button>
            ))}
          </div>
          <div className="flex gap-1 rounded-lg border border-black/10 p-1 dark:border-white/10">
            {rangeTabs.map((tab) => (
              <button
                key={tab.id}
                type="button"
                onClick={() => setRange(tab.id)}
                className={cn(
                  "rounded-md px-2.5 py-1 text-xs font-medium",
                  range === tab.id
                    ? "bg-brand text-on-brand"
                    : "text-muted hover:bg-black/5 dark:hover:bg-white/10",
                )}
              >
                {tab.label}
              </button>
            ))}
          </div>
          <label className="flex items-center gap-2 text-xs text-muted">
            <input
              type="checkbox"
              checked={includePlatform}
              onChange={(e) => setIncludePlatform(e.target.checked)}
            />
            Include platform-wide events (operators only)
          </label>
          <span className="text-xs text-faint">{logs.length} events</span>
        </div>
      </div>

      <div className="rounded-xl border border-red-200/60 bg-red-50/40 py-2 dark:border-red-900/40 dark:bg-red-950/20">
        <button
          type="button"
          className="flex w-full items-start justify-between gap-3 px-5 py-4 text-left"
          onClick={() => setDeleteOpen((open) => !open)}
          aria-expanded={deleteOpen}
        >
          <div>
            <h2 className="text-sm font-semibold text-[var(--ui-text)]">Delete logs by date</h2>
            <p className="mt-1 text-xs text-muted">
              Removes stored log events from the database. Expand when you need to delete by day or range.
            </p>
          </div>
          <span className="mt-0.5 shrink-0 text-xs text-muted" aria-hidden>
            {deleteOpen ? "▲" : "▼"}
          </span>
        </button>
        {deleteOpen && (
          <div className="space-y-3 border-t border-red-200/60 px-5 py-5 dark:border-red-900/40">
            <p className="text-xs text-muted">Uses your local timezone for calendar days.</p>
            <label className="flex items-center gap-2 text-xs text-muted">
              <input
                type="checkbox"
                checked={applyDeleteFilters}
                onChange={(e) => setApplyDeleteFilters(e.target.checked)}
              />
              Also apply current session / request / level / message filters
            </label>
            <div className="flex flex-wrap gap-2">
              <Button
                type="button"
                variant="danger"
                size="sm"
                disabled={deleting}
                onClick={() => {
                  const r = todayRange();
                  void runDelete(r.since, r.until, "today");
                }}
              >
                Delete today
              </Button>
              <Button
                type="button"
                variant="danger"
                size="sm"
                disabled={deleting}
                onClick={() => {
                  const r = yesterdayRange();
                  void runDelete(r.since, r.until, "yesterday");
                }}
              >
                Delete yesterday
              </Button>
            </div>
            <div className="flex flex-wrap items-end gap-2">
              <div>
                <Label htmlFor="delete-on-date">On date</Label>
                <Input
                  id="delete-on-date"
                  type="date"
                  value={deleteOnDate}
                  onChange={(e) => setDeleteOnDate(e.target.value)}
                />
              </div>
              <Button
                type="button"
                variant="danger"
                size="sm"
                disabled={deleting || !deleteOnDate}
                onClick={() => {
                  const r = dayRange(deleteOnDate);
                  void runDelete(r.since, r.until, deleteOnDate);
                }}
              >
                Delete on date
              </Button>
            </div>
            <div className="flex flex-wrap items-end gap-2">
              <div>
                <Label htmlFor="delete-from">From</Label>
                <Input
                  id="delete-from"
                  type="date"
                  value={deleteFrom}
                  onChange={(e) => setDeleteFrom(e.target.value)}
                />
              </div>
              <div>
                <Label htmlFor="delete-to">To</Label>
                <Input
                  id="delete-to"
                  type="date"
                  value={deleteTo}
                  onChange={(e) => setDeleteTo(e.target.value)}
                />
              </div>
              <Button
                type="button"
                variant="danger"
                size="sm"
                disabled={deleting}
                onClick={() => {
                  const r = betweenRange(deleteFrom, deleteTo);
                  if (!r) {
                    setError("Invalid date range — end date must be on or after start date.");
                    return;
                  }
                  void runDelete(r.since, r.until, `${deleteFrom} → ${deleteTo}`);
                }}
              >
                Delete between dates
              </Button>
            </div>
            {deleteStatus && (
              <p className="text-sm text-green-800 dark:text-green-300">{deleteStatus}</p>
            )}
          </div>
        )}
      </div>

      {error && (
        <p className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800 dark:border-red-900/50 dark:bg-red-950/40 dark:text-red-200">
          {error}
        </p>
      )}

      <Table>
        <THead>
          <TR>
            <TH>Time</TH>
            <TH>Level</TH>
            <TH>Session</TH>
            <TH>User</TH>
            <TH>Message</TH>
          </TR>
        </THead>
        <TBody>
          {logs.map((row) => (
            <Fragment key={row.id}>
              <TR
                className="cursor-pointer hover:bg-black/[0.02] dark:hover:bg-white/[0.03]"
                onClick={() => setExpandedId(expandedId === row.id ? null : row.id)}
              >
                <TD className="whitespace-nowrap text-xs text-muted"><LocalTime value={row.timestamp} format="datetime" /></TD>
                <TD>
                  <Badge tone={levelTone(row.level)}>{row.level}</Badge>
                </TD>
                <TD className="font-mono text-xs">
                  {row.sessionId ? (
                    <button
                      type="button"
                      className="text-brand hover:underline"
                      title={row.sessionId}
                      onClick={(e) => {
                        e.stopPropagation();
                        setSessionId(row.sessionId ?? "");
                      }}
                    >
                      {shortId(row.sessionId, 12)}
                    </button>
                  ) : (
                    "—"
                  )}
                </TD>
                <TD className="max-w-[10rem] truncate text-xs">{row.principal ?? "—"}</TD>
                <TD className="max-w-xl truncate text-sm">{row.message}</TD>
              </TR>
              {expandedId === row.id && (
                <tr>
                  <td colSpan={5} className="bg-black/[0.02] px-4 py-3 text-xs dark:bg-white/[0.03]">
                    <div className="grid gap-1 font-mono text-muted">
                      <div>
                        <span className="text-faint">logger </span>
                        {row.logger}
                      </div>
                      <div>
                        <span className="text-faint">request </span>
                        {row.requestId ?? "—"}
                      </div>
                      <div>
                        <span className="text-faint">ip </span>
                        {row.ip ?? "—"}
                      </div>
                      {typeof row.metadata.stackTrace === "string" && (
                        <pre className="mt-2 max-h-48 overflow-auto whitespace-pre-wrap rounded border border-black/10 p-2 text-[11px] dark:border-white/10">
                          {row.metadata.stackTrace}
                        </pre>
                      )}
                    </div>
                  </td>
                </tr>
              )}
            </Fragment>
          ))}
          {!loading && logs.length === 0 && (
            <tr>
              <td colSpan={5} className="px-4 py-10 text-center text-sm text-faint">
                No log events match your filters.
              </td>
            </tr>
          )}
        </TBody>
      </Table>
    </div>
  );
}
