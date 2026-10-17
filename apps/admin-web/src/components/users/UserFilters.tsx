"use client";

import { useEffect, useState } from "react";
import { createPortal } from "react-dom";
import { cn } from "@/lib/cn";

export type FilterOption = { value: string; label: string; count: number };

export type FilterSection = {
  id: string;
  label: string;
  options: FilterOption[];
  selected: string[];
  onChange: (selected: string[]) => void;
};

/**
 * "Filters" button that opens a dialog with the checkbox sections side by side (each option shows how
 * many rows it matches). Within a section options are or-ed; sections are and-ed.
 */
export function UserFilters({
  sections,
  title = "Filter users",
  resultCount,
}: {
  sections: FilterSection[];
  /** Dialog title (also its accessible name). */
  title?: string;
  /** Rows matching the current filters, shown in the dialog footer. */
  resultCount?: number;
}) {
  const [open, setOpen] = useState(false);
  const activeCount = sections.reduce((n, s) => n + s.selected.length, 0);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && setOpen(false);
    document.addEventListener("keydown", onKey);
    document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = "";
    };
  }, [open]);

  function toggle(section: FilterSection, value: string) {
    section.onChange(
      section.selected.includes(value)
        ? section.selected.filter((v) => v !== value)
        : [...section.selected, value],
    );
  }

  function clearAll() {
    sections.forEach((s) => s.selected.length > 0 && s.onChange([]));
  }

  const columns =
    sections.length >= 3 ? "sm:grid-cols-2 lg:grid-cols-3" : sections.length === 2 ? "sm:grid-cols-2" : "";

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        aria-haspopup="dialog"
        className={cn(
          "inline-flex h-9 items-center gap-2 whitespace-nowrap rounded-[var(--ui-radius)] border px-3 text-sm font-medium transition-colors",
          activeCount > 0
            ? "border-ui-primary bg-brand-muted text-brand"
            : "border-ui text-ui hover:bg-ui-elevated",
        )}
      >
        <svg aria-hidden viewBox="0 0 16 16" className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="1.5">
          <path d="M2 3.5h12M4.5 8h7M7 12.5h2" strokeLinecap="round" />
        </svg>
        Filters
        {activeCount > 0 && (
          <span className="rounded-full bg-[var(--ui-primary)] px-1.5 text-[10px] font-semibold leading-4 text-white">
            {activeCount}
          </span>
        )}
      </button>

      {open &&
        createPortal(
          <div className="fixed inset-0 z-100 flex items-start justify-center overflow-y-auto p-4 sm:p-8">
            <div className="fixed inset-0 bg-black/50 backdrop-blur-sm" onClick={() => setOpen(false)} />
            <div
              role="dialog"
              aria-modal="true"
              aria-label={title}
              className="relative z-10 my-4 flex max-h-[90vh] w-full max-w-[min(60rem,calc(100vw-2rem))] flex-col rounded-2xl border border-black/10 bg-[var(--background)] shadow-2xl dark:border-white/10"
            >
              <div className="flex items-start justify-between gap-4 border-b border-ui px-5 py-4">
                <div>
                  <h2 className="text-base font-semibold text-ui">{title}</h2>
                  <p className="mt-0.5 text-xs text-muted">
                    Tick any options. Within a section any match counts; across sections all must match.
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => setOpen(false)}
                  aria-label="Close"
                  className="rounded-lg px-2 text-lg leading-none text-black/40 hover:bg-black/5 dark:text-white/40 dark:hover:bg-white/10"
                >
                  ×
                </button>
              </div>

              <div className={cn("grid min-h-0 flex-1 gap-4 overflow-y-auto p-5", columns)}>
                {sections.map((section) => (
                  <fieldset key={section.id} className="min-w-0 rounded-xl border border-ui bg-ui-surface/50">
                    <legend className="sr-only">{section.label}</legend>
                    <div className="flex items-center justify-between gap-2 border-b border-ui px-3 py-2">
                      <span className="text-[11px] font-semibold uppercase tracking-wide text-faint">
                        {section.label}
                        {section.selected.length > 0 && (
                          <span className="ml-1.5 rounded-full bg-brand-muted px-1.5 text-brand">
                            {section.selected.length}
                          </span>
                        )}
                      </span>
                      {section.selected.length > 0 && (
                        <button
                          type="button"
                          onClick={() => section.onChange([])}
                          className="text-xs font-medium text-brand hover:underline"
                        >
                          Clear
                        </button>
                      )}
                    </div>
                    <div className="max-h-64 space-y-0.5 overflow-y-auto p-1.5">
                      {section.options.map((option) => {
                        const checked = section.selected.includes(option.value);
                        return (
                          <label
                            key={option.value}
                            className={cn(
                              "flex cursor-pointer items-center gap-2 rounded-md px-2 py-1.5 text-sm hover:bg-ui-elevated",
                              checked ? "bg-brand-muted/40 text-ui" : option.count === 0 ? "text-muted" : "text-ui",
                            )}
                            title={option.label}
                          >
                            <input
                              type="checkbox"
                              checked={checked}
                              onChange={() => toggle(section, option.value)}
                              className="h-4 w-4 shrink-0 accent-[var(--ui-primary)]"
                            />
                            <span className="min-w-0 flex-1 truncate">{option.label}</span>
                            <span
                              className={cn(
                                "shrink-0 rounded-full px-1.5 text-xs tabular-nums",
                                option.count > 0 ? "bg-ui-elevated text-muted" : "text-faint",
                              )}
                            >
                              {option.count}
                            </span>
                          </label>
                        );
                      })}
                    </div>
                  </fieldset>
                ))}
              </div>

              <div className="flex flex-wrap items-center justify-between gap-3 rounded-b-2xl border-t border-ui px-5 py-3.5">
                <p className="text-xs text-muted">
                  {activeCount === 0 ? "No filters" : `${activeCount} ${activeCount === 1 ? "filter" : "filters"} on`}
                  {resultCount !== undefined && ` · ${resultCount} ${resultCount === 1 ? "match" : "matches"}`}
                </p>
                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    onClick={clearAll}
                    disabled={activeCount === 0}
                    className="rounded-lg px-3 py-1.5 text-sm font-medium text-muted hover:bg-ui-elevated hover:text-ui disabled:opacity-40 disabled:hover:bg-transparent"
                  >
                    Clear all
                  </button>
                  <button
                    type="button"
                    onClick={() => setOpen(false)}
                    className="rounded-lg bg-[var(--ui-primary)] px-4 py-1.5 text-sm font-medium text-white hover:opacity-90"
                  >
                    Done
                  </button>
                </div>
              </div>
            </div>
          </div>,
          document.body,
        )}
    </>
  );
}

