"use client";

import Link from "next/link";
import { useState } from "react";
import { cn } from "@/lib/cn";
import type { DocsNode } from "@/lib/documentation/types";
import { ChevronIcon, LockIcon, SpaceIcon } from "./DocsIcons";

function contains(node: DocsNode, slug: string): boolean {
  return node.slug === slug || node.children.some((child) => contains(child, slug));
}

function TreeItem({ node, activeSlug, depth, onNavigate }: {
  node: DocsNode;
  activeSlug: string;
  depth: number;
  onNavigate?: () => void;
}) {
  const hasChildren = node.children.length > 0;
  const [open, setOpen] = useState(() => contains(node, activeSlug));
  const active = node.slug === activeSlug;
  return (
    <li>
      <div
        className={cn(
          "group flex items-center rounded-md transition-colors",
          active ? "bg-brand-muted text-brand" : "text-soft hover:bg-ui-elevated hover:text-ui",
        )}
        style={{ paddingLeft: depth * 12 }}
      >
        {hasChildren ? (
          <button
            type="button"
            onClick={() => setOpen((v) => !v)}
            className="flex h-7 w-6 shrink-0 items-center justify-center text-faint hover:text-ui"
            aria-label={open ? `Collapse ${node.title}` : `Expand ${node.title}`}
            aria-expanded={open}
          >
            <ChevronIcon className={cn("h-3.5 w-3.5 transition-transform", open && "rotate-90")} />
          </button>
        ) : (
          <span className="w-6 shrink-0" />
        )}
        <Link
          href={`/documentation/${node.slug}`}
          onClick={onNavigate}
          aria-current={active ? "page" : undefined}
          className={cn("flex min-w-0 flex-1 items-center gap-1.5 py-1.5 pr-2 text-[13px] leading-snug", active && "font-medium")}
        >
          <span className="truncate">{node.title}</span>
          {node.locked && <LockIcon className="h-3 w-3 shrink-0 text-faint" aria-label="Sign in to read" />}
        </Link>
      </div>
      {hasChildren && open && (
        <ul className="mt-0.5 space-y-0.5">
          {node.children.map((child) => (
            <TreeItem key={child.slug} node={child} activeSlug={activeSlug} depth={depth + 1} onNavigate={onNavigate} />
          ))}
        </ul>
      )}
    </li>
  );
}

/** Spaces with their page trees; the current space starts open, like a Confluence space sidebar. */
export function DocsSidebar({ spaces, activeSlug, onNavigate }: {
  spaces: DocsNode[];
  activeSlug: string;
  onNavigate?: () => void;
}) {
  const current = spaces.find((space) => contains(space, activeSlug));
  const [openSpaces, setOpenSpaces] = useState<Set<string>>(() => new Set(current ? [current.slug] : []));
  const toggle = (slug: string) =>
    setOpenSpaces((prev) => {
      const next = new Set(prev);
      if (next.has(slug)) next.delete(slug);
      else next.add(slug);
      return next;
    });

  return (
    <nav aria-label="Documentation" className="space-y-1 px-3 py-5">
      <Link
        href="/documentation"
        onClick={onNavigate}
        className={cn(
          "mb-3 flex items-center gap-2 rounded-md px-2 py-1.5 text-[13px] font-medium",
          activeSlug === "" ? "bg-brand-muted text-brand" : "text-soft hover:bg-ui-elevated hover:text-ui",
        )}
      >
        All spaces
      </Link>
      <p className="px-2 pb-1 text-[11px] font-semibold uppercase tracking-wider text-faint">Spaces</p>
      {spaces.map((space) => {
        const open = openSpaces.has(space.slug);
        const inSpace = space === current;
        return (
          <div key={space.slug}>
            <div
              className={cn(
                "flex items-center gap-2 rounded-md px-2 py-1.5 transition-colors",
                inSpace ? "text-ui" : "text-soft hover:bg-ui-elevated hover:text-ui",
              )}
            >
              <span
                className={cn(
                  "flex h-7 w-7 shrink-0 items-center justify-center rounded-lg",
                  inSpace ? "bg-brand text-on-brand" : "bg-ui-elevated text-muted",
                )}
              >
                <SpaceIcon slug={space.slug} className="h-4 w-4" />
              </span>
              <Link
                href={`/documentation/${space.slug}`}
                onClick={onNavigate}
                className={cn("min-w-0 flex-1 truncate text-[13px]", inSpace ? "font-semibold" : "font-medium")}
              >
                {space.title}
              </Link>
              {space.locked && <LockIcon className="h-3 w-3 text-faint" aria-label="Sign in to read" />}
              <button
                type="button"
                onClick={() => toggle(space.slug)}
                className="flex h-6 w-6 items-center justify-center rounded text-faint hover:text-ui"
                aria-label={open ? `Collapse ${space.title}` : `Expand ${space.title}`}
                aria-expanded={open}
              >
                <ChevronIcon className={cn("h-3.5 w-3.5 transition-transform", open && "rotate-90")} />
              </button>
            </div>
            {open && space.children.length > 0 && (
              <ul className="mb-2 ml-3 mt-0.5 space-y-0.5 border-l border-ui pl-1">
                {space.children.map((child) => (
                  <TreeItem key={child.slug} node={child} activeSlug={activeSlug} depth={0} onNavigate={onNavigate} />
                ))}
              </ul>
            )}
          </div>
        );
      })}
    </nav>
  );
}
