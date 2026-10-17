"use client";

import { ActiveFilterChips, UserFilters, type FilterSection } from "@/components/users/UserFilters";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMemo, useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import {
  ADMIN_MIRROR_RBAC_ROLES,
  groupConsoleAccessByUser,
  consoleRoleLabels,
} from "@/components/tenants/console-access-utils";
import { CONSOLE_FEATURE_LABELS } from "@/lib/api/admin-console-capabilities";
import type { ConsoleAccessAssignment } from "@/lib/api/admin-console-access";
import { UserFormModal } from "@/components/forms/UserFormModal";
import { UserImportExportMenu } from "@/components/users/UserImportExportMenu";
import { TenantRosterImportDialog } from "@/components/tenant/TenantRosterImportDialog";
import { Badge } from "@/components/ui/Badge";
import { Card, CardHeader } from "@/components/ui/Card";
import { Input, Select } from "@/components/ui/Field";
import { Table, TBody, TD, TH, THead, TR } from "@/components/ui/Table";
import { useToast } from "@/components/ui/Toast";
import {
  fetchImportableUsers,
  fetchTenantWorkspace,
  grantUserApplication,
  isInvitedAccount,
  mergeTenantWorkspace,
  patchTenantWorkspaceUser,
  removeUserFromTenantRoster,
  resolveRosterConsoleFeatures,
  revokeUserApplication,
  rosterSourceLabel,
  userPermissionKeys,
  type TenantRosterSource,
  type TenantWorkspace,
  type TenantWorkspaceUser,
  type TenantWorkspaceUserPatch,
} from "@/lib/api/tenant-workspace";
import { TenantRosterAccessDialog } from "@/components/tenant/TenantRosterAccessDialog";
import { useAdminContext } from "@/components/AdminContextProvider";
import {
  canViewUserInOperatorList,
  filterUsersForOperatorList,
  operatorListViewerFromContext,
  userConsoleRoleTypes,
} from "@/lib/operator-list-visibility";
import { fetchUserDirectorySettings } from "@/lib/api/user-directory";
import { buildAppPath } from "@/lib/app-routes";
import { listRoles } from "@/lib/api/roles";
import { statusTone } from "@/lib/status";
import type { Role, Status, Tenant } from "@/lib/types";
import { LocalTime } from "@/components/ui/LocalTime";

const STATUS_OPTIONS = ["active", "invited", "suspended", "disabled"] as const;
const SOURCE_OPTIONS: { value: string; label: string }[] = [
  { value: "direct", label: "Added directly" },
  { value: "imported", label: "Imported from an app" },
  { value: "console_access", label: "Console access" },
  { value: "console_only", label: "Console only" },
];

function normalizeStatus(status: string): string {
  const s = status.toLowerCase();
  return s === "pending" ? "invited" : s;
}

function sourceTone(source: TenantRosterSource | null): "neutral" | "info" | "success" | "warning" {
  switch (source) {
    case "imported":
      return "info";
    case "direct":
      return "success";
    case "console_access":
    case "console_only":
      return "warning";
    default:
      return "neutral";
  }
}

