"use client";

import { createContext, useCallback, useContext, useMemo, useRef, useState, type ReactNode } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { UserDetailPanel } from "@/components/users/UserDetailPanel";
import { UserImportExportMenu } from "@/components/users/UserImportExportMenu";
import { ActiveFilterChips, UserFilters, type FilterSection } from "@/components/users/UserFilters";
import { UserFormModal } from "@/components/forms/UserFormModal";
import Link from "next/link";
import { fetchUserDirectorySettings, type UserDirectorySettings } from "@/lib/api/user-directory";
import { listUsers } from "@/lib/api/users";
import {
  filterUsersForOperatorList,
  operatorListViewerFromContext,
} from "@/lib/operator-list-visibility";
import { useAdminContext } from "@/components/AdminContextProvider";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { Input } from "@/components/ui/Field";
import { initials } from "@/lib/format";
import { statusTone } from "@/lib/status";
import type { Role, Tenant, User } from "@/lib/types";
import { LocalTime } from "@/components/ui/LocalTime";

type UsersRefreshHandler = (userId?: string) => void;

const UsersWorkspaceRefreshContext = createContext<UsersRefreshHandler | null>(null);
const UsersWorkspaceRegisterContext = createContext<((fn: UsersRefreshHandler) => void) | null>(
  null,
);

export function UsersWorkspaceProvider({ children }: { children: ReactNode }) {
  const handlerRef = useRef<UsersRefreshHandler>(() => {});
  const refreshUsers = useCallback((userId?: string) => {
    handlerRef.current(userId);
  }, []);
  const registerRefreshHandler = useCallback((fn: UsersRefreshHandler) => {
    handlerRef.current = fn;
  }, []);

  return (
    <UsersWorkspaceRegisterContext.Provider value={registerRefreshHandler}>
      <UsersWorkspaceRefreshContext.Provider value={refreshUsers}>
        {children}
      </UsersWorkspaceRefreshContext.Provider>
    </UsersWorkspaceRegisterContext.Provider>
  );
}

export function UsersWorkspaceHeaderActions({
  tenants,
  roles,
  tenantId,
  applicationId,
  directory,
}: {
  tenants: Tenant[];
  roles: Role[];
  tenantId: string;
  applicationId: string;
  directory?: UserDirectorySettings | null;
}) {
  const refreshUsers = useContext(UsersWorkspaceRefreshContext);

  function onUserCreated(userId: string) {
    refreshUsers?.(userId);
  }

  return (
    <div className="flex flex-wrap items-center gap-2">
      <UserImportExportMenu applicationId={applicationId} directory={directory ?? null} />
      <UserFormModal
        tenants={tenants}
        roles={roles}
        tenantId={tenantId}
        applicationId={applicationId}
        lockToApp
        triggerLabel="+ Invite user"
        onCreated={onUserCreated}
      />
    </div>
  );
}

type CreatedSort = "asc" | "desc";

const USER_STATUSES = ["active", "invited", "suspended", "disabled"] as const;

