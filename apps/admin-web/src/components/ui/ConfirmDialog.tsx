"use client";

import { useEffect, useState } from "react";
import { createPortal, useFormStatus } from "react-dom";
import { Button } from "./Button";

function ConfirmButton({ label, disabled = false }: { label: string; disabled?: boolean }) {
  const { pending } = useFormStatus();
  return (
    <Button type="submit" variant="danger" disabled={pending || disabled}>
      {pending ? "Working…" : label}
    </Button>
  );
}

export function ConfirmDialog({
  action,
  id,
  triggerLabel,
  triggerVariant = "danger",
  triggerSize = "md",
  title,
  message,
  confirmLabel = "Delete",
  confirmText,
}: {
  /** May return { error } to keep the dialog open and show why the action was refused. */
  action: (formData: FormData) => Promise<void | { error?: string }>;
  id: string;
  triggerLabel: React.ReactNode;
  triggerVariant?: "primary" | "secondary" | "ghost" | "danger";
  triggerSize?: "sm" | "md";
  title: string;
  message: string;
  confirmLabel?: string;
  /** For destructive actions: the confirm button stays disabled until this exact text is typed. */
  confirmText?: string;
}) {
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [typed, setTyped] = useState("");
  const confirmed = !confirmText || typed.trim() === confirmText;

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && setOpen(false);
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [open]);

  return (
    <>
      <Button
        variant={triggerVariant}
        size={triggerSize}
        onClick={() => {
          setError(null);
          setTyped("");
          setOpen(true);
        }}
      >
        {triggerLabel}
      </Button>
      {open && createPortal(
        // On <body>, so no card or panel on the page can paint over the dialog.
        <div className="fixed inset-0 z-100 flex items-center justify-center p-4">
          <div className="fixed inset-0 bg-black/40 backdrop-blur-sm" onClick={() => setOpen(false)} />
          <div
            role="alertdialog"
            aria-modal="true"
            className="relative z-10 w-full max-w-md rounded-2xl border border-black/10 bg-[var(--background)] p-6 shadow-2xl dark:border-white/10"
          >
            <h2 className="text-base font-semibold">{title}</h2>
            <p className="mt-2 text-sm text-black/60 dark:text-white/60">{message}</p>
            {confirmText && (
              <label className="mt-4 block text-sm">
                <span className="text-black/70 dark:text-white/70">
                  Type <strong className="font-mono">{confirmText}</strong> to confirm
                </span>
                <input
                  value={typed}
                  onChange={(e) => setTyped(e.target.value)}
                  autoComplete="off"
                  spellCheck={false}
                  className="mt-1 block w-full rounded-md border border-ui bg-ui-elevated px-3 py-2 text-sm text-ui outline-none focus:border-ui-primary"
                />
              </label>
            )}
            {error && (
              <p role="alert" className="mt-3 rounded-lg bg-red-500/10 px-3 py-2 text-sm text-red-600 dark:text-red-300">
                {error}
              </p>
            )}
            <div className="mt-6 flex justify-end gap-2">
              <Button variant="secondary" onClick={() => setOpen(false)}>
                Cancel
              </Button>
              <form
                action={async (fd) => {
                  setError(null);
                  const result = await action(fd);
                  if (result && result.error) setError(result.error);
                }}
              >
                <input type="hidden" name="id" value={id} />
                <ConfirmButton label={confirmLabel} disabled={!confirmed} />
              </form>
            </div>
          </div>
        </div>,
        document.body,
      )}
    </>
  );
}
