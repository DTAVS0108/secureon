"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/Button";
import { useToast } from "@/components/ui/Toast";
import { applicationIsolateAction } from "@/lib/actions";
import type { ApplicationProduct } from "@/lib/types";

export function IsolateApplicationButton({
  application,
  className,
}: {
  application: ApplicationProduct;
  className?: string;
}) {
  const { toast } = useToast();
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [pending, setPending] = useState(false);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && setOpen(false);
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [open]);

  if (application.schemaName) {
    return (
      <p className={`text-xs text-muted ${className ?? ""}`}>
        Isolated in schema <code className="font-mono">{application.schemaName}</code>
      </p>
    );
  }

  async function confirmIsolate() {
    setPending(true);
    try {
      const fd = new FormData();
      fd.set("id", application.id);
      const result = await applicationIsolateAction(fd);
      if (!result.ok) {
        toast(result.error ?? "Isolation failed", "error");
        return;
      }
      toast(`Application isolated into schema ${result.schemaName ?? "dedicated"}`, "success");
      setOpen(false);
      router.refresh();
    } catch (e) {
      toast(e instanceof Error ? e.message : "Isolation failed", "error");
    } finally {
      setPending(false);
    }
  }

  return (
    <>
      <Button
        type="button"
        variant="secondary"
        size="sm"
        className={className}
        onClick={() => setOpen(true)}
      >
        Isolate application
      </Button>
      {open && (
        <div className="fixed inset-0 z-100 flex items-center justify-center p-4">
          <div className="fixed inset-0 bg-black/40 backdrop-blur-sm" onClick={() => setOpen(false)} />
          <div
            role="alertdialog"
            aria-modal="true"
            className="relative z-10 w-full max-w-md rounded-2xl border border-black/10 bg-[var(--background)] p-6 shadow-2xl dark:border-white/10"
          >
            <h2 className="text-base font-semibold">Isolate application?</h2>
            <p className="mt-2 text-sm text-black/60 dark:text-white/60">
              Move IAM data (roles, permissions, users, settings) for <strong>{application.name}</strong> from
              the shared platform schema into a dedicated PostgreSQL schema derived from the application slug.
              OAuth clients stay in the platform registry. This cannot be undone automatically.
            </p>
            <div className="mt-6 flex justify-end gap-2">
              <Button variant="secondary" onClick={() => setOpen(false)} disabled={pending}>
                Cancel
              </Button>
              <Button variant="danger" onClick={confirmIsolate} disabled={pending}>
                {pending ? "Isolating…" : "Isolate"}
              </Button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