export function UsersWorkspace({
  users: initialUsers,
  roles,
  tenants,
  tenantId,
  applicationId,
  appName,
  tenantName,
}: {
  users: User[];
  roles: Role[];
  tenants: Tenant[];
  tenantId: string;
  applicationId: string;
  appName: string;
  tenantName: string;
}) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const adminContext = useAdminContext();
  const listViewer = useMemo(
    () => operatorListViewerFromContext(adminContext),
    [adminContext],
  );
  const registerRefreshHandler = useContext(UsersWorkspaceRegisterContext);
  const selectedId = searchParams.get("user");
  const [users, setUsers] = useState(initialUsers);
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState<string[]>([]);
  const [roleFilter, setRoleFilter] = useState<string[]>([]);
  const [createdSort, setCreatedSort] = useState<CreatedSort>("desc");
  const [directory, setDirectory] = useState<UserDirectorySettings | null>(null);

  const reloadUsers = useCallback(async () => {
    const rows = await listUsers(tenantId, applicationId);
    const visible = filterUsersForOperatorList(rows, listViewer);
    setUsers(visible);
    return visible;
  }, [tenantId, applicationId, listViewer]);

  const openUser = useCallback(
    (id: string) => {
      const params = new URLSearchParams(searchParams.toString());
      params.set("user", id);
      router.push(`${pathname}?${params.toString()}`, { scroll: false });
    },
    [pathname, router, searchParams],
  );

  const handleUserCreated = useCallback(
    (userId?: string) => {
      void reloadUsers().then((rows) => {
        if (userId && rows.some((u) => u.id === userId)) {
          openUser(userId);
        }
      });
      router.refresh();
    },
    [reloadUsers, openUser, router],
  );

  useDeferredEffect(() => {
    setUsers(filterUsersForOperatorList(initialUsers, listViewer));
  }, [initialUsers, listViewer]);

  useDeferredEffect(() => {
    let cancelled = false;
    void listUsers(tenantId, applicationId)
      .then((rows) => {
        if (!cancelled) setUsers(filterUsersForOperatorList(rows, listViewer));
      })
      .catch(() => {
        /* keep server-provided list */
      });
    return () => {
      cancelled = true;
    };
  }, [tenantId, applicationId, listViewer]);

  useDeferredEffect(() => {
    registerRefreshHandler?.(handleUserCreated);
    return () => registerRefreshHandler?.(() => {});
  }, [registerRefreshHandler, handleUserCreated]);

  useDeferredEffect(() => {
    fetchUserDirectorySettings(applicationId)
      .then(setDirectory)
      .catch(() => setDirectory(null));
  }, [applicationId]);

  useDeferredEffect(() => {
    try {
      const created = sessionStorage.getItem("users:lastCreated");
      if (created && users.some((u) => u.id === created) && selectedId !== created) {
        const params = new URLSearchParams(searchParams.toString());
        params.set("user", created);
        router.replace(`${pathname}?${params.toString()}`, { scroll: false });
        sessionStorage.removeItem("users:lastCreated");
      }
    } catch {
      /* ignore */
    }
  }, [users, selectedId, searchParams, pathname, router]);

  useDeferredEffect(() => {
    if (selectedId && !users.some((u) => u.id === selectedId)) {
      let pendingCreated: string | null = null;
      try {
        pendingCreated = sessionStorage.getItem("users:lastCreated");
      } catch {
        /* ignore */
      }
      if (pendingCreated === selectedId) return;
      const params = new URLSearchParams(searchParams.toString());
      params.delete("user");
      const qs = params.toString();
      router.replace(qs ? `${pathname}?${qs}` : pathname, { scroll: false });
    }
  }, [selectedId, users, searchParams, pathname, router]);

  const hasRole = useCallback(
    (u: User, roleId: string) =>
      u.roleIds.includes(roleId) ||
      (u.roleNames ?? []).includes(roles.find((r) => r.id === roleId)?.name ?? ""),
    [roles],
  );

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return users.filter((u) => {
      if (statusFilter.length > 0 && !statusFilter.includes(u.status)) return false;
      if (roleFilter.length > 0 && !roleFilter.some((roleId) => hasRole(u, roleId))) return false;
      if (!q) return true;
      const name = `${u.firstName} ${u.lastName}`.toLowerCase();
      const roleText = (u.roleNames ?? []).join(" ").toLowerCase();
      return (
        name.includes(q) ||
        u.email.toLowerCase().includes(q) ||
        u.username.toLowerCase().includes(q) ||
        roleText.includes(q)
      );
    });
  }, [users, query, statusFilter, roleFilter, hasRole]);

  const filterSections: FilterSection[] = useMemo(
    () => [
      {
        id: "status",
        label: "Status",
        options: USER_STATUSES.map((s) => ({
          value: s,
          label: s.charAt(0).toUpperCase() + s.slice(1),
          count: users.filter((u) => u.status === s).length,
        })),
        selected: statusFilter,
        onChange: setStatusFilter,
      },
      ...(roles.length > 0
        ? [
            {
              id: "role",
              label: "Role",
              options: roles.map((r) => ({
                value: r.id,
                label: r.name,
                count: users.filter((u) => hasRole(u, r.id)).length,
              })),
              selected: roleFilter,
              onChange: setRoleFilter,
            },
          ]
        : []),
    ],
    [users, roles, statusFilter, roleFilter, hasRole],
  );

  const displayed = useMemo(() => {
    const rows = [...filtered];
    rows.sort((a, b) => {
      const aTime = a.createdAt ? new Date(a.createdAt).getTime() : 0;
      const bTime = b.createdAt ? new Date(b.createdAt).getTime() : 0;
      return createdSort === "desc" ? bTime - aTime : aTime - bTime;
    });
    return rows;
  }, [filtered, createdSort]);

  const selected = users.find((u) => u.id === selectedId);

  function closeUser() {
    const params = new URLSearchParams(searchParams.toString());
    params.delete("user");
    const qs = params.toString();
    router.replace(qs ? `${pathname}?${qs}` : pathname, { scroll: false });
  }

  const formProps = {
    tenants,
    roles,
    tenantId,
    applicationId,
    lockToApp: true as const,
    onCreated: handleUserCreated,
  };

  const activeCount = users.filter((u) => u.status === "active").length;
  const mfaCount = users.filter((u) => u.mfaFactors.length > 0).length;

  if (selected) {
    return (
      <div className="flex min-h-0 flex-1 flex-col">
        <UserDetailPanel
          userSummary={selected}
          applicationId={applicationId}
          tenantId={tenantId}
          tenantName={tenantName}
          appName={appName}
          roles={roles}
          tenants={tenants}
          onBack={closeUser}
          onUserChanged={() => handleUserCreated()}
          onDeleted={() => {
            closeUser();
            handleUserCreated();
          }}
        />
      </div>
    );
  }

  return (
    <div className="space-y-5">
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <SummaryCard label="Members" value={users.length} />
        <SummaryCard label="Active" value={activeCount} />
        <SummaryCard label="With MFA" value={mfaCount} />
        <SummaryCard label="Application" value={appName} isText />
      </div>

      <Card padded={false} className="flex min-h-0 flex-1 flex-col">
        <CardHeader
          title="Users"
          description={`${appName} · ${displayed.length} of ${users.length} members — select a row to manage`}
        />
        {directory && !directory.importEnabled && !directory.exportEnabled && (
          <p className="border-b border-ui px-4 py-2 text-xs text-muted">
            Import and export are off. Enable them in{" "}
            <Link href={`/app/${applicationId}/settings`} className="text-brand hover:underline">
              Settings → User directory
            </Link>
            .
          </p>
        )}
        <div className="border-b border-ui px-4 py-3">
          <div className="flex items-center gap-2">
            <div className="min-w-0 flex-1">
              <Input
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Search name, email, username, role…"
                aria-label="Search users"
              />
            </div>
            <UserFilters sections={filterSections} resultCount={displayed.length} />
          </div>
          <ActiveFilterChips sections={filterSections} />
        </div>

        <div className="flex min-h-0 flex-1 flex-col">
        {displayed.length > 0 ? (
          <>
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="border-b border-ui text-xs font-semibold uppercase tracking-wide text-faint">
                  <th className="px-4 py-3 font-semibold">User</th>
                  <th className="hidden px-4 py-3 font-semibold sm:table-cell">Username</th>
                  <th className="hidden px-4 py-3 font-semibold md:table-cell">Roles</th>
                  <th className="px-4 py-3 font-semibold">Status</th>
                  <th className="hidden px-4 py-3 font-semibold lg:table-cell">
                    <button
                      type="button"
                      onClick={() =>
                        setCreatedSort((current) => (current === "desc" ? "asc" : "desc"))
                      }
                      className="inline-flex items-center gap-1 uppercase tracking-wide text-faint transition-colors hover:text-ui"
                      aria-label={`Sort by created date, currently ${createdSort === "desc" ? "newest first" : "oldest first"}`}
                    >
                      Created
                      <span aria-hidden className="text-brand">
                        {createdSort === "desc" ? "↓" : "↑"}
                      </span>
                    </button>
                  </th>
                  <th className="px-4 py-3 font-semibold w-24" />
                </tr>
              </thead>
              <tbody className="divide-y divide-ui">
                {displayed.map((u) => {
                  const name = `${u.firstName} ${u.lastName}`.trim() || u.email;
                  return (
                    <tr
                      key={u.id}
                      className="cursor-pointer transition-colors hover:bg-ui-elevated/80"
                      onClick={() => openUser(u.id)}
                      onKeyDown={(e) => {
                        if (e.key === "Enter" || e.key === " ") {
                          e.preventDefault();
                          openUser(u.id);
                        }
                      }}
                      tabIndex={0}
                      role="button"
                      aria-label={`Open ${name}`}
                    >
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-3">
                          <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-brand-muted text-xs font-semibold text-brand">
                            {initials(name)}
                          </div>
                          <div className="min-w-0">
                            <p className="truncate font-medium text-ui">{name}</p>
                            <p className="truncate text-xs text-muted">{u.email}</p>
                          </div>
                        </div>
                      </td>
                      <td className="hidden px-4 py-3 text-muted sm:table-cell">@{u.username}</td>
                      <td className="hidden px-4 py-3 md:table-cell">
                        <div className="flex flex-wrap gap-1">
                          {(u.roleNames ?? []).length > 0 ? (
                            u.roleNames.map((roleName) => (
                              <Badge key={roleName} tone="neutral" className="text-[10px]">
                                {roleName}
                              </Badge>
                            ))
                          ) : (
                            <span className="text-xs text-faint">None</span>
                          )}
                        </div>
                      </td>
                      <td className="px-4 py-3">
                        <div className="flex flex-wrap gap-1">
                          <Badge tone={statusTone(u.status)} className="capitalize text-[10px]">
                            {u.status}
                          </Badge>
                          {!u.emailVerified && (
                            <Badge tone="warning" className="text-[10px]">
                              unverified
                            </Badge>
                          )}
                          {u.locked && (
                            <Badge tone="danger" className="text-[10px]">
                              locked
                            </Badge>
                          )}
                        </div>
                      </td>
                      <td className="hidden px-4 py-3 text-xs text-muted lg:table-cell">
                        <LocalTime value={u.createdAt} />
                      </td>
                      <td className="px-4 py-3 text-right">
                        <Button
                          type="button"
                          variant="ghost"
                          size="sm"
                          onClick={(e) => {
                            e.stopPropagation();
                            openUser(u.id);
                          }}
                        >
                          Manage →
                        </Button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          <p className="mt-auto border-t border-ui px-5 py-3 text-center text-xs text-faint">
            Click a row or <strong className="font-medium text-muted">Manage</strong> to edit profile,
            security, MFA, roles, and sign-in methods.
          </p>
          </>
        ) : (
          <div className="flex flex-1 flex-col items-center justify-center px-6 py-16 text-center">
            <p className="max-w-md text-sm text-muted">
              {users.length === 0
                ? "No users in this application yet. Invite someone or import from CSV when enabled in settings."
                : "No users match your search or filters."}
            </p>
            {users.length === 0 && (
              <div className="mt-6 flex flex-wrap justify-center gap-3">
                <UserFormModal {...formProps} triggerLabel="Invite first user" />
                <Link
                  href={`/app/${applicationId}/settings`}
                  className="inline-flex items-center rounded-lg border border-ui px-4 py-2 text-sm font-medium text-ui hover:bg-ui-elevated"
                >
                  User directory settings
                </Link>
              </div>
            )}
          </div>
        )}
        </div>
      </Card>
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
