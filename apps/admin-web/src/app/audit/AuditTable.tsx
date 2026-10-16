"use client";

import { useMemo, useState } from "react";
import { Badge } from "@/components/ui/Badge";
import { Table, TBody, TD, TH, THead, TR } from "@/components/ui/Table";
import { Input } from "@/components/ui/Field";
import { cn } from "@/lib/cn";
import type { AuditEvent } from "@/lib/types";
import { LocalTime } from "@/components/ui/LocalTime";

type Filter = "all" | "success" | "failure";

/** The server returns at most this many of the newest events (AdminListLimit.DEFAULT). */
const SERVER_LIMIT = 500;

export function AuditTable({ events }: { events: AuditEvent[] }) {
  const [filter, setFilter] = useState<Filter>("all");
  const [query, setQuery] = useState("");

  const filtered = useMemo(() => {
    return events.filter((e) => {
      if (filter !== "all" && e.result !== filter) return false;
      if (query) {
        const q = query.toLowerCase();
        return (
          e.actor.toLowerCase().includes(q) ||
          e.action.toLowerCase().includes(q) ||
          e.target.toLowerCase().includes(q)
        );
      }
      return true;
    });
  }, [events, filter, query]);

  const tabs: { id: Filter; label: string }[] = [
    { id: "all", label: "All" },
    { id: "success", label: "Success" },
    { id: "failure", label: "Failures" },
  ];

  return (
    <div>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div className="flex gap-1 rounded-lg border border-black/10 p-1 dark:border-white/10">
          {tabs.map((t) => (
            <button
              key={t.id}
              onClick={() => setFilter(t.id)}
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
            placeholder="Filter by actor, action, target…"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        </div>
      </div>

      {events.length >= SERVER_LIMIT && (
        <p className="mb-3 text-xs text-muted">
          Showing the newest {SERVER_LIMIT} events. Filters apply to these events only.
        </p>
      )}

      <Table>
        <THead>
          <tr>
            <TH>Time</TH>
            <TH>Actor</TH>
            <TH>Action</TH>
            <TH>Target</TH>
            <TH>IP</TH>
            <TH>Result</TH>
          </tr>
        </THead>
        <TBody>
          {filtered.map((e) => (
            <TR key={e.id}>
              <TD className="whitespace-nowrap text-muted"><LocalTime value={e.timestamp} format="datetime" /></TD>
              <TD>{e.actor}</TD>
              <TD><code className="font-mono text-xs text-brand">{e.action}</code></TD>
              <TD className="text-soft">{e.target}</TD>
              <TD className="font-mono text-xs text-muted">{e.ip}</TD>
              <TD><Badge tone={e.result === "success" ? "success" : "danger"} dot>{e.result}</Badge></TD>
            </TR>
          ))}
          {filtered.length === 0 && (
            <tr>
              <td colSpan={6} className="px-4 py-10 text-center text-sm text-faint">
                No events match your filters.
              </td>
            </tr>
          )}
        </TBody>
      </Table>
    </div>
  );
}
