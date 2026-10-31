"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { BrandMark } from "@/components/BrandMark";
import { withBasePath } from "@/lib/config";
import type { DocsHeading, DocsNode, DocsViewer } from "@/lib/documentation/types";
import { DocsSearch } from "./DocsSearch";
import { DocsSidebar } from "./DocsSidebar";
import { DocsToc } from "./DocsToc";
import { CloseIcon, MenuIcon, SearchIcon } from "./DocsIcons";

/**
 * The documentation frame: top bar (search, sign in / open console), space sidebar, the page, and "On this page".
 * Ctrl+K (or ⌘K, or "/") opens search from anywhere.
 */
export function DocsShell({
  spaces,
  activeSlug,
  viewer,
  headings,
  children,
}: {
  spaces: DocsNode[];
  activeSlug: string;
  viewer: DocsViewer;
  headings?: DocsHeading[];
  children: React.ReactNode;
}) {
  const [menuOpen, setMenuOpen] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);
  const [signingOut, setSigningOut] = useState(false);

  // Same sign-out as the console's top bar; stay on this page, which reloads for a signed-out reader.
  async function signOut() {
    setSigningOut(true);
    try {
      await fetch(withBasePath("/api/auth/logout"), { method: "POST" });
    } finally {
      window.location.reload();
    }
  }

  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      const typing = e.target instanceof HTMLElement && /^(INPUT|TEXTAREA|SELECT)$/.test(e.target.tagName);
      if ((e.key === "k" && (e.ctrlKey || e.metaKey)) || (e.key === "/" && !typing)) {
        e.preventDefault();
        setSearchOpen(true);
      }
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  const next = `/documentation${activeSlug ? `/${activeSlug}` : ""}`;
  const showToc = headings && headings.length >= 2;

  return (
    <div className="min-h-screen bg-ui-surface">
      <header className="sticky top-0 z-30 border-b border-ui bg-[color-mix(in_srgb,var(--ui-surface)_88%,transparent)] backdrop-blur">
        <div className="mx-auto flex h-14 max-w-[1440px] items-center gap-3 px-4 lg:px-6">
          <button
            type="button"
            onClick={() => setMenuOpen(true)}
            className="rounded-md p-1.5 text-soft hover:bg-ui-elevated lg:hidden"
            aria-label="Open the page list"
          >
            <MenuIcon className="h-5 w-5" />
          </button>
          <Link href="/documentation" className="flex shrink-0 items-center gap-2.5">
            <BrandMark size={28} className="rounded-lg" />
            <span className="text-[15px] font-semibold text-ui">SecureOne</span>
            <span className="hidden text-[15px] text-muted sm:inline">Documentation</span>
          </Link>
          <button
            type="button"
            onClick={() => setSearchOpen(true)}
            className="mx-auto flex h-9 w-full max-w-md items-center gap-2 rounded-lg border border-ui bg-ui-elevated px-3 text-sm text-faint transition-colors hover:border-[var(--ui-primary)]"
          >
            <SearchIcon className="h-4 w-4" />
            <span className="flex-1 text-left">Search documentation</span>
            <kbd className="docs-kbd hidden sm:inline">Ctrl K</kbd>
          </button>
          {viewer.signedIn ? (
            <div className="flex shrink-0 items-center gap-2">
              <a
                href={withBasePath("/")}
                className="rounded-lg bg-brand px-3 py-1.5 text-sm font-medium text-on-brand hover:opacity-90"
              >
                Open console
              </a>
              <button
                type="button"
                onClick={() => void signOut()}
                disabled={signingOut}
                className="rounded-lg border border-ui px-3 py-1.5 text-sm font-medium text-soft hover:bg-ui-elevated disabled:opacity-60"
              >
                {signingOut ? "Signing out…" : "Sign out"}
              </button>
            </div>
          ) : (
            <a
              href={withBasePath(`/login?next=${encodeURIComponent(next)}`)}
              className="shrink-0 rounded-lg border border-ui px-3 py-1.5 text-sm font-medium text-soft hover:bg-ui-elevated"
            >
              Sign in
            </a>
          )}
        </div>
      </header>

      <div className="mx-auto flex max-w-[1440px]">
        <aside className="sticky top-14 hidden h-[calc(100vh-3.5rem)] w-72 shrink-0 overflow-y-auto border-r border-ui lg:block">
          <DocsSidebar spaces={spaces} activeSlug={activeSlug} />
        </aside>

        <main className="min-w-0 flex-1 px-5 pb-20 pt-8 sm:px-8 lg:px-12">{children}</main>

        {showToc && (
          <aside className="sticky top-14 hidden h-[calc(100vh-3.5rem)] w-64 shrink-0 overflow-y-auto py-8 pr-6 xl:block">
            <DocsToc headings={headings} />
          </aside>
        )}
      </div>

      {menuOpen && (
        <div className="fixed inset-0 z-40 lg:hidden" role="dialog" aria-modal="true" aria-label="Pages">
          <div className="absolute inset-0 bg-black/40" onClick={() => setMenuOpen(false)} />
          <div className="absolute inset-y-0 left-0 w-80 max-w-[85vw] overflow-y-auto bg-ui-surface shadow-xl">
            <div className="flex items-center justify-between border-b border-ui px-4 py-3">
              <span className="text-sm font-semibold text-ui">Pages</span>
              <button type="button" onClick={() => setMenuOpen(false)} className="rounded-md p-1 text-soft" aria-label="Close">
                <CloseIcon className="h-5 w-5" />
              </button>
            </div>
            <DocsSidebar spaces={spaces} activeSlug={activeSlug} onNavigate={() => setMenuOpen(false)} />
          </div>
        </div>
      )}

      <DocsSearch open={searchOpen} onClose={() => setSearchOpen(false)} />
    </div>
  );
}
