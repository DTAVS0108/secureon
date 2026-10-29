"use client";

import { useEffect, useState } from "react";
import { cn } from "@/lib/cn";
import type { DocsHeading } from "@/lib/documentation/types";

/** "On this page": the page's headings, with the one you are reading highlighted as you scroll. */
export function DocsToc({ headings }: { headings: DocsHeading[] }) {
  const [active, setActive] = useState<string | null>(headings[0]?.id ?? null);

  useEffect(() => {
    const elements = headings
      .map((h) => document.getElementById(h.id))
      .filter((el): el is HTMLElement => el !== null);
    if (elements.length === 0) return;
    const observer = new IntersectionObserver(
      (entries) => {
        const visible = entries.filter((e) => e.isIntersecting).sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top);
        if (visible[0]) setActive(visible[0].target.id);
      },
      { rootMargin: "-72px 0px -70% 0px" },
    );
    elements.forEach((el) => observer.observe(el));
    return () => observer.disconnect();
  }, [headings]);

  return (
    <nav aria-label="On this page" className="text-[13px]">
      <p className="mb-3 text-[11px] font-semibold uppercase tracking-wider text-faint">On this page</p>
      <ul className="space-y-1 border-l border-ui">
        {headings.map((h) => (
          <li key={h.id}>
            <a
              href={`#${h.id}`}
              onClick={(e) => {
                e.preventDefault();
                document.getElementById(h.id)?.scrollIntoView({ behavior: "smooth", block: "start" });
                history.replaceState(null, "", `#${h.id}`);
                setActive(h.id);
              }}
              className={cn(
                "-ml-px block border-l py-1 leading-snug transition-colors",
                h.depth === 3 ? "pl-6" : "pl-3",
                active === h.id ? "border-[var(--ui-primary)] font-medium text-brand" : "border-transparent text-muted hover:text-ui",
              )}
            >
              {h.text}
            </a>
          </li>
        ))}
      </ul>
    </nav>
  );
}
