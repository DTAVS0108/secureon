"use client";

import Link from "next/link";
import { useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { Badge } from "@/components/ui/Badge";
import { assignRolePermissionApi, removeRolePermissionApi } from "@/lib/api/roles";
import type { Permission, Role } from "@/lib/types";

export function PermissionRoleMatrix({
  permission,
  roles,
  assignedRoleIds,
  applicationId,
  readOnly = false,
  onChanged,
}: {
  permission: Permission;
  roles: Role[];
  assignedRoleIds: Set<string>;
  applicationId: string;
  readOnly?: boolean;
  onChanged?: () => void;
}) {
  const [selected, setSelected] = useState(assignedRoleIds);
  const [busyRoleId, setBusyRoleId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useDeferredEffect(() => {
    setSelected(assignedRoleIds);
  }, [assignedRoleIds]);

  async function toggle(role: Role, checked: boolean) {
    if (readOnly || role.isSystem) return;
    setBusyRoleId(role.id);
    setError(null);
    const prev = new Set(selected);
    const next = new Set(selected);
    if (checked) next.add(role.id);
    else next.delete(role.id);
    setSelected(next);
    try {
      if (checked) {
        await assignRolePermissionApi(applicationId, role.id, permission.id);
      } else {
        await removeRolePermissionApi(applicationId, role.id, permission.id);
      }
      onChanged?.();
    } catch (e) {
      setSelected(prev);
      setError(e instanceof Error ? e.message : "Could not update role");
    } finally {
      setBusyRoleId(null);
    }
  }

  if (roles.length === 0) {
    return <p className="text-sm text-muted">No roles in this application.</p>;
  }

  return (
    <div className="space-y-3">
      <p className="text-xs text-muted">
        Toggle which roles grant <code className="font-mono text-[11px]">{permission.key}</code>.
      </p>
      {error && (
        <p className="rounded-lg border border-red-500/30 bg-red-500/10 px-3 py-2 text-xs text-red-800 dark:text-red-200">
          {error}
        </p>
      )}
      <ul className="divide-y divide-ui rounded-xl border border-ui">
        {roles.map((role) => {
          const checked = selected.has(role.id);
          const disabled = readOnly || role.isSystem || busyRoleId === role.id;
          return (
            <li key={role.id} className="flex items-center gap-3 px-4 py-3">
              {!readOnly ? (
                <input
                  type="checkbox"
                  checked={checked}
                  disabled={disabled}
                  onChange={(e) => toggle(role, e.target.checked)}
                  className="h-4 w-4 accent-[var(--ui-primary,#4f46e5)]"
                  aria-label={`Assign to ${role.name}`}
                />
              ) : (
                <span
                  className={`h-2 w-2 shrink-0 rounded-full ${checked ? "bg-brand" : "bg-ui-elevated ring-1 ring-ui"}`}
                  aria-hidden
                />
              )}
              <div className="min-w-0 flex-1">
                <Link
                  href={`/app/${applicationId}/roles?roleId=${role.id}`}
                  className="font-medium text-brand hover:underline"
                >
                  {role.name}
                </Link>
                {role.isSystem && (
                  <Badge tone="neutral" className="ml-2">
                    System
                  </Badge>
                )}
                <p className="truncate text-xs text-muted">{role.description || "—"}</p>
              </div>
              {busyRoleId === role.id && <span className="text-xs text-muted">Saving…</span>}
            </li>
          );
        })}
      </ul>
    </div>
  );
}
