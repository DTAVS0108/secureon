"use client";

import { cn } from "@/lib/cn";

/**
 * Info icon with hover tooltip. Use beside field labels to explain setup and behavior.
 */
export function InfoTip({
  content,
  className,
  maxWidth = "max-w-md",
}: {
  content: string;
  className?: string;
  maxWidth?: string;
}) {
  return (
    <span className={cn("group/info relative inline-flex align-middle", className)}>
      <button
        type="button"
        tabIndex={0}
        aria-label="More information"
        className="ml-1.5 inline-flex h-4 w-4 shrink-0 items-center justify-center rounded-full border border-ui text-[10px] font-bold leading-none text-muted transition-colors hover:border-brand hover:text-brand focus:outline-none focus:ring-2 focus:ring-brand/30"
      >
        i
      </button>
      <span
        role="tooltip"
        className={cn(
          "pointer-events-none absolute bottom-full left-1/2 z-50 mb-2 w-max min-w-72 -translate-x-1/2 rounded-md border border-ui bg-ui-elevated px-3 py-2 text-left text-xs font-normal leading-relaxed text-[var(--ui-text)] opacity-0 shadow-lg transition-opacity duration-150",
          "group-hover/info:opacity-100 group-focus-within/info:opacity-100",
          maxWidth,
        )}
      >
        {content}
        <span
          className="absolute left-1/2 top-full -translate-x-1/2 border-4 border-transparent border-t-ui-elevated"
          aria-hidden
        />
      </span>
    </span>
  );
}
