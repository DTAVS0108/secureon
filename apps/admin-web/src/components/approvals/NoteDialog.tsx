"use client";

import { useEffect, useState } from "react";
import { createPortal } from "react-dom";
import { Button } from "@/components/ui/Button";
import { Textarea } from "@/components/ui/Field";

/**
 * Confirmation with a note: asking for a change (reason), approving (optional note) or rejecting
 * (required reason). `onConfirm` returns an error message to keep the dialog open.
 */
export function NoteDialog({
  triggerLabel,
  triggerVariant = "secondary",
  triggerSize = "sm",
  title,
  message,
  noteLabel,
  notePlaceholder,
  noteRequired = false,
  confirmLabel,
  confirmVariant = "primary",
  onConfirm,
}: {
  triggerLabel: React.ReactNode;
  triggerVariant?: "primary" | "secondary" | "ghost" | "danger";
  triggerSize?: "sm" | "md";
  title: string;
  message: React.ReactNode;
  noteLabel: string;
  notePlaceholder?: string;
  noteRequired?: boolean;
  confirmLabel: string;
  confirmVariant?: "primary" | "danger";
  onConfirm: (note: string) => Promise<string | void>;
}) {
  const [open, setOpen] = useState(false);
  const [note, setNote] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && !busy && setOpen(false);
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [open, busy]);

  async function confirm() {
    setBusy(true);
    setError(null);
    try {
      const failure = await onConfirm(note.trim());
      if (failure) setError(failure);
      else setOpen(false);
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <Button
        variant={triggerVariant}
        size={triggerSize}
        onClick={() => {
          setNote("");
          setError(null);
          setOpen(true);
        }}
      >
        {triggerLabel}
      </Button>
      {open &&
        createPortal(
          <div className="fixed inset-0 z-100 flex items-center justify-center p-4">
            <div className="fixed inset-0 bg-black/40 backdrop-blur-sm" onClick={() => !busy && setOpen(false)} />
            <div
              role="dialog"
              aria-modal="true"
              className="relative z-10 w-full max-w-lg rounded-2xl border border-black/10 bg-[var(--background)] p-6 shadow-2xl dark:border-white/10"
            >
              <h2 className="text-base font-semibold">{title}</h2>
              <div className="mt-2 text-sm text-soft">{message}</div>
              <label className="mt-4 block text-sm">
                <span className="font-medium">
                  {noteLabel}
                  {!noteRequired && <span className="font-normal text-faint"> (optional)</span>}
                </span>
                <Textarea
                  value={note}
                  onChange={(e) => setNote(e.target.value)}
                  placeholder={notePlaceholder}
                  maxLength={500}
                  className="mt-1"
                  autoFocus
                />
              </label>
              {error && (
                <p role="alert" className="mt-3 rounded-lg bg-red-500/10 px-3 py-2 text-sm text-red-600 dark:text-red-300">
                  {error}
                </p>
              )}
              <div className="mt-6 flex justify-end gap-2">
                <Button variant="secondary" onClick={() => setOpen(false)} disabled={busy}>
                  Cancel
                </Button>
                <Button
                  variant={confirmVariant}
                  onClick={() => void confirm()}
                  disabled={busy || (noteRequired && !note.trim())}
                >
                  {busy ? "Working…" : confirmLabel}
                </Button>
              </div>
            </div>
          </div>,
          document.body,
        )}
    </>
  );
}
