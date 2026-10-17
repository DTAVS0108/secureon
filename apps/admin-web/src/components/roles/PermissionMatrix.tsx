"use client";

import { cn } from "@/lib/cn";
import type { Permission } from "@/lib/types";

function groupByResource(permissions: Permission[]): Map<string, Permission[]> {
  const groups = new Map<string, Permission[]>();
  for (const p of permissions) {
    const list = groups.get(p.resource) ?? [];
    list.push(p);
    groups.set(p.resource, list);
  }
  return groups;
}

export function PermissionMatrix({
  permissions,
  selectedIds,
  onChange,
  readOnly = false,
  compact = false,
  groupToggle = false,
}: {
  permissions: Permission[];
  selectedIds: Set<string>;
  onChange?: (ids: Set<string>) => void;
  readOnly?: boolean;
  compact?: boolean;
  /** Show "Select all / Clear" per resource. Only for callers whose onChange accepts several changes. */
  groupToggle?: boolean;
}) {
  const groups = groupByResource(permissions);

  if (permissions.length === 0) {
    return (
      <p className="text-sm text-muted">No permissions defined for this application yet.</p>
    );
  }

  return (
    <div className={cn("space-y-4", compact && "space-y-3")}>
      {[...groups.entries()].map(([resource, items]) => (
        <div key={resource} className="rounded-lg border border-ui">
          <div className="flex items-center justify-between gap-3 border-b border-ui bg-ui-elevated px-3 py-2">
            <p className="text-xs font-semibold uppercase tracking-wide text-muted">{resource}</p>
            {!readOnly && onChange && groupToggle && (
              <ResourceToggle items={items} selectedIds={selectedIds} onChange={onChange} />
            )}
          </div>
          <ul className="divide-y divide-ui">
            {items.map((p) => {
              const checked = selectedIds.has(p.id);
              return (
                <li key={p.id} className="flex items-start gap-3 px-3 py-2.5">
                  {!readOnly && onChange ? (
                    <input
                      type="checkbox"
                      checked={checked}
                      onChange={() => {
                        const next = new Set(selectedIds);
                        if (checked) next.delete(p.id);
                        else next.add(p.id);
                        onChange(next);
                      }}
                      className="mt-0.5 h-4 w-4 accent-[var(--ui-primary,#4f46e5)]"
                      aria-label={p.key}
                    />
                  ) : (
                    <span
                      className={cn(
                        "mt-1 h-2 w-2 shrink-0 rounded-full",
                        checked ? "bg-brand" : "bg-ui-elevated ring-1 ring-ui",
                      )}
                      aria-hidden
                    />
                  )}
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <code className="font-mono text-xs text-ui">{p.key}</code>
                      <span className="text-[10px] uppercase text-faint">{p.action}</span>
                    </div>
                    <p className="text-xs text-muted">{p.description}</p>
                  </div>
                </li>
              );
            })}
          </ul>
        </div>
      ))}
    </div>
  );
}

/** "Select all" / "Clear" for one resource's permissions. */
function ResourceToggle({
  items,
  selectedIds,
  onChange,
}: {
  items: Permission[];
  selectedIds: Set<string>;
  onChange: (ids: Set<string>) => void;
}) {
  const chosen = items.filter((p) => selectedIds.has(p.id)).length;
  const all = chosen === items.length;
  return (
    <span className="flex items-center gap-2 text-[11px]">
      <span className="tabular-nums text-faint">
        {chosen}/{items.length}
      </span>
      <button
        type="button"
        onClick={() => {
          const next = new Set(selectedIds);
          items.forEach((p) => (all ? next.delete(p.id) : next.add(p.id)));
          onChange(next);
        }}
        className="font-medium text-brand hover:underline"
      >
        {all ? "Clear" : "Select all"}
      </button>
    </span>
  );
}
