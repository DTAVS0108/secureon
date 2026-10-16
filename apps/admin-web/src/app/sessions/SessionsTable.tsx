"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { Badge } from "@/components/ui/Badge";
import { Input } from "@/components/ui/Field";
import { Table, TBody, TD, TH, THead, TR } from "@/components/ui/Table";
import { cn } from "@/lib/cn";
import type { LoginEvent } from "@/lib/types";
import { LocalTime } from "@/components/ui/LocalTime";

type Filter = "all" | LoginEvent["result"];

/** The server returns at most this many of the newest sign-ins (AdminListLimit.DEFAULT). */
const SERVER_LIMIT = 500;

const RESULT_LABEL: Record<LoginEvent["result"], string> = {
  success: "Signed in",
  failure: "Failed",
  mfa_required: "MFA required",
};

function resultTone(result: LoginEvent["result"]): "success" | "danger" | "neutral" {
  if (result === "success") return "success";
  if (result === "failure") return "danger";
  return "neutral";
}

export function SessionsTable({ applicationId, events }: { applicationId: string; events: LoginEvent[] }) {
  const [filter, setFilter] = useState<Filter>("all");
  const [query, setQuery] = useState("");

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return events.filter((e) => {
      if (filter !== "all" && e.result !== filter) return false;
      if (!q) return true;
      return (
        e.userEmail.toLowerCase().includes(q) ||
        (e.sessionId ?? "").toLowerCase().includes(q) ||
        (e.ip ?? "").toLowerCase().includes(q)
      );
    });
  }, [events, filter, query]);

  const tabs: { id: Filter; label: string }[] = [
    { id: "all", label: "All" },
    { id: "success", label: "Signed in" },
    { id: "failure", label: "Failed" },
    { id: "mfa_required", label: "MFA required" },
  ];

  return (
    <div>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div className="flex gap-1 rounded-lg border border-black/10 p-1 dark:border-white/10">
          {tabs.map((t) => (
            <button
              key={t.id}
              type="button"
              onClick={() => setFilter(t.id)}
              aria-pressed={filter === t.id}
              className={cn(
                "rounded-md px-3 py-1 text-xs font-medium transition-colors",
                filter === t.id
                  ? "bg-brand text-on-brand"
                  : "text-black/60 hover:bg-black/5 dark:text-white/60 dark:hover:bg-white/10",
              )}
            >
              {t.label}
            </button>
          ))}
        </div>
        <div className="w-full max-w-xs">
          <Input
            aria-label="Filter sign-ins"
            placeholder="Filter by user, session, IP…"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        </div>
      </div>

      {events.length >= SERVER_LIMIT && (
        <p className="mb-3 text-xs text-muted">
          Showing the newest {SERVER_LIMIT} sign-ins. Filters apply to these sign-ins only.
        </p>
      )}

      <Table>
        <THead>
          <TR>
            <TH>User</TH>
            <TH>Method</TH>
            <TH>Result</TH>
            <TH>IP</TH>
            <TH>Session</TH>
            <TH>When</TH>
          </TR>
        </THead>
        <TBody>
          {filtered.map((s) => (
            <TR key={s.id}>
              <TD>
                {s.userId ? (
                  <Link href={`/app/${applicationId}/users?user=${s.userId}`} className="hover:underline">
                    {s.userEmail}
                  </Link>
                ) : (
                  s.userEmail
                )}
              </TD>
              <TD>{s.method}</TD>
              <TD>
                <Badge tone={resultTone(s.result)}>{RESULT_LABEL[s.result] ?? s.result}</Badge>
              </TD>
              <TD className="font-mono text-xs text-muted">{s.ip || "—"}</TD>
              <TD className="font-mono text-xs">
                {s.sessionId ? (
                  <Link
                    href={`/app/${applicationId}/logs?sessionId=${encodeURIComponent(s.sessionId)}`}
                    className="text-brand hover:underline"
                    title={`View logs for session ${s.sessionId}`}
                  >
                    {s.sessionId.slice(0, 12)}…
                  </Link>
                ) : (
                  "—"
                )}
              </TD>
              <TD className="whitespace-nowrap text-sm text-muted"><LocalTime value={s.timestamp} format="datetime" /></TD>
            </TR>
          ))}
          {filtered.length === 0 && (
            <tr>
              <td colSpan={6} className="px-4 py-10 text-center text-sm text-faint">
                {events.length === 0 ? "No sign-ins for this application yet." : "No sign-ins match your filters."}
              </td>
            </tr>
          )}
        </TBody>
      </Table>
    </div>
  );
}
