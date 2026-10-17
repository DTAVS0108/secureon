"use client";

import { useMemo, useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { createPortal } from "react-dom";
import { Button } from "@/components/ui/Button";
import { FieldRow, Select } from "@/components/ui/Field";
import { useToast } from "@/components/ui/Toast";
import { assignTenantAdminOperator } from "@/lib/api/tenant-admins";
import { listUsers } from "@/lib/api/users";
import type { Application, User } from "@/lib/types";

type UserSource = "tenant" | "application";

export function AssignTenantAdminDialog({
  open,
  onClose,
  tenantId,
  applications,
  tenantUsers,
  onAssigned,
}: {
  open: boolean;
  onClose: () => void;
  tenantId: string;
  applications: Application[];
  tenantUsers: User[];
  onAssigned: () => void | Promise<void>;
}) {
  const { toast } = useToast();
  const [mounted, setMounted] = useState(false);
  const [applicationId, setApplicationId] = useState(applications[0]?.id ?? "");
  const [userSource, setUserSource] = useState<UserSource>("application");
  const [userId, setUserId] = useState("");
  const [appUsers, setAppUsers] = useState<User[]>([]);
  const [loadingAppUsers, setLoadingAppUsers] = useState(false);
  const [submitting, setSubmitting] = useState(false);

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

  useDeferredEffect(() => {
    if (!open || !applicationId || userSource !== "application") {
      setAppUsers([]);
      return;
    }
    let cancelled = false;
    setLoadingAppUsers(true);
    listUsers(undefined, applicationId)
      .then((rows) => {
        if (!cancelled) setAppUsers(rows);
      })
      .catch(() => {
        if (!cancelled) setAppUsers([]);
      })
      .finally(() => {
        if (!cancelled) setLoadingAppUsers(false);
      });
    return () => {
      cancelled = true;
    };
  }, [open, applicationId, userSource]);

  const selectableUsers = useMemo(
    () => (userSource === "application" ? appUsers : tenantUsers),
    [userSource, appUsers, tenantUsers],
  );

  useDeferredEffect(() => {
    if (!selectableUsers.some((u) => u.id === userId)) {
      setUserId("");
    }
  }, [selectableUsers, userId]);

  async function handleSubmit() {
    if (!userId || !applicationId) {
      toast("Select an application and user", "error");
      return;
    }
    setSubmitting(true);
    try {
      await assignTenantAdminOperator(tenantId, userId, applicationId);
      toast("Tenant Admin assigned", "success");
      setUserId("");
      await onAssigned();
      onClose();
    } catch (e) {
      toast(e instanceof Error ? e.message : "Could not assign Tenant Admin", "error");
    } finally {
      setSubmitting(false);
    }
  }

  if (!open || !mounted) return null;

  const selectedApp = applications.find((a) => a.id === applicationId);

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
        aria-labelledby="assign-tenant-admin-title"
        className="relative z-[10000] my-8 w-full max-w-[max(36rem,60vw)] rounded-2xl border border-ui bg-[var(--ui-surface)] shadow-2xl"
      >
        <div className="flex items-start justify-between gap-4 border-b border-ui px-5 py-4">
          <div>
            <h2 id="assign-tenant-admin-title" className="text-base font-semibold text-ui">
              Assign Tenant Admin
            </h2>
            <p className="mt-0.5 text-xs text-muted">
              Grants Tenant Admin on the selected application. The user can then manage all
              applications in this tenant.
            </p>
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

        <div className="space-y-4 px-5 py-5">
          <FieldRow label="Application" hint="Tenant Admin role is created on this application.">
            <Select
              value={applicationId}
              onChange={(e) => setApplicationId(e.target.value)}
              className="w-full"
            >
              {applications.map((a) => (
                <option key={a.id} value={a.id}>
                  {a.name}
                </option>
              ))}
            </Select>
          </FieldRow>

          <FieldRow label="User list">
            <Select
              value={userSource}
              onChange={(e) => setUserSource(e.target.value as UserSource)}
              className="w-full"
            >
              <option value="application">
                Members of {selectedApp?.name ?? "selected application"}
              </option>
              <option value="tenant">All users in tenant</option>
            </Select>
          </FieldRow>

          <FieldRow
            label="User"
            hint={
              userSource === "application" && loadingAppUsers
                ? "Loading application members…"
                : "Pick a user to promote to Tenant Admin."
            }
          >
            <Select
              value={userId}
              onChange={(e) => setUserId(e.target.value)}
              className="w-full"
              disabled={loadingAppUsers}
            >
              <option value="">Select user…</option>
              {selectableUsers.map((u) => (
                <option key={u.id} value={u.id}>
                  {u.email} ({u.firstName} {u.lastName})
                </option>
              ))}
            </Select>
          </FieldRow>

          {userSource === "application" && !loadingAppUsers && selectableUsers.length === 0 && (
            <p className="text-xs text-amber-700 dark:text-amber-300">
              No members on this application yet. Switch to &quot;All users in tenant&quot; or add
              the user to the application first.
            </p>
          )}
        </div>

        <div className="flex justify-end gap-2 border-t border-ui px-5 py-4">
          <Button variant="secondary" onClick={onClose} disabled={submitting}>
            Cancel
          </Button>
          <Button onClick={() => void handleSubmit()} disabled={submitting || !userId || !applicationId}>
            {submitting ? "Assigning…" : "Assign Tenant Admin"}
          </Button>
        </div>
      </div>
    </div>,
    document.body,
  );
}
