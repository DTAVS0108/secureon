"use client";

import { useEffect, useState } from "react";
import { createPortal } from "react-dom";
import { Button } from "./Button";
import { cn } from "@/lib/cn";

type Variant = "primary" | "secondary" | "ghost" | "danger";

export function Modal({
  triggerLabel,
  triggerVariant = "primary",
  triggerSize = "md",
  title,
  description,
  width = "md",
  children,
}: {
  triggerLabel: React.ReactNode;
  triggerVariant?: Variant;
  triggerSize?: "sm" | "md";
  title: string;
  description?: string;
  /** Kept for callers; every dialog is now 60% of the screen wide. */
  width?: "md" | "lg" | "xl" | "2xl";
  children: (close: () => void) => React.ReactNode;
}) {
  const [open, setOpen] = useState(false);

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

  return (
    <>
      <Button variant={triggerVariant} size={triggerSize} onClick={() => setOpen(true)}>
        {triggerLabel}
      </Button>
      {open && createPortal(
        // Rendered on <body>: inside a card or panel the dialog would join that element's stacking
        // context, and the page's top bar or tabs could paint over it whatever its z-index.
        <div className="fixed inset-0 z-100 flex items-start justify-center overflow-y-auto p-4 sm:p-8">
          <div
            className="fixed inset-0 bg-black/50 backdrop-blur-sm"
            onClick={() => setOpen(false)}
          />
          <div
            role="dialog"
            aria-modal="true"
            data-width={width}
            className={cn(
              // 60% of the screen (never below 36rem; full width on small screens).
              "relative z-10 my-4 flex max-h-[90vh] w-full max-w-[max(36rem,60vw)] flex-col rounded-2xl border border-black/10 bg-[var(--background)] shadow-2xl dark:border-white/10",
            )}
          >
            <div className="flex shrink-0 items-start justify-between gap-4 border-b border-black/10 px-5 py-4 dark:border-white/10">
              <div>
                <h2 className="text-base font-semibold text-ui">{title}</h2>
                {description && (
                  <p className="mt-0.5 text-xs leading-relaxed text-muted">{description}</p>
                )}
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
            <div className="min-h-0 flex-1 overflow-y-auto p-5">
              {children(() => setOpen(false))}
            </div>
          </div>
        </div>,
        document.body,
      )}
    </>
  );
}
