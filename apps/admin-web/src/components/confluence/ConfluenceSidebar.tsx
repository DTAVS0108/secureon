"use client";

import Link from "next/link";
import { useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { CONFLUENCE_CATEGORIES, type ConfluenceCategory } from "@/lib/confluence/catalog";
import { cn } from "@/lib/cn";

function ConfluenceCategorySection({
  category,
  activeSlug,
}: {
  category: ConfluenceCategory;
  activeSlug: string;
}) {
  const categoryActive = category.pages.some((page) => page.slug === activeSlug);
  const [open, setOpen] = useState(categoryActive);

  useDeferredEffect(() => {
    if (categoryActive) setOpen(true);
  }, [categoryActive]);

  return (
    <div className="flex flex-col">
      <button
        type="button"
        onClick={() => setOpen((value) => !value)}
        className={cn(
          "flex w-full items-start justify-between gap-2 rounded-lg px-2 py-2 text-left transition-colors",
          categoryActive ? "text-ui" : "text-soft hover:bg-ui-elevated",
        )}
        aria-expanded={open}
      >
        <span className="min-w-0">
          <span className="block text-[11px] font-semibold uppercase tracking-wider text-faint">
            {category.label}
          </span>
          {category.description && (
            <span className="mt-0.5 block text-[10px] leading-snug text-muted">{category.description}</span>
          )}
        </span>
        <span
          className={cn(
            "mt-0.5 shrink-0 text-[10px] text-faint transition-transform duration-200",
            open ? "rotate-180" : "",
          )}
          aria-hidden
        >
          ▼
        </span>
      </button>
      {open && (
        <div className="mb-1 ml-2 flex flex-col gap-0.5 border-l border-ui pl-2">
          {category.pages.map((page) => {
            const active = page.slug === activeSlug;
            return (
              <Link
                key={page.slug}
                href={`/confluence/${page.slug}`}
                className={cn(
                  "rounded-lg px-2.5 py-2 text-sm transition-colors",
                  active ? "font-medium text-brand" : "text-soft hover:bg-ui-elevated",
                )}
                style={
                  active
                    ? { backgroundColor: "color-mix(in srgb, var(--ui-primary) 10%, transparent)" }
                    : undefined
                }
              >
                <span className="block">{page.title}</span>
                {page.description && (
                  <span className="mt-0.5 block text-[11px] font-normal text-muted">{page.description}</span>
                )}
              </Link>
            );
          })}
        </div>
      )}
    </div>
  );
}

export function ConfluenceSidebar({ activeSlug }: { activeSlug: string }) {
  return (
    <aside className="w-64 shrink-0 border-r border-ui bg-ui-surface">
      <div className="sticky top-0 max-h-screen overflow-y-auto px-3 py-4">
        <div className="mb-4 px-2">
          <p className="text-xs font-semibold uppercase tracking-wider text-faint">SecureOne Confluence</p>
          <p className="mt-0.5 text-xs text-muted">Browse by category</p>
        </div>
        <nav className="flex flex-col gap-2">
          {CONFLUENCE_CATEGORIES.map((category) => (
            <ConfluenceCategorySection key={category.id} category={category} activeSlug={activeSlug} />
          ))}
        </nav>
      </div>
    </aside>
  );
}
