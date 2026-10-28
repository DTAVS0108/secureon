"use client";

import { useMemo, useState } from "react";
import { useDialog } from "@/components/ui/Dialogs";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { Input } from "@/components/ui/Field";
import { useToast } from "@/components/ui/Toast";
import { CONSOLE_FEATURE_LABELS } from "@/lib/api/admin-console-capabilities";
import type { AdminConsoleRoleType } from "@/lib/api/admin-console-access";
import type { TenantConsoleRolesCatalog } from "@/lib/api/tenant-console-roles";
import { saveConsoleRoleFeaturesAction } from "@/lib/actions/tenant-console-roles";
import {
  createTenantPermissionAction,
  deleteTenantPermissionAction,
  deleteTenantRoleAction,
  getTenantRoleAction,
  listTenantPermissionsAction,
  listTenantRolesAction,
  updateTenantRoleAction,
} from "@/lib/actions/tenant-rbac";
import {
  consoleFeaturesFromPermissionIds,
  isConsolePermissionKey,
  syncConsolePermissionIds,
} from "@/lib/tenant-console-sections";
import { CreateTenantCustomRoleDialog } from "@/components/tenant-rbac/CreateTenantCustomRoleDialog";
import {
  isTenantCatalogPermission,
  type TenantPermission,
  type TenantRole,
  type TenantRoleDetail,
} from "@/lib/api/tenant-rbac";
import { isUserDefinedTenantRole } from "@/lib/tenant-rbac-catalog";
import type { Application } from "@/lib/types";

type WorkspaceTab = "roles" | "permissions";
type RoleSelection = { kind: "console"; roleType: string } | { kind: "custom"; roleId: string };

function SummaryCard({ label, value }: { label: string; value: number | string }) {
  return (
    <div className="rounded-xl border border-ui bg-ui-surface px-4 py-3">
      <p className="text-xs text-muted">{label}</p>
      <p className="mt-1 text-xl font-semibold tabular-nums text-ui">{value}</p>
    </div>
  );
}

function scopeLabel(role: TenantConsoleRolesCatalog["roles"][number]): string {
  if (role.allApplications) return "All applications in tenant";
  if (role.applicationScoped) return "Single assigned application";
  return "Assigned applications only";
}

function ConsoleSectionGrid({
  features,
  selected,
  editable,
  onToggle,
}: {
  features: string[];
  selected: string[];
  editable: boolean;
  onToggle?: (feature: string) => void;
}) {
  return (
    <div className="grid gap-2 sm:grid-cols-2">
      {features.map((key) => {
        const enabled = selected.includes(key);
        const body = (
          <>
            <span
              className={`inline-block h-2 w-2 shrink-0 rounded-full ${
                enabled ? "bg-brand" : "bg-faint"
              }`}
              aria-hidden
            />
            <span>{CONSOLE_FEATURE_LABELS[key] ?? key}</span>
          </>
        );
        if (!editable) {
          return (
            <div
              key={key}
              className={`flex items-center gap-2 rounded-lg border px-3 py-2 text-sm ${
                enabled ? "border-brand bg-brand-muted/30" : "border-ui opacity-60"
              }`}
            >
              {body}
            </div>
          );
        }
        return (
          <label
            key={key}
            className={`flex cursor-pointer items-center gap-2 rounded-lg border px-3 py-2 text-sm ${
              enabled ? "border-brand bg-brand-muted/30" : "border-ui"
            }`}
          >
            <input
              type="checkbox"
              className="sr-only"
              checked={enabled}
              onChange={() => onToggle?.(key)}
            />
            {body}
          </label>
        );
      })}
    </div>
  );
}

