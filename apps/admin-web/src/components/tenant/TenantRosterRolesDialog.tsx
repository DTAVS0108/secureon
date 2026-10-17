"use client";

import { useMemo, useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { Select } from "@/components/ui/Field";
import { useToast } from "@/components/ui/Toast";
import { getRole, listRoles } from "@/lib/api/roles";
import { updateUserApplicationRoles, type TenantWorkspaceUser } from "@/lib/api/tenant-workspace";
import type { Role, RoleDetail } from "@/lib/types";

export function TenantRosterRolesDialog({
  open,
  user,
  applications,
  tenantId,
  onClose,
  onSaved,
}: {
  open: boolean;
  user: TenantWorkspaceUser | null;
  applications: { id: string; name: string }[];
  tenantId: string;
  onClose: () => void;
  onSaved: () => Promise<void>;
}) {
  const { toast } = useToast();
  const [appId, setAppId] = useState(applications[0]?.id ?? "");
  const [roles, setRoles] = useState<Role[]>([]);
  const [roleDetails, setRoleDetails] = useState<Map<string, RoleDetail>>(new Map());
  const [roleIds, setRoleIds] = useState<string[]>([]);
  const [loadingRoles, setLoadingRoles] = useState(false);
  const [saving, setSaving] = useState(false);

  const memberApps = useMemo(
    () => applications.filter((a) => user?.applicationIds.includes(a.id)),
    [applications, user?.applicationIds],
  );

  useDeferredEffect(() => {
    if (!open || !user) return;
    const first = memberApps[0]?.id ?? applications[0]?.id ?? "";
    setAppId(first);
  }, [open, user?.id, memberApps, applications]);

  useDeferredEffect(() => {
    if (!open || !appId) {
      setRoles([]);
      setRoleDetails(new Map());
      return;
    }
    let cancelled = false;
    setLoadingRoles(true);
    void listRoles({ applicationId: appId })
      .then(async (rows) => {
        if (cancelled) return;
        setRoles(rows);
        const details = await Promise.all(rows.map((r) => getRole(r.id, appId, r)));
        if (!cancelled) {
          setRoleDetails(new Map(details.map((d) => [d.id, d])));
        }
      })
      .catch(() => {
        if (!cancelled) {
          setRoles([]);
          setRoleDetails(new Map());
        }
      })
      .finally(() => {
        if (!cancelled) setLoadingRoles(false);
      });
    return () => {
      cancelled = true;
    };
  }, [open, appId]);

  useDeferredEffect(() => {
    if (!open || !user || !appId) {
      setRoleIds([]);
      return;
    }
    const access = user.applicationAccess.find((a) => a.applicationId === appId);
    setRoleIds(access?.roleIds ?? []);
  }, [open, user, appId]);

  const effectivePermissions = useMemo(() => {
    const keys = new Set<string>();
    for (const id of roleIds) {
      const detail = roleDetails.get(id);
      if (!detail) continue;
      for (const p of detail.permissions) keys.add(p.key);
    }
    return [...keys].sort();
  }, [roleIds, roleDetails]);

  function toggleRole(id: string) {
    setRoleIds((prev) =>
      prev.includes(id) ? prev.filter((r) => r !== id) : [...prev, id],
    );
  }

  async function save() {
    if (!user || !appId) return;
    setSaving(true);
    try {
      await updateUserApplicationRoles(user.id, appId, tenantId, roleIds);
      toast("Roles updated", "success");
      await onSaved();
      onClose();
    } catch (e) {
      toast(e instanceof Error ? e.message : "Could not update roles", "error");
    } finally {
      setSaving(false);
    }
  }

  if (!open || !user) return null;

  return (
    <div className="fixed inset-0 z-[9999] flex items-start justify-center overflow-y-auto bg-black/50 p-4 sm:p-8">
      <div
        role="dialog"
        aria-modal="true"
        className="relative my-8 w-full max-w-[max(36rem,60vw)] rounded-2xl border border-ui bg-[var(--ui-surface)] shadow-2xl"
      >
        <div className="border-b border-ui px-5 py-4">
          <h2 className="text-base font-semibold text-ui">Manage roles & permissions</h2>
          <p className="mt-0.5 text-xs text-muted">
            {user.displayName} · assign in-app RBAC roles per application (read, write, delete, etc.)
          </p>
        </div>

        <div className="space-y-4 px-5 py-4">
          <div>
            <label className="mb-1 block text-xs font-medium text-muted">Application</label>
            <Select
              value={appId}
              onChange={(e) => setAppId(e.target.value)}
              className="w-full text-sm"
            >
              {(memberApps.length > 0 ? memberApps : applications).map((a) => (
                <option key={a.id} value={a.id}>
                  {a.name}
                  {!user.applicationIds.includes(a.id) ? " (not a member)" : ""}
                </option>
              ))}
            </Select>
            {!user.applicationIds.includes(appId) && (
              <p className="mt-1 text-xs text-amber-600">
                Grant app membership first to assign roles.
              </p>
            )}
          </div>

          {loadingRoles ? (
            <p className="text-sm text-muted">Loading roles…</p>
          ) : roles.length === 0 ? (
            <p className="text-sm text-muted">No roles defined for this application.</p>
          ) : (
            <div className="space-y-2">
              {roles.map((r) => {
                const detail = roleDetails.get(r.id);
                const selected = roleIds.includes(r.id);
                const rolePermissions = detail?.permissions ?? [];
                return (
                  <label
                    key={r.id}
                    className={`flex cursor-pointer flex-col gap-1.5 rounded-lg border px-3 py-2 text-sm transition-colors ${
                      selected
                        ? "border-brand bg-brand-muted/30"
                        : "border-black/10 hover:bg-ui-elevated dark:border-white/10 dark:hover:bg-white/[0.03]"
                    }`}
                  >
                    <span className="flex items-center gap-2">
                      <input
                        type="checkbox"
                        checked={selected}
                        disabled={!user.applicationIds.includes(appId) || saving}
                        onChange={() => toggleRole(r.id)}
                        className="h-4 w-4 accent-[var(--ui-primary)]"
                      />
                      <span className="min-w-0">
                        <span className="block truncate font-medium">{r.name}</span>
                        {r.description && (
                          <span className="block truncate text-xs text-faint">{r.description}</span>
                        )}
                      </span>
                    </span>
                    {selected && rolePermissions.length > 0 && (
                      <div className="ml-6 flex flex-wrap gap-1">
                        {rolePermissions.map((p) => (
                          <span
                            key={p.id}
                            className="rounded-md bg-ui-elevated px-1.5 py-0.5 font-mono text-[10px] text-soft"
                            title={p.description}
                          >
                            {p.key}
                          </span>
                        ))}
                      </div>
                    )}
                  </label>
                );
              })}
            </div>
          )}

          {effectivePermissions.length > 0 && (
            <div>
              <p className="mb-1 text-[10px] font-medium uppercase tracking-wide text-faint">
                Effective permissions (all selected roles)
              </p>
              <div className="flex flex-wrap gap-1">
                {effectivePermissions.map((key) => (
                  <span
                    key={key}
                    className="rounded-md bg-ui-elevated px-2 py-0.5 font-mono text-[10px] text-soft"
                  >
                    {key}
                  </span>
                ))}
              </div>
            </div>
          )}
        </div>

        <div className="flex justify-end gap-2 border-t border-ui px-5 py-4">
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg border border-ui px-4 py-2 text-sm font-medium text-ui hover:bg-ui-elevated"
          >
            Cancel
          </button>
          <button
            type="button"
            disabled={saving || !user.applicationIds.includes(appId)}
            onClick={() => void save()}
            className="rounded-lg bg-brand px-4 py-2 text-sm font-medium text-on-brand disabled:opacity-60"
          >
            {saving ? "Saving…" : "Save roles"}
          </button>
        </div>
      </div>
    </div>
  );
}
