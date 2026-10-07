"use client";

import { useEffect, useMemo, useState } from "react";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { FieldRow, Select } from "@/components/ui/Field";
import { Table, TBody, TD, TH, THead, TR } from "@/components/ui/Table";
import { useToast } from "@/components/ui/Toast";
import {
  groupConsoleAccessByUser,
  consoleRoleLabels,
} from "@/components/tenants/console-access-utils";
import {
  CONSOLE_ROLE_LABELS,
  grantConsoleAccess,
  revokeConsoleAccess,
  type AdminConsoleRoleType,
  type ConsoleAccessAssignment,
} from "@/lib/api/admin-console-access";
import { listUsers } from "@/lib/api/users";
import { useAdminContext } from "@/components/AdminContextProvider";
import {
  canViewUserInOperatorList,
  filterUsersForOperatorList,
  operatorListViewerFromContext,
  userConsoleRoleTypes,
} from "@/lib/operator-list-visibility";
import type { Application } from "@/lib/types";

export function AdminConsoleAccessPanel({
  tenantId,
  applications,
  assignments,
  onMutated,
}: {
  tenantId: string;
  applications: Application[];
  assignments: ConsoleAccessAssignment[];
  onMutated: () => Promise<void>;
}) {
  const { toast } = useToast();
  const adminContext = useAdminContext();
  const listViewer = useMemo(
    () => operatorListViewerFromContext(adminContext),
    [adminContext],
  );
  const [open, setOpen] = useState(false);
  const [userId, setUserId] = useState("");
  const [roleType, setRoleType] = useState<AdminConsoleRoleType>("APPLICATION_ADMIN");
  const [applicationId, setApplicationId] = useState(applications[0]?.id ?? "");
  const [busy, setBusy] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [grantableUsers, setGrantableUsers] = useState<
    { id: string; email: string; label: string }[]
  >([]);

  const needsApp = roleType !== "TENANT_SUPER_ADMIN";
  /** Tenant super admins may grant application and tenant admin, not their own role. */
  const canManageSuperAdmins = adminContext.platformSuperAdmin;

  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    async function loadGrantable() {
      if (roleType === "TENANT_SUPER_ADMIN") {
        const byId = new Map<string, { id: string; email: string; label: string }>();
        for (const app of applications) {
          const rows = await listUsers(tenantId, app.id);
          for (const u of rows) {
            if (!byId.has(u.id)) {
              const name = `${u.firstName} ${u.lastName}`.trim();
              byId.set(u.id, { id: u.id, email: u.email, label: name || u.email });
            }
          }
        }
        if (!cancelled) {
          const visible = filterUsersForOperatorList([...byId.values()], listViewer, assignments);
          setGrantableUsers(visible.sort((a, b) => a.label.localeCompare(b.label)));
        }
        return;
      }
      if (!applicationId) {
        setGrantableUsers([]);
        return;
      }
      const rows = await listUsers(tenantId, applicationId);
      if (!cancelled) {
        const mapped = rows.map((u) => {
          const name = `${u.firstName} ${u.lastName}`.trim();
          return { id: u.id, email: u.email, label: name || u.email };
        });
        setGrantableUsers(
          filterUsersForOperatorList(mapped, listViewer, assignments).sort((a, b) =>
            a.label.localeCompare(b.label),
          ),
        );
      }
    }
    void loadGrantable().catch(() => {
      if (!cancelled) setGrantableUsers([]);
    });
    return () => {
      cancelled = true;
    };
  }, [open, roleType, applicationId, tenantId, applications, assignments, listViewer]);

  const grouped = useMemo(() => {
    const rows = groupConsoleAccessByUser(assignments);
    return rows.filter((group) =>
      canViewUserInOperatorList(
        listViewer,
        group.userId,
        userConsoleRoleTypes(group.userId, assignments),
      ),
    );
  }, [assignments, listViewer]);

  function scopeLabel(entries: ConsoleAccessAssignment[]) {
    if (entries.some((e) => e.roleType === "TENANT_SUPER_ADMIN")) {
      return "All applications in tenant";
    }
    return entries
      .map((e) => e.applicationName ?? e.applicationId ?? "—")
      .join(", ");
  }

  async function refresh() {
    await onMutated();
  }

  async function handleGrant() {
    if (!userId) {
      toast("Select a user", "error");
      return;
    }
    if (needsApp && !applicationId) {
      toast("Select an application", "error");
      return;
    }
    setSubmitting(true);
    try {
      await grantConsoleAccess(tenantId, {
        userId,
        roleType,
        applicationId: needsApp ? applicationId : undefined,
      });
      toast(
        "Console access granted. Set the user password under the application Users tab before they sign in.",
        "success",
      );
      setOpen(false);
      setUserId("");
      await refresh();
    } catch (e) {
      toast(e instanceof Error ? e.message : "Could not grant access", "error");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleRevoke(id: string) {
    setBusy(id);
    try {
      await revokeConsoleAccess(tenantId, id);
      toast("Console access removed", "success");
      await refresh();
    } catch (e) {
      toast(e instanceof Error ? e.message : "Could not remove access", "error");
    } finally {
      setBusy(null);
    }
  }

  return (
    <Card padded={false} className="mt-6">
      <CardHeader
        title="Admin console access"
        description="Summary of tenant operators with console sign-in. Grant access, features, and in-app roles from Manage access on each roster row."
        action={
          <Button size="sm" onClick={() => setOpen(true)} disabled={applications.length === 0}>
            + Grant access
          </Button>
        }
      />
      <Table>
        <THead>
          <tr>
            <TH>User</TH>
            <TH>Role</TH>
            <TH>Scope</TH>
            <TH />
          </tr>
        </THead>
        <TBody>
          {grouped.map((group) => (
            <TR key={group.userId}>
              <TD>
                <p className="font-medium">{group.displayName}</p>
                <p className="text-xs text-faint">{group.email}</p>
              </TD>
              <TD>
                <Badge tone="info">{consoleRoleLabels(group.entries).join(", ")}</Badge>
              </TD>
              <TD className="text-sm text-soft">{scopeLabel(group.entries)}</TD>
              <TD>
                <div className="flex flex-col items-end gap-1">
                  {group.entries
                    .filter((entry) => canManageSuperAdmins || entry.roleType !== "TENANT_SUPER_ADMIN")
                    .map((entry) => (
                    <Button
                      key={entry.id}
                      variant="ghost"
                      size="sm"
                      disabled={busy === entry.id}
                      onClick={() => void handleRevoke(entry.id)}
                    >
                      Remove
                      {group.entries.length > 1
                        ? ` (${entry.applicationName ?? CONSOLE_ROLE_LABELS[entry.roleType]})`
                        : ""}
                    </Button>
                  ))}
                </div>
              </TD>
            </TR>
          ))}
          {grouped.length === 0 && (
            <TR>
              <TD colSpan={4} className="py-8 text-center text-sm text-faint">
                No admin console access granted yet.
              </TD>
            </TR>
          )}
        </TBody>
      </Table>

      {open && (
        <div className="fixed inset-0 z-[9999] flex items-start justify-center overflow-y-auto bg-black/50 p-4 sm:p-8">
          <div
            role="dialog"
            aria-modal="true"
            className="relative my-8 w-full max-w-lg rounded-2xl border border-ui bg-[var(--ui-surface)] shadow-2xl"
          >
            <div className="border-b border-ui px-5 py-4">
              <h2 className="text-base font-semibold text-ui">Grant admin console access</h2>
              <p className="mt-0.5 text-xs text-muted">
                Application Admin — one app. Tenant Admin — assigned apps only. Tenant Super Admin —
                all apps in this tenant.
              </p>
            </div>
            <div className="space-y-4 px-5 py-5">
              <FieldRow label="User" hint="Pick an application member; they are added to the tenant roster when access is granted.">
                <Select value={userId} onChange={(e) => setUserId(e.target.value)} className="w-full">
                  <option value="">Select user…</option>
                  {grantableUsers.map((u) => (
                    <option key={u.id} value={u.id}>
                      {u.label} ({u.email})
                    </option>
                  ))}
                </Select>
              </FieldRow>
              <FieldRow label="Role">
                <Select
                  value={roleType}
                  onChange={(e) => setRoleType(e.target.value as AdminConsoleRoleType)}
                  className="w-full"
                >
                  <option value="APPLICATION_ADMIN">Application Admin</option>
                  <option value="TENANT_ADMIN">Tenant Admin</option>
                  {canManageSuperAdmins && <option value="TENANT_SUPER_ADMIN">Tenant Super Admin</option>}
                </Select>
              </FieldRow>
              {needsApp && (
                <FieldRow label="Application">
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
              )}
            </div>
            <div className="flex justify-end gap-2 border-t border-ui px-5 py-4">
              <Button variant="secondary" onClick={() => setOpen(false)} disabled={submitting}>
                Cancel
              </Button>
              <Button onClick={() => void handleGrant()} disabled={submitting}>
                {submitting ? "Granting…" : "Grant access"}
              </Button>
            </div>
          </div>
        </div>
      )}
    </Card>
  );
}