export function TenantRolesWorkspace({
  tenantId,
  tenantName,
  applications,
  consoleCatalog: consoleCatalogProp,
  initialCustomRoles,
  initialPermissions,
}: {
  tenantId: string;
  tenantName: string;
  applications: Application[];
  consoleCatalog: TenantConsoleRolesCatalog;
  initialCustomRoles: TenantRole[];
  initialPermissions: TenantPermission[];
}) {
  const { toast } = useToast();
  const [tab, setTab] = useState<WorkspaceTab>("roles");
  const [consoleCatalog, setConsoleCatalog] = useState(consoleCatalogProp);
  const [customRoles, setCustomRoles] = useState(initialCustomRoles);
  const [permissions, setPermissions] = useState(initialPermissions);
  const [query, setQuery] = useState("");
  const [selected, setSelected] = useState<RoleSelection>({
    kind: "console",
    roleType: consoleCatalogProp.roles[0]?.roleType ?? "APPLICATION_ADMIN",
  });
  const [editConsoleFeatures, setEditConsoleFeatures] = useState<string[]>([]);
  const [customDetail, setCustomDetail] = useState<TenantRoleDetail | null>(null);
  const [editName, setEditName] = useState("");
  const [editDescription, setEditDescription] = useState("");
  const [editPermissionIds, setEditPermissionIds] = useState<string[]>([]);
  const [editApplicationIds, setEditApplicationIds] = useState<string[]>([]);
  const [saving, setSaving] = useState(false);
  const dialog = useDialog();
  const [createRoleOpen, setCreateRoleOpen] = useState(false);
  const [permKey, setPermKey] = useState("");
  const [permDescription, setPermDescription] = useState("");

  const consoleRole = consoleCatalog.roles.find(
    (r) => r.roleType === (selected.kind === "console" ? selected.roleType : ""),
  );

  const userCustomRoles = useMemo(
    () => customRoles.filter(isUserDefinedTenantRole),
    [customRoles],
  );

  const governancePermissions = permissions.filter((p) => !isConsolePermissionKey(p.key));
  const editCustomConsoleFeatures = consoleFeaturesFromPermissionIds(
    permissions,
    editPermissionIds,
    consoleCatalog.features,
  );

  useDeferredEffect(() => {
    if (selected.kind === "console" && consoleRole) {
      setEditConsoleFeatures(consoleRole.defaultFeatures);
    }
  }, [selected, consoleRole?.roleType, consoleRole?.defaultFeatures]);

  async function reloadCustom() {
    const [roles, perms] = await Promise.all([
      listTenantRolesAction(tenantId),
      listTenantPermissionsAction(tenantId),
    ]);
    setCustomRoles(roles);
    setPermissions(perms);
  }

  useDeferredEffect(() => {
    if (selected.kind !== "custom") {
      setCustomDetail(null);
      return;
    }
    let cancelled = false;
    void getTenantRoleAction(tenantId, selected.roleId)
      .then((row) => {
        if (!cancelled) {
          setCustomDetail(row);
          setEditName(row.name);
          setEditDescription(row.description ?? "");
          setEditPermissionIds(row.permissionIds);
          setEditApplicationIds(row.applicationIds);
        }
      })
      .catch(() => {
        if (!cancelled) setCustomDetail(null);
      });
    return () => {
      cancelled = true;
    };
  }, [tenantId, selected]);

  const filteredConsole = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return consoleCatalog.roles;
    return consoleCatalog.roles.filter(
      (r) =>
        r.name.toLowerCase().includes(q) ||
        r.description.toLowerCase().includes(q) ||
        r.roleType.toLowerCase().includes(q),
    );
  }, [consoleCatalog.roles, query]);

  const filteredCustom = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return customRoles;
    return customRoles.filter(
      (r) =>
        r.name.toLowerCase().includes(q) ||
        (r.description ?? "").toLowerCase().includes(q),
    );
  }, [customRoles, query]);

  const customPermissions = permissions.filter((p) => !isTenantCatalogPermission(p.key));
  const catalogPermissions = permissions.filter((p) => isTenantCatalogPermission(p.key));

  function togglePermission(id: string) {
    setEditPermissionIds((prev) =>
      prev.includes(id) ? prev.filter((p) => p !== id) : [...prev, id],
    );
  }

  function toggleConsoleFeature(feature: string) {
    setEditConsoleFeatures((prev) =>
      prev.includes(feature) ? prev.filter((f) => f !== feature) : [...prev, feature],
    );
  }

  function toggleCustomConsoleFeature(feature: string) {
    setEditPermissionIds((prev) =>
      syncConsolePermissionIds(permissions, prev, feature, !editCustomConsoleFeatures.includes(feature)),
    );
  }

  async function saveConsoleRole() {
    if (selected.kind !== "console") return;
    setSaving(true);
    try {
      const updated = await saveConsoleRoleFeaturesAction(
        tenantId,
        selected.roleType as AdminConsoleRoleType,
        editConsoleFeatures,
      );
      setConsoleCatalog((prev) => ({
        ...prev,
        roles: prev.roles.map((r) =>
          r.roleType === selected.roleType
            ? { ...r, defaultFeatures: updated, customized: true }
            : r,
        ),
      }));
      toast("Console sections saved", "success");
    } catch (e) {
      toast(e instanceof Error ? e.message : "Could not save console sections", "error");
    } finally {
      setSaving(false);
    }
  }

  function toggleApplication(id: string) {
    setEditApplicationIds((prev) =>
      prev.includes(id) ? prev.filter((a) => a !== id) : [...prev, id],
    );
  }

  async function saveCustomRole() {
    if (selected.kind !== "custom" || !customDetail || !editName.trim()) return;
    setSaving(true);
    try {
      const updated = await updateTenantRoleAction(tenantId, selected.roleId, {
        name: editName.trim(),
        description: editDescription.trim(),
        permissionIds: editPermissionIds,
        applicationIds: editApplicationIds,
      });
      setCustomDetail(updated);
      toast("Role saved", "success");
      await reloadCustom();
    } catch (e) {
      toast(e instanceof Error ? e.message : "Could not save role", "error");
    } finally {
      setSaving(false);
    }
  }

  async function removeCustomRole() {
    if (selected.kind !== "custom" || !customDetail) return;
    if (!(await dialog.confirm({ title: `Delete the role "${customDetail.name}"?`, message: "Users lose what it gave them.", confirmLabel: "Delete role" }))) return;
    setSaving(true);
    try {
      await deleteTenantRoleAction(tenantId, selected.roleId);
      toast("Role deleted", "success");
      setSelected({ kind: "console", roleType: "APPLICATION_ADMIN" });
      setCustomDetail(null);
      await reloadCustom();
    } catch (e) {
      toast(e instanceof Error ? e.message : "Could not delete role", "error");
    } finally {
      setSaving(false);
    }
  }

  async function addPermission() {
    const key = permKey.trim().toLowerCase();
    if (!key) return;
    setSaving(true);
    try {
      await createTenantPermissionAction(tenantId, { key, description: permDescription.trim() });
      toast("Permission created", "success");
      setPermKey("");
      setPermDescription("");
      await reloadCustom();
    } catch (e) {
      toast(e instanceof Error ? e.message : "Could not create permission", "error");
    } finally {
      setSaving(false);
    }
  }

  async function removePermission(permission: TenantPermission) {
    if (!(await dialog.confirm({ title: `Delete the permission "${permission.key}"?`, message: "Roles that include it lose it.", confirmLabel: "Delete permission" }))) return;
    setSaving(true);
    try {
      await deleteTenantPermissionAction(tenantId, permission.id);
      toast("Permission deleted", "success");
      await reloadCustom();
    } catch (e) {
      toast(e instanceof Error ? e.message : "Could not delete permission", "error");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="space-y-5">
      <div className="rounded-xl border border-ui bg-ui-elevated/40 px-4 py-3 text-sm text-muted">
        Manage console operator roles and custom tenant governance roles for{" "}
        <strong className="text-ui">{tenantName}</strong>. Console roles (Application Admin, Tenant
        Admin, Tenant Super Admin) control operator sign-in; custom roles below are tenant-defined
        and use the permission catalog.
      </div>

      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <SummaryCard
          label="Console roles"
          value={consoleCatalog.roles.length}
        />
        <SummaryCard label="Custom roles" value={userCustomRoles.length} />
        <SummaryCard label="Permissions" value={permissions.length} />
        <SummaryCard
          label="Assignments"
          value={consoleCatalog.roles.reduce((s, r) => s + r.assignmentCount, 0)}
        />
      </div>

      <div className="flex gap-1 rounded-lg border border-ui bg-ui-surface p-1 w-fit">
        {(["roles", "permissions"] as const).map((t) => (
          <button
            key={t}
            type="button"
            onClick={() => setTab(t)}
            className={`rounded-md px-3 py-1.5 text-sm capitalize transition-colors ${
              tab === t ? "bg-brand font-medium text-on-brand" : "text-muted hover:bg-ui-elevated"
            }`}
          >
            {t}
          </button>
        ))}
      </div>

      {tab === "permissions" ? (
        <Card padded={false}>
          <CardHeader
            title="Permission catalog"
            description="Platform catalog keys plus custom permissions for tenant roles"
            action={
              <div className="flex flex-wrap items-end gap-2">
                <Input
                  value={permKey}
                  onChange={(e) => setPermKey(e.target.value)}
                  placeholder="Permission key"
                  aria-label="Permission key"
                  className="w-40"
                />
                <Input
                  value={permDescription}
                  onChange={(e) => setPermDescription(e.target.value)}
                  placeholder="Description"
                  aria-label="Permission description"
                  className="w-48"
                />
                <Button size="sm" disabled={saving || !permKey.trim()} onClick={() => void addPermission()}>
                  + Add permission
                </Button>
              </div>
            }
          />
          <div className="grid gap-6 px-5 py-5 lg:grid-cols-2">
            <div>
              <p className="mb-3 text-sm font-medium text-ui">Platform catalog</p>
              <ul className="divide-y divide-ui rounded-lg border border-ui">
                {catalogPermissions.map((p) => (
                  <li key={p.id} className="px-3 py-2.5">
                    <p className="font-mono text-xs text-ui">{p.key}</p>
                    <p className="text-xs text-faint">{p.description}</p>
                  </li>
                ))}
              </ul>
            </div>
            <div>
              <p className="mb-3 text-sm font-medium text-ui">Custom permissions</p>
              {customPermissions.length === 0 ? (
                <p className="text-sm text-faint">No custom permissions yet.</p>
              ) : (
                <ul className="divide-y divide-ui rounded-lg border border-ui">
                  {customPermissions.map((p) => (
                    <li key={p.id} className="flex items-start justify-between gap-3 px-3 py-2.5">
                      <div>
                        <p className="font-mono text-xs text-ui">{p.key}</p>
                        <p className="text-xs text-faint">{p.description || "—"}</p>
                      </div>
                      <Button
                        size="sm"
                        variant="ghost"
                        disabled={saving}
                        onClick={() => void removePermission(p)}
                      >
                        Delete
                      </Button>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>
        </Card>
      ) : (
        <div className="grid grid-cols-1 gap-5 xl:grid-cols-12">
          <Card padded={false} className="xl:col-span-4 xl:sticky xl:top-4 xl:self-start">
            <CardHeader
              title="Role directory"
              description="Console and custom roles"
              action={
                <Button
                  size="sm"
                  variant="secondary"
                  disabled={saving}
                  onClick={() => setCreateRoleOpen(true)}
                >
                  + New role
                </Button>
              }
            />
            <div className="border-b border-ui px-4 py-3">
              <Input
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Search roles…"
                aria-label="Search roles"
              />
            </div>
            <div className="max-h-[min(32rem,70vh)] overflow-y-auto">
              <p className="px-4 pt-3 text-[10px] font-semibold uppercase tracking-wider text-faint">
                Console roles
              </p>
              <ul className="divide-y divide-ui">
                {filteredConsole.map((r) => (
                  <li key={r.roleType}>
                    <button
                      type="button"
                      onClick={() => setSelected({ kind: "console", roleType: r.roleType })}
                      className={`flex w-full items-start justify-between gap-3 px-4 py-3 text-left transition-colors hover:bg-ui-elevated ${
                        selected.kind === "console" && selected.roleType === r.roleType
                          ? "bg-brand-muted/50"
                          : ""
                      }`}
                    >
                      <div className="min-w-0">
                        <p className="truncate font-medium text-ui">{r.name}</p>
                        <p className="truncate text-xs text-faint">{r.description}</p>
                      </div>
                      <Badge tone="info">System</Badge>
                    </button>
                  </li>
                ))}
              </ul>
              <p className="px-4 pt-4 text-[10px] font-semibold uppercase tracking-wider text-faint">
                Custom roles
              </p>
              <ul className="divide-y divide-ui">
                {filteredCustom.map((r) => (
                  <li key={r.id}>
                    <button
                      type="button"
                      onClick={() => setSelected({ kind: "custom", roleId: r.id })}
                      className={`flex w-full items-start justify-between gap-3 px-4 py-3 text-left transition-colors hover:bg-ui-elevated ${
                        selected.kind === "custom" && selected.roleId === r.id
                          ? "bg-brand-muted/50"
                          : ""
                      }`}
                    >
                      <div className="min-w-0">
                        <p className="truncate font-medium text-ui">{r.name}</p>
                        <p className="truncate text-xs text-faint">{r.description || "—"}</p>
                      </div>
                    </button>
                  </li>
                ))}
                {filteredCustom.length === 0 && (
                  <li className="px-4 py-6 text-center text-xs text-faint">No custom roles yet.</li>
                )}
              </ul>
            </div>
          </Card>

          <Card padded={false} className="xl:col-span-8">
            {selected.kind === "console" && consoleRole ? (
              <>
                <CardHeader
                  title={consoleRole.name}
                  description={
                    consoleRole.customized
                      ? "Customized default console sections for this tenant"
                      : "Default console sections — customize and save for this tenant"
                  }
                />
                <div className="space-y-6 px-5 py-5">
                  <p className="text-sm text-muted">{consoleRole.description}</p>
                  <div className="grid gap-4 sm:grid-cols-2">
                    <div className="rounded-lg border border-ui px-3 py-2">
                      <p className="text-xs text-faint">Application scope</p>
                      <p className="mt-1 text-sm text-ui">{scopeLabel(consoleRole)}</p>
                    </div>
                    <div className="rounded-lg border border-ui px-3 py-2">
                      <p className="text-xs text-faint">Active assignments</p>
                      <p className="mt-1 text-sm tabular-nums text-ui">{consoleRole.assignmentCount}</p>
                    </div>
                  </div>
                  <div>
                    <p className="mb-2 text-sm font-medium text-ui">Console sections</p>
                    <p className="mb-3 text-xs text-muted">
                      Default sections granted to operators with this role. Per-user overrides can
                      still be set from the tenant roster.
                    </p>
                    <ConsoleSectionGrid
                      features={consoleCatalog.features}
                      selected={editConsoleFeatures}
                      editable
                      onToggle={toggleConsoleFeature}
                    />
                    <div className="mt-4 flex justify-end border-t border-ui pt-4">
                      <Button disabled={saving} onClick={() => void saveConsoleRole()}>
                        {saving ? "Saving…" : "Save console sections"}
                      </Button>
                    </div>
                  </div>
                </div>
              </>
            ) : selected.kind === "custom" && customDetail ? (
              <>
                <CardHeader
                  title={customDetail.name}
                  description={
                    customDetail.systemRole ? "System tenant role" : "Custom tenant role"
                  }
                  action={
                    <div className="flex gap-2">
                      {!customDetail.systemRole && (
                        <Button size="sm" variant="ghost" disabled={saving} onClick={() => void removeCustomRole()}>
                          Delete
                        </Button>
                      )}
                      <Button size="sm" disabled={saving} onClick={() => void saveCustomRole()}>
                        {saving ? "Saving…" : "Save changes"}
                      </Button>
                    </div>
                  }
                />
                <div className="space-y-6 px-5 py-5">
                  <div className="grid gap-4 sm:grid-cols-2">
                    <div>
                      <label className="mb-1 block text-xs font-medium text-muted">Name</label>
                      <Input
                        value={editName}
                        disabled={customDetail.systemRole}
                        onChange={(e) => setEditName(e.target.value)}
                      />
                    </div>
                    <div>
                      <label className="mb-1 block text-xs font-medium text-muted">Description</label>
                      <Input
                        value={editDescription}
                        onChange={(e) => setEditDescription(e.target.value)}
                      />
                    </div>
                  </div>
                  <div>
                    <p className="mb-2 text-sm font-medium text-ui">Console sections</p>
                    <p className="mb-3 text-xs text-muted">
                      Admin console areas operators with this role can access.
                    </p>
                    <ConsoleSectionGrid
                      features={consoleCatalog.features}
                      selected={editCustomConsoleFeatures}
                      editable
                      onToggle={toggleCustomConsoleFeature}
                    />
                  </div>
                  {governancePermissions.length > 0 && (
                    <div>
                      <p className="mb-2 text-sm font-medium text-ui">Governance permissions</p>
                      <div className="grid gap-2 sm:grid-cols-2">
                        {governancePermissions.map((p) => (
                          <label
                            key={p.id}
                            className={`flex cursor-pointer items-start gap-2 rounded-lg border px-3 py-2 text-sm ${
                              editPermissionIds.includes(p.id)
                                ? "border-brand bg-brand-muted/30"
                                : "border-ui"
                            }`}
                          >
                            <input
                              type="checkbox"
                              className="mt-0.5"
                              checked={editPermissionIds.includes(p.id)}
                              onChange={() => togglePermission(p.id)}
                            />
                            <span>
                              <span className="block font-mono text-xs text-ui">{p.key}</span>
                              <span className="block text-xs text-faint">{p.description}</span>
                            </span>
                          </label>
                        ))}
                      </div>
                    </div>
                  )}
                  <div>
                    <p className="mb-2 text-sm font-medium text-ui">Application scope</p>
                    <p className="mb-3 text-xs text-muted">
                      Leave empty to allow all applications. Select specific apps to limit this role.
                    </p>
                    <div className="flex flex-wrap gap-2">
                      {applications.map((app) => {
                        const on = editApplicationIds.includes(app.id);
                        return (
                          <button
                            key={app.id}
                            type="button"
                            onClick={() => toggleApplication(app.id)}
                            className={`rounded-full border px-3 py-1 text-xs transition-colors ${
                              on
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
                </div>
              </>
            ) : (
              <div className="px-6 py-12 text-center text-sm text-faint">
                Select a role to view or edit details.
              </div>
            )}
          </Card>
        </div>
      )}
      <CreateTenantCustomRoleDialog
        open={createRoleOpen}
        onClose={() => setCreateRoleOpen(false)}
        tenantId={tenantId}
        tenantName={tenantName}
        applications={applications}
        onCreated={async (roleId) => {
          setSelected({ kind: "custom", roleId });
          await reloadCustom();
        }}
      />
    </div>
  );
}
