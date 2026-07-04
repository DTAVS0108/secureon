"use client";

import { useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { createPortal } from "react-dom";
import { cn } from "@/lib/cn";
import { OperatorProfilePanel } from "./OperatorProfilePanel";

export function OperatorProfileModal({
  open,
  onClose,
}: {
  open: boolean;
  onClose: () => void;
}) {
  const [mounted, setMounted] = useState(false);

  useDeferredEffect(() => {
    setMounted(true);
  }, []);

  useDeferredEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    document.addEventListener("keydown", onKey);
    document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = "";
    };
  }, [open, onClose]);

  if (!open || !mounted) return null;

  return createPortal(
    <div className="fixed inset-0 z-[9999] flex items-start justify-center overflow-y-auto p-4 sm:p-8">
      <div
        className="fixed inset-0 z-[9999] bg-black/50 backdrop-blur-sm"
        onClick={onClose}
        aria-hidden
      />
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="operator-profile-title"
        className={cn(
          "relative z-[10000] my-4 flex max-h-[min(90vh,48rem)] w-full max-w-lg flex-col",
          "rounded-2xl border border-ui bg-[var(--ui-surface)] shadow-2xl",
        )}
      >
        <div className="flex shrink-0 items-start justify-between gap-4 border-b border-ui px-5 py-4">
          <div>
            <h2 id="operator-profile-title" className="text-base font-semibold text-ui">
              My profile
            </h2>
            <p className="mt-0.5 text-xs text-muted">Account details, password, and MFA</p>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close"
            className="rounded-lg px-2 text-lg leading-none text-faint hover:bg-ui-elevated"
          >
            ×
          </button>
        </div>
        <div className="min-h-0 flex-1 overflow-y-auto px-5 py-4">
          <OperatorProfilePanel variant="modal" />
        </div>
      </div>
    </div>,
    document.body,
  );
}
