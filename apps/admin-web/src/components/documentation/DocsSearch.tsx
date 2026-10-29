"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { withBasePath } from "@/lib/config";
import { cn } from "@/lib/cn";
import { CloseIcon, SearchIcon } from "./DocsIcons";

type Entry = { slug: string; title: string; summary: string; where: string; headings: string[]; text: string };
type Hit = { entry: Entry; score: number; snippet: string };

let indexPromise: Promise<Entry[]> | null = null;

function loadIndex(): Promise<Entry[]> {
  indexPromise ??= fetch(withBasePath("/api/documentation/search"), { credentials: "same-origin" })
    .then((r) => (r.ok ? (r.json() as Promise<Entry[]>) : []))
    .catch(() => {
      indexPromise = null;
      return [];
    });
  return indexPromise;
}

function snippetAround(text: string, term: string): string {
  const at = text.toLowerCase().indexOf(term);
  if (at < 0) return "";
  const start = Math.max(0, at - 60);
  return (start > 0 ? "…" : "") + text.slice(start, at + 100).trim() + "…";
}

/** Every word must appear somewhere; titles count most, then headings, the summary, and the text. */
function search(entries: Entry[], query: string): Hit[] {
  const terms = query.toLowerCase().split(/\s+/).filter((t) => t.length > 1);
  if (terms.length === 0) return [];
  const hits: Hit[] = [];
  for (const entry of entries) {
    const title = entry.title.toLowerCase();
    const summary = entry.summary.toLowerCase();
    const text = entry.text.toLowerCase();
    let score = 0;
    let snippet = "";
    let all = true;
    for (const term of terms) {
      let s = 0;
      if (title.includes(term)) s += title.startsWith(term) ? 14 : 10;
      const heading = entry.headings.find((h) => h.toLowerCase().includes(term));
      if (heading) {
        s += 5;
        snippet ||= heading;
      }
      if (summary.includes(term)) s += 3;
      if (text.includes(term)) {
        s += 1;
        snippet ||= snippetAround(entry.text, term);
      }
      if (s === 0) {
        all = false;
        break;
      }
      score += s;
    }
    if (all) hits.push({ entry, score, snippet: snippet || entry.summary });
  }
  return hits.sort((a, b) => b.score - a.score).slice(0, 12);
}

export function DocsSearch({ open, onClose }: { open: boolean; onClose: () => void }) {
  const router = useRouter();
  const [entries, setEntries] = useState<Entry[] | null>(null);
  const [query, setQuery] = useState("");
  const [selected, setSelected] = useState(0);
  const input = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    void loadIndex().then((list) => {
      if (!cancelled) setEntries(list);
    });
    const t = setTimeout(() => input.current?.focus(), 10);
    return () => {
      cancelled = true;
      clearTimeout(t);
    };
  }, [open]);

  const hits = useMemo(() => (entries ? search(entries, query) : []), [entries, query]);

  if (!open) return null;

  function go(hit: Hit | undefined) {
    if (!hit) return;
    onClose();
    setQuery("");
    router.push(`/documentation/${hit.entry.slug}`);
  }

  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center bg-black/40 px-4 pt-[12vh] backdrop-blur-sm" onClick={onClose}>
      <div
        role="dialog"
        aria-modal="true"
        aria-label="Search documentation"
        className="w-full max-w-2xl overflow-hidden rounded-2xl border border-ui bg-ui-surface shadow-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center gap-3 border-b border-ui px-4">
          <SearchIcon className="h-5 w-5 text-faint" />
          <input
            ref={input}
            value={query}
            onChange={(e) => {
              setQuery(e.target.value);
              setSelected(0);
            }}
            onKeyDown={(e) => {
              if (e.key === "Escape") onClose();
              else if (e.key === "ArrowDown") {
                e.preventDefault();
                setSelected((i) => Math.min(i + 1, Math.max(hits.length - 1, 0)));
              } else if (e.key === "ArrowUp") {
                e.preventDefault();
                setSelected((i) => Math.max(i - 1, 0));
              } else if (e.key === "Enter") go(hits[selected]);
            }}
            placeholder="Search documentation"
            aria-label="Search documentation"
            className="h-14 flex-1 bg-transparent text-base text-ui outline-none placeholder:text-faint"
          />
          <button type="button" onClick={onClose} className="rounded-md p-1.5 text-faint hover:bg-ui-elevated" aria-label="Close search">
            <CloseIcon className="h-4 w-4" />
          </button>
        </div>
        <div className="max-h-[60vh] overflow-y-auto p-2">
          {entries === null ? (
            <p className="px-3 py-8 text-center text-sm text-muted">Loading…</p>
          ) : query.trim().length < 2 ? (
            <p className="px-3 py-8 text-center text-sm text-muted">Type at least two letters to search every page.</p>
          ) : hits.length === 0 ? (
            <p className="px-3 py-8 text-center text-sm text-muted">No pages match “{query}”.</p>
          ) : (
            <ul role="listbox" aria-label="Results">
              {hits.map((hit, i) => (
                <li key={hit.entry.slug} role="option" aria-selected={i === selected}>
                  <button
                    type="button"
                    onMouseEnter={() => setSelected(i)}
                    onClick={() => go(hit)}
                    className={cn(
                      "w-full rounded-xl px-3 py-2.5 text-left transition-colors",
                      i === selected ? "bg-brand-muted" : "hover:bg-ui-elevated",
                    )}
                  >
                    <span className="block text-[11px] text-faint">{hit.entry.where || "Documentation"}</span>
                    <span className={cn("block text-sm font-medium", i === selected ? "text-brand" : "text-ui")}>
                      {hit.entry.title}
                    </span>
                    {hit.snippet && <span className="mt-0.5 line-clamp-2 block text-xs text-muted">{hit.snippet}</span>}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
        <div className="flex items-center gap-4 border-t border-ui px-4 py-2 text-[11px] text-faint">
          <span>
            <kbd className="docs-kbd">↑</kbd> <kbd className="docs-kbd">↓</kbd> to move
          </span>
          <span>
            <kbd className="docs-kbd">Enter</kbd> to open
          </span>
          <span>
            <kbd className="docs-kbd">Esc</kbd> to close
          </span>
        </div>
      </div>
    </div>
  );
}
