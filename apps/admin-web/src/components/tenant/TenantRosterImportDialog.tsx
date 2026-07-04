"use client";

import { useMemo, useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { Badge } from "@/components/ui/Badge";
import { Input } from "@/components/ui/Field";
import { useToast } from "@/components/ui/Toast";
import {
  bulkImportUsersToTenantRoster,
  importUserToTenantRoster,
  isInvitedAccount,
  userPermissionKeys,
  type TenantWorkspaceUser,
} from "@/lib/api/tenant-workspace";
import { statusTone } from "@/lib/status";
import type { Status } from "@/lib/types";

const STATUS_FILTERS = ["ALL", "active", "invited", "suspended", "disabled"] as const;

function normalizeStatus(status: string): string {
  const s = status.toLowerCase();
  return s === "pending" ? "invited" : s;
}

export function TenantRosterImportDialog({
  open,
  applicationId,
  applicationName,
  tenantId,
  importable,
  loading,
  onClose,
  onImported,
}: {
  open: boolean;
  applicationId: string;
  applicationName: string;
  tenantId: string;
  importable: TenantWorkspaceUser[];
  loading: boolean;
  onClose: () => void;
  onImported: () => Promise<void>;
}) {
  const { toast } = useToast();
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState<(typeof STATUS_FILTERS)[number]>("ALL");
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [busyId, setBusyId] = useState<string | null>(null);
  const [bulkBusy, setBulkBusy] = useState(false);

  useDeferredEffect(() => {
    if (!open) {
      setQuery("");
      setStatusFilter("ALL");
      setSelected(new Set());
    }
  }, [open]);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return importable.filter((u) => {
      const status = normalizeStatus(u.status);
      if (statusFilter !== "ALL" && status !== statusFilter) return false;
      if (!q) return true;
      return (
        u.email.toLowerCase().includes(q) ||
        u.displayName.toLowerCase().includes(q) ||
        (u.roleNames ?? []).some((r) => r.toLowerCase().includes(q)) ||
        userPermissionKeys(u).some((p) => p.toLowerCase().includes(q))
      );
    });
  }, [importable, query, statusFilter]);

  const allFilteredSelected =
    filtered.length > 0 && filtered.every((u) => selected.has(u.id));

  function toggleAll() {
    if (allFilteredSelected) {
      setSelected((prev) => {
        const next = new Set(prev);
        for (const u of filtered) next.delete(u.id);
        return next;
      });
      return;
    }
    setSelected((prev) => {
      const next = new Set(prev);
      for (const u of filtered) next.add(u.id);
      return next;
    });
  }

  function toggleOne(id: string) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  async function importOne(userId: string) {
    setBusyId(userId);
    try {
      await importUserToTenantRoster(userId, applicationId, tenantId);
      toast("User added to tenant roster", "success");
      setSelected((prev) => {
        const next = new Set(prev);
        next.delete(userId);
        return next;
      });
      await onImported();
    } catch (e) {
      toast(e instanceof Error ? e.message : "Could not import user", "error");
    } finally {
      setBusyId(null);
    }
  }

  async function importIds(ids: string[]) {
    if (ids.length === 0) return;
    setBulkBusy(true);
    try {
      const imported = await bulkImportUsersToTenantRoster(ids, applicationId, tenantId);
      toast(`${imported} user${imported === 1 ? "" : "s"} added to tenant roster`, "success");
      setSelected(new Set());
      await onImported();
    } catch (e) {
      toast(e instanceof Error ? e.message : "Could not import users", "error");
    } finally {
      setBulkBusy(false);
    }
  }

  async function importSelected() {
    const ids = [...selected].filter((id) => importable.some((u) => u.id === id));
    await importIds(ids);
  }

  async function importAllVisible() {
    await importIds(filtered.map((u) => u.id));
  }

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-[9999] flex items-start justify-center overflow-y-auto bg-black/50 p-4 sm:p-8">
      <div
        role="dialog"
        aria-modal="true"
        className="relative my-8 w-full max-w-2xl rounded-2xl border border-ui bg-[var(--ui-surface)] shadow-2xl"
      >
        <div className="border-b border-ui px-5 py-4">
          <h2 className="text-base font-semibold text-ui">Import from application</h2>
          <p className="mt-0.5 text-xs text-muted">
            Import <span className="font-medium text-ui">Tenant Admin</span> operators from{" "}
            <span className="font-medium text-ui">{applicationName}</span> into the tenant roster.
            End-user accounts (Member, etc.) stay in application user management only.
          </p>
        </div>

        <div className="space-y-3 border-b border-ui px-5 py-4">
          <Input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search by name, email, role, or permission…"
          />
          <div className="flex flex-wrap gap-2">
            {STATUS_FILTERS.map((s) => (
              <button
                key={s}
                type="button"
                onClick={() => setStatusFilter(s)}
                className={`rounded-full border px-2.5 py-1 text-xs capitalize transition-colors ${
                  statusFilter === s
                    ? "border-brand bg-brand-muted text-brand"
                    : "border-ui text-muted hover:bg-ui-elevated"
                }`}
              >
                {s === "ALL" ? "All statuses" : s}
              </button>
            ))}
          </div>
          <div className="flex flex-wrap items-center justify-between gap-2">
            <label className="flex items-center gap-2 text-sm text-soft">
              <input
                type="checkbox"
                checked={allFilteredSelected}
                disabled={filtered.length === 0 || loading}
                onChange={toggleAll}
              />
              Select all {filtered.length > 0 ? `(${filtered.length})` : ""}
            </label>
            <div className="flex flex-wrap gap-2">
              <button
                type="button"
                disabled={filtered.length === 0 || bulkBusy || loading}
                onClick={() => void importAllVisible()}
                className="rounded-lg border border-ui px-3 py-1.5 text-xs font-medium text-ui hover:bg-ui-elevated disabled:opacity-60"
              >
                {bulkBusy ? "Importing…" : `Import all visible (${filtered.length})`}
              </button>
              <button
                type="button"
                disabled={selected.size === 0 || bulkBusy}
                onClick={() => void importSelected()}
                className="rounded-lg bg-brand px-3 py-1.5 text-xs font-medium text-on-brand disabled:opacity-60"
              >
                {bulkBusy ? "Importing…" : `Import selected (${selected.size})`}
              </button>
            </div>
          </div>
        </div>

        <div className="max-h-[28rem] overflow-y-auto px-5 py-2">
          {loading ? (
            <p className="py-6 text-sm text-muted">Loading Tenant Admins…</p>
          ) : filtered.length === 0 ? (
            <p className="py-6 text-sm text-muted">
              {importable.length === 0
                ? "No Tenant Admin operators left to import from this application. Assign the application role Tenant Admin under Application → Users (invite or edit user), confirm the From app dropdown matches that application, or use + Add tenant user if they are already on the roster."
                : "No Tenant Admins match your search or filters."}
            </p>
          ) : (
            <ul className="divide-y divide-ui">
              {filtered.map((u) => {
                const permissions = userPermissionKeys(u);
                return (
                  <li key={u.id} className="flex items-start justify-between gap-3 py-3">
                    <label className="flex min-w-0 flex-1 items-start gap-3">
                      <input
                        type="checkbox"
                        className="mt-1"
                        checked={selected.has(u.id)}
                        onChange={() => toggleOne(u.id)}
                      />
                      <div className="min-w-0">
                        <p className="truncate text-sm font-medium text-ui">{u.displayName}</p>
                        <p className="truncate text-xs text-faint">{u.email}</p>
                        <div className="mt-1 flex flex-wrap items-center gap-1">
                          <Badge
                            tone={statusTone(normalizeStatus(u.status) as Status)}
                            dot
                            className="capitalize"
                          >
                            {normalizeStatus(u.status)}
                          </Badge>
                          {isInvitedAccount(u.status) && (
                            <Badge tone="warning">Invite pending</Badge>
                          )}
                          {u.emailVerified && (
                            <Badge tone="success">Email verified</Badge>
                          )}
                          {u.roleNames.slice(0, 3).map((r) => (
                            <Badge key={r} tone="neutral">
                              {r}
                            </Badge>
                          ))}
                        </div>
                        {permissions.length > 0 && (
                          <div className="mt-1.5 flex flex-wrap gap-1">
                            {permissions.slice(0, 6).map((p) => (
                              <span
                                key={p}
                                className="rounded-md bg-ui-elevated px-1.5 py-0.5 font-mono text-[10px] text-soft"
                              >
                                {p}
                              </span>
                            ))}
                            {permissions.length > 6 && (
                              <span className="text-[10px] text-faint">
                                +{permissions.length - 6} permissions
                              </span>
                            )}
                          </div>
                        )}
                      </div>
                    </label>
                    <button
                      type="button"
                      disabled={busyId === u.id || bulkBusy}
                      onClick={() => void importOne(u.id)}
                      className="shrink-0 rounded-lg border border-ui px-3 py-1.5 text-xs font-medium text-ui hover:bg-ui-elevated disabled:opacity-60"
                    >
                      {busyId === u.id ? "Importing…" : "Import"}
                    </button>
                  </li>
                );
              })}
            </ul>
          )}
        </div>

        <div className="flex justify-end border-t border-ui px-5 py-4">
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg border border-ui px-4 py-2 text-sm font-medium text-ui hover:bg-ui-elevated"
          >
            Close
          </button>
        </div>
      </div>
    </div>
  );
}
