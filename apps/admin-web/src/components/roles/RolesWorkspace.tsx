"use client";

import { useMemo, useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { useRouter } from "next/navigation";
import { RoleLabelBadge } from "@/components/roles/RoleLabelBadge";
import { RoleDetailPanel } from "@/components/roles/RoleDetailPanel";
import { AddRoleButton } from "@/components/roles/AddRoleButton";
import { Badge } from "@/components/ui/Badge";
import { Card, CardHeader } from "@/components/ui/Card";
import { Input } from "@/components/ui/Field";
import { ROLE_LABEL_META } from "@/lib/role-labels";
import type { RoleLabel } from "@/lib/types";
import type { Application, Permission, Role, Tenant } from "@/lib/types";

type RolesFormProps = {
  roles: Role[];
  permissions: Permission[];
  tenants: Tenant[];
  applications: Application[];
  tenantId: string;
  applicationId: string;
};

/** Page header action — create role scoped to the current app (no tenant/app pickers). */
export function RolesWorkspaceHeaderActions(props: RolesFormProps) {
  return <AddRoleButton {...props} triggerLabel="+ New role" />;
}

export type RolesApiCapabilities = {
  enterprise: boolean;
  permissions: boolean;
};

export function RolesWorkspace({
  roles,
  permissions,
  tenants,
  applications,
  tenantId,
  applicationId,
  appName,
  capabilities,
  initialSelectedRoleId,
}: {
  roles: Role[];
  permissions: Permission[];
  tenants: Tenant[];
  applications: Application[];
  tenantId: string;
  applicationId: string;
  appName: string;
  capabilities: RolesApiCapabilities;
  initialSelectedRoleId?: string;
}) {
  const router = useRouter();
  const [selectedId, setSelectedId] = useState<string | null>(
    initialSelectedRoleId && roles.some((r) => r.id === initialSelectedRoleId)
      ? initialSelectedRoleId
      : roles[0]?.id ?? null,
  );
  const [query, setQuery] = useState("");
  const [labelFilter, setLabelFilter] = useState<RoleLabel | "ALL">("ALL");

  useDeferredEffect(() => {
    try {
      const created = sessionStorage.getItem("roles:lastCreated");
      if (created && roles.some((r) => r.id === created)) {
        setSelectedId(created);
        sessionStorage.removeItem("roles:lastCreated");
      }
    } catch {
      /* ignore */
    }
  }, [roles]);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return roles.filter((r) => {
      if (labelFilter !== "ALL" && r.label !== labelFilter) return false;
      if (!q) return true;
      return (
        r.name.toLowerCase().includes(q) ||
        r.description.toLowerCase().includes(q) ||
        r.label.toLowerCase().includes(q)
      );
    });
  }, [roles, query, labelFilter]);

  const selected = roles.find((r) => r.id === selectedId);

  const formProps = {
    roles,
    permissions,
    tenants,
    applications,
    tenantId,
    applicationId,
    onCreated: (roleId: string) => setSelectedId(roleId),
  };

  return (
    <div className="space-y-5">
      <div className="flex flex-col gap-3 rounded-xl border border-ui bg-ui-surface/60 px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
        <p className="text-sm text-muted">
          Permission catalog:{" "}
          <a href={`/app/${applicationId}/permissions`} className="font-medium text-brand hover:underline">
            {permissions.length} keys
          </a>
          {" · "}
          <a href={`/app/${applicationId}/groups`} className="font-medium text-brand hover:underline">
            Groups
          </a>
          {" · "}
          Click a role to edit permissions, inheritance, and assigned users. Built-in roles allow edits except rename.
        </p>
      </div>

      {!capabilities.enterprise && (
        <div className="rounded-lg border border-amber-500/30 bg-amber-500/10 px-4 py-3 text-sm text-amber-900 dark:text-amber-200">
          <strong>Legacy auth-server detected.</strong> Restart{" "}
          <code className="text-xs">apps/auth-server</code> (with migration V11) for enterprise labels,
          permission counts, and the permission matrix. Basic role create/edit still works.
        </div>
      )}

      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <SummaryCard label="Total roles" value={roles.length} />
        <SummaryCard
          label="System / built-in"
          value={roles.filter((r) => r.isSystem || r.isDefault).length}
        />
        <SummaryCard label="Composite" value={roles.filter((r) => r.isComposite).length} />
        <SummaryCard label="Permissions" value={permissions.length} />
      </div>

      <div className="flex flex-wrap gap-2">
        {(Object.keys(ROLE_LABEL_META) as RoleLabel[]).map((label) => (
          <button
            key={label}
            type="button"
            onClick={() => setLabelFilter(labelFilter === label ? "ALL" : label)}
            className={`rounded-full border px-2.5 py-1 text-xs transition-colors ${
              labelFilter === label
                ? "border-brand bg-brand-muted text-brand"
                : "border-ui text-muted hover:bg-ui-elevated"
            }`}
          >
            {ROLE_LABEL_META[label].title}
          </button>
        ))}
        {labelFilter !== "ALL" && (
          <button
            type="button"
            onClick={() => setLabelFilter("ALL")}
            className="text-xs text-brand hover:underline"
          >
            Clear filter
          </button>
        )}
      </div>

      <div className="grid grid-cols-1 gap-5 xl:grid-cols-12 xl:gap-6">
        <Card padded={false} className="xl:col-span-4 xl:sticky xl:top-4 xl:self-start">
          <CardHeader
            title="Role directory"
            description={`${appName} · click a row to inspect`}
            action={
              <AddRoleButton {...formProps} triggerLabel="+ New" triggerSize="sm" triggerVariant="secondary" />
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
          <ul className="max-h-[min(32rem,70vh)] divide-y divide-ui overflow-y-auto">
            {filtered.map((r) => (
              <li key={r.id}>
                <button
                  type="button"
                  onClick={() => setSelectedId(r.id)}
                  className={`flex w-full items-start justify-between gap-3 px-4 py-3 text-left transition-colors hover:bg-ui-elevated ${
                    selectedId === r.id ? "bg-brand-muted/50" : ""
                  }`}
                >
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium text-ui">{r.name}</p>
                    <p className="truncate text-xs text-muted">{r.description || "—"}</p>
                    <div className="mt-2 flex flex-wrap gap-1.5">
                      <RoleLabelBadge label={r.label} />
                      {r.isComposite && <Badge tone="indigo">Composite</Badge>}
                    </div>
                  </div>
                  <div className="shrink-0 text-right text-xs text-muted">
                    <p>{r.permissionCount ?? 0} perms</p>
                    <p>{r.userCount} users</p>
                  </div>
                </button>
              </li>
            ))}
          </ul>
          {filtered.length === 0 && (
            <div className="px-4 py-10 text-center">
              <p className="text-sm text-muted">
                {roles.length === 0
                  ? "No roles yet. Create one to get started."
                  : "No roles match your search."}
              </p>
              {roles.length === 0 && (
                <div className="mt-4">
                  <AddRoleButton {...formProps} triggerLabel="Create first role" triggerSize="sm" />
                </div>
              )}
            </div>
          )}
        </Card>

        <div className="xl:col-span-8">
          {selectedId && selected ? (
            <RoleDetailPanel
              roleId={selectedId}
              roleSummary={selected}
              applicationId={applicationId}
              roles={roles}
              permissions={permissions}
              tenants={tenants}
              applications={applications}
              tenantId={tenantId}
              capabilities={capabilities}
              onClose={() => setSelectedId(null)}
              onDeleted={() => {
                setSelectedId(null);
                router.refresh();
              }}
            />
          ) : (
            <Card className="flex min-h-96 items-center justify-center p-8">
              <p className="text-center text-sm text-muted">
                Select a role from the directory to view permissions, inheritance, and actions.
              </p>
            </Card>
          )}
        </div>
      </div>
    </div>
  );
}

function SummaryCard({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-xl border border-ui bg-ui-surface px-4 py-3">
      <p className="text-xs font-medium uppercase tracking-wide text-faint">{label}</p>
      <p className="mt-1 text-2xl font-semibold text-ui">{value}</p>
    </div>
  );
}
