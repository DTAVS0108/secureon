"use client";

import { useEffect, useState } from "react";
import { useFormStatus } from "react-dom";
import { Button } from "./Button";

function ConfirmButton({ label }: { label: string }) {
  const { pending } = useFormStatus();
  return (
    <Button type="submit" variant="danger" disabled={pending}>
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
}) {
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);

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
          setOpen(true);
        }}
      >
        {triggerLabel}
      </Button>
      {open && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div className="fixed inset-0 bg-black/40 backdrop-blur-sm" onClick={() => setOpen(false)} />
          <div
            role="alertdialog"
            aria-modal="true"
            className="relative z-10 w-full max-w-md rounded-2xl border border-black/10 bg-[var(--background)] p-6 shadow-2xl dark:border-white/10"
          >
            <h2 className="text-base font-semibold">{title}</h2>
            <p className="mt-2 text-sm text-black/60 dark:text-white/60">{message}</p>
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
                <ConfirmButton label={confirmLabel} />
              </form>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
