"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { getPermission } from "@/lib/api/permissions";
import { PermissionRoleMatrix } from "@/components/permissions/PermissionRoleMatrix";
import type { PermissionDetail, Role } from "@/lib/types";
import { PermissionFormModal } from "@/components/permissions/PermissionFormModal";
import { SeedDefaultsButton } from "@/components/permissions/SeedDefaultsButton";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { Input } from "@/components/ui/Field";
import { permissionDeleteAction } from "@/lib/actions";
import type { Permission } from "@/lib/types";

export function PermissionsWorkspaceHeaderActions({
  applicationId,
  permissions,
  onCreated,
}: {
  applicationId: string;
  permissions: Permission[];
  onCreated?: (id: string) => void;
}) {
  return (
    <div className="flex flex-wrap items-center gap-2">
      <SeedDefaultsButton applicationId={applicationId} missingCount={permissions.length} />
      <PermissionFormModal
        applicationId={applicationId}
        onCreated={onCreated}
        triggerLabel="+ New permission"
      />
    </div>
  );
}

export function PermissionsWorkspace({
  permissions,
  roles,
  applicationId,
  appName,
}: {
  permissions: Permission[];
  roles: Role[];
  applicationId: string;
  appName: string;
}) {
  const [query, setQuery] = useState("");
  const [selectedId, setSelectedId] = useState<string | null>(permissions[0]?.id ?? null);

  useDeferredEffect(() => {
    try {
      const created = sessionStorage.getItem("permissions:lastCreated");
      if (created && permissions.some((p) => p.id === created)) {
        setSelectedId(created);
        sessionStorage.removeItem("permissions:lastCreated");
      }
    } catch {
      /* ignore */
    }
  }, [permissions]);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return permissions;
    return permissions.filter(
      (p) =>
        p.key.toLowerCase().includes(q) ||
        p.description.toLowerCase().includes(q) ||
        p.resource.toLowerCase().includes(q) ||
        p.action.toLowerCase().includes(q) ||
        p.id.toLowerCase().includes(q),
    );
  }, [permissions, query]);

  const selected = permissions.find((p) => p.id === selectedId);

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-3 rounded-xl border border-ui bg-ui-surface/60 px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
        <p className="text-sm text-muted">
          <strong className="text-ui">Permission catalog</strong> — assign keys to roles below or from{" "}
          <Link href={`/app/${applicationId}/groups`} className="text-brand hover:underline">
            Groups
          </Link>
          {" · "}
          <Link href={`/app/${applicationId}/roles`} className="text-brand hover:underline">
            Roles
          </Link>
          .
        </p>
        <SeedDefaultsButton applicationId={applicationId} missingCount={permissions.length} />
      </div>

      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
        <SummaryCard label="Total permissions" value={permissions.length} />
        <SummaryCard
          label="Resources"
          value={new Set(permissions.map((p) => p.resource)).size}
        />
        <SummaryCard label="Application" value={appName} isText />
      </div>

      <div className="grid grid-cols-1 gap-5 xl:grid-cols-12 xl:gap-6">
        <Card padded={false} className="xl:col-span-4 xl:sticky xl:top-4 xl:self-start">
          <CardHeader
            title="Permission directory"
            description={`${appName} · mirrors DB rows`}
            action={
              <PermissionFormModal
                applicationId={applicationId}
                onCreated={(id) => setSelectedId(id)}
                triggerLabel="+ New"
                triggerSize="sm"
                triggerVariant="secondary"
              />
            }
          />
          <div className="border-b border-ui px-4 py-3">
            <Input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search key, description, id…"
              aria-label="Search permissions"
            />
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="border-b border-ui bg-ui-elevated/50 text-xs uppercase tracking-wide text-faint">
                  <th className="px-4 py-2 font-medium">Key</th>
                  <th className="px-4 py-2 font-medium">Roles</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-ui">
                {filtered.map((p) => (
                  <tr key={p.id}>
                    <td className="px-0 py-0">
                      <button
                        type="button"
                        onClick={() => setSelectedId(p.id)}
                        className={`block w-full px-4 py-3 text-left transition-colors hover:bg-ui-elevated ${
                          selectedId === p.id ? "bg-brand-muted/50" : ""
                        }`}
                      >
                        <span className="font-mono text-sm text-ui">{p.key}</span>
                        <p className="mt-0.5 truncate text-xs text-muted">
                          {p.description || "—"}
                        </p>
                      </button>
                    </td>
                    <td className="px-4 py-3 text-xs text-muted">{p.roleCount ?? "—"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {filtered.length === 0 && (
            <div className="px-4 py-10 text-center">
              <p className="text-sm text-muted">
                {permissions.length === 0
                  ? "No permissions in the database for this app."
                  : "No permissions match your search."}
              </p>
              {permissions.length === 0 && (
                <div className="mt-4">
                  <PermissionFormModal
                    applicationId={applicationId}
                    onCreated={(id) => setSelectedId(id)}
                    triggerLabel="Create first permission"
                    triggerSize="sm"
                  />
                </div>
              )}
            </div>
          )}
        </Card>

        <div className="xl:col-span-8">
          {selected ? (
            <PermissionDetailPanel
              permission={selected}
              roles={roles}
              applicationId={applicationId}
              onDeleted={() => setSelectedId(null)}
            />
          ) : (
            <Card className="flex min-h-80 items-center justify-center p-8">
              <p className="text-center text-sm text-muted">
                Select a permission to view database fields and linked roles.
              </p>
            </Card>
          )}
        </div>
      </div>
    </div>
  );
}

function PermissionDetailPanel({
  permission,
  roles,
  applicationId,
  onDeleted,
}: {
  permission: Permission;
  roles: Role[];
  applicationId: string;
  onDeleted: () => void;
}) {
  const [detail, setDetail] = useState<PermissionDetail | null>(null);
  const [tab, setTab] = useState<"details" | "roles">("roles");

  useDeferredEffect(() => {
    getPermission(applicationId, permission.id)
      .then(setDetail)
      .catch(() => setDetail({ ...permission, roles: [], roleCount: permission.roleCount ?? 0 }));
  }, [applicationId, permission]);

  const assignedRoles = detail?.roles ?? [];
  const roleCount = detail?.roleCount ?? permission.roleCount ?? 0;
  const assignedIds = new Set(assignedRoles.map((r) => r.id));

  return (
    <Card padded={false}>
      <div className="flex flex-wrap items-start justify-between gap-4 border-b border-ui p-5">
        <div>
          <h2 className="font-mono text-lg font-semibold text-ui">{permission.key}</h2>
          <p className="mt-1 text-sm text-muted">{permission.description || "No description"}</p>
          <div className="mt-3 flex flex-wrap gap-2">
            <Badge tone="neutral">{permission.resource}</Badge>
            <Badge tone="indigo">{permission.action}</Badge>
          </div>
        </div>
        <div className="flex flex-wrap gap-2">
          <PermissionFormModal
            permission={permission}
            applicationId={applicationId}
            triggerLabel="Edit"
            triggerVariant="secondary"
            triggerSize="sm"
          />
          {roleCount === 0 ? (
            <ConfirmDialog
              action={async (fd) => {
                fd.set("applicationId", applicationId);
                await permissionDeleteAction(fd);
                onDeleted();
              }}
              id={permission.id}
              triggerLabel="Delete"
              triggerVariant="danger"
              triggerSize="sm"
              title="Delete permission"
              message={`Remove "${permission.key}" from the database?`}
              confirmLabel="Delete"
            />
          ) : (
            <Button variant="danger" size="sm" disabled title="Unassign from all roles first">
              Delete
            </Button>
          )}
        </div>
      </div>

      <nav className="flex gap-1 border-b border-ui px-5" aria-label="Permission sections">
        {(
          [
            { id: "roles" as const, label: "Role assignment", count: roleCount },
            { id: "details" as const, label: "Details" },
          ] as const
        ).map((t) => (
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
            {"count" in t && t.count !== undefined && (
              <span className="ml-1.5 rounded-full bg-ui-elevated px-1.5 py-0.5 text-[10px] font-semibold text-faint">
                {t.count}
              </span>
            )}
          </button>
        ))}
      </nav>

      {tab === "roles" && (
        <div className="p-5">
          <PermissionRoleMatrix
            permission={permission}
            roles={roles}
            assignedRoleIds={assignedIds}
            applicationId={applicationId}
            onChanged={() => {
              getPermission(applicationId, permission.id)
                .then(setDetail)
                .catch(() => undefined);
            }}
          />
        </div>
      )}

      {tab === "details" && (
        <>
          <dl className="grid gap-px border-b border-ui bg-ui sm:grid-cols-2">
            <DbField label="id (PK)" value={permission.id} mono />
            <DbField label="application_id" value={permission.applicationId ?? applicationId} mono />
            <DbField label="key" value={permission.key} mono />
            <DbField label="description" value={permission.description || "NULL"} />
          </dl>
          <div className="p-5">
            <p className="text-xs font-semibold uppercase tracking-wide text-muted">SQL reference</p>
            <pre className="mt-2 overflow-x-auto rounded-lg border border-ui bg-ui-elevated p-3 text-xs text-muted">
{`INSERT INTO permission (id, application_id, key, description)
VALUES (gen_random_uuid(), '${applicationId}', '${permission.key}', '…')
ON CONFLICT (application_id, key) DO NOTHING;`}
            </pre>
          </div>
        </>
      )}
    </Card>
  );
}

function DbField({
  label,
  value,
  mono,
}: {
  label: string;
  value: string;
  mono?: boolean;
}) {
  return (
    <div className="bg-ui-surface px-4 py-3">
      <dt className="text-[10px] font-semibold uppercase tracking-wide text-faint">{label}</dt>
      <dd className={`mt-1 break-all text-sm text-ui ${mono ? "font-mono text-xs" : ""}`}>
        {value}
      </dd>
    </div>
  );
}

function SummaryCard({
  label,
  value,
  isText,
}: {
  label: string;
  value: number | string;
  isText?: boolean;
}) {
  return (
    <div className="rounded-xl border border-ui bg-ui-surface px-4 py-3">
      <p className="text-xs font-medium uppercase tracking-wide text-faint">{label}</p>
      <p
        className={`mt-1 font-semibold text-ui ${isText ? "truncate text-sm" : "text-2xl"}`}
        title={isText ? String(value) : undefined}
      >
        {value}
      </p>
    </div>
  );
}
