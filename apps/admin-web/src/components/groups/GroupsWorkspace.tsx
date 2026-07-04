"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { useDeferredEffect } from "@/lib/react/useDeferredEffect";
import { GroupFormModal } from "@/components/groups/GroupFormModal";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { Input } from "@/components/ui/Field";
import { groupDeleteAction } from "@/lib/actions";
import { getGroup } from "@/lib/api/groups";
import { timeAgo } from "@/lib/format";
import type { RbacGroup, RbacGroupDetail, Role, User } from "@/lib/types";

export function GroupsWorkspaceHeaderActions({
  roles,
  users,
  tenantId,
  applicationId,
  onCreated,
}: {
  roles: Role[];
  users: User[];
  tenantId: string;
  applicationId: string;
  onCreated?: (id: string) => void;
}) {
  return (
    <GroupFormModal
      roles={roles}
      users={users}
      tenantId={tenantId}
      applicationId={applicationId}
      onCreated={onCreated}
      triggerLabel="+ New group"
    />
  );
}

export function GroupsWorkspace({
  groups,
  roles,
  users,
  tenantId,
  applicationId,
  appName,
  apiAvailable,
}: {
  groups: RbacGroup[];
  roles: Role[];
  users: User[];
  tenantId: string;
  applicationId: string;
  appName: string;
  apiAvailable: boolean;
}) {
  const [selectedId, setSelectedId] = useState<string | null>(groups[0]?.id ?? null);
  const [query, setQuery] = useState("");

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return groups;
    return groups.filter(
      (g) =>
        g.name.toLowerCase().includes(q) ||
        g.description.toLowerCase().includes(q),
    );
  }, [groups, query]);

  const selected = groups.find((g) => g.id === selectedId);

  return (
    <div className="space-y-5">
      {!apiAvailable && (
        <div className="rounded-lg border border-amber-500/30 bg-amber-500/10 px-4 py-3 text-sm text-amber-900 dark:text-amber-200">
          <strong>Groups API unavailable.</strong> Restart <code className="text-xs">apps/auth-server</code> after
          migration V42 to enable group management.
        </div>
      )}

      <div className="flex flex-col gap-3 rounded-xl border border-ui bg-ui-surface/60 px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
        <p className="text-sm text-muted">
          Assign <strong className="text-ui">multiple roles</strong> to a group, then add members. Effective access =
          direct user roles + all roles from groups.
        </p>
        <div className="flex gap-2 text-sm">
          <Link href={`/app/${applicationId}/roles`} className="text-brand hover:underline">
            Roles
          </Link>
          <span className="text-faint">·</span>
          <Link href={`/app/${applicationId}/permissions`} className="text-brand hover:underline">
            Permissions
          </Link>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
        <SummaryCard label="Groups" value={groups.length} />
        <SummaryCard label="Roles available" value={roles.length} />
        <SummaryCard label="Application" value={appName} isText />
      </div>

      <div className="grid grid-cols-1 gap-5 xl:grid-cols-12 xl:gap-6">
        <Card padded={false} className="xl:col-span-4 xl:sticky xl:top-4 xl:self-start">
          <CardHeader
            title="Group directory"
            description={`${appName} · role bundles`}
            action={
              apiAvailable ? (
                <GroupFormModal
                  roles={roles}
                  users={users}
                  tenantId={tenantId}
                  applicationId={applicationId}
                  onCreated={(id) => setSelectedId(id)}
                  triggerLabel="+ New"
                  triggerSize="sm"
                  triggerVariant="secondary"
                />
              ) : undefined
            }
          />
          <div className="border-b border-ui px-4 py-3">
            <Input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search groups…"
              aria-label="Search groups"
            />
          </div>
          <ul className="divide-y divide-ui">
            {filtered.map((g) => (
              <li key={g.id}>
                <button
                  type="button"
                  onClick={() => setSelectedId(g.id)}
                  className={`block w-full px-4 py-3 text-left transition-colors hover:bg-ui-elevated ${
                    selectedId === g.id ? "bg-brand-muted/50" : ""
                  }`}
                >
                  <div className="flex items-center justify-between gap-2">
                    <span className="font-medium text-ui">{g.name}</span>
                    <Badge tone="neutral">{g.memberCount} members</Badge>
                  </div>
                  <p className="mt-0.5 truncate text-xs text-muted">
                    {g.roleCount} role{g.roleCount === 1 ? "" : "s"}
                    {g.description ? ` · ${g.description}` : ""}
                  </p>
                </button>
              </li>
            ))}
          </ul>
          {filtered.length === 0 && (
            <p className="px-4 py-10 text-center text-sm text-muted">
              {groups.length === 0 ? "No groups yet." : "No groups match your search."}
            </p>
          )}
        </Card>

        <div className="xl:col-span-8">
          {selected && apiAvailable ? (
            <GroupDetailPanel
              groupId={selected.id}
              groupSummary={selected}
              applicationId={applicationId}
              tenantId={tenantId}
              roles={roles}
              users={users}
              onDeleted={() => setSelectedId(null)}
            />
          ) : (
            <Card className="flex min-h-80 items-center justify-center p-8">
              <p className="text-center text-sm text-muted">
                {apiAvailable ? "Select a group to manage roles and members." : "Groups require auth-server V42+."}
              </p>
            </Card>
          )}
        </div>
      </div>
    </div>
  );
}

