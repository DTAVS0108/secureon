"use client";

import { useMemo, useState } from "react";
import { Input } from "@/components/ui/Field";
import { cn } from "@/lib/cn";

export type SelectableItem = {
  id: string;
  label: string;
  /** Second line, e.g. a description or an email. */
  detail?: string | null;
  /** Small trailing content, e.g. a badge. */
  aside?: React.ReactNode;
};

/**
 * A titled, searchable checkbox list with Select all / Clear and a "n of m selected" count. When `name`
 * is set, the selection is posted as hidden inputs, so items hidden by the search keep their state.
 */
export function SelectableList({
  title,
  description,
  items,
  selected,
  onChange,
  name,
  searchPlaceholder = "Search…",
  emptyText,
  disabled = false,
  className,
}: {
  title: string;
  description?: string;
  items: SelectableItem[];
  selected: string[];
  onChange: (selected: string[]) => void;
  name?: string;
  searchPlaceholder?: string;
  emptyText: string;
  disabled?: boolean;
  className?: string;
}) {
  const [query, setQuery] = useState("");
  const visible = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return items;
    return items.filter(
      (item) => item.label.toLowerCase().includes(q) || (item.detail ?? "").toLowerCase().includes(q),
    );
  }, [items, query]);
  const selectedCount = selected.filter((id) => items.some((item) => item.id === id)).length;

  function toggle(id: string) {
    onChange(selected.includes(id) ? selected.filter((v) => v !== id) : [...selected, id]);
  }

  // Select all / Clear act on what the search shows, so "search + Select all" picks a subset.
  function selectVisible() {
    onChange([...new Set([...selected, ...visible.map((item) => item.id)])]);
  }
  function clearVisible() {
    const hide = new Set(visible.map((item) => item.id));
    onChange(selected.filter((id) => !hide.has(id)));
  }

  return (
    <section className={cn("flex min-w-0 flex-col overflow-hidden rounded-xl border border-ui bg-ui-surface/50", className)}>
      <header className="flex items-start justify-between gap-3 border-b border-ui bg-ui-elevated/40 px-4 py-3">
        <div className="min-w-0">
          <h3 className="text-sm font-semibold text-ui">{title}</h3>
          {description && <p className="mt-0.5 text-xs leading-relaxed text-muted">{description}</p>}
        </div>
        {items.length > 0 && (
          <span className="shrink-0 rounded-full bg-brand-muted px-2 py-0.5 text-[11px] font-medium tabular-nums text-brand">
            {selectedCount} of {items.length}
          </span>
        )}
      </header>
      {name && selected.map((id) => <input key={id} type="hidden" name={name} value={id} />)}
      {items.length === 0 ? (
        <p className="px-4 py-6 text-center text-sm text-muted">{emptyText}</p>
      ) : (
        <div className="flex min-h-0 flex-1 flex-col gap-3 p-4">
          <div className="flex items-center gap-3">
            <div className="min-w-0 flex-1">
              <Input
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder={searchPlaceholder}
                aria-label={searchPlaceholder}
                disabled={disabled}
              />
            </div>
            <button
              type="button"
              onClick={selectVisible}
              disabled={disabled || visible.every((item) => selected.includes(item.id))}
              className="whitespace-nowrap text-xs font-medium text-brand hover:underline disabled:no-underline disabled:opacity-40"
            >
              Select all
            </button>
            <button
              type="button"
              onClick={clearVisible}
              disabled={disabled || !visible.some((item) => selected.includes(item.id))}
              className="whitespace-nowrap text-xs font-medium text-muted hover:text-ui disabled:opacity-40"
            >
              Clear
            </button>
          </div>
          <div className="-mx-1 max-h-[22rem] min-h-0 space-y-1.5 overflow-y-auto px-1">
            {visible.map((item) => {
              const checked = selected.includes(item.id);
              return (
                <label
                  key={item.id}
                  className={cn(
                    "flex cursor-pointer items-start gap-2.5 rounded-lg border px-3 py-2 transition-colors",
                    checked ? "border-ui-primary bg-brand-muted/40" : "border-ui hover:bg-ui-elevated",
                    disabled && "cursor-not-allowed opacity-60",
                  )}
                >
                  <input
                    type="checkbox"
                    checked={checked}
                    onChange={() => toggle(item.id)}
                    disabled={disabled}
                    className="mt-0.5 h-4 w-4 accent-[var(--ui-primary)]"
                  />
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm font-medium text-ui">{item.label}</span>
                    {item.detail && <span className="block text-xs leading-snug text-muted">{item.detail}</span>}
                  </span>
                  {item.aside}
                </label>
              );
            })}
            {visible.length === 0 && <p className="px-1 py-2 text-xs text-muted">Nothing matches your search.</p>}
          </div>
        </div>
      )}
    </section>
  );
}
