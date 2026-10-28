"use client";

import { useId, useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { createPortal } from "react-dom";
import { Button } from "@/components/ui/Button";
import { FieldRow, Input, Textarea } from "@/components/ui/Field";
import { useToast } from "@/components/ui/Toast";
import { createTenantRoleAction } from "@/lib/actions/tenant-rbac";
import type { Application } from "@/lib/types";

export function CreateTenantCustomRoleDialog({
  open,
  onClose,
  tenantId,
  tenantName,
  applications,
  onCreated,
}: {
  open: boolean;
  onClose: () => void;
  tenantId: string;
  tenantName: string;
  applications: Application[];
  onCreated: (roleId: string) => void | Promise<void>;
}) {
  const { toast } = useToast();
  const nameId = useId();
  const descriptionId = useId();
  const [mounted, setMounted] = useState(false);
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [applicationIds, setApplicationIds] = useState<string[]>([]);
  const [submitting, setSubmitting] = useState(false);

  useDeferredEffect(() => {
    setMounted(true);
  }, []);

  useDeferredEffect(() => {
    if (!open) return;
    setName("");
    setDescription("");
    setApplicationIds([]);
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && !submitting && onClose();
    document.addEventListener("keydown", onKey);
    document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = "";
    };
  }, [open, onClose, submitting]);

  function toggleApplication(id: string) {
    setApplicationIds((prev) =>
      prev.includes(id) ? prev.filter((a) => a !== id) : [...prev, id],
    );
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    const trimmed = name.trim();
    if (!trimmed) {
      toast("Enter a role name", "error");
      return;
    }
    setSubmitting(true);
    try {
      const created = await createTenantRoleAction(tenantId, {
        name: trimmed,
        description: description.trim(),
        permissionIds: [],
        applicationIds,
      });
      toast("Custom role created", "success");
      await onCreated(created.id);
      onClose();
    } catch (err) {
      toast(err instanceof Error ? err.message : "Could not create role", "error");
    } finally {
      setSubmitting(false);
    }
  }

  if (!open || !mounted) return null;

  return createPortal(
    <div className="fixed inset-0 z-[9999] flex items-start justify-center overflow-y-auto p-4 sm:p-8">
      <div
        className="fixed inset-0 z-[9999] bg-black/50 backdrop-blur-sm"
        onClick={() => !submitting && onClose()}
        aria-hidden
      />
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="create-tenant-role-title"
        className="relative z-[10000] my-8 w-full max-w-[max(36rem,60vw)] rounded-2xl border border-ui bg-[var(--ui-surface)] shadow-2xl"
      >
        <div className="flex items-start justify-between gap-4 border-b border-ui px-5 py-4">
          <div>
            <h2 id="create-tenant-role-title" className="text-base font-semibold text-ui">
              New custom role
            </h2>
            <p className="mt-0.5 text-xs leading-relaxed text-muted">
              Add a tenant-specific role for <strong className="text-ui">{tenantName}</strong>.
              Console sections and permissions can be configured after creation.
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            disabled={submitting}
            aria-label="Close"
            className="rounded-lg px-2 text-lg leading-none text-faint hover:bg-ui-elevated disabled:opacity-50"
          >
            ×
          </button>
        </div>

        <form onSubmit={(e) => void handleSubmit(e)} className="space-y-5 px-5 py-5">
          <FieldRow label="Role name" htmlFor={nameId} hint="Shown in the role directory">
            <Input
              id={nameId}
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Role name"
              autoFocus
              required
              disabled={submitting}
            />
          </FieldRow>

          <FieldRow label="Description" htmlFor={descriptionId} hint="Optional">
            <Textarea
              id={descriptionId}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="What members with this role can do"
              rows={3}
              disabled={submitting}
            />
          </FieldRow>

          {applications.length > 0 && (
            <div>
              <p className="mb-1 text-sm font-medium text-ui">Application scope</p>
              <p className="mb-3 text-xs text-muted">
                Leave none selected to allow all applications in this tenant.
              </p>
              <div className="flex flex-wrap gap-2">
                {applications.map((app) => {
                  const selected = applicationIds.includes(app.id);
                  return (
                    <button
                      key={app.id}
                      type="button"
                      disabled={submitting}
                      onClick={() => toggleApplication(app.id)}
                      className={`rounded-full border px-3 py-1.5 text-xs font-medium transition-colors disabled:opacity-50 ${
                        selected
                          ? "border-brand bg-brand-muted text-brand"
                          : "border-ui text-muted hover:bg-ui-elevated"
                      }`}
                    >
                      {app.name}
                    </button>
                  );
                })}
              </div>
            </div>
          )}

          <div className="flex justify-end gap-2 border-t border-ui pt-4">
            <Button type="button" variant="secondary" onClick={onClose} disabled={submitting}>
              Cancel
            </Button>
            <Button type="submit" disabled={submitting || !name.trim()}>
              {submitting ? "Creating…" : "Create role"}
            </Button>
          </div>
        </form>
      </div>
    </div>,
    document.body,
  );
}