type Tab = "overview" | "roles" | "members";

function GroupDetailPanel({
  groupId,
  groupSummary,
  applicationId,
  tenantId,
  roles,
  users,
  onDeleted,
}: {
  groupId: string;
  groupSummary: RbacGroup;
  applicationId: string;
  tenantId: string;
  roles: Role[];
  users: User[];
  onDeleted: () => void;
}) {
  const [detail, setDetail] = useState<RbacGroupDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [tab, setTab] = useState<Tab>("overview");

  useDeferredEffect(() => {
    setLoading(true);
    getGroup(applicationId, groupId)
      .then(setDetail)
      .catch(() =>
        setDetail({
          ...groupSummary,
          roleIds: groupSummary.roleIds ?? [],
          memberUserIds: groupSummary.memberUserIds ?? [],
          roles: [],
          members: [],
        }),
      )
      .finally(() => setLoading(false));
  }, [groupId, applicationId, groupSummary]);

  if (loading || !detail) {
    return (
      <Card className="flex min-h-96 items-center justify-center p-8">
        <p className="text-sm text-muted">Loading group…</p>
      </Card>
    );
  }

  const tabs: { id: Tab; label: string; count?: number }[] = [
    { id: "overview", label: "Overview" },
    { id: "roles", label: "Roles", count: detail.roleCount },
    { id: "members", label: "Members", count: detail.memberCount },
  ];

  return (
    <Card padded={false} className="flex min-h-[28rem] flex-col overflow-hidden">
      <div className="border-b border-ui bg-ui-surface/80 px-5 py-4">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="min-w-0 flex-1">
            <h2 className="truncate text-lg font-semibold text-ui">{detail.name}</h2>
            <p className="mt-1 line-clamp-2 text-sm text-muted">{detail.description || "No description"}</p>
          </div>
          <div className="flex shrink-0 flex-wrap gap-2">
            <GroupFormModal
              group={detail}
              roles={roles}
              users={users}
              tenantId={tenantId}
              applicationId={applicationId}
              triggerLabel="Edit"
              triggerVariant="secondary"
              triggerSize="sm"
            />
            <ConfirmDialog
              action={async (fd) => {
                fd.set("applicationId", applicationId);
                await groupDeleteAction(fd);
                onDeleted();
              }}
              id={detail.id}
              triggerLabel="Delete"
              triggerVariant="danger"
              triggerSize="sm"
              title="Delete group"
              message={`Remove "${detail.name}"? Members lose group-derived roles immediately.`}
              confirmLabel="Delete"
            />
          </div>
        </div>

        <nav className="-mb-px mt-4 flex gap-1 border-b border-ui" aria-label="Group sections">
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

      <div className="min-h-0 flex-1 overflow-y-auto p-5">
        {tab === "overview" && (
          <div className="space-y-4">
            <div className="grid grid-cols-2 gap-px rounded-lg border border-ui bg-ui sm:grid-cols-3">
              <Stat label="Roles assigned" value={String(detail.roleCount)} />
              <Stat label="Members" value={String(detail.memberCount)} />
              <Stat
                label="Created"
                value={detail.createdAt ? timeAgo(detail.createdAt) : "—"}
              />
            </div>
            <p className="text-xs text-muted">
              Group membership grants all linked roles without creating individual{" "}
              <code className="text-[10px]">user_role</code> rows. Use{" "}
              <Link href={`/app/${applicationId}/users`} className="text-brand hover:underline">
                Users
              </Link>{" "}
              for direct role assignment.
            </p>
          </div>
        )}

        {tab === "roles" && (
          <div>
            {detail.roles.length === 0 ? (
              <p className="text-sm text-muted">No roles assigned. Click Edit to add roles.</p>
            ) : (
              <ul className="flex flex-wrap gap-2">
                {detail.roles.map((r) => (
                  <li key={r.id}>
                    <Link
                      href={`/app/${applicationId}/roles?roleId=${r.id}`}
                      className="rounded-full border border-ui bg-ui-elevated px-3 py-1 text-sm text-ui hover:border-brand"
                    >
                      {r.name}
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </div>
        )}

        {tab === "members" && (
          <div>
            {detail.members.length === 0 ? (
              <div className="rounded-xl border border-dashed border-ui px-6 py-10 text-center">
                <p className="text-sm text-muted">No members in this group.</p>
                <Button variant="secondary" size="sm" className="mt-3" disabled>
                  Use Edit to add members
                </Button>
              </div>
            ) : (
              <ul className="divide-y divide-ui rounded-xl border border-ui">
                {detail.members.map((m) => (
                  <li key={m.id} className="flex items-center justify-between gap-4 px-4 py-3">
                    <div>
                      <Link href={`/users/${m.id}`} className="font-medium text-brand hover:underline">
                        {m.displayName || m.email}
                      </Link>
                      <p className="text-xs text-muted">{m.email}</p>
                    </div>
                    <Badge tone="neutral">{m.status}</Badge>
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

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="bg-ui-surface px-4 py-3">
      <p className="text-[10px] font-semibold uppercase tracking-wide text-faint">{label}</p>
      <p className="mt-0.5 text-lg font-semibold text-ui">{value}</p>
    </div>
  );
}