export function TenantWorkspacePanel({
  workspace,
  tenant,
  roles,
  manageTenantId,
  useOperatorWorkspace = false,
  consoleAssignments = [],
  onMutated,
}: {
  workspace: TenantWorkspace;
  tenant: Tenant;
  roles: Role[];
  manageTenantId?: string;
  /** Tenant super-admin: omit tenantId on workspace API calls (scoped to signed-in tenant). */
  useOperatorWorkspace?: boolean;
  consoleAssignments?: ConsoleAccessAssignment[];
  onMutated?: () => Promise<void>;
}) {
  const router = useRouter();
  const { toast } = useToast();
  const adminContext = useAdminContext();
  const listViewer = useMemo(
    () => operatorListViewerFromContext(adminContext),
    [adminContext],
  );
  const [data, setData] = useState(workspace);
  const [query, setQuery] = useState("");
  // Filters: options within one section are or-ed, sections are and-ed (empty = no filter).
  const [statusFilter, setStatusFilter] = useState<string[]>([]);
  const [sourceFilter, setSourceFilter] = useState<string[]>([]);
  const [consoleFilter, setConsoleFilter] = useState<string[]>([]);
  const [verifiedFilter, setVerifiedFilter] = useState<string[]>([]);
  const [appFilter, setAppFilter] = useState<string[]>([]);
  const [importAppId, setImportAppId] = useState(workspace.applications[0]?.id ?? "");
  const [directory, setDirectory] = useState<Awaited<
    ReturnType<typeof fetchUserDirectorySettings>
  > | null>(null);
  const [busyKey, setBusyKey] = useState<string | null>(null);
  const [importOpen, setImportOpen] = useState(false);
  const [importable, setImportable] = useState<TenantWorkspaceUser[]>([]);
  const [importLoading, setImportLoading] = useState(false);
  const [accessUserId, setAccessUserId] = useState<string | null>(null);
  const [accessInitialTab, setAccessInitialTab] = useState<
    "console" | "features" | "roles" | "tenantRoles"
  >("console");
  const [removeBusyId, setRemoveBusyId] = useState<string | null>(null);
  const [removeTarget, setRemoveTarget] = useState<TenantWorkspaceUser | null>(null);
  const [inviteRoles, setInviteRoles] = useState<Role[]>(roles);

  const apiTenantId = useOperatorWorkspace ? undefined : manageTenantId;

  useDeferredEffect(() => {
    if (!importAppId) {
      setInviteRoles([]);
      return;
    }
    let cancelled = false;
    void listRoles({ applicationId: importAppId })
      .then((rows) => {
        if (!cancelled) setInviteRoles(rows);
      })
      .catch(() => {
        if (!cancelled) setInviteRoles([]);
      });
    return () => {
      cancelled = true;
    };
  }, [importAppId]);

  useDeferredEffect(() => {
    setData((prev) => mergeTenantWorkspace(workspace, prev));
  }, [workspace]);

  useDeferredEffect(() => {
    if (importAppId) void loadDirectory(importAppId);
  }, [importAppId]);

  const visibleConsoleAssignments = useMemo(
    () =>
      consoleAssignments.filter((assignment) =>
        canViewUserInOperatorList(
          listViewer,
          assignment.userId,
          userConsoleRoleTypes(assignment.userId, consoleAssignments),
        ),
      ),
    [consoleAssignments, listViewer],
  );

  const consoleByUser = useMemo(
    () =>
      new Map(
        groupConsoleAccessByUser(visibleConsoleAssignments).map((group) => [group.userId, group]),
      ),
    [visibleConsoleAssignments],
  );

  const tenantUsers = useMemo(
    () => filterUsersForOperatorList(data.users ?? [], listViewer, consoleAssignments),
    [data.users, listViewer, consoleAssignments],
  );
  const accessUser = accessUserId
    ? (tenantUsers.find((u) => u.id === accessUserId) ?? null)
    : null;

  useDeferredEffect(() => {
    if (accessUserId && !tenantUsers.some((u) => u.id === accessUserId)) {
      setAccessUserId(null);
    }
  }, [accessUserId, tenantUsers]);

  const filteredUsers = useMemo(() => {
    const q = query.trim().toLowerCase();
    return tenantUsers.filter((u) => {
      if (statusFilter.length > 0 && !statusFilter.includes(normalizeStatus(u.status))) return false;
      if (sourceFilter.length > 0 && !sourceFilter.includes(u.rosterSource ?? "")) return false;
      if (appFilter.length > 0 && !appFilter.some((id) => u.applicationIds.includes(id))) return false;
      if (consoleFilter.length > 0 && !consoleFilter.includes(consoleByUser.has(u.id) ? "with" : "without")) {
        return false;
      }
      if (verifiedFilter.length > 0 && !verifiedFilter.includes(u.emailVerified ? "verified" : "unverified")) {
        return false;
      }
      if (!q) return true;
      const sourceLabel = rosterSourceLabel(u.rosterSource, u.sourceApplicationName).toLowerCase();
      return (
        u.email.toLowerCase().includes(q) ||
        u.displayName.toLowerCase().includes(q) ||
        (u.roleNames ?? []).some((r) => r.toLowerCase().includes(q)) ||
        sourceLabel.includes(q) ||
        userPermissionKeys(u).some((p) => p.toLowerCase().includes(q))
      );
    });
  }, [tenantUsers, query, statusFilter, sourceFilter, appFilter, consoleFilter, verifiedFilter, consoleByUser]);

  const filterSections: FilterSection[] = useMemo(() => {
    const count = (match: (u: TenantWorkspaceUser) => boolean) => tenantUsers.filter(match).length;
    return [
      {
        id: "status",
        label: "Status",
        options: STATUS_OPTIONS.map((st) => ({
          value: st,
          label: st.charAt(0).toUpperCase() + st.slice(1),
          count: count((u) => normalizeStatus(u.status) === st),
        })),
        selected: statusFilter,
        onChange: setStatusFilter,
      },
      {
        id: "source",
        label: "Source",
        options: SOURCE_OPTIONS.map((o) => ({ ...o, count: count((u) => u.rosterSource === o.value) })),
        selected: sourceFilter,
        onChange: setSourceFilter,
      },
      ...(data.applications.length > 0
        ? [
            {
              id: "app",
              label: "Application",
              options: data.applications.map((a) => ({
                value: a.id,
                label: a.name,
                count: count((u) => u.applicationIds.includes(a.id)),
              })),
              selected: appFilter,
              onChange: setAppFilter,
            },
          ]
        : []),
      {
        id: "console",
        label: "Console access",
        options: [
          { value: "with", label: "Has console access", count: count((u) => consoleByUser.has(u.id)) },
          { value: "without", label: "No console access", count: count((u) => !consoleByUser.has(u.id)) },
        ],
        selected: consoleFilter,
        onChange: setConsoleFilter,
      },
      {
        id: "email",
        label: "Email",
        options: [
          { value: "verified", label: "Verified", count: count((u) => u.emailVerified) },
          { value: "unverified", label: "Unverified", count: count((u) => !u.emailVerified) },
        ],
        selected: verifiedFilter,
        onChange: setVerifiedFilter,
      },
    ];
  }, [tenantUsers, data.applications, consoleByUser, statusFilter, sourceFilter, appFilter, consoleFilter, verifiedFilter]);

  function applyUserPatch(patch: TenantWorkspaceUserPatch) {
    setData((prev) => ({
      ...prev,
      users: prev.users.map((u) => patchTenantWorkspaceUser(u, patch)),
    }));
  }

  async function refresh(patch?: TenantWorkspaceUserPatch) {
    if (patch) applyUserPatch(patch);
    if (onMutated) {
      await onMutated();
    }
    if (manageTenantId) {
      const next = await fetchTenantWorkspace(apiTenantId);
      setData((prev) => {
        const merged = mergeTenantWorkspace(next, prev);
        if (!patch) return merged;
        return {
          ...merged,
          users: merged.users.map((u) => patchTenantWorkspaceUser(u, patch)),
        };
      });
    } else if (!onMutated) {
      const next = await fetchTenantWorkspace(apiTenantId);
      setData(next);
      router.refresh();
    }
  }

  async function loadDirectory(appId: string) {
    if (!appId) {
      setDirectory(null);
      return;
    }
    try {
      setDirectory(await fetchUserDirectorySettings(appId));
    } catch {
      setDirectory(null);
    }
  }

  async function toggleApp(userId: string, applicationId: string, hasAccess: boolean) {
    const key = `${userId}:${applicationId}`;
    setBusyKey(key);
    try {
      if (hasAccess) {
        await revokeUserApplication(userId, applicationId, apiTenantId);
        toast("Application access removed", "success");
      } else {
        await grantUserApplication(userId, applicationId, apiTenantId);
        toast("Application access granted", "success");
      }
      await refresh();
    } catch (e) {
      toast(e instanceof Error ? e.message : "Could not update access", "error");
    } finally {
      setBusyKey(null);
    }
  }

  async function openImportDialog() {
    if (!importAppId || !manageTenantId) return;
    setImportOpen(true);
    setImportLoading(true);
    try {
      setImportable(await fetchImportableUsers(importAppId, apiTenantId));
    } catch (e) {
      toast(e instanceof Error ? e.message : "Could not load importable users", "error");
      setImportable([]);
    } finally {
      setImportLoading(false);
    }
  }

  async function reloadImportable() {
    if (!importAppId || !manageTenantId) return;
    setImportLoading(true);
    try {
      setImportable(await fetchImportableUsers(importAppId, apiTenantId));
      await refresh();
    } catch (e) {
      toast(e instanceof Error ? e.message : "Could not refresh import list", "error");
    } finally {
      setImportLoading(false);
    }
  }

  async function confirmRemoveFromRoster() {
    if (!manageTenantId || !removeTarget) return;
    setRemoveBusyId(removeTarget.id);
    try {
      await removeUserFromTenantRoster(removeTarget.id, apiTenantId);
      toast("Removed from tenant roster", "success");
      setRemoveTarget(null);
      await refresh();
      if (importOpen && importAppId) {
        setImportable(await fetchImportableUsers(importAppId, apiTenantId));
      }
    } catch (e) {
      toast(e instanceof Error ? e.message : "Could not remove user", "error");
    } finally {
      setRemoveBusyId(null);
    }
  }

  const showOverview = !manageTenantId;
  const importAppName =
    data.applications.find((a) => a.id === importAppId)?.name ?? "Application";

  return (
    <div className="space-y-6">
      {showOverview && (
        <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
          <Card className="p-4">
            <p className="text-xs text-faint">Users</p>
            <p className="text-2xl font-semibold">{data.userCount}</p>
          </Card>
          <Card className="p-4">
            <p className="text-xs text-faint">Applications</p>
            <p className="text-2xl font-semibold">{data.applicationCount}</p>
          </Card>
          <Card className="p-4">
            <p className="text-xs text-faint">Plan</p>
            <p className="text-2xl font-semibold capitalize">{data.plan}</p>
          </Card>
          <Card className="p-4">
            <p className="text-xs text-faint">Status</p>
            <Badge tone={statusTone(data.status as Status)} dot className="mt-1 capitalize">
              {data.status}
            </Badge>
          </Card>
        </div>
      )}

      <Card padded={false}>
        <CardHeader title="Applications" description="Applications registered in this tenant" />
        <Table>
          <THead>
            <tr>
              <TH>Application</TH>
              <TH>Status</TH>
              <TH>Members</TH>
              <TH />
            </tr>
          </THead>
          <TBody>
            {data.applications.map((app) => (
              <TR key={app.id}>
                <TD>
                  <p className="font-medium">{app.name}</p>
                  <p className="font-mono text-xs text-faint">{app.slug}</p>
                </TD>
                <TD>
                  <Badge tone={statusTone(app.status as Status)} dot className="capitalize">
                    {app.status}
                  </Badge>
                </TD>
                <TD>{app.userCount}</TD>
                <TD>
                  <div className="flex flex-wrap items-center justify-end gap-4">
                    <Link href={buildAppPath(app.id, "settings")} className="text-sm text-muted hover:text-ui">
                      Settings
                    </Link>
                    <Link href={buildAppPath(app.id, "users")} className="text-sm link-brand">
                      Open console →
                    </Link>
                  </div>
                </TD>
              </TR>
            ))}
          </TBody>
        </Table>
      </Card>

      <Card padded={false}>
        <CardHeader
          title="Tenant roster"
          description={`${filteredUsers.length} of ${tenantUsers.length} shown · import Tenant Admin operators from an app for tenant-wide console access; end users stay in application user management`}
          action={
            <UserFormModal
              tenants={[tenant]}
              roles={roles}
              tenantId={tenant.id}
              defaultStatus="active"
              triggerLabel="+ Add to roster"
              triggerVariant="secondary"
              triggerSize="sm"
              onCreated={() => void refresh()}
            />
          }
        />

        {manageTenantId && data.applications.length > 0 && (
          <div className="flex flex-wrap items-center gap-2 border-b border-ui bg-ui-elevated/40 px-5 py-3">
            <label htmlFor="roster-app" className="text-xs font-medium text-muted">
              Application
            </label>
            <Select
              id="roster-app"
              value={importAppId}
              onChange={(e) => {
                const id = e.target.value;
                setImportAppId(id);
                void loadDirectory(id);
              }}
              className="h-9 min-w-[10rem] text-sm"
            >
              {data.applications.map((a) => (
                <option key={a.id} value={a.id}>
                  {a.name}
                </option>
              ))}
            </Select>
            <button
              type="button"
              onClick={() => void openImportDialog()}
              className="inline-flex h-9 items-center rounded-lg border border-ui px-3 text-sm font-medium text-ui hover:bg-ui-elevated"
            >
              Import from application
            </button>
            {importAppId && (
              <UserFormModal
                tenants={[tenant]}
                roles={inviteRoles}
                tenantId={tenant.id}
                applicationId={importAppId}
                lockToApp
                triggerLabel="+ Invite Tenant Admin"
                triggerVariant="secondary"
                triggerSize="sm"
                onCreated={() => void openImportDialog()}
              />
            )}
            {importAppId && directory && (
              <UserImportExportMenu applicationId={importAppId} directory={directory} />
            )}
          </div>
        )}

        <div className="border-b border-ui px-5 py-3">
          <div className="flex items-center gap-2">
            <div className="min-w-0 max-w-xl flex-1">
              <Input
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Search users, roles, permissions, source…"
                aria-label="Search the tenant roster"
              />
            </div>
            <UserFilters sections={filterSections} title="Filter the tenant roster" resultCount={filteredUsers.length} />
          </div>
          <ActiveFilterChips sections={filterSections} />
        </div>

        <Table>
          <THead>
            <tr>
              <TH>User</TH>
              <TH>Origin</TH>
              <TH>Roles & permissions</TH>
              <TH>App membership</TH>
              <TH />
            </tr>
          </THead>
          <TBody>
            {filteredUsers.map((user) => {
              const consoleGroup = consoleByUser.get(user.id);
              const consoleRoles = consoleGroup ? consoleRoleLabels(consoleGroup.entries) : [];
              const appRoles = (user.roleNames ?? []).filter(
                (r) => !ADMIN_MIRROR_RBAC_ROLES.has(r),
              );
              const allPermissions = userPermissionKeys(user);
              const consoleFeatureKeys = consoleGroup
                ? resolveRosterConsoleFeatures(
                    user,
                    consoleGroup.entries.map((e) => e.roleType),
                  )
                : [];
              const consoleOverrideCount = user.consoleFeatureOverrides?.length ?? 0;
              return (
                <TR key={user.id}>
                  <TD>
                    <p className="font-medium">{user.displayName}</p>
                    <p className="text-xs text-faint">{user.email}</p>
                    <div className="mt-1 flex flex-wrap items-center gap-1">
                      <Badge
                        tone={statusTone(normalizeStatus(user.status) as Status)}
                        dot
                        className="capitalize"
                      >
                        {normalizeStatus(user.status)}
                      </Badge>
                      {isInvitedAccount(user.status) && (
                        <Badge tone="warning">Invite pending</Badge>
                      )}
                      {user.emailVerified ? (
                        <Badge tone="success">Verified</Badge>
                      ) : (
                        <Badge tone="neutral">Unverified</Badge>
                      )}
                    </div>
                  </TD>
                  <TD>
                    <Badge tone={sourceTone(user.rosterSource)} className="capitalize">
                      {rosterSourceLabel(user.rosterSource, user.sourceApplicationName)}
                    </Badge>
                    {user.rosterSource === "imported" && user.sourceApplicationId && (
                      <p className="mt-1 text-xs text-soft">
                        From{" "}
                        <Link
                          href={buildAppPath(user.sourceApplicationId, "users")}
                          className="link-brand"
                        >
                          {user.sourceApplicationName ?? "application"}
                        </Link>
                      </p>
                    )}
                    {user.rosterAddedAt && (
                      <p className="mt-1 text-[10px] text-faint">
                        Added <LocalTime value={user.rosterAddedAt} />
                      </p>
                    )}
                    {user.rosterAddedByLabel && (
                      <p className="text-[10px] text-faint">By {user.rosterAddedByLabel}</p>
                    )}
                    {!user.onRoster && (
                      <p className="mt-1 text-[10px] text-faint">Not on roster table</p>
                    )}
                  </TD>
                  <TD>
                    <div className="space-y-2">
                      {consoleRoles.length > 0 && (
                        <div>
                          <p className="mb-1 text-[10px] font-medium uppercase tracking-wide text-faint">
                            Admin console
                          </p>
                          <div className="flex flex-wrap gap-1">
                            {consoleRoles.map((r) => (
                              <Badge key={r} tone="info">
                                {r}
                              </Badge>
                            ))}
                          </div>
                          {consoleFeatureKeys.length > 0 && (
                            <div className="mt-1.5 flex flex-wrap items-center gap-1">
                              {consoleFeatureKeys.slice(0, 6).map((f) => {
                                const override = user.consoleFeatureOverrides?.find(
                                  (o) => o.featureKey === f,
                                );
                                return (
                                  <span
                                    key={f}
                                    className={`rounded-md px-1.5 py-0.5 text-[10px] ${
                                      override?.effect === "GRANT"
                                        ? "bg-green-100 text-green-800 dark:bg-green-950 dark:text-green-300"
                                        : override?.effect === "DENY"
                                          ? "bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-300"
                                          : "bg-ui-elevated text-soft"
                                    }`}
                                    title={
                                      override
                                        ? `${override.effect === "GRANT" ? "Granted" : "Denied"} override`
                                        : "Role default"
                                    }
                                  >
                                    {CONSOLE_FEATURE_LABELS[f] ?? f}
                                  </span>
                                );
                              })}
                              {consoleFeatureKeys.length > 6 && (
                                <span className="text-[10px] text-faint">
                                  +{consoleFeatureKeys.length - 6}
                                </span>
                              )}
                              {consoleOverrideCount > 0 && (
                                <span className="text-[10px] text-faint">
                                  · {consoleOverrideCount} override
                                  {consoleOverrideCount === 1 ? "" : "s"}
                                </span>
                              )}
                            </div>
                          )}
                        </div>
                      )}
                      {(user.tenantGovernanceRoleNames?.length ?? 0) > 0 && (
                        <div>
                          <p className="mb-1 text-[10px] font-medium uppercase tracking-wide text-faint">
                            Tenant roles
                          </p>
                          <div className="flex flex-wrap gap-1">
                            {user.tenantGovernanceRoleNames.map((r) => (
                              <Badge key={r} tone="neutral">
                                {r}
                              </Badge>
                            ))}
                          </div>
                        </div>
                      )}
                      <div>
                        <p className="mb-1 text-[10px] font-medium uppercase tracking-wide text-faint">
                          In-app roles
                        </p>
                        <div className="flex flex-wrap gap-1">
                          {appRoles.length > 0 ? (
                            appRoles.map((r) => (
                              <Badge key={r} tone="neutral">
                                {r}
                              </Badge>
                            ))
                          ) : (
                            <span className="text-xs text-faint">—</span>
                          )}
                        </div>
                      </div>
                      {allPermissions.length > 0 && (
                        <div>
                          <p className="mb-1 text-[10px] font-medium uppercase tracking-wide text-faint">
                            Permissions
                          </p>
                          <div className="flex flex-wrap gap-1">
                            {allPermissions.slice(0, 8).map((p) => (
                              <span
                                key={p}
                                className="rounded-md bg-ui-elevated px-1.5 py-0.5 font-mono text-[10px] text-soft"
                              >
                                {p}
                              </span>
                            ))}
                            {allPermissions.length > 8 && (
                              <span className="text-[10px] text-faint">
                                +{allPermissions.length - 8} more
                              </span>
                            )}
                          </div>
                        </div>
                      )}
                      {manageTenantId && user.onRoster && (
                        <button
                          type="button"
                          onClick={() => {
                            setAccessInitialTab(
                              consoleByUser.has(user.id) ? "features" : "console",
                            );
                            setAccessUserId(user.id);
                          }}
                          className="text-xs font-medium text-brand hover:underline"
                        >
                          {consoleByUser.has(user.id)
                            ? "Manage access →"
                            : "Grant console access →"}
                        </button>
                      )}
                    </div>
                  </TD>
                  <TD>
                    <div className="flex flex-col gap-2">
                      {data.applications.map((app) => {
                        const hasAccess = user.applicationIds.includes(app.id);
                        const key = `${user.id}:${app.id}`;
                        return (
                          <label
                            key={app.id}
                            className="flex items-center gap-2 text-sm text-soft"
                          >
                            <input
                              type="checkbox"
                              checked={hasAccess}
                              disabled={busyKey === key}
                              onChange={() => void toggleApp(user.id, app.id, hasAccess)}
                            />
                            {app.name}
                          </label>
                        );
                      })}
                    </div>
                  </TD>
                  <TD>
                    {manageTenantId && user.onRoster && (
                      <button
                        type="button"
                        disabled={removeBusyId === user.id}
                        onClick={() => setRemoveTarget(user)}
                        className="text-xs font-medium text-red-600 hover:underline disabled:opacity-60"
                      >
                        {removeBusyId === user.id ? "Removing…" : "Remove"}
                      </button>
                    )}
                  </TD>
                </TR>
              );
            })}
            {filteredUsers.length === 0 && (
              <TR>
                <TD colSpan={5} className="py-8 text-center text-sm text-faint">
                  {tenantUsers.length === 0
                    ? "No users on the tenant roster yet. Import from an application or add a tenant user."
                    : "No users match your filters."}
                </TD>
              </TR>
            )}
          </TBody>
        </Table>
      </Card>

      {removeTarget && (
        <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/50 p-4">
          <div
            role="alertdialog"
            aria-modal="true"
            className="w-full max-w-md rounded-2xl border border-ui bg-[var(--ui-surface)] p-6 shadow-2xl"
          >
            <h2 className="text-base font-semibold text-ui">Remove from tenant roster?</h2>
            <p className="mt-2 text-sm text-muted">
              <span className="font-medium text-ui">{removeTarget.displayName}</span> will be
              removed from the tenant roster
              {consoleByUser.has(removeTarget.id)
                ? " and their admin console access will be revoked"
                : ""}
              . Application memberships and in-app roles are kept. They can be imported again from
              an application afterward.
            </p>
            <div className="mt-6 flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setRemoveTarget(null)}
                className="rounded-lg border border-ui px-4 py-2 text-sm font-medium text-ui hover:bg-ui-elevated"
              >
                Cancel
              </button>
              <button
                type="button"
                disabled={removeBusyId === removeTarget.id}
                onClick={() => void confirmRemoveFromRoster()}
                className="rounded-lg bg-red-600 px-4 py-2 text-sm font-medium text-white disabled:opacity-60"
              >
                {removeBusyId === removeTarget.id ? "Removing…" : "Remove"}
              </button>
            </div>
          </div>
        </div>
      )}

      {manageTenantId && (
        <>
          <TenantRosterImportDialog
            open={importOpen}
            applicationId={importAppId}
            applicationName={importAppName}
            tenantId={manageTenantId}
            importable={importable}
            loading={importLoading}
            onClose={() => setImportOpen(false)}
            onImported={reloadImportable}
          />
          <TenantRosterAccessDialog
            open={accessUserId !== null}
            user={accessUser}
            applications={data.applications.map((a) => ({ id: a.id, name: a.name }))}
            tenantId={manageTenantId}
            initialTab={accessInitialTab}
            consoleAssignments={visibleConsoleAssignments.filter(
              (a) => a.userId === accessUserId,
            )}
            onClose={() => setAccessUserId(null)}
            onSaved={refresh}
          />
        </>
      )}
    </div>
  );
}
