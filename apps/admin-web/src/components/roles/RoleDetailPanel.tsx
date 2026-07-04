"use client";

import Link from "next/link";
import { useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { RoleLabelBadge } from "@/components/roles/RoleLabelBadge";
import { PermissionMatrix } from "@/components/roles/PermissionMatrix";
import { RoleFormModal } from "@/components/forms/RoleFormModal";
import type { RolesApiCapabilities } from "@/components/roles/RolesWorkspace";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { roleDeleteAction } from "@/lib/actions";
import { assignRolePermissionApi, getRole, listRoleUsers, removeRolePermissionApi } from "@/lib/api/roles";
import { initials, timeAgo } from "@/lib/format";
import {
  canDeleteRole,
  canEditRoleDetails,
  canRenameRole,
  isTenantAdminOperatorRole,
} from "@/lib/role-management";
import { ROLE_LABEL_META } from "@/lib/role-labels";
import { statusTone } from "@/lib/status";
import type {
  Application,
  Permission,
  Role,
  RoleAssignedUser,
  RoleDetail,
  Status,
  Tenant,
} from "@/lib/types";

type Tab = "overview" | "permissions" | "users";

export function RoleDetailPanel({
  roleId,
  roleSummary,
  applicationId,
  roles,
  permissions,
  tenants,
  applications,
  tenantId,
  capabilities,
  onClose,
  onDeleted,
}: {
  roleId: string;
  roleSummary: Role;
  applicationId: string;
  roles: Role[];
  permissions: Permission[];
  tenants: Tenant[];
  applications: Application[];
  tenantId: string;
  capabilities: RolesApiCapabilities;
  onClose: () => void;
  onDeleted: () => void;
}) {
  const reloadDetail = () => {
    getRole(roleId, applicationId, roleSummary)
      .then(setDetail)
      .catch(() => undefined);
  };
  const [detail, setDetail] = useState<RoleDetail | null>(null);
  const [assignedUsers, setAssignedUsers] = useState<RoleAssignedUser[]>([]);
  const [loading, setLoading] = useState(true);
  const [usersLoading, setUsersLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [tab, setTab] = useState<Tab>("overview");
  const [permSaving, setPermSaving] = useState(false);
  const [permError, setPermError] = useState<string | null>(null);

  useDeferredEffect(() => {
    setLoading(true);
    setLoadError(null);
    getRole(roleId, applicationId, roleSummary)
      .then(setDetail)
      .catch((e) => {
        setDetail({
          ...roleSummary,
          permissionIds: roleSummary.permissionIds ?? [],
          childRoleIds: roleSummary.childRoleIds ?? [],
          permissions: permissions,
          childRoles: [],
        });
        setLoadError(e instanceof Error ? e.message : "Could not load full role detail");
      })
      .finally(() => setLoading(false));
  }, [roleId, applicationId, roleSummary, permissions]);

  useDeferredEffect(() => {
    if (tab !== "users") return;
    setUsersLoading(true);
    listRoleUsers(applicationId, roleId)
      .then(setAssignedUsers)
      .catch(() => setAssignedUsers([]))
      .finally(() => setUsersLoading(false));
  }, [tab, applicationId, roleId]);

  if (loading) {
    return (
      <Card className="flex min-h-96 items-center justify-center p-8">
        <p className="text-sm text-muted">Loading role…</p>
      </Card>
    );
  }

  if (!detail) {
    return (
      <Card className="p-6">
        <p className="text-sm text-muted">Role not found.</p>
        <Button variant="ghost" size="sm" className="mt-3" onClick={onClose}>
          Close
        </Button>
      </Card>
    );
  }

  const labelKey =
    detail.label && detail.label in ROLE_LABEL_META ? detail.label : "CUSTOM";
  const labelMeta = ROLE_LABEL_META[labelKey];
  const selected = new Set(detail.permissionIds);
  const matrixPermissions =
    detail.permissions.length > 0 ? detail.permissions : permissions;
  const userCount = detail.userCount ?? roleSummary.userCount;
  const editable = canEditRoleDetails(detail);
  const tenantAdminOperator = isTenantAdminOperatorRole(detail);
  const canEditPermissions = editable && capabilities.permissions;
  const showRenameHint = editable && !canRenameRole(detail);

  async function togglePermission(permissionId: string, checked: boolean) {
    if (!canEditPermissions) return;
    setPermSaving(true);
    setPermError(null);
    try {
      const updated = checked
        ? await assignRolePermissionApi(applicationId, roleId, permissionId)
        : await removeRolePermissionApi(applicationId, roleId, permissionId);
      setDetail(updated);
    } catch (e) {
      setPermError(e instanceof Error ? e.message : "Could not update permission");
    } finally {
      setPermSaving(false);
    }
  }

  const tabs: { id: Tab; label: string; count?: number }[] = [
    { id: "overview", label: "Overview" },
    { id: "permissions", label: "Permissions", count: roleSummary.permissionCount ?? selected.size },
    { id: "users", label: "Assigned users", count: userCount },
  ];

  return (
    <Card padded={false} className="flex min-h-[28rem] flex-col overflow-hidden">
      <div className="border-b border-ui bg-ui-surface/80 px-5 py-4">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-2">
              <h2 className="truncate text-lg font-semibold text-ui">{detail.name}</h2>
              <RoleLabelBadge label={labelKey} />
              {detail.isComposite && <Badge tone="indigo">Composite</Badge>}
            </div>
            <p className="mt-1 line-clamp-2 text-sm text-muted">
              {detail.description || "No description"}
            </p>
            {tenantAdminOperator && (
              <p className="mt-2 rounded-lg border border-ui bg-ui-elevated px-3 py-2 text-xs text-soft">
                System operator role. Assign from user management, then import to the tenant roster.
                Permissions are managed by SecureOne and cannot be edited here.
              </p>
            )}
          </div>
          <div className="flex shrink-0 flex-wrap gap-2">
            {editable && (
              <RoleFormModal
                role={detail}
                roles={roles}
                permissions={matrixPermissions}
                tenants={tenants}
                applications={applications}
                tenantId={tenantId}
                applicationId={applicationId}
                lockToApplication
                triggerLabel="Edit role"
                triggerVariant="secondary"
                triggerSize="sm"
                onUpdated={reloadDetail}
              />
            )}
            {canDeleteRole(detail) && (
              <ConfirmDialog
                action={async (fd) => {
                  fd.set("applicationId", applicationId);
                  await roleDeleteAction(fd);
                  onDeleted();
                }}
                id={detail.id}
                triggerLabel="Delete"
                triggerVariant="danger"
                triggerSize="sm"
                title="Delete role"
                message={`Remove "${detail.name}"? Unassign all ${userCount} user(s) first.`}
                confirmLabel="Delete"
              />
            )}
          </div>
        </div>

        <nav className="mt-4 flex gap-1 border-b border-ui -mb-px" aria-label="Role sections">
          {tabs.map((t) => (
            <button
              key={t.id}
              type="button"
              onClick={() => setTab(t.id)}
              className={`relative px-3 py-2 text-sm font-medium transition-colors ${
                tab === t.id
                  ? "text-brand after:absolute after:inset-x-0 after:bottom-0 after:h-0.5 after:bg-brand"
                  : "text-muted hover:text-ui"
              }`}
            >
              {t.label}
              {t.count !== undefined && (
                <span className="ml-1.5 rounded-full bg-ui-elevated px-1.5 py-0.5 text-[10px] font-semibold text-faint">
                  {t.count}
                </span>
              )}
            </button>
          ))}
        </nav>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto">
        {loadError && tab === "overview" && (
          <p className="mx-5 mt-4 rounded-lg border border-ui bg-ui-elevated px-3 py-2 text-xs text-muted">
            {loadError}
          </p>
        )}
        {!capabilities.permissions && tab === "permissions" && (
          <p className="mx-5 mt-4 rounded-lg border border-amber-500/30 bg-amber-500/10 px-3 py-2 text-xs text-amber-900 dark:text-amber-200">
            Restart auth-server with migration V11+ for the permission catalog.
          </p>
        )}

        {tab === "overview" && (
          <div className="space-y-0">
            <p className="border-b border-ui px-5 py-3 text-xs text-faint">{labelMeta.description}</p>
            {showRenameHint && (
              <p className="border-b border-ui bg-ui-elevated/40 px-5 py-2.5 text-xs text-muted">
                Built-in and system role names are locked. Use <strong className="text-ui">Edit role</strong> or
                the Permissions tab to change description, inheritance, and permission grants.
              </p>
            )}
            <div className="grid grid-cols-2 gap-px bg-ui sm:grid-cols-4">
              <Stat label="Assigned users" value={String(userCount)} />
              <Stat
                label="Effective permissions"
                value={String(roleSummary.permissionCount ?? detail.permissionIds.length)}
              />
              <Stat
                label="Inherited roles"
                value={detail.isComposite ? String(detail.childRoleIds.length) : "—"}
              />
              <Stat label="Catalog size" value={String(matrixPermissions.length)} />
            </div>
            {detail.isComposite && detail.childRoles.length > 0 && (
              <div className="border-t border-ui px-5 py-4">
                <p className="text-xs font-semibold uppercase tracking-wide text-muted">
                  Inherited roles
                </p>
                <ul className="mt-2 flex flex-wrap gap-2">
                  {detail.childRoles.map((c) => (
                    <li key={c.id}>
                      <Link
                        href={`/app/${applicationId}/roles?roleId=${c.id}`}
                        className="rounded-full border border-ui bg-ui-elevated px-3 py-1 text-sm text-ui hover:border-brand"
                      >
                        {c.name}
                      </Link>
                    </li>
                  ))}
                </ul>
              </div>
            )}
            {detail.isComposite && editable && (
              <div className="border-t border-ui px-5 py-3">
                <RoleFormModal
                  role={detail}
                  roles={roles}
                  permissions={matrixPermissions}
                  tenants={tenants}
                  applications={applications}
                  tenantId={tenantId}
                  applicationId={applicationId}
                  lockToApplication
                  triggerLabel="Edit inheritance"
                  triggerVariant="ghost"
                  triggerSize="sm"
                  onUpdated={reloadDetail}
                />
              </div>
            )}
            <div className="border-t border-ui px-5 py-4">
              <p className="font-mono text-[10px] text-faint">role.id</p>
              <p className="mt-0.5 break-all font-mono text-xs text-muted">{detail.id}</p>
            </div>
          </div>
        )}

        {tab === "permissions" && (
          <div className="p-5">
            <p className="mb-3 text-xs text-muted">
              {tenantAdminOperator
                ? "All SecureOne console permissions granted to this operator role — read-only."
                : canEditPermissions
                  ? "Toggle permissions directly — changes save immediately. Or use Edit role for bulk changes."
                  : "Direct grants on this role. Composite roles also inherit from child roles."}
            </p>
            {!canEditPermissions && editable && !capabilities.permissions && (
              <p className="mb-3 text-xs text-amber-800 dark:text-amber-200">
                Permission catalog unavailable — restart auth-server with migration V11+.
              </p>
            )}
            {permError && (
              <p className="mb-3 rounded-lg border border-red-500/30 bg-red-500/10 px-3 py-2 text-xs text-red-800 dark:text-red-200">
                {permError}
              </p>
            )}
            {permSaving && (
              <p className="mb-3 text-xs text-muted">Saving permission change…</p>
            )}
            {matrixPermissions.length === 0 ? (
              <p className="text-sm text-muted">
                No permissions in catalog.{" "}
                <Link href={`/app/${applicationId}/permissions`} className="text-brand hover:underline">
                  Install defaults
                </Link>
                .
              </p>
            ) : (
              <PermissionMatrix
                permissions={matrixPermissions}
                selectedIds={selected}
                readOnly={!canEditPermissions}
                compact
                onChange={
                  canEditPermissions
                    ? (ids) => {
                        const added = [...ids].find((id) => !selected.has(id));
                        const removed = [...selected].find((id) => !ids.has(id));
                        if (added) void togglePermission(added, true);
                        else if (removed) void togglePermission(removed, false);
                      }
                    : undefined
                }
              />
            )}
          </div>
        )}

        {tab === "users" && (
          <div className="p-5">
            {usersLoading ? (
              <p className="text-sm text-muted">Loading assigned users…</p>
            ) : assignedUsers.length === 0 ? (
              <div className="rounded-xl border border-dashed border-ui px-6 py-10 text-center">
                <p className="text-sm text-muted">No users are assigned to this role yet.</p>
                <Link
                  href={`/app/${applicationId}/users`}
                  className="mt-3 inline-block text-sm text-brand hover:underline"
                >
                  Manage users →
                </Link>
              </div>
            ) : (
              <ul className="divide-y divide-ui rounded-xl border border-ui">
                {assignedUsers.map((u) => (
                  <li key={u.id} className="flex items-center gap-4 px-4 py-3 hover:bg-ui-elevated/50">
                    <div
                      className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-brand-muted text-sm font-semibold text-brand"
                      aria-hidden
                    >
                      {initials(u.displayName || u.email)}
                    </div>
                    <div className="min-w-0 flex-1">
                      <Link
                        href={`/users/${u.id}`}
                        className="font-medium text-brand hover:underline"
                      >
                        {u.displayName || u.email}
                      </Link>
                      <p className="truncate text-xs text-muted">{u.email}</p>
                      {u.grantedAt && (
                        <p className="text-[10px] text-faint">Granted {timeAgo(u.grantedAt)}</p>
                      )}
                    </div>
                    <Badge tone={statusTone(normalizeStatus(u.status))}>{u.status}</Badge>
                  </li>
                ))}
              </ul>
            )}
          </div>
        )}
      </div>
    </Card>
  );
}

function normalizeStatus(raw: string): Status {
  const s = raw.toLowerCase();
  if (s === "active" || s === "invited" || s === "suspended" || s === "disabled") {
    return s;
  }
  return "disabled";
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="bg-ui-surface px-4 py-3">
      <p className="text-[10px] font-semibold uppercase tracking-wide text-faint">{label}</p>
      <p className="mt-0.5 text-lg font-semibold text-ui">{value}</p>
    </div>
  );
}