/** The active filters as chips; each × removes one value. */
export function ActiveFilterChips({ sections }: { sections: FilterSection[] }) {
  const chips = sections.flatMap((section) =>
    section.selected.map((value) => ({
      section,
      value,
      label: section.options.find((o) => o.value === value)?.label ?? value,
    })),
  );
  if (chips.length === 0) return null;

  return (
    <div className="flex flex-wrap items-center gap-2 pt-3">
      {chips.map(({ section, value, label }) => (
        <span
          key={`${section.id}:${value}`}
          className="inline-flex items-center gap-1 rounded-full border border-ui-primary bg-brand-muted py-0.5 pl-2.5 pr-1 text-xs text-brand"
        >
          <span className="text-faint">{section.label}:</span> {label}
          <button
            type="button"
            onClick={() => section.onChange(section.selected.filter((v) => v !== value))}
            aria-label={`Remove filter ${section.label} ${label}`}
            className="ml-0.5 rounded-full px-1 leading-none hover:bg-black/10 dark:hover:bg-white/10"
          >
            ×
          </button>
        </span>
      ))}
      <button
        type="button"
        onClick={() => sections.forEach((s) => s.selected.length > 0 && s.onChange([]))}
        className="text-xs text-muted hover:text-ui hover:underline"
      >
        Clear all
      </button>
    </div>
  );
}
